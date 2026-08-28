/**
 * Azhand Release Bot - Cloudflare Worker
 * Version: 0.1.0
 *
 * Responsibilities:
 * - Telegram webhook
 * - Admin allow-list
 * - Receive release ZIP (Telegram Bot API download limit: 20 MB)
 * - Store ZIP in R2
 * - Record release metadata in D1
 * - Trigger GitHub Actions via repository_dispatch
 * - Securely expose the stored ZIP to GitHub Actions
 *
 * IMPORTANT:
 * Never hardcode tokens in this file.
 * Configure sensitive values as Cloudflare Worker Secrets.
 */

const GITHUB_API_VERSION = "2026-03-10";
const DEFAULT_EVENT_TYPE = "azhand_zip_release";
const DEFAULT_MAX_ZIP_BYTES = 20_000_000;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        service: env.PROJECT_NAME || "Azhand Release Bot",
        version: env.BOT_VERSION || "0.1.0",
        d1: Boolean(env.DB),
        r2: Boolean(env.RELEASES_BUCKET),
        githubConfigured: Boolean(env.GITHUB_OWNER && env.GITHUB_REPO),
      });
    }

    if (request.method === "POST" && url.pathname === "/admin/setup-webhook") {
      if (!authorizedBearer(request, env.SETUP_TOKEN)) {
        return json({ ok: false, error: "unauthorized" }, 401);
      }

      const webhookUrl = `${url.origin}/telegram/webhook`;
      const result = await telegramApi(env, "setWebhook", {
        url: webhookUrl,
        secret_token: env.TELEGRAM_WEBHOOK_SECRET,
        allowed_updates: ["message"],
        drop_pending_updates: true,
      });

      return json({
        ok: true,
        webhook: webhookUrl,
        telegram: result,
      });
    }

    if (request.method === "POST" && url.pathname === "/telegram/webhook") {
      if (!validTelegramWebhook(request, env)) {
        return json({ ok: false, error: "invalid telegram secret" }, 403);
      }

      let update;
      try {
        update = await request.json();
      } catch {
        return json({ ok: false, error: "invalid json" }, 400);
      }

      ctx.waitUntil(handleTelegramUpdate(update, env, url.origin));
      return json({ ok: true });
    }

    if (request.method === "GET" && url.pathname.startsWith("/release-file/")) {
      if (!authorizedBearer(request, env.RELEASE_DOWNLOAD_SECRET)) {
        return json({ ok: false, error: "unauthorized" }, 401);
      }

      const encodedKey = url.pathname.slice("/release-file/".length);
      let key;
      try {
        key = decodeURIComponent(encodedKey);
      } catch {
        return json({ ok: false, error: "bad key" }, 400);
      }

      if (!key || key.includes("..")) {
        return json({ ok: false, error: "bad key" }, 400);
      }

      const object = await env.RELEASES_BUCKET.get(key);
      if (!object) {
        return json({ ok: false, error: "not found" }, 404);
      }

      const headers = new Headers();
      object.writeHttpMetadata(headers);
      headers.set("etag", object.httpEtag);
      headers.set("cache-control", "private, no-store");

      return new Response(object.body, { headers });
    }

    return new Response("Azhand Release Bot", {
      status: 200,
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  },
};

async function handleTelegramUpdate(update, env, origin) {
  const message = update?.message;
  if (!message) return;

  const chatId = message.chat?.id;
  const userId = message.from?.id;
  if (!chatId || !userId) return;

  if (!isAdmin(userId, env)) {
    await sendMessage(env, chatId, "⛔️ دسترسی غیرمجاز.");
    return;
  }

  const text = (message.text || "").trim();

  if (text === "/start" || text === "/help") {
    await sendMessage(
      env,
      chatId,
      [
        "🏢 Azhand Release Bot",
        "",
        "دستورات:",
        "/status — آخرین وضعیت نسخه‌ها",
        "/version — آخرین نسخه ثبت‌شده",
        "/release 0.1.0 — اجرای Release بدون ZIP",
        "",
        "برای انتشار ZIP:",
        "فایل .zip را همراه Caption نسخه بفرست:",
        "v0.1.0",
        "",
        "حداکثر اندازه ZIP از طریق Telegram Bot API: 20MB",
      ].join("\n")
    );
    return;
  }

  if (text === "/status") {
    await sendStatus(env, chatId);
    return;
  }

  if (text === "/version") {
    await sendLatestVersion(env, chatId);
    return;
  }

  if (text.startsWith("/release ")) {
    const version = normalizeVersion(text.slice("/release ".length));
    if (!version) {
      await sendMessage(env, chatId, "❌ نسخه معتبر نیست. نمونه: /release 0.1.0");
      return;
    }

    const eventType = env.RELEASE_EVENT_TYPE || DEFAULT_EVENT_TYPE;
    try {
      await githubDispatch(env, eventType, {
        version,
        source: "telegram-command",
        telegram_user_id: String(userId),
      });
      await sendMessage(env, chatId, `🚀 درخواست انتشار v${version} برای GitHub ارسال شد.`);
    } catch (error) {
      await sendMessage(env, chatId, `🚨 ارسال به GitHub ناموفق بود:\n${safeError(error)}`);
    }
    return;
  }

  if (message.document) {
    await handleZipDocument(message, env, origin);
    return;
  }

  await sendMessage(env, chatId, "دستور شناخته نشد. /help");
}

async function handleZipDocument(message, env, origin) {
  const chatId = message.chat.id;
  const userId = message.from.id;
  const doc = message.document;
  const fileName = sanitizeFileName(doc.file_name || "release.zip");
  const fileSize = Number(doc.file_size || 0);

  if (!fileName.toLowerCase().endsWith(".zip")) {
    await sendMessage(env, chatId, "❌ فقط فایل ZIP پذیرفته می‌شود.");
    return;
  }

  const maxBytes = Math.min(
    Number(env.MAX_ZIP_BYTES || DEFAULT_MAX_ZIP_BYTES),
    DEFAULT_MAX_ZIP_BYTES
  );

  if (!fileSize || fileSize > maxBytes) {
    await sendMessage(
      env,
      chatId,
      `❌ حجم ZIP باید حداکثر ${Math.floor(maxBytes / 1_000_000)}MB باشد.`
    );
    return;
  }

  const version =
    normalizeVersion(message.caption || "") ||
    normalizeVersion(fileName);

  if (!version) {
    await sendMessage(
      env,
      chatId,
      "❌ نسخه پیدا نشد. فایل را دوباره با Caption مثل v0.1.0 ارسال کن."
    );
    return;
  }

  await sendMessage(
    env,
    chatId,
    `⏳ دریافت نسخه v${version}\nفایل: ${fileName}\nدر حال ثبت و ارسال به GitHub...`
  );

  try {
    const tgFile = await telegramApi(env, "getFile", { file_id: doc.file_id });
    const filePath = tgFile?.result?.file_path;
    if (!filePath) throw new Error("Telegram getFile did not return file_path");

    const downloadUrl =
      `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${filePath}`;

    const fileResponse = await fetch(downloadUrl);
    if (!fileResponse.ok) {
      throw new Error(`Telegram file download failed: HTTP ${fileResponse.status}`);
    }

    // Telegram ZIPs are capped to <=20MB here, so buffering is safe and lets us hash.
    const bytes = await fileResponse.arrayBuffer();
    if (bytes.byteLength > maxBytes) {
      throw new Error("Downloaded ZIP exceeds configured maximum");
    }

    const sha256 = await sha256Hex(bytes);
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const objectKey = `incoming/v${version}/${timestamp}-${fileName}`;

    await env.RELEASES_BUCKET.put(objectKey, bytes, {
      httpMetadata: {
        contentType: "application/zip",
        contentDisposition: `attachment; filename="${fileName}"`,
      },
      customMetadata: {
        version,
        sha256,
        telegramUserId: String(userId),
        originalFileName: fileName,
      },
    });

    const eventType = env.RELEASE_EVENT_TYPE || DEFAULT_EVENT_TYPE;
    const releaseDownloadUrl =
      `${origin}/release-file/${encodeURIComponent(objectKey)}`;

    let dbRowId = null;
    try {
      const result = await env.DB.prepare(
        `INSERT INTO release_uploads
          (version, file_name, r2_key, sha256, file_size, telegram_user_id, github_event_type, status)
         VALUES (?, ?, ?, ?, ?, ?, ?, 'stored')`
      )
        .bind(
          version,
          fileName,
          objectKey,
          sha256,
          bytes.byteLength,
          String(userId),
          eventType
        )
        .run();

      dbRowId = result?.meta?.last_row_id || null;
    } catch (dbError) {
      // Do not block the release if only audit logging fails.
      console.error("D1 insert failed:", dbError);
    }

    await githubDispatch(env, eventType, {
      version,
      source: "telegram-zip",
      file_name: fileName,
      file_size: bytes.byteLength,
      sha256,
      r2_key: objectKey,
      download_url: releaseDownloadUrl,
      telegram_user_id: String(userId),
      d1_release_id: dbRowId,
    });

    if (dbRowId) {
      try {
        await env.DB.prepare(
          `UPDATE release_uploads
           SET status='dispatched', dispatched_at=CURRENT_TIMESTAMP
           WHERE id=?`
        )
          .bind(dbRowId)
          .run();
      } catch (dbError) {
        console.error("D1 update failed:", dbError);
      }
    }

    await sendMessage(
      env,
      chatId,
      [
        `✅ Azhand v${version} ثبت شد.`,
        `📦 ${fileName}`,
        `🔐 SHA-256: ${sha256.slice(0, 16)}…`,
        `☁️ R2: ذخیره شد`,
        `⚙️ GitHub Actions: Trigger شد`,
        "",
        "نتیجه Build/Deploy در مرحله بعدی Release Pipeline به ربات برگردانده می‌شود.",
      ].join("\n")
    );
  } catch (error) {
    console.error(error);
    await sendMessage(
      env,
      chatId,
      `🚨 پردازش ZIP ناموفق بود:\n${safeError(error)}`
    );
  }
}

async function sendStatus(env, chatId) {
  try {
    const result = await env.DB.prepare(
      `SELECT version, file_name, status, created_at
       FROM release_uploads
       ORDER BY id DESC
       LIMIT 5`
    ).all();

    const rows = result?.results || [];
    if (!rows.length) {
      await sendMessage(env, chatId, "هنوز نسخه‌ای ثبت نشده است.");
      return;
    }

    const lines = ["🏢 آخرین Releaseها", ""];
    for (const row of rows) {
      lines.push(`v${row.version} — ${row.status}`);
      lines.push(`${row.file_name}`);
      lines.push(`${row.created_at}`);
      lines.push("");
    }

    await sendMessage(env, chatId, lines.join("\n"));
  } catch (error) {
    await sendMessage(
      env,
      chatId,
      `⚠️ خواندن وضعیت D1 ممکن نشد:\n${safeError(error)}`
    );
  }
}

async function sendLatestVersion(env, chatId) {
  try {
    const row = await env.DB.prepare(
      `SELECT version, status, created_at
       FROM release_uploads
       ORDER BY id DESC
       LIMIT 1`
    ).first();

    if (!row) {
      await sendMessage(env, chatId, "هنوز نسخه‌ای ثبت نشده است.");
      return;
    }

    await sendMessage(
      env,
      chatId,
      `🏷 آخرین نسخه: v${row.version}\nوضعیت: ${row.status}\n${row.created_at}`
    );
  } catch (error) {
    await sendMessage(env, chatId, `⚠️ D1 error:\n${safeError(error)}`);
  }
}

async function githubDispatch(env, eventType, clientPayload) {
  if (!env.GITHUB_TOKEN || !env.GITHUB_OWNER || !env.GITHUB_REPO) {
    throw new Error("GitHub configuration is incomplete");
  }

  const response = await fetch(
    `https://api.github.com/repos/${encodeURIComponent(env.GITHUB_OWNER)}/${encodeURIComponent(env.GITHUB_REPO)}/dispatches`,
    {
      method: "POST",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${env.GITHUB_TOKEN}`,
        "Content-Type": "application/json",
        "User-Agent": "azhand-release-bot",
        "X-GitHub-Api-Version": GITHUB_API_VERSION,
      },
      body: JSON.stringify({
        event_type: eventType,
        client_payload: clientPayload,
      }),
    }
  );

  if (response.status !== 204) {
    const body = await response.text();
    throw new Error(`GitHub dispatch failed: HTTP ${response.status} ${body.slice(0, 500)}`);
  }
}

async function telegramApi(env, method, payload) {
  if (!env.TELEGRAM_BOT_TOKEN) {
    throw new Error("TELEGRAM_BOT_TOKEN is missing");
  }

  const response = await fetch(
    `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
    }
  );

  const data = await response.json();
  if (!response.ok || !data.ok) {
    throw new Error(
      `Telegram ${method} failed: ${response.status} ${JSON.stringify(data).slice(0, 500)}`
    );
  }
  return data;
}

function sendMessage(env, chatId, text) {
  return telegramApi(env, "sendMessage", {
    chat_id: chatId,
    text,
    disable_web_page_preview: true,
  });
}

function isAdmin(userId, env) {
  const allowed = String(env.ADMIN_TELEGRAM_IDS || "")
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);

  return allowed.includes(String(userId));
}

function validTelegramWebhook(request, env) {
  if (!env.TELEGRAM_WEBHOOK_SECRET) return false;
  const received = request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "";
  return received === env.TELEGRAM_WEBHOOK_SECRET;
}

function authorizedBearer(request, expected) {
  if (!expected) return false;
  const auth = request.headers.get("authorization") || "";
  return auth === `Bearer ${expected}`;
}

function normalizeVersion(input) {
  const match = String(input || "").match(
    /(?:^|[^0-9])v?(\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?)(?:$|[^0-9A-Za-z.+-])/
  );
  return match ? match[1] : null;
}

function sanitizeFileName(name) {
  return String(name || "release.zip")
    .replace(/[^\p{L}\p{N}._+-]+/gu, "_")
    .slice(0, 120);
}

async function sha256Hex(arrayBuffer) {
  const digest = await crypto.subtle.digest("SHA-256", arrayBuffer);
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function safeError(error) {
  const text = error instanceof Error ? error.message : String(error);
  return text.replace(/bot\d+:[A-Za-z0-9_-]+/g, "bot<redacted>").slice(0, 900);
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value, null, 2), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

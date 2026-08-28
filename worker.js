/**
 * Azhand Release Bot
 * Version: 0.3.3
 *
 * Features:
 * - First-run web setup
 * - Telegram ZIP releases
 * - KV temporary ZIP storage
 * - D1 release audit metadata
 * - Automatic GitHub workflow bootstrap/update
 * - GitHub main synchronization
 * - Automatic self-deploy of worker.js to Cloudflare
 *
 * Required bindings:
 *   DB             -> D1
 *   RELEASE_FILES  -> KV
 *
 * Cloudflare API token permission:
 *   Account -> Workers Scripts -> Edit
 *
 * GitHub fine-grained token:
 *   Contents  -> Read and write
 *   Workflows -> Read and write
 */

const BUILD_VERSION = "0.3.3";
const GITHUB_API_VERSION = "2022-11-28";
const DEFAULT_EVENT_TYPE = "azhand_zip_release";
const DEFAULT_MAX_ZIP_BYTES = 20_000_000;
const DEFAULT_FILE_TTL = 604800;
const ONE_TIME_TTL = 1800;

const RELEASE_WORKFLOW = `name: Azhand Release Sync

on:
  repository_dispatch:
    types: [azhand_zip_release]

permissions:
  contents: write

concurrency:
  group: azhand-release-main
  cancel-in-progress: false

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 25

    steps:
      - name: Validate payload
        shell: bash
        env:
          VERSION: \${{ github.event.client_payload.release.version }}
          DOWNLOAD_URL: \${{ github.event.client_payload.release.download_url }}
          DOWNLOAD_TOKEN: \${{ github.event.client_payload.release.download_token }}
          DEPLOY_URL: \${{ github.event.client_payload.release.deploy_url }}
          DEPLOY_TOKEN: \${{ github.event.client_payload.release.deploy_token }}
          EXPECTED_SHA256: \${{ github.event.client_payload.release.sha256 }}
        run: |
          set -euo pipefail
          test -n "$VERSION"
          test -n "$DOWNLOAD_URL"
          test -n "$DOWNLOAD_TOKEN"
          test -n "$DEPLOY_URL"
          test -n "$DEPLOY_TOKEN"
          test -n "$EXPECTED_SHA256"

      - name: Checkout main
        uses: actions/checkout@v4
        with:
          ref: main
          fetch-depth: 0
          persist-credentials: true

      - name: Download release ZIP
        shell: bash
        env:
          DOWNLOAD_URL: \${{ github.event.client_payload.release.download_url }}
          DOWNLOAD_TOKEN: \${{ github.event.client_payload.release.download_token }}
        run: |
          set -euo pipefail
          curl --fail --silent --show-error --location \\
            -H "Authorization: Bearer $DOWNLOAD_TOKEN" \\
            "$DOWNLOAD_URL" -o /tmp/azhand-release.zip

      - name: Verify SHA-256
        shell: bash
        env:
          EXPECTED_SHA256: \${{ github.event.client_payload.release.sha256 }}
        run: |
          set -euo pipefail
          echo "$EXPECTED_SHA256  /tmp/azhand-release.zip" | sha256sum --check

      - name: Validate archive
        shell: python
        run: |
          import stat, zipfile
          from pathlib import PurePosixPath

          with zipfile.ZipFile("/tmp/azhand-release.zip") as zf:
              infos = zf.infolist()
              if not infos:
                  raise SystemExit("ZIP is empty")
              if len(infos) > 10000:
                  raise SystemExit("Too many files")

              total = 0
              for info in infos:
                  total += info.file_size
                  if total > 300 * 1024 * 1024:
                      raise SystemExit("Expanded ZIP too large")

                  name = info.filename.replace("\\\\", "/")
                  p = PurePosixPath(name)
                  if p.is_absolute() or ".." in p.parts:
                      raise SystemExit(f"Unsafe path: {name}")

                  mode = (info.external_attr >> 16) & 0xFFFF
                  if stat.S_ISLNK(mode):
                      raise SystemExit(f"Symlink not allowed: {name}")

      - name: Extract
        shell: bash
        run: |
          set -euo pipefail
          rm -rf /tmp/azhand-extracted
          mkdir -p /tmp/azhand-extracted
          unzip -q /tmp/azhand-release.zip -d /tmp/azhand-extracted

      - name: Resolve project root
        id: root
        shell: bash
        run: |
          set -euo pipefail
          shopt -s dotglob nullglob
          entries=(/tmp/azhand-extracted/*)
          if [ "\${#entries[@]}" -eq 1 ] && [ -d "\${entries[0]}" ]; then
            src="\${entries[0]}"
          else
            src="/tmp/azhand-extracted"
          fi
          echo "src=$src" >> "$GITHUB_OUTPUT"

      - name: Sync project to main
        shell: bash
        env:
          SRC: \${{ steps.root.outputs.src }}
        run: |
          set -euo pipefail
          rsync -a --delete \\
            --exclude='.git/' \\
            --exclude='.github/workflows/azhand-release.yml' \\
            "$SRC"/ ./

      - name: Commit and push
        shell: bash
        env:
          VERSION: \${{ github.event.client_payload.release.version }}
        run: |
          set -euo pipefail
          git config user.name "Azhand Release Bot"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add -A
          if git diff --cached --quiet; then
            echo "No source changes"
          else
            git commit -m "chore(release): import Azhand v$VERSION"
            git push origin HEAD:main
          fi

      - name: Locate Worker source
        id: worker
        shell: bash
        run: |
          set -euo pipefail
          file=""
          for candidate in worker.js worker/worker.js backend/worker.js apps/worker/worker.js; do
            if [ -f "$candidate" ]; then
              file="$candidate"
              break
            fi
          done
          if [ -z "$file" ]; then
            echo "No worker.js found; Cloudflare deploy skipped."
            echo "found=false" >> "$GITHUB_OUTPUT"
          else
            echo "Worker source: $file"
            echo "found=true" >> "$GITHUB_OUTPUT"
            echo "file=$file" >> "$GITHUB_OUTPUT"
          fi

      - name: Deploy Worker through Azhand gateway
        if: steps.worker.outputs.found == 'true'
        shell: bash
        env:
          VERSION: \${{ github.event.client_payload.release.version }}
          DEPLOY_URL: \${{ github.event.client_payload.release.deploy_url }}
          DEPLOY_TOKEN: \${{ github.event.client_payload.release.deploy_token }}
          WORKER_FILE: \${{ steps.worker.outputs.file }}
        run: |
          set -euo pipefail
          curl --fail --silent --show-error \\
            -X PUT \\
            -H "Authorization: Bearer $DEPLOY_TOKEN" \\
            -H "Content-Type: application/javascript+module" \\
            -H "X-Azhand-Version: $VERSION" \\
            --data-binary "@$WORKER_FILE" \\
            "$DEPLOY_URL"

      - name: Summary
        shell: bash
        env:
          VERSION: \${{ github.event.client_payload.release.version }}
        run: |
          {
            echo "## ✅ Azhand v$VERSION"
            echo ""
            echo "- Repository: synchronized to main"
            echo "- SHA-256: verified"
            echo "- Cloudflare Worker: auto deploy completed or skipped if worker.js was absent"
          } >> "$GITHUB_STEP_SUMMARY"
`;

const CONFIG_SECRET_NAMES = [
  "PROJECT_NAME",
  "BOT_VERSION",
  "GITHUB_OWNER",
  "GITHUB_REPO",
  "GITHUB_TOKEN",
  "ADMIN_TELEGRAM_IDS",
  "RELEASE_EVENT_TYPE",
  "MAX_ZIP_BYTES",
  "RELEASE_FILE_TTL_SECONDS",
  "TELEGRAM_BOT_TOKEN",
  "TELEGRAM_WEBHOOK_SECRET",
  "SETUP_ADMIN_KEY",
  "CLOUDFLARE_ACCOUNT_ID",
  "CLOUDFLARE_WORKER_NAME",
  "CLOUDFLARE_API_TOKEN"
];

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return html(isConfigured(env) ? dashboardPage(env, url.origin) : setupPage(env));
    }

    if (request.method === "GET" && url.pathname === "/setup") {
      return html(setupPage(env));
    }

    if (request.method === "POST" && url.pathname === "/api/setup") {
      return handleSetup(request, env, url.origin, ctx);
    }

    if (
      request.method === "GET" &&
      url.pathname.startsWith("/api/setup-status/")
    ) {
      return getSetupStatus(env, url);
    }

    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        version: BUILD_VERSION,
        configured: isConfigured(env),
        d1: Boolean(env.DB),
        kv: Boolean(env.RELEASE_FILES),
        github: Boolean(env.GITHUB_TOKEN && env.GITHUB_OWNER && env.GITHUB_REPO),
        cloudflareDeploy: Boolean(
          env.CLOUDFLARE_ACCOUNT_ID &&
          env.CLOUDFLARE_WORKER_NAME &&
          env.CLOUDFLARE_API_TOKEN
        )
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
      return serveReleaseFile(request, env, url);
    }

    if (request.method === "PUT" && url.pathname === "/admin/deploy-worker") {
      return deployWorkerFromGithub(request, env);
    }

    return json({ ok: false, error: "not found" }, 404);
  }
};

async function handleSetup(request, env, origin, ctx) {
  if (!env.RELEASE_FILES) {
    return json({
      ok: false,
      error: "برای Setup پایدار، KV Binding با نام RELEASE_FILES باید متصل باشد."
    }, 503);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ ok: false, error: "JSON نامعتبر است." }, 400);
  }

  let setupRecoveryUsed = false;

  if (env.SETUP_ADMIN_KEY) {
    // Normalize accidental whitespace from copy/paste.
    const suppliedCurrentKey = String(body.current_setup_key || "").trim();
    const storedCurrentKey = String(env.SETUP_ADMIN_KEY || "").trim();

    if (suppliedCurrentKey !== storedCurrentKey) {
      // Recovery path:
      // Anyone holding a valid Cloudflare API token with access to this account
      // can already modify the Worker, so it is safe to use that capability to
      // recover/reset the Setup Admin Key instead of permanently locking setup.
      const recovery = await verifyCloudflareRecoveryToken(
        String(body.CLOUDFLARE_API_TOKEN || "").trim()
      );

      if (!recovery.ok) {
        return json({
          ok: false,
          error:
            "کلید فعلی Setup مطابقت ندارد و Cloudflare API Token هم برای بازیابی تأیید نشد.",
          recovery_error: recovery.error || null
        }, 403);
      }

      setupRecoveryUsed = true;
    }
  }

  const values = normalizeSetupValues(body);
  const error = validateSetupValues(values);
  if (error) {
    return json({ ok: false, error }, 400);
  }

  const jobId = randomToken(18);

  await setSetupStatus(env, jobId, {
    state: "queued",
    stage: "queued",
    message: setupRecoveryUsed
      ? "درخواست Setup ثبت شد؛ بازیابی قفل با Cloudflare Token انجام شد."
      : "درخواست Setup ثبت شد.",
    setup_recovery_used: setupRecoveryUsed,
    created_at: new Date().toISOString()
  });

  ctx.waitUntil(
    runSetupJob(values, env, origin, jobId).catch(async (error) => {
      try {
        await setSetupStatus(env, jobId, {
          state: "failed",
          stage: "internal",
          message: safeError(error)
        });
      } catch {}
    })
  );

  return json({
    ok: true,
    accepted: true,
    job_id: jobId,
    status_url: `/api/setup-status/${jobId}`
  }, 202);
}

function normalizeSetupValues(body) {
  return {
    PROJECT_NAME: clean(body.PROJECT_NAME || "Azhand Release Bot", 100),
    BOT_VERSION: BUILD_VERSION,
    GITHUB_OWNER: clean(body.GITHUB_OWNER, 100),
    GITHUB_REPO: clean(body.GITHUB_REPO, 100),
    GITHUB_TOKEN: String(body.GITHUB_TOKEN || "").trim(),
    ADMIN_TELEGRAM_IDS: clean(body.ADMIN_TELEGRAM_IDS, 500).replace(/\s+/g, ""),
    RELEASE_EVENT_TYPE: clean(body.RELEASE_EVENT_TYPE || DEFAULT_EVENT_TYPE, 100),
    MAX_ZIP_BYTES: String(Number(body.MAX_ZIP_BYTES || DEFAULT_MAX_ZIP_BYTES)),
    RELEASE_FILE_TTL_SECONDS: String(
      Number(body.RELEASE_FILE_TTL_SECONDS || DEFAULT_FILE_TTL)
    ),
    TELEGRAM_BOT_TOKEN: String(body.TELEGRAM_BOT_TOKEN || "").trim(),
    TELEGRAM_WEBHOOK_SECRET: String(body.TELEGRAM_WEBHOOK_SECRET || "").trim(),
    SETUP_ADMIN_KEY: String(body.SETUP_ADMIN_KEY || "").trim(),
    CLOUDFLARE_ACCOUNT_ID: clean(body.CLOUDFLARE_ACCOUNT_ID, 64),
    CLOUDFLARE_WORKER_NAME: clean(body.CLOUDFLARE_WORKER_NAME, 128),
    CLOUDFLARE_API_TOKEN: String(body.CLOUDFLARE_API_TOKEN || "").trim()
  };
}

function validateSetupValues(values) {
  const required = [
    "GITHUB_OWNER",
    "GITHUB_REPO",
    "GITHUB_TOKEN",
    "ADMIN_TELEGRAM_IDS",
    "TELEGRAM_BOT_TOKEN",
    "TELEGRAM_WEBHOOK_SECRET",
    "SETUP_ADMIN_KEY",
    "CLOUDFLARE_ACCOUNT_ID",
    "CLOUDFLARE_WORKER_NAME",
    "CLOUDFLARE_API_TOKEN"
  ];

  const missing = required.filter((name) => !values[name]);
  if (missing.length) {
    return `فیلدهای الزامی: ${missing.join(", ")}`;
  }

  if (!/^\d+(?:,\d+)*$/.test(values.ADMIN_TELEGRAM_IDS)) {
    return "Telegram Admin ID باید عددی باشد.";
  }

  if (values.TELEGRAM_WEBHOOK_SECRET.length < 20) {
    return "Webhook Secret حداقل 20 کاراکتر باشد.";
  }

  if (values.SETUP_ADMIN_KEY.length < 12) {
    return "Setup Admin Key حداقل 12 کاراکتر باشد.";
  }

  if (!/^[a-fA-F0-9]{32}$/.test(values.CLOUDFLARE_ACCOUNT_ID)) {
    return "Cloudflare Account ID باید شناسه 32 کاراکتری معتبر باشد.";
  }

  return null;
}

async function runSetupJob(values, env, origin, jobId) {
  await setSetupStatus(env, jobId, {
    state: "running",
    stage: "github",
    message: "در حال بررسی GitHub و ساخت Workflow..."
  });

  const github = await setupGithubWorkflow(values);
  if (!github.ok) {
    await setSetupStatus(env, jobId, {
      state: "failed",
      stage: "github",
      message: github.error || "ساخت Workflow گیت‌هاب ناموفق بود.",
      github
    });
    return;
  }

  await setSetupStatus(env, jobId, {
    state: "running",
    stage: "telegram",
    message: "GitHub آماده شد. در حال تنظیم Telegram Webhook...",
    github
  });

  const telegram = await setupTelegramWebhook(values, origin);
  if (!telegram.ok) {
    await setSetupStatus(env, jobId, {
      state: "failed",
      stage: "telegram",
      message: telegram.error || "تنظیم Webhook تلگرام ناموفق بود.",
      github,
      telegram
    });
    return;
  }

  // Save secrets LAST. Updating secrets on this same Worker can create a new
  // Worker version. The browser is already polling a separate status endpoint,
  // so the form never performs a normal navigation/refresh.
  await setSetupStatus(env, jobId, {
    state: "running",
    stage: "cloudflare",
    message: "GitHub و Telegram آماده‌اند. در حال ذخیره تنظیمات در Cloudflare...",
    github,
    telegram
  });

  const secrets = {};
  for (const name of CONFIG_SECRET_NAMES) {
    secrets[name] = {
      type: "secret_text",
      name,
      text: String(values[name])
    };
  }

  const cfResponse = await fetch(
    `https://api.cloudflare.com/client/v4/accounts/${encodeURIComponent(values.CLOUDFLARE_ACCOUNT_ID)}` +
    `/workers/scripts/${encodeURIComponent(values.CLOUDFLARE_WORKER_NAME)}/secrets-bulk`,
    {
      method: "PATCH",
      headers: {
        "Authorization": `Bearer ${values.CLOUDFLARE_API_TOKEN}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ secrets })
    }
  );

  let cfData = {};
  try {
    cfData = await cfResponse.json();
  } catch {}

  if (!cfResponse.ok || !cfData?.success) {
    await setSetupStatus(env, jobId, {
      state: "failed",
      stage: "cloudflare",
      message:
        `ذخیره Secrets در Cloudflare ناموفق بود: ` +
        `${cloudflareError(cfData) || `HTTP ${cfResponse.status}`}`,
      github,
      telegram
    });
    return;
  }

  await setSetupStatus(env, jobId, {
    state: "done",
    stage: "done",
    message: "Setup کامل شد.",
    github,
    telegram,
    cloudflare: {
      ok: true,
      worker: values.CLOUDFLARE_WORKER_NAME
    }
  });
}

async function setSetupStatus(env, jobId, data) {
  await env.RELEASE_FILES.put(
    `setup-job:${jobId}`,
    JSON.stringify({
      ...data,
      updated_at: new Date().toISOString()
    }),
    { expirationTtl: 900 }
  );
}

async function getSetupStatus(env, url) {
  if (!env.RELEASE_FILES) {
    return json({ ok: false, error: "KV unavailable" }, 503);
  }

  const jobId = url.pathname.slice("/api/setup-status/".length);
  if (!/^[a-f0-9]{36}$/.test(jobId)) {
    return json({ ok: false, error: "invalid job id" }, 400);
  }

  const raw = await env.RELEASE_FILES.get(`setup-job:${jobId}`);
  if (!raw) {
    return json({ ok: false, error: "setup job not found or expired" }, 404);
  }

  let status;
  try {
    status = JSON.parse(raw);
  } catch {
    return json({ ok: false, error: "invalid setup status" }, 500);
  }

  // If the secrets update caused a Worker version switch before the background
  // task wrote its final status, a new request will see the new env. Treat that
  // as success when all required configuration is now present.
  if (
    status.state === "running" &&
    status.stage === "cloudflare" &&
    isConfigured(env)
  ) {
    return json({
      ok: true,
      state: "done",
      stage: "done",
      message: "تنظیمات Cloudflare اعمال شده‌اند.",
      github: status.github,
      telegram: status.telegram,
      recovered_after_worker_update: true
    });
  }

  return json({ ok: true, ...status });
}


async function verifyCloudflareRecoveryToken(token) {
  if (!token) {
    return { ok: false, error: "Cloudflare API Token وارد نشده است." };
  }

  try {
    const response = await fetch(
      "https://api.cloudflare.com/client/v4/user/tokens/verify",
      {
        method: "GET",
        headers: {
          "Authorization": `Bearer ${token}`,
          "Accept": "application/json"
        }
      }
    );

    let data = {};
    try {
      data = await response.json();
    } catch {}

    if (!response.ok || !data?.success) {
      return {
        ok: false,
        error:
          cloudflareError(data) ||
          data?.messages?.map?.((x) => x?.message).filter(Boolean).join(" | ") ||
          `HTTP ${response.status}`
      };
    }

    const status = String(data?.result?.status || "").toLowerCase();
    if (status && status !== "active") {
      return {
        ok: false,
        error: `Cloudflare API Token status: ${status}`
      };
    }

    return { ok: true };
  } catch (error) {
    return { ok: false, error: safeError(error) };
  }
}

async function setupTelegramWebhook(values, origin) {
  try {
    const response = await fetch(
      `https://api.telegram.org/bot${values.TELEGRAM_BOT_TOKEN}/setWebhook`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          url: `${origin}/telegram/webhook`,
          secret_token: values.TELEGRAM_WEBHOOK_SECRET,
          allowed_updates: ["message"],
          drop_pending_updates: true
        })
      }
    );
    const data = await response.json();
    return {
      ok: Boolean(response.ok && data?.ok),
      error: data?.ok ? null : data?.description || `HTTP ${response.status}`
    };
  } catch (error) {
    return { ok: false, error: safeError(error) };
  }
}

async function setupGithubWorkflow(values) {
  const owner = encodeURIComponent(values.GITHUB_OWNER);
  const repo = encodeURIComponent(values.GITHUB_REPO);
  const path = ".github/workflows/azhand-release.yml";
  const api = `https://api.github.com/repos/${owner}/${repo}/contents/${path}`;

  const headers = {
    "Accept": "application/vnd.github+json",
    "Authorization": `Bearer ${values.GITHUB_TOKEN}`,
    "User-Agent": "azhand-release-bot",
    "X-GitHub-Api-Version": GITHUB_API_VERSION
  };

  let sha = null;

  try {
    const existing = await fetch(`${api}?ref=main`, { headers });
    if (existing.ok) {
      const data = await existing.json();
      sha = data?.sha || null;
    } else if (existing.status !== 404) {
      return { ok: false, error: `GET workflow HTTP ${existing.status}` };
    }

    const payload = {
      message: sha ? "ci: update Azhand release workflow" : "ci: add Azhand release workflow",
      content: base64Utf8(RELEASE_WORKFLOW),
      branch: "main"
    };
    if (sha) payload.sha = sha;

    const put = await fetch(api, {
      method: "PUT",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    let data = {};
    try { data = await put.json(); } catch {}

    if (!put.ok) {
      const details = Array.isArray(data?.errors)
        ? data.errors.map((x) => x?.message).filter(Boolean).join(" | ")
        : "";
      return {
        ok: false,
        error:
          (data?.message || `PUT workflow HTTP ${put.status}`) +
          (details ? ` — ${details}` : "")
      };
    }

    return { ok: true, updated: Boolean(sha) };
  } catch (error) {
    return { ok: false, error: safeError(error) };
  }
}

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

  const text = String(message.text || "").trim();

  if (text === "/start" || text === "/help") {
    await sendMessage(
      env,
      chatId,
      [
        "🏢 Azhand Release Bot v" + BUILD_VERSION,
        "",
        "/status",
        "/version",
        "/release 0.3.3",
        "",
        "برای انتشار ZIP، فایل را با Caption مثل v0.3.3 بفرست.",
        "بعد از ثبت، GitHub main و Cloudflare Worker خودکار آپدیت می‌شوند."
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

  if (message.document) {
    await handleZipDocument(message, env, origin);
    return;
  }

  await sendMessage(env, chatId, "دستور شناخته نشد. /help");
}

async function handleZipDocument(message, env, origin) {
  const chatId = message.chat.id;
  const userId = message.from.id;

  if (!env.RELEASE_FILES) {
    await sendMessage(env, chatId, "🚨 KV Binding با نام RELEASE_FILES متصل نیست.");
    return;
  }

  const doc = message.document;
  const fileName = sanitizeFileName(doc.file_name || "release.zip");
  const fileSize = Number(doc.file_size || 0);

  if (!fileName.toLowerCase().endsWith(".zip")) {
    await sendMessage(env, chatId, "❌ فقط ZIP پذیرفته می‌شود.");
    return;
  }

  const maxBytes = Math.min(
    Number(env.MAX_ZIP_BYTES || DEFAULT_MAX_ZIP_BYTES),
    DEFAULT_MAX_ZIP_BYTES
  );

  if (!fileSize || fileSize > maxBytes) {
    await sendMessage(env, chatId, `❌ حداکثر حجم ZIP: ${Math.floor(maxBytes / 1_000_000)}MB`);
    return;
  }

  const version =
    normalizeVersion(message.caption || "") ||
    normalizeVersion(fileName);

  if (!version) {
    await sendMessage(env, chatId, "❌ Caption نسخه مثل v0.3.3 لازم است.");
    return;
  }

  await sendMessage(env, chatId, `⏳ دریافت Azhand v${version}...`);

  try {
    const tgFile = await telegramApi(env, "getFile", { file_id: doc.file_id });
    const filePath = tgFile?.result?.file_path;
    if (!filePath) throw new Error("Telegram file_path missing");

    const fileResponse = await fetch(
      `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${filePath}`
    );

    if (!fileResponse.ok) {
      throw new Error(`Telegram download HTTP ${fileResponse.status}`);
    }

    const bytes = await fileResponse.arrayBuffer();
    if (bytes.byteLength > maxBytes) throw new Error("ZIP too large");

    const sha256 = await sha256Hex(bytes);
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    const kvKey = `releases/v${version}/${stamp}-${fileName}`;

    const ttl = Number(env.RELEASE_FILE_TTL_SECONDS || DEFAULT_FILE_TTL);
    await env.RELEASE_FILES.put(kvKey, bytes, {
      expirationTtl: Math.max(60, Math.floor(ttl)),
      metadata: { version, fileName, sha256 }
    });

    const downloadToken = randomToken(32);
    const deployToken = randomToken(32);

    await env.RELEASE_FILES.put(
      `auth:download:${downloadToken}`,
      JSON.stringify({ kvKey, version }),
      { expirationTtl: ONE_TIME_TTL }
    );

    await env.RELEASE_FILES.put(
      `auth:deploy:${deployToken}`,
      JSON.stringify({ version, worker: env.CLOUDFLARE_WORKER_NAME }),
      { expirationTtl: ONE_TIME_TTL }
    );

    let rowId = null;
    if (env.DB) {
      try {
        const result = await env.DB.prepare(
          `INSERT INTO release_uploads
           (version, file_name, storage_key, storage_type, sha256, file_size,
            telegram_user_id, github_event_type, status)
           VALUES (?, ?, ?, 'kv', ?, ?, ?, ?, 'dispatched')`
        ).bind(
          version,
          fileName,
          kvKey,
          sha256,
          bytes.byteLength,
          String(userId),
          env.RELEASE_EVENT_TYPE || DEFAULT_EVENT_TYPE
        ).run();
        rowId = result?.meta?.last_row_id || null;
      } catch (error) {
        console.error("D1 insert failed", error);
      }
    }

    await githubDispatch(env, env.RELEASE_EVENT_TYPE || DEFAULT_EVENT_TYPE, {
      version,
      file_name: fileName,
      file_size: bytes.byteLength,
      sha256,
      source: "telegram-zip",
      storage: "kv",
      storage_key: kvKey,
      d1_release_id: rowId,
      target_branch: "main",
      download_url: `${origin}/release-file/${encodeURIComponent(kvKey)}`,
      download_token: downloadToken,
      deploy_url: `${origin}/admin/deploy-worker`,
      deploy_token: deployToken
    });

    await sendMessage(
      env,
      chatId,
      [
        `✅ Azhand v${version} پذیرفته شد.`,
        `📦 ${fileName}`,
        `🔐 SHA-256: ${sha256.slice(0, 16)}…`,
        "⚙️ GitHub Actions: Trigger شد",
        "☁️ Worker: بعد از Sync خودکار Deploy می‌شود"
      ].join("\n")
    );
  } catch (error) {
    await sendMessage(env, chatId, `🚨 Release ناموفق بود:\n${safeError(error)}`);
  }
}

async function serveReleaseFile(request, env, url) {
  if (!env.RELEASE_FILES) return json({ ok: false, error: "KV unavailable" }, 503);

  const token = bearer(request);
  if (!token) return json({ ok: false, error: "unauthorized" }, 401);

  const encodedKey = url.pathname.slice("/release-file/".length);
  let kvKey;
  try { kvKey = decodeURIComponent(encodedKey); }
  catch { return json({ ok: false, error: "bad key" }, 400); }

  const authKey = `auth:download:${token}`;
  const authRaw = await env.RELEASE_FILES.get(authKey);
  if (!authRaw) return json({ ok: false, error: "expired/invalid token" }, 401);

  let auth;
  try { auth = JSON.parse(authRaw); }
  catch { return json({ ok: false, error: "invalid token state" }, 401); }

  if (auth.kvKey !== kvKey) return json({ ok: false, error: "token/key mismatch" }, 403);

  const bytes = await env.RELEASE_FILES.get(kvKey, { type: "arrayBuffer" });
  if (!bytes) return json({ ok: false, error: "file not found" }, 404);

  await env.RELEASE_FILES.delete(authKey);

  return new Response(bytes, {
    status: 200,
    headers: {
      "Content-Type": "application/zip",
      "Content-Disposition": 'attachment; filename="azhand-release.zip"',
      "Cache-Control": "private, no-store"
    }
  });
}

async function deployWorkerFromGithub(request, env) {
  if (
    !env.RELEASE_FILES ||
    !env.CLOUDFLARE_API_TOKEN ||
    !env.CLOUDFLARE_ACCOUNT_ID ||
    !env.CLOUDFLARE_WORKER_NAME
  ) {
    return json({ ok: false, error: "Cloudflare deploy configuration incomplete" }, 503);
  }

  const token = bearer(request);
  if (!token) return json({ ok: false, error: "unauthorized" }, 401);

  const authKey = `auth:deploy:${token}`;
  const authRaw = await env.RELEASE_FILES.get(authKey);
  if (!authRaw) return json({ ok: false, error: "expired/invalid deploy token" }, 401);

  let auth;
  try { auth = JSON.parse(authRaw); }
  catch { return json({ ok: false, error: "invalid deploy token state" }, 401); }

  const version = String(request.headers.get("X-Azhand-Version") || "");
  if (!version || version !== String(auth.version || "")) {
    return json({ ok: false, error: "version mismatch" }, 403);
  }

  const bytes = await request.arrayBuffer();
  if (!bytes.byteLength || bytes.byteLength > 2_000_000) {
    return json({ ok: false, error: "worker.js size invalid" }, 400);
  }

  const source = new TextDecoder().decode(bytes);
  if (!source.includes("export default")) {
    return json({ ok: false, error: "worker.js does not look like module syntax" }, 400);
  }

  const form = new FormData();
  form.append(
    "metadata",
    JSON.stringify({ main_module: "worker.js" })
  );
  form.append(
    "worker.js",
    new Blob([bytes], { type: "application/javascript+module" }),
    "worker.js"
  );

  const api =
    `https://api.cloudflare.com/client/v4/accounts/${encodeURIComponent(env.CLOUDFLARE_ACCOUNT_ID)}` +
    `/workers/scripts/${encodeURIComponent(env.CLOUDFLARE_WORKER_NAME)}/content`;

  const response = await fetch(api, {
    method: "PUT",
    headers: {
      "Authorization": `Bearer ${env.CLOUDFLARE_API_TOKEN}`
    },
    body: form
  });

  let data = {};
  try { data = await response.json(); } catch {}

  if (!response.ok || !data?.success) {
    return json({
      ok: false,
      error: `Cloudflare deploy failed: ${cloudflareError(data) || response.status}`
    }, 502);
  }

  await env.RELEASE_FILES.delete(authKey);

  try {
    const adminId = String(env.ADMIN_TELEGRAM_IDS || "").split(",")[0].trim();
    if (adminId) {
      await sendMessage(
        env,
        adminId,
        `☁️ Worker آژند با موفقیت به نسخه v${version} بروزرسانی شد.`
      );
    }
  } catch {}

  return json({
    ok: true,
    version,
    worker: env.CLOUDFLARE_WORKER_NAME,
    cloudflare: "deployed"
  });
}

async function githubDispatch(env, eventType, clientPayload) {
  const response = await fetch(
    `https://api.github.com/repos/${encodeURIComponent(env.GITHUB_OWNER)}/${encodeURIComponent(env.GITHUB_REPO)}/dispatches`,
    {
      method: "POST",
      headers: {
        "Accept": "application/vnd.github+json",
        "Authorization": `Bearer ${env.GITHUB_TOKEN}`,
        "Content-Type": "application/json",
        "User-Agent": "azhand-release-bot",
        "X-GitHub-Api-Version": GITHUB_API_VERSION
      },
      body: JSON.stringify({
        event_type: eventType,
        client_payload: {
          release: clientPayload
        }
      })
    }
  );

  if (response.status !== 204) {
    const responseText = await response.text();

    if (
      response.status === 422 &&
      responseText.includes("No more than 10 properties")
    ) {
      throw new Error(
        "GitHub dispatch rejected because client_payload exceeded the 10-property limit."
      );
    }

    throw new Error(
      `GitHub dispatch HTTP ${response.status} ${responseText.slice(0, 400)}`
    );
  }
}

async function telegramApi(env, method, payload) {
  const response = await fetch(
    `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }
  );

  const data = await response.json();
  if (!response.ok || !data?.ok) {
    throw new Error(`Telegram ${method} failed: ${data?.description || response.status}`);
  }
  return data;
}

function sendMessage(env, chatId, text) {
  return telegramApi(env, "sendMessage", {
    chat_id: chatId,
    text,
    disable_web_page_preview: true
  });
}

async function sendStatus(env, chatId) {
  if (!env.DB) {
    await sendMessage(env, chatId, "⚠️ D1 Binding متصل نیست.");
    return;
  }

  try {
    const result = await env.DB.prepare(
      `SELECT version, file_name, status, created_at
       FROM release_uploads ORDER BY id DESC LIMIT 5`
    ).all();

    const rows = result?.results || [];
    if (!rows.length) {
      await sendMessage(env, chatId, "هنوز Release ثبت نشده.");
      return;
    }

    const lines = ["🏢 آخرین Releaseها", ""];
    for (const row of rows) {
      lines.push(`v${row.version} — ${row.status}`);
      lines.push(row.file_name);
      lines.push(row.created_at);
      lines.push("");
    }
    await sendMessage(env, chatId, lines.join("\n"));
  } catch (error) {
    await sendMessage(env, chatId, `⚠️ D1: ${safeError(error)}`);
  }
}

async function sendLatestVersion(env, chatId) {
  if (!env.DB) {
    await sendMessage(env, chatId, "⚠️ D1 Binding متصل نیست.");
    return;
  }
  try {
    const row = await env.DB.prepare(
      `SELECT version, status, created_at
       FROM release_uploads ORDER BY id DESC LIMIT 1`
    ).first();

    if (!row) {
      await sendMessage(env, chatId, "هنوز نسخه‌ای ثبت نشده.");
      return;
    }
    await sendMessage(
      env,
      chatId,
      `🏷 آخرین نسخه: v${row.version}\nوضعیت: ${row.status}\n${row.created_at}`
    );
  } catch (error) {
    await sendMessage(env, chatId, `⚠️ D1: ${safeError(error)}`);
  }
}

function setupPage(env) {
  const configured = isConfigured(env);

  return `<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Azhand Setup</title>
<style>
:root{color-scheme:dark;--bg:#07111f;--card:#0e1b2d;--line:#263e5c;--gold:#e0b84d;--ok:#45d38e;--err:#ff6b78;--muted:#a6b7cc}
*{box-sizing:border-box}
body{margin:0;background:radial-gradient(circle at top,#142843,#07111f 55%);color:#eef5ff;font-family:Tahoma,Arial,sans-serif;min-height:100vh}
main{max-width:860px;margin:auto;padding:24px 16px 70px}
h1{text-align:center;margin:15px 0 8px}.sub{text-align:center;color:var(--muted);line-height:1.8}
.card{background:#0e1b2df0;border:1px solid var(--line);border-radius:20px;padding:18px;margin:14px 0}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.full{grid-column:1/-1}
label{display:block;margin-bottom:6px;color:#c6d4e5;font-size:13px}
input{width:100%;padding:12px;border-radius:11px;border:1px solid #35506f;background:#081522;color:#fff;outline:none}
input:focus{border-color:var(--gold)}
button{width:100%;padding:14px;border:0;border-radius:12px;background:var(--gold);color:#07111f;font-weight:bold;font-size:16px;cursor:pointer}
button:disabled{opacity:.55;cursor:not-allowed}
.note{color:var(--muted);line-height:1.8;font-size:13px}
#status{display:none;margin-top:15px;padding:15px;border:1px solid #35506f;border-radius:14px;background:#081522;line-height:1.95;white-space:pre-wrap}
.ok{border-color:#2c7c5b!important;color:#dffff0}.err{border-color:#8f3944!important;color:#ffe5e8}
.progress{height:7px;background:#13263d;border-radius:99px;overflow:hidden;margin:12px 0}.bar{height:100%;width:0;background:var(--gold);transition:width .3s}
.small{font-size:12px;color:var(--muted)}
@media(max-width:650px){.grid{grid-template-columns:1fr}.full{grid-column:auto}}
</style>
</head>
<body>
<main>
<h1>🏢 راه‌اندازی Azhand Release Bot v${BUILD_VERSION}</h1>
<div class="sub">این نسخه هنگام ذخیره صفحه را Reload نمی‌کند و نتیجه هر مرحله را جداگانه نشان می‌دهد.</div>

<form id="f" onsubmit="return false;">
${configured ? `<div class="card">
<label>کلید فعلی Setup</label>
<input type="password" name="current_setup_key" autocomplete="current-password">
<p class="note">
اگر کلید قبلی Match نشود، v0.3.3 می‌تواند با Cloudflare API Token معتبر قفل Setup را بازیابی و کلید جدید را ثبت کند.
</p>
</div>` : ""}

<div class="card"><h3>☁️ Cloudflare</h3><div class="grid">
<div><label>Account ID</label><input name="CLOUDFLARE_ACCOUNT_ID" required></div>
<div><label>Worker Name</label><input name="CLOUDFLARE_WORKER_NAME" value="azhand" required></div>
<div class="full"><label>API Token — Workers Scripts Edit</label><input type="password" name="CLOUDFLARE_API_TOKEN" required></div>
</div></div>

<div class="card"><h3>🤖 Telegram</h3><div class="grid">
<div class="full"><label>Bot Token</label><input type="password" name="TELEGRAM_BOT_TOKEN" required></div>
<div><label>Admin Numeric ID</label><input name="ADMIN_TELEGRAM_IDS" required></div>
<div><label>Webhook Secret</label><input type="password" name="TELEGRAM_WEBHOOK_SECRET" required></div>
</div></div>

<div class="card"><h3>🐙 GitHub</h3><div class="grid">
<div><label>Owner</label><input name="GITHUB_OWNER" required></div>
<div><label>Repository</label><input name="GITHUB_REPO" required></div>
<div class="full"><label>Fine-grained PAT</label><input type="password" name="GITHUB_TOKEN" required></div>
</div><p class="note">مجوزها: Contents Read/Write + Workflows Read/Write</p></div>

<div class="card"><h3>⚙️ Release</h3><div class="grid">
<div><label>Project Name</label><input name="PROJECT_NAME" value="Azhand Release Bot"></div>
<div><label>Bot Version</label><input name="BOT_VERSION" value="${BUILD_VERSION}" readonly></div>
<div><label>Event Type</label><input name="RELEASE_EVENT_TYPE" value="${DEFAULT_EVENT_TYPE}"></div>
<div><label>MAX ZIP Bytes</label><input name="MAX_ZIP_BYTES" value="${DEFAULT_MAX_ZIP_BYTES}"></div>
<div><label>KV File TTL Seconds</label><input name="RELEASE_FILE_TTL_SECONDS" value="${DEFAULT_FILE_TTL}"></div>
<div><label>Setup Admin Key</label><input type="password" name="SETUP_ADMIN_KEY" required></div>
</div></div>

<button id="saveBtn" type="button" onclick="saveSetup()">ذخیره و راه‌اندازی کامل</button>

<div id="status">
<div id="statusText"></div>
<div class="progress"><div id="bar" class="bar"></div></div>
<div id="detail" class="small"></div>
</div>
</form>
</main>

<script>
const form=document.getElementById('f');
const statusBox=document.getElementById('status');
const statusText=document.getElementById('statusText');
const detail=document.getElementById('detail');
const bar=document.getElementById('bar');
const btn=document.getElementById('saveBtn');

const remembered=['CLOUDFLARE_ACCOUNT_ID','CLOUDFLARE_WORKER_NAME','GITHUB_OWNER','GITHUB_REPO','ADMIN_TELEGRAM_IDS'];
for(const name of remembered){
  const el=form.elements[name];
  if(!el) continue;
  const saved=localStorage.getItem('azhand_setup_'+name);
  if(saved) el.value=saved;
  el.addEventListener('input',()=>localStorage.setItem('azhand_setup_'+name,el.value));
}

function progress(stage){
  const map={queued:8,github:30,telegram:55,cloudflare:82,done:100};
  bar.style.width=(map[stage]||10)+'%';
}

async function saveSetup(){
  if(!form.reportValidity()) return;

  btn.disabled=true;
  statusBox.style.display='block';
  statusBox.className='';
  statusText.textContent='⏳ شروع Setup...';
  detail.textContent='صفحه Refresh نمی‌شود.';
  bar.style.width='5%';

  const data=Object.fromEntries(new FormData(form).entries());

  try{
    const r=await fetch('/api/setup',{
      method:'POST',
      headers:{'content-type':'application/json'},
      body:JSON.stringify(data)
    });

    const j=await r.json();
    if(!r.ok || !j.ok){
      throw new Error(
        (j.error || ('HTTP ' + r.status)) +
        (j.recovery_error ? '\nCloudflare recovery: ' + j.recovery_error : '')
      );
    }

    statusText.textContent='⏳ Setup در پس‌زمینه شروع شد...';
    await pollSetup(j.status_url);
  }catch(err){
    statusBox.classList.add('err');
    statusText.textContent='❌ Setup شروع نشد';
    detail.textContent=err.message;
    btn.disabled=false;
  }
}

async function pollSetup(url){
  for(let i=0;i<120;i++){
    await new Promise(r=>setTimeout(r,1500));

    try{
      const r=await fetch(url,{cache:'no-store'});
      const j=await r.json();

      if(!r.ok || !j.ok){
        detail.textContent=j.error||('HTTP '+r.status);
        continue;
      }

      progress(j.stage);
      statusText.textContent=
        (j.state==='done'?'✅ ':'⏳ ')+(j.message||j.stage||'در حال پردازش');

      const lines=[];
      if(j.github) lines.push('GitHub Workflow: '+(j.github.ok?'✅':'❌')+(j.github.error?' — '+j.github.error:''));
      if(j.telegram) lines.push('Telegram Webhook: '+(j.telegram.ok?'✅':'❌')+(j.telegram.error?' — '+j.telegram.error:''));
      if(j.cloudflare) lines.push('Cloudflare Secrets: '+(j.cloudflare.ok?'✅':'❌'));
      detail.textContent=lines.join('\\n');

      if(j.state==='done'){
        statusBox.classList.add('ok');
        bar.style.width='100%';
        btn.disabled=false;
        return;
      }

      if(j.state==='failed'){
        statusBox.classList.add('err');
        statusText.textContent='❌ '+(j.message||'Setup ناموفق بود');
        btn.disabled=false;
        return;
      }
    }catch(err){
      // A Worker secret update can briefly switch Worker versions.
      // Keep polling instead of reloading the page.
      detail.textContent='اتصال موقتاً قطع شد؛ در حال بررسی مجدد...';
    }
  }

  statusBox.classList.add('err');
  statusText.textContent='⚠️ زمان انتظار Setup تمام شد';
  detail.textContent='صفحه را Refresh نکن؛ ابتدا /health را بررسی کن.';
  btn.disabled=false;
}
</script>
</body>
</html>`;
}

function dashboardPage(env, origin) {
  return `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Azhand</title>
<style>body{background:#07111f;color:#fff;font-family:Tahoma,Arial;margin:0}main{max-width:700px;margin:50px auto;padding:20px}.c{background:#0e1b2d;border:1px solid #2b4564;padding:22px;border-radius:20px}a{color:#e0b84d}</style></head>
<body><main><div class="c"><h1>🏢 Azhand Release Bot</h1>
<p>✅ تنظیم شده — v${escapeHtml(BUILD_VERSION)}</p>
<p>GitHub: ${escapeHtml(env.GITHUB_OWNER || "")}/${escapeHtml(env.GITHUB_REPO || "")}</p>
<p>D1: ${env.DB ? "✅" : "⚠️"} | KV: ${env.RELEASE_FILES ? "✅" : "⚠️"} | Auto Worker Deploy: ${env.CLOUDFLARE_API_TOKEN ? "✅" : "⚠️"}</p>
<p><a href="/health">Health</a> | <a href="/setup">Setup</a></p>
</div></main></body></html>`;
}

function isConfigured(env) {
  return Boolean(
    env.GITHUB_TOKEN &&
    env.GITHUB_OWNER &&
    env.GITHUB_REPO &&
    env.TELEGRAM_BOT_TOKEN &&
    env.ADMIN_TELEGRAM_IDS &&
    env.TELEGRAM_WEBHOOK_SECRET &&
    env.SETUP_ADMIN_KEY &&
    env.CLOUDFLARE_ACCOUNT_ID &&
    env.CLOUDFLARE_WORKER_NAME &&
    env.CLOUDFLARE_API_TOKEN
  );
}

function isAdmin(userId, env) {
  return String(env.ADMIN_TELEGRAM_IDS || "")
    .split(",").map(x => x.trim()).filter(Boolean)
    .includes(String(userId));
}

function validTelegramWebhook(request, env) {
  return Boolean(
    env.TELEGRAM_WEBHOOK_SECRET &&
    (request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "") === env.TELEGRAM_WEBHOOK_SECRET
  );
}

function bearer(request) {
  const auth = request.headers.get("Authorization") || "";
  return auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
}

function normalizeVersion(input) {
  const match = String(input || "").match(
    /v?(\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?)/i
  );
  return match ? match[1] : null;
}

function sanitizeFileName(name) {
  return String(name || "release.zip")
    .replace(/[^\p{L}\p{N}._+-]+/gu, "_")
    .slice(0, 120);
}

async function sha256Hex(buffer) {
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  return Array.from(new Uint8Array(digest))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

function randomToken(bytes = 32) {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return Array.from(a).map(b => b.toString(16).padStart(2, "0")).join("");
}

function base64Utf8(text) {
  const bytes = new TextEncoder().encode(text);
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function clean(value, max = 500) {
  return String(value || "").trim().slice(0, max);
}

function cloudflareError(data) {
  return (Array.isArray(data?.errors) ? data.errors : [])
    .map(x => x?.message).filter(Boolean).join(" | ").slice(0, 700);
}

function safeError(error) {
  return (error instanceof Error ? error.message : String(error))
    .replace(/bot\d+:[A-Za-z0-9_-]+/g, "bot<redacted>")
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, "Bearer <redacted>")
    .slice(0, 900);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&","&amp;").replaceAll("<","&lt;")
    .replaceAll(">","&gt;").replaceAll('"',"&quot;")
    .replaceAll("'","&#039;");
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value, null, 2), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

function html(value, status = 200) {
  return new Response(value, {
    status,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer"
    }
  });
}

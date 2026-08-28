package com.azhand.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AdminUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long
)

object AdminUpdateManager {
    private const val MAX_APK_BYTES = 20_000_000L

    suspend fun check(): AdminUpdateInfo? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.API_BASE_URL.trimEnd('/') +
            "/api/admin-app/update?current_version_code=${BuildConfig.VERSION_CODE}&_=${System.currentTimeMillis()}"
        val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (c.responseCode != 200) return@withContext null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (!j.optBoolean("ok") || !j.optBoolean("update_available")) {
                return@withContext null
            }
            val info = AdminUpdateInfo(
                versionName = j.optString("latest_version_name"),
                versionCode = j.optInt("latest_version_code"),
                apkUrl = j.optString("apk_url"),
                sha256 = j.optString("sha256").lowercase(),
                sizeBytes = j.optLong("size_bytes")
            )
            if (info.versionCode <= BuildConfig.VERSION_CODE ||
                !info.apkUrl.startsWith("https://") ||
                info.sha256.length != 64 ||
                info.sizeBytes <= 0 || info.sizeBytes > MAX_APK_BYTES
            ) null else info
        } finally {
            c.disconnect()
        }
    }

    suspend fun download(context: Context, info: AdminUpdateInfo): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "Azhand-Admin-v${info.versionName}.apk")
            if (file.exists()) file.delete()

            val c = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                useCaches = false
            }
            try {
                if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                var total = 0L
                c.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_APK_BYTES) error("APK too large")
                            output.write(buffer, 0, n)
                        }
                    }
                }
                if (total != info.sizeBytes) error("APK size mismatch")
                if (!sha256(file).equals(info.sha256, true)) {
                    file.delete()
                    error("APK integrity check failed")
                }
                file
            } finally {
                c.disconnect()
            }
        }

    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

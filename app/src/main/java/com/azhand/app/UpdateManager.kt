package com.azhand.app

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

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
    val mandatory: Boolean
)

object UpdateManager {
    private const val MAX_APK_BYTES = 20_000_000L

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val endpoint =
            "$base/api/app/update?current_version_code=${BuildConfig.VERSION_CODE}"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Azhand-App-Version", BuildConfig.VERSION_NAME)
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)

            if (!json.optBoolean("ok", false) ||
                !json.optBoolean("update_available", false)
            ) {
                return@withContext null
            }

            val apkUrl = json.optString("apk_url")
            val sha256 = json.optString("sha256").lowercase()
            val versionCode = json.optInt("latest_version_code", 0)
            val versionName = json.optString("latest_version_name")
            val size = json.optLong("size_bytes", 0L)

            if (apkUrl.isBlank() ||
                sha256.length != 64 ||
                versionCode <= BuildConfig.VERSION_CODE ||
                size <= 0L ||
                size > MAX_APK_BYTES
            ) {
                return@withContext null
            }

            AppUpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                apkUrl = apkUrl,
                sha256 = sha256,
                sizeBytes = size,
                notes = json.optString("notes", "نسخه جدید آژند آماده نصب است."),
                mandatory = json.optBoolean("mandatory", false)
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadUpdate(
        context: Context,
        info: AppUpdateInfo
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "Azhand-v${info.versionName}.apk")
        if (target.exists()) target.delete()

        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_APK_BYTES) {
                throw IllegalStateException("APK too large")
            }

            var copied = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        copied += count
                        if (copied > MAX_APK_BYTES) {
                            throw IllegalStateException("APK too large")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }

            if (copied != info.sizeBytes) {
                target.delete()
                throw IllegalStateException("APK size mismatch")
            }

            val actualSha = sha256(target)
            if (!actualSha.equals(info.sha256, ignoreCase = true)) {
                target.delete()
                throw IllegalStateException("APK integrity check failed")
            }

            target
        } finally {
            connection.disconnect()
        }
    }

    fun startInstaller(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

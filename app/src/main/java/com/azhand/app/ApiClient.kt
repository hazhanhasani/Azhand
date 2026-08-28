package com.azhand.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(val token: String)

data class ProfileData(
    val fullName: String,
    val mobile: String,
    val role: String,
    val unitNumber: String,
    val block: String,
    val relation: String
)

data class ChargeData(
    val id: Long,
    val title: String,
    val amount: Long,
    val paidAmount: Long,
    val status: String,
    val dueDate: String
)

data class AnnouncementData(
    val id: Long,
    val title: String,
    val body: String,
    val priority: String,
    val publishedAt: String
)

data class ServiceRequestData(
    val id: Long,
    val category: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: String
)

data class ExpenseData(
    val id: Long,
    val title: String,
    val category: String,
    val amount: Long,
    val expenseDate: String
)

data class DashboardData(
    val profile: ProfileData,
    val totalDue: Long,
    val currentChargeTitle: String,
    val currentChargeAmount: Long,
    val openRequests: Int,
    val charges: List<ChargeData>,
    val announcements: List<AnnouncementData>,
    val serviceRequests: List<ServiceRequestData>,
    val expenses: List<ExpenseData>
)

class ApiException(val statusCode: Int, message: String) : Exception(message)

object ApiClient {
    private fun baseUrl(): String = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun login(mobile: String, accessCode: String): LoginResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("mobile", mobile)
                .put("access_code", accessCode)

            val json = requestJson(
                method = "POST",
                path = "/api/app/auth/login",
                body = body
            )

            LoginResult(token = json.getString("token"))
        }

    suspend fun dashboard(token: String): DashboardData =
        withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/app/dashboard",
                token = token
            )

            val p = json.getJSONObject("profile")
            val summary = json.getJSONObject("summary")

            DashboardData(
                profile = ProfileData(
                    fullName = p.optString("full_name"),
                    mobile = p.optString("mobile"),
                    role = p.optString("role"),
                    unitNumber = p.optString("unit_number"),
                    block = p.optString("block"),
                    relation = p.optString("relation")
                ),
                totalDue = summary.optLong("total_due", 0L),
                currentChargeTitle = summary.optString("current_charge_title"),
                currentChargeAmount = summary.optLong("current_charge_amount", 0L),
                openRequests = summary.optInt("open_requests", 0),
                charges = parseCharges(json.optJSONArray("charges")),
                announcements = parseAnnouncements(json.optJSONArray("announcements")),
                serviceRequests = parseRequests(json.optJSONArray("service_requests")),
                expenses = parseExpenses(json.optJSONArray("expenses"))
            )
        }

    suspend fun createServiceRequest(
        token: String,
        category: String,
        title: String,
        description: String
    ) = withContext(Dispatchers.IO) {
        requestJson(
            method = "POST",
            path = "/api/app/service-requests",
            token = token,
            body = JSONObject()
                .put("category", category)
                .put("title", title)
                .put("description", description)
        )
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            requestJson(
                method = "POST",
                path = "/api/app/auth/logout",
                token = token,
                body = JSONObject()
            )
        }
    }

    private fun requestJson(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val connection = (URL(baseUrl() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Azhand-App-Version", BuildConfig.VERSION_NAME)
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(body.toString())
                }
            }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream else connection.errorStream

            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)

            if (status !in 200..299 || !json.optBoolean("ok", status in 200..299)) {
                throw ApiException(
                    status,
                    json.optString("error", "خطای ارتباط با سرور")
                )
            }

            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCharges(array: JSONArray?): List<ChargeData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    ChargeData(
                        id = j.optLong("id"),
                        title = j.optString("title"),
                        amount = j.optLong("amount"),
                        paidAmount = j.optLong("paid_amount"),
                        status = j.optString("status"),
                        dueDate = j.optString("due_date")
                    )
                )
            }
        }
    }

    private fun parseAnnouncements(array: JSONArray?): List<AnnouncementData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    AnnouncementData(
                        id = j.optLong("id"),
                        title = j.optString("title"),
                        body = j.optString("body"),
                        priority = j.optString("priority"),
                        publishedAt = j.optString("published_at")
                    )
                )
            }
        }
    }

    private fun parseRequests(array: JSONArray?): List<ServiceRequestData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    ServiceRequestData(
                        id = j.optLong("id"),
                        category = j.optString("category"),
                        title = j.optString("title"),
                        description = j.optString("description"),
                        status = j.optString("status"),
                        createdAt = j.optString("created_at")
                    )
                )
            }
        }
    }

    private fun parseExpenses(array: JSONArray?): List<ExpenseData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    ExpenseData(
                        id = j.optLong("id"),
                        title = j.optString("title"),
                        category = j.optString("category"),
                        amount = j.optLong("amount"),
                        expenseDate = j.optString("expense_date")
                    )
                )
            }
        }
    }
}

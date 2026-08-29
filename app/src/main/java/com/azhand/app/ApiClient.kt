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

data class PaymentSubmissionData(
    val id: Long,
    val chargeId: Long,
    val chargeTitle: String,
    val amount: Long,
    val referenceId: String,
    val note: String,
    val status: String,
    val reviewerNote: String,
    val receiptNo: String,
    val createdAt: String
)

data class NotificationData(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val entityType: String,
    val entityId: String,
    val readAt: String,
    val createdAt: String
)

data class BlupalInvoiceData(
    val invoiceId: Long,
    val chargeId: Long,
    val chargeTitle: String,
    val amountToman: Long,
    val amountRial: Long,
    val finalAmountRial: Long,
    val status: String,
    val paymentLink: String,
    val callbackUrl: String,
    val cardNumber: String,
    val mode: String,
    val receiptNo: String,
    val createdAt: String
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
    val expenses: List<ExpenseData>,
    val paymentSubmissions: List<PaymentSubmissionData>,
    val notifications: List<NotificationData>,
    val unreadNotifications: Int,
    val blupalInvoices: List<BlupalInvoiceData>
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
                expenses = parseExpenses(json.optJSONArray("expenses")),
                paymentSubmissions = parsePaymentSubmissions(
                    json.optJSONArray("payment_submissions")
                ),
                notifications = parseNotifications(
                    json.optJSONArray("notifications")
                ),
                unreadNotifications = summary.optInt(
                    "unread_notifications",
                    0
                ),
                blupalInvoices = parseBlupalInvoices(
                    json.optJSONArray("blupal_invoices")
                )
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

    suspend fun submitPayment(
        token: String,
        chargeId: Long,
        amount: Long,
        referenceId: String,
        note: String
    ) = withContext(Dispatchers.IO) {
        requestJson(
            method = "POST",
            path = "/api/app/payment-submissions",
            token = token,
            body = JSONObject()
                .put("charge_id", chargeId)
                .put("amount", amount)
                .put("reference_id", referenceId)
                .put("note", note)
        )
    }

    suspend fun createBlupalInvoice(
        token: String,
        chargeId: Long,
        amountToman: Long
    ): BlupalInvoiceData = withContext(Dispatchers.IO) {
        val json = requestJson(
            method = "POST",
            path = "/api/app/payments/blupal/create",
            token = token,
            body = JSONObject()
                .put("charge_id", chargeId)
                .put("amount_toman", amountToman)
        )
        parseBlupalInvoice(json.getJSONObject("invoice"))
    }

    suspend fun checkBlupalInvoice(
        token: String,
        invoiceId: Long
    ): BlupalInvoiceData = withContext(Dispatchers.IO) {
        val json = requestJson(
            method = "GET",
            path = "/api/app/payments/blupal/status?invoice_id=$invoiceId",
            token = token
        )
        parseBlupalInvoice(json.getJSONObject("invoice"))
    }

    suspend fun markNotificationRead(
        token: String,
        notificationId: Long
    ) = withContext(Dispatchers.IO) {
        requestJson(
            method = "POST",
            path = "/api/app/notifications/read",
            token = token,
            body = JSONObject().put(
                "notification_id",
                notificationId
            )
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

    private fun parsePaymentSubmissions(
        array: JSONArray?
    ): List<PaymentSubmissionData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    PaymentSubmissionData(
                        id = j.optLong("id"),
                        chargeId = j.optLong("charge_id"),
                        chargeTitle = j.optString("charge_title"),
                        amount = j.optLong("amount"),
                        referenceId = j.optString("reference_id"),
                        note = j.optString("note"),
                        status = j.optString("status"),
                        reviewerNote = j.optString("reviewer_note"),
                        receiptNo = j.optString("receipt_no"),
                        createdAt = j.optString("created_at")
                    )
                )
            }
        }
    }

    private fun parseBlupalInvoice(j: JSONObject): BlupalInvoiceData =
        BlupalInvoiceData(
            invoiceId = j.optLong("invoice_id"),
            chargeId = j.optLong("charge_id"),
            chargeTitle = j.optString("charge_title"),
            amountToman = j.optLong("amount_toman"),
            amountRial = j.optLong("amount_rial"),
            finalAmountRial = j.optLong("final_amount_rial"),
            status = j.optString("status"),
            paymentLink = j.optString("payment_link"),
            callbackUrl = j.optString("callback_url"),
            cardNumber = j.optString("card_number"),
            mode = j.optString("mode"),
            receiptNo = j.optString("receipt_no"),
            createdAt = j.optString("created_at")
        )

    private fun parseBlupalInvoices(
        array: JSONArray?
    ): List<BlupalInvoiceData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(parseBlupalInvoice(array.getJSONObject(i)))
            }
        }
    }

    private fun parseNotifications(
        array: JSONArray?
    ): List<NotificationData> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    NotificationData(
                        id = j.optLong("id"),
                        type = j.optString("type"),
                        title = j.optString("title"),
                        body = j.optString("body"),
                        entityType = j.optString("entity_type"),
                        entityId = j.optString("entity_id"),
                        readAt = j.optString("read_at"),
                        createdAt = j.optString("created_at")
                    )
                )
            }
        }
    }
}

package com.azhand.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AdminSummary(
    val members: Int,
    val units: Int,
    val pendingPayments: Int,
    val openRequests: Int
)

data class AdminMember(
    val id: Long,
    val fullName: String,
    val mobile: String,
    val block: String,
    val unitNumber: String,
    val relation: String
)

data class AdminPayment(
    val id: Long,
    val fullName: String,
    val unitNumber: String,
    val block: String,
    val chargeTitle: String,
    val amount: Long,
    val referenceId: String,
    val status: String
)

data class AdminRequest(
    val id: Long,
    val fullName: String,
    val unitNumber: String,
    val block: String,
    val title: String,
    val category: String,
    val status: String
)

data class AdminCharge(
    val id: Long,
    val title: String,
    val unitNumber: String,
    val block: String,
    val amount: Long,
    val paidAmount: Long,
    val status: String
)

data class AdminOnlinePayment(
    val invoiceId: Long,
    val fullName: String,
    val unitNumber: String,
    val block: String,
    val chargeTitle: String,
    val amountToman: Long,
    val status: String,
    val receiptNo: String,
    val createdAt: String
)

data class BlupalIntegration(
    val configured: Boolean,
    val mode: String,
    val webhookUrl: String,
    val callbackPage: String
)

data class AdminData(
    val summary: AdminSummary,
    val members: List<AdminMember>,
    val payments: List<AdminPayment>,
    val requests: List<AdminRequest>,
    val charges: List<AdminCharge>,
    val onlinePayments: List<AdminOnlinePayment>
)

class AdminApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

object AdminApi {
    private fun base() = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun login(key: String): String = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/auth/login",
            body = JSONObject().put("admin_key", key)
        ).getString("token")
    }

    suspend fun data(token: String): AdminData = withContext(Dispatchers.IO) {
        val j = request("GET", "/api/admin/data", token)
        val s = j.getJSONObject("summary")

        AdminData(
            summary = AdminSummary(
                members = s.optInt("members"),
                units = s.optInt("units"),
                pendingPayments = s.optInt("pending_payments"),
                openRequests = s.optInt("open_requests")
            ),
            members = parseMembers(j.optJSONArray("members")),
            payments = parsePayments(
                j.optJSONArray("payment_submissions")
            ),
            requests = parseRequests(
                j.optJSONArray("service_requests")
            ),
            charges = parseCharges(j.optJSONArray("charges")),
            onlinePayments = parseOnlinePayments(
                j.optJSONArray("blupal_invoices")
            )
        )
    }

    suspend fun reviewPayment(
        token: String,
        id: Long,
        status: String,
        note: String = ""
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/payment/review",
            token,
            JSONObject()
                .put("payment_submission_id", id)
                .put("status", status)
                .put("reviewer_note", note)
        )
    }

    suspend fun setRequestStatus(
        token: String,
        id: Long,
        status: String,
        note: String = ""
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/service-request/status",
            token,
            JSONObject()
                .put("request_id", id)
                .put("status", status)
                .put("note", note)
        )
    }

    suspend fun resetAccessCode(
        token: String,
        id: Long
    ): String = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/member/access-code",
            token,
            JSONObject().put("member_id", id)
        ).getString("access_code")
    }

    suspend fun createExpense(
        token: String,
        title: String,
        category: String,
        amount: Long,
        date: String
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/expense",
            token,
            JSONObject()
                .put("title", title)
                .put("category", category)
                .put("amount", amount)
                .put("expense_date", date)
                .put("description", "")
        )
    }

    suspend fun createAnnouncement(
        token: String,
        title: String,
        body: String
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/announcement",
            token,
            JSONObject()
                .put("title", title)
                .put("body", body)
                .put("priority", "normal")
        )
    }

    suspend fun blupal(
        token: String
    ): BlupalIntegration = withContext(Dispatchers.IO) {
        val j = request(
            "GET",
            "/api/admin/integrations/blupal",
            token
        )
        BlupalIntegration(
            configured = j.optBoolean("configured"),
            mode = j.optString("mode"),
            webhookUrl = j.optString("webhook_url"),
            callbackPage = j.optString("callback_page")
        )
    }

    suspend fun configureBlupal(
        token: String,
        key: String
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/admin/integrations/blupal/configure",
            token,
            JSONObject().put("api_key", key)
        )
    }

    suspend fun logout(token: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                request(
                    "POST",
                    "/api/admin/auth/logout",
                    token,
                    JSONObject()
                )
            }
        }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val c = (
            URL(base() + path)
                .openConnection() as HttpURLConnection
            ).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                if (!token.isNullOrBlank()) {
                    setRequestProperty(
                        "Authorization",
                        "Bearer $token"
                    )
                }
                if (body != null) doOutput = true
            }

        try {
            if (body != null) {
                c.outputStream
                    .bufferedWriter(Charsets.UTF_8)
                    .use { it.write(body.toString()) }
            }

            val status = c.responseCode
            val raw = (
                if (status in 200..299) {
                    c.inputStream
                } else {
                    c.errorStream
                }
                )
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            val j =
                if (raw.isBlank()) JSONObject()
                else JSONObject(raw)

            if (
                status !in 200..299 ||
                !j.optBoolean(
                    "ok",
                    status in 200..299
                )
            ) {
                throw AdminApiException(
                    status,
                    j.optString("error", "خطای سرور")
                )
            }

            return j
        } finally {
            c.disconnect()
        }
    }

    private fun parseMembers(a: JSONArray?) = buildList {
        if (a != null) {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                add(
                    AdminMember(
                        id = j.optLong("id"),
                        fullName = j.optString("full_name"),
                        mobile = j.optString("mobile"),
                        block = j.optString("block"),
                        unitNumber = j.optString("unit_number"),
                        relation = j.optString("relation_type")
                    )
                )
            }
        }
    }

    private fun parsePayments(a: JSONArray?) = buildList {
        if (a != null) {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                add(
                    AdminPayment(
                        id = j.optLong("id"),
                        fullName = j.optString("full_name"),
                        unitNumber = j.optString("unit_number"),
                        block = j.optString("block"),
                        chargeTitle = j.optString("charge_title"),
                        amount = j.optLong("amount"),
                        referenceId = j.optString("reference_id"),
                        status = j.optString("status")
                    )
                )
            }
        }
    }

    private fun parseRequests(a: JSONArray?) = buildList {
        if (a != null) {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                add(
                    AdminRequest(
                        id = j.optLong("id"),
                        fullName = j.optString("full_name"),
                        unitNumber = j.optString("unit_number"),
                        block = j.optString("block"),
                        title = j.optString("title"),
                        category = j.optString("category"),
                        status = j.optString("status")
                    )
                )
            }
        }
    }

    private fun parseCharges(a: JSONArray?) = buildList {
        if (a != null) {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                add(
                    AdminCharge(
                        id = j.optLong("id"),
                        title = j.optString("title"),
                        unitNumber = j.optString("unit_number"),
                        block = j.optString("block"),
                        amount = j.optLong("amount"),
                        paidAmount = j.optLong("paid_amount"),
                        status = j.optString("status")
                    )
                )
            }
        }
    }

    private fun parseOnlinePayments(
        a: JSONArray?
    ) = buildList {
        if (a != null) {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                add(
                    AdminOnlinePayment(
                        invoiceId = j.optLong("invoice_id"),
                        fullName = j.optString("full_name"),
                        unitNumber = j.optString("unit_number"),
                        block = j.optString("block"),
                        chargeTitle = j.optString("charge_title"),
                        amountToman = j.optLong("amount_toman"),
                        status = j.optString("status"),
                        receiptNo = j.optString("receipt_no"),
                        createdAt = j.optString("created_at")
                    )
                )
            }
        }
    }
}

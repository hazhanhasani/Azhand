package com.azhand.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Navy = Color(0xFF07111F)
private val Surface = Color(0xFF0E1B2D)
private val Surface2 = Color(0xFF13233A)
private val Gold = Color(0xFFE4B84B)
private val TextPrimary = Color(0xFFF2F6FC)
private val TextMuted = Color(0xFFAAB8CB)
private val Success = Color(0xFF4DD39A)
private val Danger = Color(0xFFFF7783)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Gold,
                        background = Navy,
                        surface = Surface,
                        onPrimary = Navy,
                        onBackground = TextPrimary,
                        onSurface = TextPrimary
                    )
                ) { AzhandRoot() }
            }
        }
    }
}

private enum class AppTab(val label: String) {
    HOME("خانه"), FINANCE("مالی"), SERVICES("خدمات"), NOTICES("اعلانات"), ACCOUNT("حساب")
}

@Composable
private fun AzhandRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(SessionStore.getToken(context)) }
    var dashboard by remember { mutableStateOf<DashboardData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        updateInfo = when (val r = UpdateManager.checkForUpdate()) {
            is UpdateCheckResult.Available -> r.info
            else -> null
        }
    }

    LaunchedEffect(token, refreshKey) {
        val t = token ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            dashboard = ApiClient.dashboard(t)
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                SessionStore.clear(context)
                token = null
            } else error = e.message
        } catch (_: Exception) {
            error = "ارتباط با سرور برقرار نشد."
        } finally {
            loading = false
        }
    }

    if (token == null) {
        LoginScreen { newToken ->
            SessionStore.setToken(context, newToken)
            token = newToken
            refreshKey++
        }
    } else {
        ResidentApp(
            dashboard = dashboard,
            loading = loading,
            error = error,
            onRefresh = { refreshKey++ },
            onCreateOnlinePayment = { chargeId, amount, done ->
                scope.launch {
                    try {
                        done(ApiClient.createBlupalInvoice(token!!, chargeId, amount), null)
                        refreshKey++
                    } catch (e: Exception) {
                        done(null, e.message ?: "ساخت فاکتور ناموفق بود.")
                    }
                }
            },
            onCheckOnlinePayment = { invoiceId, done ->
                scope.launch {
                    try {
                        done(ApiClient.checkBlupalInvoice(token!!, invoiceId), null)
                        refreshKey++
                    } catch (e: Exception) {
                        done(null, e.message ?: "بررسی پرداخت ناموفق بود.")
                    }
                }
            },
            onCreateRequest = { category, title, description, done ->
                scope.launch {
                    try {
                        ApiClient.createServiceRequest(token!!, category, title, description)
                        refreshKey++
                        done(true, null)
                    } catch (e: Exception) {
                        done(false, e.message ?: "ثبت درخواست ناموفق بود.")
                    }
                }
            },
            onReadNotification = { id ->
                scope.launch {
                    runCatching { ApiClient.markNotificationRead(token!!, id) }
                    refreshKey++
                }
            },
            onLogout = {
                val t = token
                SessionStore.clear(context)
                token = null
                if (t != null) scope.launch { ApiClient.logout(t) }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("نسخه جدید آژند") },
            text = { Text("نسخه ${info.versionName} آماده نصب است.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            val apk = UpdateManager.downloadUpdate(context, info)
                            UpdateManager.startInstaller(context, apk)
                        }
                    }
                }) { Text("دانلود و نصب") }
            },
            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("بعداً") } },
            containerColor = Surface
        )
    }
}

@Composable
private fun LoginScreen(onLoggedIn: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("آژند", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("سامانه مجتمع تجاری، مسکونی • نسخه ۰.۸.۲", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(26.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(mobile, { mobile = it }, label = { Text("شماره موبایل") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("کد دسترسی ۶ رقمی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Danger, fontSize = 12.sp) }
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !busy && mobile.isNotBlank() && code.length == 6,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try { onLoggedIn(ApiClient.login(mobile.trim(), code).token) }
                            catch (e: Exception) { error = e.message ?: "ورود ناموفق بود." }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "در حال ورود..." else "ورود") }
            }
        }
    }
}

@Composable
private fun ResidentApp(
    dashboard: DashboardData?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onCreateOnlinePayment: (Long, Long, (BlupalInvoiceData?, String?) -> Unit) -> Unit,
    onCheckOnlinePayment: (Long, (BlupalInvoiceData?, String?) -> Unit) -> Unit,
    onCreateRequest: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onReadNotification: (Long) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf(AppTab.HOME) }
    Scaffold(
        containerColor = Navy,
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text("●") },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Navy, indicatorColor = Gold, selectedTextColor = Gold, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                    )
                }
            }
        }
    ) { padding ->
        when {
            loading && dashboard == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            error != null && dashboard == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(error, color = Danger) }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (tab) {
                    AppTab.HOME -> HomeScreen(dashboard, onRefresh)
                    AppTab.FINANCE -> FinanceScreen(dashboard, onCreateOnlinePayment, onCheckOnlinePayment)
                    AppTab.SERVICES -> ServicesScreen(dashboard, onCreateRequest)
                    AppTab.NOTICES -> NoticesScreen(dashboard, onReadNotification)
                    AppTab.ACCOUNT -> AccountScreen(dashboard, onLogout)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(data: DashboardData?, onRefresh: () -> Unit) {
    Text("آژند", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("مجتمع تجاری، مسکونی • نسخه ۰.۸.۲", color = TextMuted, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("سلام ${data?.profile?.fullName.orEmpty()} 👋", color = TextMuted)
            Text("واحد ${data?.profile?.unitNumber ?: "—"}", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("مانده حساب", color = TextMuted)
            Text(if ((data?.totalDue ?: 0) > 0) money(data?.totalDue ?: 0) else "تسویه", color = if ((data?.totalDue ?: 0) > 0) Danger else Success, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("بروزرسانی اطلاعات") }
        }
    }
}

@Composable
private fun FinanceScreen(
    data: DashboardData?,
    onCreateOnlinePayment: (Long, Long, (BlupalInvoiceData?, String?) -> Unit) -> Unit,
    onCheckOnlinePayment: (Long, (BlupalInvoiceData?, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedCharge by remember { mutableStateOf<ChargeData?>(null) }
    var invoice by remember { mutableStateOf<BlupalInvoiceData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("مالی", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Text("پرداخت آنلاین بلوپال", color = TextMuted, fontSize = 12.sp)
    Spacer(Modifier.height(14.dp))

    data?.charges.orEmpty().forEach { charge ->
        val remaining = (charge.amount - charge.paidAmount).coerceAtLeast(0)
        Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(charge.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(if (remaining == 0L) "پرداخت شد" else money(remaining), color = if (remaining == 0L) Success else Gold)
                }
                if (remaining > 0) {
                    Spacer(Modifier.height(10.dp))
                    Button(enabled = remaining >= 10_000L, onClick = { selectedCharge = charge; invoice = null; error = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (remaining >= 10_000L) "پرداخت آنلاین با بلوپال" else "کمتر از حداقل درگاه")
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("پرداخت‌های آنلاین", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    data?.blupalInvoices.orEmpty().forEach { inv ->
        Card(colors = CardDefaults.cardColors(containerColor = Surface2), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(15.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(inv.chargeTitle, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(statusFa(inv.status), color = if (inv.status == "PAID") Success else Gold)
                }
                Text("فاکتور ${inv.invoiceId} • ${money(inv.amountToman)}", color = TextMuted, fontSize = 12.sp)
                if (inv.receiptNo.isNotBlank()) Text("رسید: ${inv.receiptNo}", color = Gold, fontSize = 12.sp)
                if (inv.status == "PENDING") OutlinedButton(onClick = { onCheckOnlinePayment(inv.invoiceId) { _, _ -> } }, modifier = Modifier.fillMaxWidth()) { Text("بررسی وضعیت") }
            }
        }
    }

    selectedCharge?.let { charge ->
        val remaining = (charge.amount - charge.paidAmount).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { if (!busy) { selectedCharge = null; invoice = null; error = null } },
            title = { Text("پرداخت بلوپال") },
            text = {
                Column {
                    Text(charge.title, color = Gold, fontWeight = FontWeight.Bold)
                    Text("مبلغ شارژ: ${money(remaining)}", color = TextMuted)
                    invoice?.let { inv ->
                        Spacer(Modifier.height(10.dp))
                        Text("مبلغ دقیق واریز: ${rial(inv.finalAmountRial)}", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("وضعیت: ${statusFa(inv.status)}", color = if (inv.status == "PAID") Success else Gold)
                        if (inv.cardNumber.isNotBlank()) Text("کارت مقصد: ${inv.cardNumber}", color = TextMuted, fontSize = 12.sp)
                        if (inv.receiptNo.isNotBlank()) Text("رسید آژند: ${inv.receiptNo}", color = Gold, fontSize = 12.sp)
                        if (inv.status == "PENDING" && inv.paymentLink.startsWith("https://")) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(inv.paymentLink))) }, modifier = Modifier.fillMaxWidth()) { Text("رفتن به صفحه پرداخت") }
                            OutlinedButton(onClick = {
                                busy = true
                                onCheckOnlinePayment(inv.invoiceId) { result, message ->
                                    busy = false
                                    if (result != null) invoice = result else error = message
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال بررسی..." else "بررسی وضعیت") }
                        }
                    }
                    error?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                if (invoice == null) TextButton(enabled = !busy, onClick = {
                    busy = true
                    onCreateOnlinePayment(charge.id, remaining) { result, message ->
                        busy = false
                        if (result != null) invoice = result else error = message
                    }
                }) { Text(if (busy) "در حال ساخت..." else "ساخت فاکتور") }
            },
            dismissButton = { TextButton(onClick = { selectedCharge = null; invoice = null }) { Text("بستن") } },
            containerColor = Surface
        )
    }
}

@Composable
private fun ServicesScreen(data: DashboardData?, onCreateRequest: (String, String, String, (Boolean, String?) -> Unit) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Text("خدمات", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("عنوان درخواست") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("توضیحات") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onCreateRequest("عمومی", title, description) { ok, err -> message = if (ok) "درخواست ثبت شد." else err } }, modifier = Modifier.fillMaxWidth()) { Text("ثبت درخواست") }
            message?.let { Text(it, color = Gold, fontSize = 12.sp) }
        }
    }
    data?.serviceRequests.orEmpty().forEach { r ->
        Card(colors = CardDefaults.cardColors(containerColor = Surface2), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(14.dp)) { Text(r.title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(r.status, color = TextMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun NoticesScreen(data: DashboardData?, onRead: (Long) -> Unit) {
    Text("اعلانات", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    data?.notifications.orEmpty().forEach { n ->
        Card(colors = CardDefaults.cardColors(containerColor = if (n.readAt.isBlank()) Surface2 else Surface), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text(n.title, color = if (n.readAt.isBlank()) Gold else TextPrimary, fontWeight = FontWeight.Bold)
                Text(n.body, color = TextPrimary)
                if (n.readAt.isBlank()) TextButton(onClick = { onRead(n.id) }) { Text("خواندم") }
            }
        }
    }
    data?.announcements.orEmpty().forEach { a ->
        Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(15.dp)) { Text(a.title, color = Gold, fontWeight = FontWeight.Bold); Text(a.body, color = TextPrimary) }
        }
    }
}

@Composable
private fun AccountScreen(data: DashboardData?, onLogout: () -> Unit) {
    Text("حساب من", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(data?.profile?.fullName ?: "—", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(data?.profile?.mobile ?: "—", color = TextMuted)
            Text("واحد ${data?.profile?.unitNumber ?: "—"}", color = TextMuted)
            Text("نسخه ${BuildConfig.VERSION_NAME}", color = TextMuted, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
}

private fun money(value: Long): String = "${NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)} تومان"
private fun rial(value: Long): String = "${NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)} ریال"
private fun statusFa(status: String): String = when (status) { "PAID" -> "پرداخت شد"; "EXPIRED" -> "منقضی"; "CANCELED" -> "لغو شد"; else -> "در انتظار پرداخت" }

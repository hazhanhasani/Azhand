package com.azhand.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
                ) {
                    AzhandRoot()
                }
            }
        }
    }
}

private enum class AppTab(val label: String, val emoji: String) {
    HOME("خانه", "⌂"),
    FINANCE("مالی", "﷼"),
    SERVICES("خدمات", "⚙"),
    NOTICES("اعلانات", "●"),
    ACCOUNT("حساب", "◉")
}

@Composable
private fun AzhandRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(SessionStore.getToken(context)) }
    var dashboard by remember { mutableStateOf<DashboardData?>(null) }
    var dashboardLoading by remember { mutableStateOf(token != null) }
    var dashboardError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateStatus by remember {
        mutableStateOf("در انتظار بررسی بروزرسانی")
    }
    var lastUpdateCheckAt by remember { mutableLongStateOf(0L) }

    fun requestUpdateCheck(force: Boolean = false) {
        if (updateChecking) return

        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < 15_000L) return

        lastUpdateCheckAt = now
        updateChecking = true
        updateStatus = "در حال بررسی بروزرسانی..."

        scope.launch {
            when (val result = UpdateManager.checkForUpdate()) {
                is UpdateCheckResult.Available -> {
                    updateInfo = result.info
                    updateStatus =
                        "نسخه ${result.info.versionName} آماده نصب است."
                }

                is UpdateCheckResult.UpToDate -> {
                    updateInfo = null
                    updateStatus = "آخرین نسخه نصب است."
                }

                is UpdateCheckResult.Failed -> {
                    updateStatus = "خطا: ${result.message}"
                }
            }
            updateChecking = false
        }
    }

    LaunchedEffect(Unit) {
        requestUpdateCheck(force = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                requestUpdateCheck(force = false)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(token, refreshKey) {
        val current = token
        if (current == null) {
            dashboard = null
            dashboardLoading = false
            dashboardError = null
            return@LaunchedEffect
        }

        dashboardLoading = true
        dashboardError = null

        try {
            dashboard = ApiClient.dashboard(current)
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                SessionStore.clear(context)
                token = null
                dashboard = null
            } else {
                dashboardError = e.message
            }
        } catch (_: Exception) {
            dashboardError = "ارتباط با سرور برقرار نشد."
        } finally {
            dashboardLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
    ) {
        if (token == null) {
            LoginScreen(
                onLoggedIn = { newToken ->
                    SessionStore.setToken(context, newToken)
                    token = newToken
                    refreshKey++
                }
            )
        } else {
            ResidentApp(
                dashboard = dashboard,
                loading = dashboardLoading,
                error = dashboardError,
                onRefresh = { refreshKey++ },
                updateStatus = updateStatus,
                updateChecking = updateChecking,
                onCheckUpdate = { requestUpdateCheck(force = true) },
                onCreateOnlinePayment = {
                    chargeId,
                    amountToman,
                    done ->
                    val current = token
                    if (current == null) {
                        done(null, "نشست کاربری معتبر نیست.")
                    } else {
                        scope.launch {
                            try {
                                val invoice =
                                    ApiClient.createBlupalInvoice(
                                        current,
                                        chargeId,
                                        amountToman
                                    )
                                refreshKey++
                                done(invoice, null)
                            } catch (e: Exception) {
                                done(
                                    null,
                                    e.message ?: "ساخت فاکتور ناموفق بود."
                                )
                            }
                        }
                    }
                },
                onCheckOnlinePayment = {
                    invoiceId,
                    done ->
                    val current = token
                    if (current == null) {
                        done(null, "نشست کاربری معتبر نیست.")
                    } else {
                        scope.launch {
                            try {
                                val invoice =
                                    ApiClient.checkBlupalInvoice(
                                        current,
                                        invoiceId
                                    )
                                refreshKey++
                                done(invoice, null)
                            } catch (e: Exception) {
                                done(
                                    null,
                                    e.message ?: "بررسی پرداخت ناموفق بود."
                                )
                            }
                        }
                    }
                },
                onMarkNotificationRead = { notificationId ->
                    val current = token
                    if (current != null) {
                        scope.launch {
                            try {
                                ApiClient.markNotificationRead(
                                    current,
                                    notificationId
                                )
                                refreshKey++
                            } catch (_: Exception) {
                                // Keep the screen usable if marking read fails.
                            }
                        }
                    }
                },
                onSubmitPayment = {
                    chargeId,
                    amount,
                    referenceId,
                    note,
                    done ->
                    val current = token
                    if (current == null) {
                        done(false, "نشست کاربری معتبر نیست.")
                    } else {
                        scope.launch {
                            try {
                                ApiClient.submitPayment(
                                    current,
                                    chargeId,
                                    amount,
                                    referenceId,
                                    note
                                )
                                refreshKey++
                                done(true, null)
                            } catch (e: Exception) {
                                done(
                                    false,
                                    e.message ?: "ثبت پرداخت ناموفق بود."
                                )
                            }
                        }
                    }
                },
                onCreateRequest = { category, title, description, done ->
                    val current = token
                    if (current == null) {
                        done(false, "نشست کاربری معتبر نیست.")
                    } else {
                        scope.launch {
                            try {
                                ApiClient.createServiceRequest(
                                    current,
                                    category,
                                    title,
                                    description
                                )
                                refreshKey++
                                done(true, null)
                            } catch (e: Exception) {
                                done(false, e.message ?: "ثبت درخواست ناموفق بود.")
                            }
                        }
                    }
                },
                onLogout = {
                    val current = token
                    SessionStore.clear(context)
                    token = null
                    dashboard = null
                    if (current != null) {
                        scope.launch { ApiClient.logout(current) }
                    }
                }
            )
        }
    }

    val availableUpdate = updateInfo
    if (availableUpdate != null) {
        AlertDialog(
            onDismissRequest = {
                if (!updateBusy && !availableUpdate.mandatory) updateInfo = null
            },
            title = { Text("نسخه جدید آژند آماده است") },
            text = {
                Column {
                    Text("نسخه ${availableUpdate.versionName} آماده دانلود و نصب است.")
                    Spacer(Modifier.height(8.dp))
                    Text(availableUpdate.notes, color = TextMuted, fontSize = 13.sp)
                    if (updateMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(updateMessage!!, color = Gold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !updateBusy,
                    onClick = {
                        scope.launch {
                            updateBusy = true
                            updateMessage = "در حال دانلود و بررسی فایل..."
                            try {
                                val apk = UpdateManager.downloadUpdate(context, availableUpdate)
                                val started = UpdateManager.startInstaller(context, apk)
                                updateMessage = if (started) {
                                    "نصب‌کننده اندروید باز شد."
                                } else {
                                    "مجوز نصب برنامه‌های ناشناس را برای آژند فعال کن و دوباره بروزرسانی را بزن."
                                }
                            } catch (_: Exception) {
                                updateMessage = "دانلود یا بررسی بروزرسانی ناموفق بود. دوباره تلاش کن."
                            } finally {
                                updateBusy = false
                            }
                        }
                    }
                ) {
                    Text(if (updateBusy) "در حال دانلود..." else "دانلود و نصب")
                }
            },
            dismissButton = {
                if (!availableUpdate.mandatory) {
                    TextButton(enabled = !updateBusy, onClick = { updateInfo = null }) {
                        Text("بعداً")
                    }
                }
            },
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("آژند", color = Gold, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Spacer(Modifier.height(5.dp))
        Text("سامانه مجتمع تجاری، مسکونی", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(30.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("ورود ساکنین", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text("شماره موبایل و کد دسترسی واحد را وارد کنید.", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.take(16) },
                    label = { Text("شماره موبایل") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("کد دسترسی ۶ رقمی") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, color = Danger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    enabled = !busy && mobile.isNotBlank() && code.length == 6,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                onLoggedIn(ApiClient.login(mobile.trim(), code).token)
                            } catch (e: ApiException) {
                                error = when (e.statusCode) {
                                    401 -> "شماره موبایل یا کد دسترسی اشتباه است."
                                    429 -> "تلاش‌های زیادی انجام شده؛ چند دقیقه بعد دوباره امتحان کنید."
                                    else -> e.message ?: "ورود ناموفق بود."
                                }
                            } catch (_: Exception) {
                                error = "ارتباط با سرور برقرار نشد."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Navy)
                    } else {
                        Text("ورود", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("نسخه ۰.۸.۴", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ResidentApp(
    dashboard: DashboardData?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    updateStatus: String,
    updateChecking: Boolean,
    onCheckUpdate: () -> Unit,
    onCreateOnlinePayment: (
        Long,
        Long,
        (BlupalInvoiceData?, String?) -> Unit
    ) -> Unit,
    onCheckOnlinePayment: (
        Long,
        (BlupalInvoiceData?, String?) -> Unit
    ) -> Unit,
    onMarkNotificationRead: (Long) -> Unit,
    onSubmitPayment: (
        Long,
        Long,
        String,
        String,
        (Boolean, String?) -> Unit
    ) -> Unit,
    onCreateRequest: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    var selected by remember { mutableStateOf(AppTab.HOME) }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Text(tab.emoji, fontSize = 18.sp) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Navy,
                            selectedTextColor = Gold,
                            indicatorColor = Gold,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Navy).padding(padding)) {
            when {
                loading && dashboard == null -> LoadingScreen()
                error != null && dashboard == null -> ErrorScreen(error, onRefresh)
                else -> when (selected) {
                    AppTab.HOME -> HomeScreen(dashboard, onRefresh)
                    AppTab.FINANCE -> FinanceScreen(
                        dashboard,
                        onSubmitPayment,
                        onCreateOnlinePayment,
                        onCheckOnlinePayment
                    )
                    AppTab.SERVICES -> ServicesScreen(dashboard, onCreateRequest)
                    AppTab.NOTICES -> NoticesScreen(dashboard, onMarkNotificationRead)
                    AppTab.ACCOUNT -> AccountScreen(
                        dashboard,
                        updateStatus,
                        updateChecking,
                        onCheckUpdate,
                        onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Gold)
            Spacer(Modifier.height(12.dp))
            Text("در حال دریافت اطلاعات واحد...", color = TextMuted)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.padding(22.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("دریافت اطلاعات ناموفق بود", color = Danger)
                Spacer(Modifier.height(8.dp))
                Text(message, color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("تلاش مجدد") }
            }
        }
    }
}

@Composable
private fun ScreenContainer(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 25.sp)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = TextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        content()
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun HomeScreen(data: DashboardData?, onRefresh: () -> Unit) = ScreenContainer(
    title = "آژند",
    subtitle = "مجتمع تجاری، مسکونی • نسخه ۰.۸.۴"
) {
    val profile = data?.profile
    val unitText = when {
        profile == null -> "واحد —"
        profile.block.isBlank() -> "واحد ${profile.unitNumber}"
        else -> "بلوک ${profile.block} • واحد ${profile.unitNumber}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("سلام ${profile?.fullName.orEmpty()} 👋", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(unitText, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text("مانده حساب", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            val due = data?.totalDue ?: 0L
            Text(
                if (due > 0) "${money(due)} بدهکار" else "تسویه",
                color = if (due > 0) Danger else Success,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("بروزرسانی اطلاعات")
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniStat(
            modifier = Modifier.weight(1f),
            title = data?.currentChargeTitle?.ifBlank { "آخرین شارژ" } ?: "آخرین شارژ",
            value = shortMoney(data?.currentChargeAmount ?: 0L),
            hint = "تومان"
        )
        MiniStat(
            modifier = Modifier.weight(1f),
            title = "درخواست باز",
            value = (data?.openRequests ?: 0).toString(),
            hint = "مورد"
        )
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("آخرین اعلان")
    val latest = data?.announcements?.firstOrNull()
    if (latest == null) EmptyCard("هنوز اعلانی ثبت نشده است.")
    else NoticeCard(latest.title, latest.body, latest.publishedAt)
}

@Composable
private fun FinanceScreen(
    data: DashboardData?,
    onSubmitPayment: (
        Long,
        Long,
        String,
        String,
        (Boolean, String?) -> Unit
    ) -> Unit,
    onCreateOnlinePayment: (
        Long,
        Long,
        (BlupalInvoiceData?, String?) -> Unit
    ) -> Unit,
    onCheckOnlinePayment: (
        Long,
        (BlupalInvoiceData?, String?) -> Unit
    ) -> Unit
) = ScreenContainer(
    title = "مالی",
    subtitle = "شارژ، پرداخت آنلاین، واریز دستی و هزینه‌های واقعی واحد"
) {
    val context = LocalContext.current

    var paymentCharge by remember { mutableStateOf<ChargeData?>(null) }
    var onlineCharge by remember { mutableStateOf<ChargeData?>(null) }
    var onlineInvoice by remember { mutableStateOf<BlupalInvoiceData?>(null) }
    var onlineBusy by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }

    SectionTitle("صورتحساب واحد")
    val charges = data?.charges.orEmpty()

    if (charges.isEmpty()) {
        EmptyCard("هنوز شارژی برای این واحد ثبت نشده است.")
    } else {
        charges.forEach { charge ->
            val remaining =
                (charge.amount - charge.paidAmount).coerceAtLeast(0)

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            charge.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (remaining == 0L) "پرداخت شد" else "مانده",
                            color = if (remaining == 0L) Success else Danger,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(money(remaining), color = TextMuted)

                    if (remaining > 0L) {
                        Spacer(Modifier.height(12.dp))

                        Button(
                            enabled = remaining >= 10_000L,
                            onClick = {
                                onlineCharge = charge
                                onlineInvoice = null
                                onlineError = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Navy
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (remaining >= 10_000L) {
                                    "پرداخت آنلاین با بلوپال"
                                } else {
                                    "کمتر از حداقل درگاه"
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { paymentCharge = charge },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ثبت واریز دستی")
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("پرداخت‌های آنلاین")

    val onlinePayments = data?.blupalInvoices.orEmpty()
    if (onlinePayments.isEmpty()) {
        EmptyCard("هنوز پرداخت آنلاین ثبت نشده است.")
    } else {
        onlinePayments.forEach { invoice ->
            OnlinePaymentCard(
                invoice = invoice,
                onCheck = {
                    onlineBusy = true
                    onlineError = null
                    onCheckOnlinePayment(
                        invoice.invoiceId
                    ) { result, error ->
                        onlineBusy = false
                        if (result != null) {
                            onlineInvoice = result
                        } else {
                            onlineError = error
                        }
                    }
                }
            )
            Spacer(Modifier.height(9.dp))
        }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("واریزهای دستی ثبت‌شده")

    val submissions = data?.paymentSubmissions.orEmpty()
    if (submissions.isEmpty()) {
        EmptyCard("هنوز واریزی برای بررسی مدیریت ثبت نشده است.")
    } else {
        submissions.forEach { payment ->
            PaymentSubmissionCard(payment)
            Spacer(Modifier.height(9.dp))
        }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("هزینه‌های اخیر مجتمع")

    val expenses = data?.expenses.orEmpty()
    if (expenses.isEmpty()) {
        EmptyCard("هزینه‌ای ثبت نشده است.")
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 6.dp
                )
            ) {
                expenses.forEach {
                    ExpenseRow(it.title, money(it.amount))
                }
            }
        }
    }

    val selectedManualCharge = paymentCharge
    if (selectedManualCharge != null) {
        PaymentSubmissionDialog(
            charge = selectedManualCharge,
            onDismiss = { paymentCharge = null },
            onSubmit = { amount, referenceId, note, done ->
                onSubmitPayment(
                    selectedManualCharge.id,
                    amount,
                    referenceId,
                    note
                ) { ok, error ->
                    if (ok) paymentCharge = null
                    done(ok, error)
                }
            }
        )
    }

    val selectedOnlineCharge = onlineCharge
    if (selectedOnlineCharge != null) {
        val remaining = (
            selectedOnlineCharge.amount -
                selectedOnlineCharge.paidAmount
            ).coerceAtLeast(0)

        AlertDialog(
            onDismissRequest = {
                if (!onlineBusy) {
                    onlineCharge = null
                    onlineInvoice = null
                    onlineError = null
                }
            },
            title = { Text("پرداخت آنلاین بلوپال") },
            text = {
                Column {
                    Text(
                        selectedOnlineCharge.title,
                        color = Gold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "مانده شارژ: ${money(remaining)}",
                        color = TextMuted
                    )

                    if (onlineInvoice == null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "فاکتور روی سرور آژند ساخته می‌شود؛ کلید درگاه داخل اپ ذخیره نمی‌شود.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        val invoice = onlineInvoice!!
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "مبلغ دقیق پرداخت: ${rial(invoice.finalAmountRial)}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "وضعیت: ${onlineStatus(invoice.status)}",
                            color = if (invoice.status == "PAID") Success else Gold
                        )

                        if (invoice.cardNumber.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "کارت مقصد: ${invoice.cardNumber}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        if (invoice.receiptNo.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "رسید آژند: ${invoice.receiptNo}",
                                color = Gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        if (
                            invoice.status == "PENDING" &&
                            invoice.paymentLink.startsWith("https://")
                        ) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    invoice.callbackUrl
                                                        .takeIf { it.startsWith("https://") }
                                                        ?: invoice.paymentLink
                                                )
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ادامه پرداخت امن")
                            }

                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                enabled = !onlineBusy,
                                onClick = {
                                    onlineBusy = true
                                    onlineError = null
                                    onCheckOnlinePayment(
                                        invoice.invoiceId
                                    ) { result, error ->
                                        onlineBusy = false
                                        if (result != null) {
                                            onlineInvoice = result
                                        } else {
                                            onlineError = error
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (onlineBusy) {
                                        "در حال بررسی..."
                                    } else {
                                        "بررسی وضعیت پرداخت"
                                    }
                                )
                            }
                        }
                    }

                    onlineError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            color = Danger,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (onlineInvoice == null) {
                    TextButton(
                        enabled = !onlineBusy && remaining >= 10_000L,
                        onClick = {
                            onlineBusy = true
                            onlineError = null
                            onCreateOnlinePayment(
                                selectedOnlineCharge.id,
                                remaining
                            ) { invoice, error ->
                                onlineBusy = false
                                if (invoice != null) {
                                    onlineInvoice = invoice
                                } else {
                                    onlineError = error
                                }
                            }
                        }
                    ) {
                        Text(
                            if (onlineBusy) "در حال ساخت..."
                            else "ساخت فاکتور"
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !onlineBusy,
                    onClick = {
                        onlineCharge = null
                        onlineInvoice = null
                        onlineError = null
                    }
                ) {
                    Text("بستن")
                }
            },
            containerColor = Surface
        )
    }
}

@Composable
private fun OnlinePaymentCard(
    invoice: BlupalInvoiceData,
    onCheck: () -> Unit
) {
    val statusColor = when (invoice.status) {
        "PAID" -> Success
        "EXPIRED", "CANCELED" -> Danger
        else -> Gold
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    invoice.chargeTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    onlineStatus(invoice.status),
                    color = statusColor,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(money(invoice.amountToman), color = TextMuted)
            Text(
                "شناسه فاکتور: ${invoice.invoiceId}",
                color = TextMuted,
                fontSize = 11.sp
            )

            if (invoice.receiptNo.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "رسید: ${invoice.receiptNo}",
                    color = Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (invoice.status == "PENDING") {
                Spacer(Modifier.height(9.dp))
                OutlinedButton(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بررسی وضعیت")
                }
            }
        }
    }
}

private fun rial(value: Long): String =
    "${
        NumberFormat.getNumberInstance(
            Locale("fa", "IR")
        ).format(value)
    } ریال"

private fun onlineStatus(status: String): String =
    when (status) {
        "PAID" -> "پرداخت شد"
        "EXPIRED" -> "منقضی"
        "CANCELED" -> "لغو شد"
        else -> "در انتظار پرداخت"
    }

@Composable
private fun PaymentSubmissionDialog(
    charge: ChargeData,
    onDismiss: () -> Unit,
    onSubmit: (
        Long,
        String,
        String,
        (Boolean, String?) -> Unit
    ) -> Unit
) {
    val remaining =
        (charge.amount - charge.paidAmount).coerceAtLeast(0)

    var amount by remember {
        mutableStateOf(remaining.toString())
    }
    var referenceId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = {
            Text("ثبت واریز")
        },
        text = {
            Column {
                Text(
                    charge.title,
                    color = Gold,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    "مانده: ${money(remaining)}",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { ch ->
                            ch in '0'..'9'
                        }.take(12)
                    },
                    label = { Text("مبلغ واریزی (تومان)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = referenceId,
                    onValueChange = {
                        referenceId = it.take(80)
                    },
                    label = {
                        Text("شماره پیگیری / مرجع")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("توضیح اختیاری") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = Danger,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                    amount.toLongOrNull()?.let {
                        it > 0 && it <= remaining
                    } == true &&
                    referenceId.isNotBlank(),
                onClick = {
                    val value = amount.toLongOrNull()
                    if (value == null) {
                        error = "مبلغ نامعتبر است."
                        return@TextButton
                    }

                    busy = true
                    error = null

                    onSubmit(
                        value,
                        referenceId.trim(),
                        note.trim()
                    ) { ok, message ->
                        busy = false
                        if (!ok) {
                            error =
                                message ?: "ثبت واریز ناموفق بود."
                        }
                    }
                }
            ) {
                Text(
                    if (busy) "در حال ثبت..."
                    else "ارسال برای تأیید"
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) {
                Text("انصراف")
            }
        },
        containerColor = Surface
    )
}

@Composable
private fun PaymentSubmissionCard(
    payment: PaymentSubmissionData
) {
    val statusLabel = when (payment.status) {
        "approved" -> "تأیید شد"
        "rejected" -> "رد شد"
        else -> "در انتظار بررسی"
    }

    val statusColor = when (payment.status) {
        "approved" -> Success
        "rejected" -> Danger
        else -> Gold
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    payment.chargeTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusLabel,
                    color = statusColor,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                money(payment.amount),
                color = TextMuted
            )

            Spacer(Modifier.height(5.dp))
            Text(
                "پیگیری: ${payment.referenceId}",
                color = TextMuted,
                fontSize = 12.sp
            )

            if (payment.receiptNo.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "رسید: ${payment.receiptNo}",
                    color = Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (payment.reviewerNote.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "یادداشت مدیریت: ${payment.reviewerNote}",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ServicesScreen(
    data: DashboardData?,
    onCreateRequest: (String, String, String, (Boolean, String?) -> Unit) -> Unit
) = ScreenContainer(
    title = "خدمات",
    subtitle = "ثبت و پیگیری درخواست‌های واحد"
) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text("ثبت درخواست جدید", fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("درخواست‌های من")

    val requests = data?.serviceRequests.orEmpty()
    if (requests.isEmpty()) EmptyCard("هنوز درخواستی ثبت نکرده‌اید.")
    else requests.forEach { request ->
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(17.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(request.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        requestStatus(request.status),
                        color = if (request.status in listOf("closed", "done")) Success else Gold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(request.category, color = TextMuted, fontSize = 12.sp)
                if (request.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(request.description, color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }

    if (showDialog) {
        CreateRequestDialog(
            onDismiss = { showDialog = false },
            onSubmit = { category, title, description, done ->
                onCreateRequest(category, title, description) { ok, err ->
                    if (ok) showDialog = false
                    done(ok, err)
                }
            }
        )
    }
}

@Composable
private fun CreateRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var category by remember { mutableStateOf("تأسیسات") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("درخواست خدمات") },
        text = {
            Column {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(40) },
                    label = { Text("دسته‌بندی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("عنوان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("توضیحات") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && category.isNotBlank() && title.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    onSubmit(category.trim(), title.trim(), description.trim()) { ok, message ->
                        busy = false
                        if (!ok) error = message ?: "ثبت درخواست ناموفق بود."
                    }
                }
            ) {
                Text(if (busy) "در حال ثبت..." else "ثبت")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") }
        },
        containerColor = Surface
    )
}

@Composable
private fun NoticesScreen(
    data: DashboardData?,
    onMarkNotificationRead: (Long) -> Unit
) = ScreenContainer(
    title = "اعلانات",
    subtitle = if ((data?.unreadNotifications ?: 0) > 0) {
        "${data?.unreadNotifications ?: 0} پیام خوانده‌نشده"
    } else {
        "پیام‌های شخصی و اطلاعیه‌های مجتمع"
    }
) {
    SectionTitle("پیام‌های من")

    val notifications = data?.notifications.orEmpty()

    if (notifications.isEmpty()) {
        EmptyCard("پیام شخصی جدیدی ندارید.")
    } else {
        notifications.forEach { notification ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        if (notification.readAt.isBlank()) {
                            Surface2
                        } else {
                            Surface
                        }
                ),
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            notification.title,
                            color =
                                if (notification.readAt.isBlank()) {
                                    Gold
                                } else {
                                    TextPrimary
                                },
                            fontWeight = FontWeight.Bold
                        )

                        if (notification.readAt.isBlank()) {
                            Text(
                                "جدید",
                                color = Gold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(7.dp))

                    Text(
                        notification.body,
                        color = TextPrimary,
                        lineHeight = 21.sp
                    )

                    if (notification.createdAt.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            notification.createdAt,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    if (notification.readAt.isBlank()) {
                        Spacer(Modifier.height(9.dp))
                        TextButton(
                            onClick = {
                                onMarkNotificationRead(
                                    notification.id
                                )
                            }
                        ) {
                            Text("علامت‌گذاری به‌عنوان خوانده‌شده")
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("اطلاعیه‌های مجتمع")

    val announcements = data?.announcements.orEmpty()

    if (announcements.isEmpty()) {
        EmptyCard("هنوز اعلانی منتشر نشده است.")
    } else {
        announcements.forEach { notice ->
            NoticeCard(
                notice.title,
                notice.body,
                notice.publishedAt
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AccountScreen(
    data: DashboardData?,
    updateStatus: String,
    updateChecking: Boolean,
    onCheckUpdate: () -> Unit,
    onLogout: () -> Unit
) = ScreenContainer(
    title = "حساب من",
    subtitle = "پروفایل، اطلاعات واحد و بروزرسانی"
) {
    val p = data?.profile

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            ProfileLine("نام", p?.fullName ?: "—")
            ProfileLine("شماره موبایل", p?.mobile ?: "—")
            ProfileLine("واحد", p?.unitNumber ?: "—")
            ProfileLine("بلوک", p?.block?.ifBlank { "—" } ?: "—")
            ProfileLine("نوع عضویت", relationLabel(p?.relation.orEmpty()))
            ProfileLine("نسخه اپ", BuildConfig.VERSION_NAME)
        }
    }

    Spacer(Modifier.height(14.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(
                "بروزرسانی اپ",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                updateStatus,
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !updateChecking,
                onClick = onCheckUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Navy
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (updateChecking) "در حال بررسی..."
                    else "بررسی بروزرسانی"
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("خروج از حساب")
    }
}

@Composable
private fun MiniStat(modifier: Modifier, title: String, value: String, hint: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(hint, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable private fun SectionTitle(title: String) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message, color = TextMuted, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
    }
}

@Composable
private fun FinanceRow(title: String, amount: String, status: String, statusColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(status, color = statusColor, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(amount, color = TextMuted)
        }
    }
}

@Composable
private fun ExpenseRow(title: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextPrimary)
        Text(amount, color = TextMuted)
    }
    HorizontalDivider(color = Color(0xFF20354F))
}

@Composable
private fun NoticeCard(title: String, body: String, time: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body, color = TextPrimary, lineHeight = 21.sp)
            if (time.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(time, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

private fun money(value: Long): String =
    "${NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)} تومان"

private fun shortMoney(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale("fa", "IR"), "%.1f م", value.toDouble() / 1_000_000.0)
    value > 0 -> NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value)
    else -> "۰"
}

private fun relationLabel(value: String): String = when (value) {
    "owner" -> "مالک"
    "tenant" -> "مستأجر"
    "resident" -> "ساکن"
    "manager" -> "مدیر"
    else -> value.ifBlank { "—" }
}

private fun requestStatus(value: String): String = when (value) {
    "new" -> "جدید"
    "in_progress" -> "در حال انجام"
    "done" -> "انجام شد"
    "closed" -> "بسته"
    else -> value
}

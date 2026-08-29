package com.azhand.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
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
private val TextPrimary = Color(0xFFF3F7FC)
private val Muted = Color(0xFFAAB8CB)
private val Good = Color(0xFF4DD39A)
private val Bad = Color(0xFFFF7783)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Gold,
                        background = Navy,
                        surface = Surface
                    )
                ) {
                    AdminRoot()
                }
            }
        }
    }
}

private enum class AdminTab(
    val label: String,
    val emoji: String
) {
    HOME("داشبورد", "🏠"),
    PAYMENTS("پرداخت‌ها", "💳"),
    REQUESTS("درخواست‌ها", "🛠"),
    MEMBERS("ساکنین", "👥"),
    MORE("بیشتر", "⚙️")
}

@Composable
private fun AdminRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember {
        mutableStateOf(AdminSessionStore.get(context))
    }
    var data by remember { mutableStateOf<AdminData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var updateInfo by remember {
        mutableStateOf<AdminUpdateInfo?>(null)
    }

    LaunchedEffect(Unit) {
        updateInfo = runCatching {
            AdminUpdateManager.check()
        }.getOrNull()
    }

    LaunchedEffect(token, refresh) {
        val current = token ?: return@LaunchedEffect

        try {
            data = AdminApi.data(current)
            error = null
        } catch (e: AdminApiException) {
            if (e.statusCode == 401) {
                AdminSessionStore.clear(context)
                token = null
            } else {
                error = e.message
            }
        } catch (e: Exception) {
            error = e.message ?: "ارتباط با سرور برقرار نشد."
        }
    }

    if (token == null) {
        LoginScreen(
            onLogin = { key, done ->
                scope.launch {
                    try {
                        val newToken = AdminApi.login(key)
                        AdminSessionStore.set(
                            context,
                            newToken
                        )
                        token = newToken
                        done(null)
                    } catch (e: Exception) {
                        done(
                            e.message ?: "ورود ناموفق بود."
                        )
                    }
                }
            }
        )
    } else {
        AdminApp(
            data = data,
            error = error,
            token = token!!,
            onChanged = { refresh++ },
            onLogout = {
                val current = token
                AdminSessionStore.clear(context)
                token = null

                if (current != null) {
                    scope.launch {
                        AdminApi.logout(current)
                    }
                }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                updateInfo = null
            },
            title = {
                Text("نسخه جدید مدیریت آژند")
            },
            text = {
                Text(
                    "نسخه ${info.versionName} آماده نصب است."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val apk =
                                    AdminUpdateManager.download(
                                        context,
                                        info
                                    )
                                AdminUpdateManager.install(
                                    context,
                                    apk
                                )
                            }
                        }
                    }
                ) {
                    Text("دانلود و نصب")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        updateInfo = null
                    }
                ) {
                    Text("بعداً")
                }
            },
            containerColor = Surface
        )
    }
}

@Composable
private fun LoginScreen(
    onLogin: (String, (String?) -> Unit) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Surface
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Image(
                painter = painterResource(
                    id = R.mipmap.ic_launcher
                ),
                contentDescription = "آژند",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(12.dp)
                    .size(86.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "مدیریت آژند",
            color = Gold,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "اپلیکیشن مدیر مجتمع • نسخه ۰.۹.۳",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Surface
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(18.dp)
            ) {
                Text(
                    "ورود مدیر",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "کلید مدیریت فقط برای ساخت Session امن استفاده می‌شود.",
                    color = Muted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Setup Admin Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = Bad,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    enabled = !busy && key.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null

                        onLogin(key) { message ->
                            busy = false
                            error = message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Navy
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (busy) {
                            "در حال ورود..."
                        } else {
                            "ورود به مدیریت"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminApp(
    data: AdminData?,
    error: String?,
    token: String,
    onChanged: () -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember {
        mutableStateOf(AdminTab.HOME)
    }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            NavigationBar(
                containerColor = Surface
            ) {
                AdminTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                        },
                        icon = {
                            Text(
                                item.emoji,
                                fontSize = 18.sp
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                fontSize = 10.sp
                            )
                        },
                        colors =
                            NavigationBarItemDefaults.colors(
                                indicatorColor = Gold,
                                selectedIconColor = Navy,
                                selectedTextColor = Gold,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 16.dp,
                    vertical = 14.dp
                )
        ) {
            BrandHeader(
                iranNow = data?.iranNow.orEmpty(),
                onRefresh = onChanged
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                InfoCard(
                    title = "خطای ارتباط",
                    body = it,
                    accent = Bad
                )
            }

            Spacer(Modifier.height(16.dp))

            when (tab) {
                AdminTab.HOME -> DashboardTab(
                    data = data
                )

                AdminTab.PAYMENTS -> PaymentsTab(
                    data = data,
                    token = token,
                    onChanged = onChanged
                )

                AdminTab.REQUESTS -> RequestsTab(
                    data = data,
                    token = token,
                    onChanged = onChanged
                )

                AdminTab.MEMBERS -> MembersTab(
                    data = data,
                    token = token,
                    onChanged = onChanged
                )

                AdminTab.MORE -> MoreTab(
                    token = token,
                    onChanged = onChanged,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(
    iranNow: String,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(
                    id = R.mipmap.ic_launcher
                ),
                contentDescription = "آژند",
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.size(10.dp))

            Column {
                Text(
                    "مدیریت آژند",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "نسخه ${BuildConfig.VERSION_NAME}",
                    color = Muted,
                    fontSize = 11.sp
                )

                if (iranNow.isNotBlank()) {
                    Text(
                        "🕒 $iranNow",
                        color = Gold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        TextButton(
            onClick = onRefresh
        ) {
            Text("↻ تازه‌سازی")
        }
    }
}

@Composable
private fun DashboardTab(
    data: AdminData?
) {
    val summary = data?.summary
    val onlinePending =
        data?.onlinePayments
            .orEmpty()
            .count { it.status == "PENDING" }

    SectionTitle(
        "نمای کلی",
        "وضعیت لحظه‌ای مجتمع"
    )

    MetricCard(
        emoji = "👥",
        title = "اعضای فعال",
        value = summary?.members ?: 0,
        hint = "ساکنین ثبت‌شده"
    )

    MetricCard(
        emoji = "🏢",
        title = "واحدهای فعال",
        value = summary?.units ?: 0,
        hint = "واحدهای قابل مدیریت"
    )

    MetricCard(
        emoji = "💳",
        title = "واریزهای منتظر",
        value = summary?.pendingPayments ?: 0,
        hint = "نیازمند تأیید مدیریت"
    )

    MetricCard(
        emoji = "🛠",
        title = "درخواست‌های باز",
        value = summary?.openRequests ?: 0,
        hint = "در انتظار رسیدگی"
    )

    MetricCard(
        emoji = "🔵",
        title = "بلوپال در انتظار",
        value = onlinePending,
        hint = "فاکتورهای پرداخت‌نشده"
    )

    Spacer(Modifier.height(14.dp))

    SectionTitle(
        "خلاصه مالی",
        "جمع شارژها و وصولی کل مجتمع"
    )

    MoneyMetricCard(
        title = "کل شارژ صادرشده",
        value = summary?.totalBilled ?: 0L,
        accent = Muted
    )

    MoneyMetricCard(
        title = "مبلغ وصول‌شده",
        value = summary?.totalPaid ?: 0L,
        accent = Good
    )

    MoneyMetricCard(
        title = "مانده قابل وصول",
        value = summary?.totalDue ?: 0L,
        accent = Gold
    )

    MoneyMetricCard(
        title = "موجودی فعلی ساختمان",
        value = summary?.buildingBalance ?: 0L,
        accent =
            if (
                (summary?.buildingBalance ?: 0L) >= 0L
            ) {
                Good
            } else {
                Bad
            }
    )

    Spacer(Modifier.height(16.dp))

    SectionTitle(
        "آخرین پرداخت آنلاین",
        "وضعیت فاکتورهای بلوپال"
    )

    val latestOnline =
        data?.onlinePayments.orEmpty().take(3)

    if (latestOnline.isEmpty()) {
        EmptyState("پرداخت آنلاین ثبت نشده است.")
    } else {
        latestOnline.forEach {
            OnlinePaymentAdminCard(it)
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(12.dp))

    SectionTitle(
        "آخرین درخواست‌ها",
        "مواردی که نیاز به توجه دارند"
    )

    val latestRequests =
        data?.requests.orEmpty().take(3)

    if (latestRequests.isEmpty()) {
        EmptyState("درخواستی ثبت نشده است.")
    } else {
        latestRequests.forEach {
            CompactRequestCard(it)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PaymentsTab(
    data: AdminData?,
    token: String,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var showChargeForm by remember {
        mutableStateOf(false)
    }
    var periodKey by remember {
        mutableStateOf("")
    }
    var chargeTitle by remember {
        mutableStateOf("شارژ ماهانه")
    }
    var chargeAmount by remember {
        mutableStateOf("")
    }
    var chargeDueDate by remember {
        mutableStateOf("")
    }
    var chargeBlock by remember {
        mutableStateOf("")
    }
    var chargeMessage by remember {
        mutableStateOf<String?>(null)
    }
    var templateDueDay by remember {
        mutableStateOf("10")
    }
    var templates by remember {
        mutableStateOf<List<ChargeTemplate>>(
            emptyList()
        )
    }

    LaunchedEffect(token) {
        templates = runCatching {
            AdminApi.chargeTemplates(token)
        }.getOrDefault(emptyList())
    }

    SectionTitle(
        "صدور شارژ",
        "صدور یکجای شارژ برای تمام واحدهای فعال"
    )

    Button(
        onClick = {
            showChargeForm = !showChargeForm
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Navy
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (showChargeForm) {
                "بستن فرم صدور شارژ"
            } else {
                "＋ صدور شارژ ماهانه"
            }
        )
    }

    if (showChargeForm) {
        Spacer(Modifier.height(10.dp))

        FormCard {
            OutlinedTextField(
                value = periodKey,
                onValueChange = {
                    periodKey = it.take(40)
                },
                label = {
                    Text("دوره شمسی، مثال 1405-07")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = chargeTitle,
                onValueChange = {
                    chargeTitle = it.take(100)
                },
                label = {
                    Text("عنوان شارژ")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = chargeAmount,
                onValueChange = {
                    chargeAmount =
                        it.filter(Char::isDigit)
                },
                label = {
                    Text("مبلغ هر واحد - تومان")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = chargeDueDate,
                onValueChange = {
                    chargeDueDate = it.take(20)
                },
                label = {
                    Text("تاریخ سررسید شمسی، مثال 1405-07-10")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = chargeBlock,
                onValueChange = {
                    chargeBlock = it.take(30)
                },
                label = {
                    Text("فقط این بلوک - اختیاری")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = templateDueDay,
                onValueChange = {
                    templateDueDay =
                        it.filter(Char::isDigit)
                            .take(2)
                },
                label = {
                    Text("روز سررسید الگو 1 تا 28")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                enabled =
                    chargeTitle.isNotBlank() &&
                        (chargeAmount.toLongOrNull() ?: 0L) > 0 &&
                        (templateDueDay.toIntOrNull() ?: 0) in 1..28,
                onClick = {
                    val amount =
                        chargeAmount.toLongOrNull()
                            ?: return@OutlinedButton
                    val dueDay =
                        templateDueDay.toIntOrNull()
                            ?: return@OutlinedButton

                    scope.launch {
                        try {
                            AdminApi.saveChargeTemplate(
                                token = token,
                                title = chargeTitle,
                                amount = amount,
                                dueDay = dueDay,
                                block = chargeBlock
                            )
                            templates =
                                AdminApi.chargeTemplates(
                                    token
                                )
                            chargeMessage =
                                "الگوی شارژ ذخیره شد."
                        } catch (e: Exception) {
                            chargeMessage = e.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ذخیره به‌عنوان الگوی شارژ")
            }

            Spacer(Modifier.height(10.dp))

            Button(
                enabled =
                    periodKey.isNotBlank() &&
                        chargeTitle.isNotBlank() &&
                        (chargeAmount.toLongOrNull() ?: 0L) > 0,
                onClick = {
                    val amount =
                        chargeAmount.toLongOrNull()
                            ?: return@Button

                    scope.launch {
                        try {
                            val result =
                                AdminApi.createBulkCharges(
                                    token = token,
                                    periodKey = periodKey,
                                    title = chargeTitle,
                                    amount = amount,
                                    dueDate = chargeDueDate,
                                    block = chargeBlock
                                )

                            chargeMessage =
                                "شارژ برای ${result.unitsTargeted} واحد صادر شد."

                            onChanged()
                        } catch (e: Exception) {
                            chargeMessage = e.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("صدور برای واحدها")
            }
        }
    }

    if (templates.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))

        SectionTitle(
            "الگوهای شارژ",
            "برای پرکردن سریع فرم لمس کن"
        )

        templates.take(6).forEach { template ->
            OutlinedButton(
                onClick = {
                    chargeTitle = template.title
                    chargeAmount =
                        template.amount.toString()
                    templateDueDay =
                        template.dueDay.toString()
                    chargeBlock =
                        template.block
                    showChargeForm = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    "${template.title} • ${money(template.amount)}"
                )
            }
        }
    }

    chargeMessage?.let {
        Spacer(Modifier.height(8.dp))
        InfoCard(
            title = "نتیجه صدور شارژ",
            body = it,
            accent = Gold
        )
    }

    Spacer(Modifier.height(18.dp))

    SectionTitle(
        "پرداخت‌های آنلاین",
        "فاکتورهای بلوپال"
    )

    val online = data?.onlinePayments.orEmpty()

    if (online.isEmpty()) {
        EmptyState("پرداخت آنلاین ثبت نشده است.")
    } else {
        online.forEach {
            OnlinePaymentAdminCard(it)
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(18.dp))

    SectionTitle(
        "واریزهای دستی",
        "تأیید یا رد رسیدهای ثبت‌شده"
    )

    val manual = data?.payments.orEmpty()

    if (manual.isEmpty()) {
        EmptyState("واریز دستی ثبت نشده است.")
    } else {
        manual.forEach { payment ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp)
            ) {
                Column(
                    Modifier.padding(15.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            payment.fullName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        StatusPill(
                            text = manualStatus(
                                payment.status
                            ),
                            color =
                                when (payment.status) {
                                    "approved" -> Good
                                    "rejected" -> Bad
                                    else -> Gold
                                }
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "${payment.chargeTitle} • ${money(payment.amount)}",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Text(
                        "${unitLabel(payment.block, payment.unitNumber)} • پیگیری ${payment.referenceId}",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    if (payment.status == "pending") {
                        Spacer(Modifier.height(11.dp))

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            AdminApi.reviewPayment(
                                                token,
                                                payment.id,
                                                "approved"
                                            )
                                            onChanged()
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Good,
                                        contentColor = Navy
                                    )
                            ) {
                                Text("تأیید")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            AdminApi.reviewPayment(
                                                token,
                                                payment.id,
                                                "rejected"
                                            )
                                            onChanged()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            ) {
                                Text("رد")
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    SectionTitle(
        "شارژهای اخیر",
        "مانده و وضعیت واحدها"
    )

    data?.charges.orEmpty().take(30).forEach { charge ->
        val remain =
            (charge.amount - charge.paidAmount)
                .coerceAtLeast(0)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Surface2
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            charge.title,
                            color = TextPrimary,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            unitLabel(
                                charge.block,
                                charge.unitNumber
                            ),
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        if (remain == 0L) {
                            "تسویه"
                        } else {
                            money(remain)
                        },
                        color =
                            if (remain == 0L) Good
                            else Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (charge.dueDate.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "سررسید: ${charge.dueDate}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }

                if (
                    charge.payerRelation.isNotBlank()
                ) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "طرف حساب: ${
                            relationLabel(
                                charge.payerRelation
                            )
                        } ${
                            charge.payerName
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?.let { "• $it" }
                                .orEmpty()
                        }",
                        color = Gold,
                        fontSize = 11.sp
                    )
                }

                if (
                    charge.paidAmount == 0L &&
                    remain > 0L
                ) {
                    Spacer(Modifier.height(9.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    AdminApi.deleteCharge(
                                        token,
                                        charge.id
                                    )
                                    chargeMessage =
                                        "شارژ حذف شد و بدهی واحد اصلاح شد."
                                    onChanged()
                                } catch (e: Exception) {
                                    chargeMessage =
                                        e.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حذف شارژ آزمایشی / اشتباه")
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsTab(
    data: AdminData?,
    token: String,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var search by remember {
        mutableStateOf("")
    }

    SectionTitle(
        "درخواست‌های خدمات",
        "جستجو و پیگیری وضعیت درخواست ساکنین"
    )

    OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        label = {
            Text("جستجو درخواست یا ساکن")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(10.dp))

    val q = search.trim()
    val requests = data
        ?.requests
        .orEmpty()
        .filter {
            q.isBlank() ||
                it.title.contains(
                    q,
                    ignoreCase = true
                ) ||
                it.fullName.contains(
                    q,
                    ignoreCase = true
                ) ||
                it.unitNumber.contains(
                    q,
                    ignoreCase = true
                ) ||
                it.category.contains(
                    q,
                    ignoreCase = true
                )
        }

    if (requests.isEmpty()) {
        EmptyState("درخواستی با این جستجو پیدا نشد.")
    } else {
        requests.forEach { request ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp)
            ) {
                Column(
                    Modifier.padding(15.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            request.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        StatusPill(
                            text = requestStatus(
                                request.status
                            ),
                            color =
                                when (request.status) {
                                    "done", "closed" -> Good
                                    "in_progress" -> Gold
                                    else -> Muted
                                }
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "${request.fullName} • ${unitLabel(request.block, request.unitNumber)}",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Text(
                        request.category,
                        color = Muted,
                        fontSize = 11.sp
                    )

                    if (
                        request.status != "done" &&
                        request.status != "closed"
                    ) {
                        Spacer(Modifier.height(10.dp))

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            AdminApi.setRequestStatus(
                                                token,
                                                request.id,
                                                "in_progress"
                                            )
                                            onChanged()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            ) {
                                Text("درحال انجام")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            AdminApi.setRequestStatus(
                                                token,
                                                request.id,
                                                "done"
                                            )
                                            onChanged()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            ) {
                                Text("انجام شد")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTab(
    data: AdminData?,
    token: String,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var search by remember {
        mutableStateOf("")
    }
    var showCreate by remember {
        mutableStateOf(false)
    }
    var fullName by remember {
        mutableStateOf("")
    }
    var mobile by remember {
        mutableStateOf("")
    }
    var block by remember {
        mutableStateOf("")
    }
    var unit by remember {
        mutableStateOf("")
    }
    var relation by remember {
        mutableStateOf("owner")
    }
    var issuedCode by remember {
        mutableStateOf<String?>(null)
    }
    var message by remember {
        mutableStateOf<String?>(null)
    }

    SectionTitle(
        "ساکنین و واحدها",
        "ثبت عضو جدید، جستجو و مدیریت دسترسی"
    )

    Button(
        onClick = {
            showCreate = !showCreate
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Navy
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (showCreate) {
                "بستن فرم ثبت عضو"
            } else {
                "＋ ثبت ساکن / مالک جدید"
            }
        )
    }

    if (showCreate) {
        Spacer(Modifier.height(10.dp))

        FormCard {
            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it.take(100)
                },
                label = {
                    Text("نام و نام خانوادگی")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = mobile,
                onValueChange = {
                    mobile =
                        it.filter(Char::isDigit)
                            .take(11)
                },
                label = {
                    Text("شماره موبایل")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = block,
                onValueChange = {
                    block = it.take(30)
                },
                label = {
                    Text("بلوک - اختیاری")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = unit,
                onValueChange = {
                    unit = it.take(30)
                },
                label = {
                    Text("شماره واحد")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "نوع عضویت",
                color = Muted,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                RelationChip(
                    label = "مالک",
                    selected = relation == "owner"
                ) {
                    relation = "owner"
                }

                RelationChip(
                    label = "مستأجر",
                    selected = relation == "tenant"
                ) {
                    relation = "tenant"
                }

                RelationChip(
                    label = "ساکن",
                    selected = relation == "resident"
                ) {
                    relation = "resident"
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                enabled =
                    fullName.isNotBlank() &&
                        mobile.length == 11 &&
                        unit.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            val result =
                                AdminApi.createMember(
                                    token = token,
                                    fullName = fullName,
                                    mobile = mobile,
                                    block = block,
                                    unitNumber = unit,
                                    relation = relation
                                )

                            issuedCode =
                                result.accessCode
                            message =
                                "عضو و واحد با موفقیت ثبت شدند."

                            fullName = ""
                            mobile = ""
                            block = ""
                            unit = ""
                            onChanged()
                        } catch (e: Exception) {
                            message = e.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ثبت و صدور کد ورود")
            }
        }
    }

    issuedCode?.let {
        Spacer(Modifier.height(10.dp))

        InfoCard(
            title = "کد ورود جدید",
            body = it,
            accent = Gold
        )
    }

    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            color = Gold,
            fontSize = 12.sp
        )
    }

    Spacer(Modifier.height(14.dp))

    OutlinedTextField(
        value = search,
        onValueChange = {
            search = it
        },
        label = {
            Text("جستجو نام، موبایل یا واحد")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(12.dp))

    val q = search.trim()
    val members = data
        ?.members
        .orEmpty()
        .filter {
            q.isBlank() ||
                it.fullName.contains(
                    q,
                    ignoreCase = true
                ) ||
                it.mobile.contains(q) ||
                it.unitNumber.contains(
                    q,
                    ignoreCase = true
                ) ||
                it.block.contains(
                    q,
                    ignoreCase = true
                )
        }

    if (members.isEmpty()) {
        EmptyState("عضوی با این جستجو پیدا نشد.")
    } else {
        members.forEach { member ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp)
            ) {
                Column(
                    Modifier.padding(15.dp)
                ) {
                    Text(
                        member.fullName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        member.mobile,
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Text(
                        "${unitLabel(member.block, member.unitNumber)} • ${relationLabel(member.relation)}",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(9.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    issuedCode =
                                        AdminApi.resetAccessCode(
                                            token,
                                            member.id
                                        )
                                } catch (e: Exception) {
                                    message = e.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("صدور کد ورود جدید")
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Navy
            )
        ) {
            Text(
                label,
                fontSize = 11.sp
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick
        ) {
            Text(
                label,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MoreTab(
    token: String,
    onChanged: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var integration by remember {
        mutableStateOf<BlupalIntegration?>(null)
    }
    var finance by remember {
        mutableStateOf<FinanceSettings?>(null)
    }

    var key by remember {
        mutableStateOf("")
    }
    var message by remember {
        mutableStateOf<String?>(null)
    }

    var initialBalance by remember {
        mutableStateOf("")
    }
    var ownerCharge by remember {
        mutableStateOf("")
    }
    var tenantCharge by remember {
        mutableStateOf("")
    }
    var dueDay by remember {
        mutableStateOf("10")
    }
    var autoBilling by remember {
        mutableStateOf(true)
    }

    var expenseTitle by remember {
        mutableStateOf("")
    }
    var expenseCategory by remember {
        mutableStateOf("")
    }
    var expenseAmount by remember {
        mutableStateOf("")
    }
    var expenseDate by remember {
        mutableStateOf("")
    }

    var annTitle by remember {
        mutableStateOf("")
    }
    var annBody by remember {
        mutableStateOf("")
    }

    fun loadFinance() {
        scope.launch {
            finance = runCatching {
                AdminApi.financeSettings(token)
            }.getOrNull()

            finance?.let {
                initialBalance =
                    it.initialBalance.toString()
                ownerCharge =
                    it.ownerMonthlyCharge.toString()
                tenantCharge =
                    it.tenantMonthlyCharge.toString()
                dueDay =
                    it.dueDay.toString()
                autoBilling =
                    it.autoBillingEnabled
            }
        }
    }

    LaunchedEffect(Unit) {
        integration = runCatching {
            AdminApi.blupal(token)
        }.getOrNull()

        finance = runCatching {
            AdminApi.financeSettings(token)
        }.getOrNull()

        finance?.let {
            initialBalance =
                it.initialBalance.toString()
            ownerCharge =
                it.ownerMonthlyCharge.toString()
            tenantCharge =
                it.tenantMonthlyCharge.toString()
            dueDay =
                it.dueDay.toString()
            autoBilling =
                it.autoBillingEnabled
        }
    }

    SectionTitle(
        "تنظیمات مالی ساختمان",
        "موجودی اولیه و شارژ خودکار مالک / مستأجر"
    )

    finance?.let { current ->
        MoneyMetricCard(
            title = "موجودی فعلی ساختمان",
            value = current.currentBalance,
            accent =
                if (current.currentBalance >= 0L) {
                    Good
                } else {
                    Bad
                }
        )

        MoneyMetricCard(
            title = "جمع وصولی",
            value = current.totalCollected,
            accent = Good
        )

        MoneyMetricCard(
            title = "جمع هزینه‌ها",
            value = current.totalExpenses,
            accent = Bad
        )
    }

    FormCard {
        OutlinedTextField(
            value = initialBalance,
            onValueChange = {
                initialBalance =
                    signedNumberInput(it)
            },
            label = {
                Text("موجودی اولیه ساختمان - تومان")
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = ownerCharge,
            onValueChange = {
                ownerCharge =
                    it.filter(Char::isDigit)
            },
            label = {
                Text("شارژ ماهانه مالک - تومان")
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = tenantCharge,
            onValueChange = {
                tenantCharge =
                    it.filter(Char::isDigit)
            },
            label = {
                Text("شارژ ماهانه مستأجر - تومان")
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = dueDay,
            onValueChange = {
                dueDay =
                    it.filter(Char::isDigit)
                        .take(2)
            },
            label = {
                Text("روز سررسید شمسی ۱ تا ۲۸")
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                autoBilling = !autoBilling
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (autoBilling) {
                    "✅ صدور خودکار اول هر ماه فعال است"
                } else {
                    "⏸ صدور خودکار اول هر ماه غیرفعال است"
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            enabled =
                (initialBalance.toLongOrNull() != null) &&
                    (ownerCharge.toLongOrNull() != null) &&
                    (tenantCharge.toLongOrNull() != null) &&
                    (dueDay.toIntOrNull() ?: 0) in 1..28,
            onClick = {
                scope.launch {
                    try {
                        finance =
                            AdminApi.saveFinanceSettings(
                                token = token,
                                initialBalance =
                                    initialBalance
                                        .toLong(),
                                ownerMonthlyCharge =
                                    ownerCharge
                                        .toLong(),
                                tenantMonthlyCharge =
                                    tenantCharge
                                        .toLong(),
                                autoBillingEnabled =
                                    autoBilling,
                                dueDay =
                                    dueDay.toInt()
                            )

                        message =
                            "تنظیمات مالی ذخیره شد."
                        onChanged()
                    } catch (e: Exception) {
                        message = e.message
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Navy
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ذخیره تنظیمات مالی")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            enabled =
                (ownerCharge.toLongOrNull() ?: 0L) > 0L ||
                    (tenantCharge.toLongOrNull() ?: 0L) > 0L,
            onClick = {
                scope.launch {
                    try {
                        val result =
                            AdminApi.runMonthlyBilling(
                                token
                            )

                        message =
                            "شارژ ${result.periodKey}: ${
                                result.unitsBilled
                            } واحد صادر شد، ${
                                result.unitsSkipped
                            } واحد بدون طرف حساب/مبلغ رد شد."

                        loadFinance()
                        onChanged()
                    } catch (e: Exception) {
                        message = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("اجرای آزمایشی صدور شارژ همین ماه")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "قاعده خودکار: اگر مستأجر فعال برای واحد ثبت باشد مبلغ مستأجر اعمال می‌شود؛ در غیر این صورت مبلغ مالک. صدور واقعی روز اول هر ماه جلالی و بر اساس ساعت ایران انجام می‌شود.",
            color = Muted,
            fontSize = 11.sp
        )
    }

    Spacer(Modifier.height(18.dp))

    SectionTitle(
        "درگاه بلوپال",
        "تنظیم امن پرداخت آنلاین"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    "وضعیت اتصال",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                StatusPill(
                    text =
                        if (
                            integration?.configured == true
                        ) {
                            "فعال"
                        } else {
                            "تنظیم نشده"
                        },
                    color =
                        if (
                            integration?.configured == true
                        ) {
                            Good
                        } else {
                            Gold
                        }
                )
            }

            Spacer(Modifier.height(7.dp))

            Text(
                "حالت: ${
                    integration?.mode
                        ?.ifBlank { "—" }
                        ?: "—"
                }",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "API Key داخل APK ذخیره نمی‌شود؛ فقط به Secret ورکر منتقل می‌شود.",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it.trim()
                },
                label = {
                    Text("Blupal API Key")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(
                enabled =
                    key.startsWith("blu_test_") ||
                        key.startsWith("blu_live_"),
                onClick = {
                    scope.launch {
                        try {
                            AdminApi.configureBlupal(
                                token,
                                key
                            )
                            key = ""
                            message =
                                "کلید با موفقیت ذخیره شد."
                            integration =
                                runCatching {
                                    AdminApi.blupal(
                                        token
                                    )
                                }.getOrNull()
                        } catch (e: Exception) {
                            message = e.message
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Navy
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ذخیره امن کلید")
            }

            integration
                ?.callbackPage
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "صفحه بازگشت آژند",
                        color = TextPrimary,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        it,
                        color = Muted,
                        fontSize = 10.sp
                    )
                }

            integration
                ?.webhookUrl
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Webhook بلوپال",
                        color = TextPrimary,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        it,
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
        }
    }

    Spacer(Modifier.height(18.dp))

    SectionTitle(
        "ثبت هزینه مجتمع",
        "همه تاریخ‌ها شمسی و ساعت سیستم ایران است"
    )

    FormCard {
        OutlinedTextField(
            value = expenseTitle,
            onValueChange = {
                expenseTitle = it
            },
            label = {
                Text("عنوان هزینه")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = expenseCategory,
            onValueChange = {
                expenseCategory = it
            },
            label = {
                Text("دسته‌بندی")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = expenseAmount,
            onValueChange = {
                expenseAmount =
                    it.filter(Char::isDigit)
            },
            label = {
                Text("مبلغ تومان")
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = expenseDate,
            onValueChange = {
                expenseDate = it
            },
            label = {
                Text("تاریخ شمسی، مثال 1405-07-12")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                val amount =
                    expenseAmount.toLongOrNull()
                        ?: return@Button

                scope.launch {
                    try {
                        AdminApi.createExpense(
                            token,
                            expenseTitle,
                            expenseCategory,
                            amount,
                            expenseDate
                        )

                        message = "هزینه ثبت شد."
                        expenseTitle = ""
                        expenseCategory = ""
                        expenseAmount = ""
                        expenseDate = ""
                        loadFinance()
                        onChanged()
                    } catch (e: Exception) {
                        message = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ثبت هزینه")
        }
    }

    Spacer(Modifier.height(18.dp))

    SectionTitle(
        "اعلان جدید",
        "ارسال پیام برای همه ساکنین"
    )

    FormCard {
        OutlinedTextField(
            value = annTitle,
            onValueChange = {
                annTitle = it
            },
            label = {
                Text("عنوان اعلان")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = annBody,
            onValueChange = {
                annBody = it
            },
            label = {
                Text("متن اعلان")
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        AdminApi.createAnnouncement(
                            token,
                            annTitle,
                            annBody
                        )

                        message = "اعلان منتشر شد."
                        annTitle = ""
                        annBody = ""
                        onChanged()
                    } catch (e: Exception) {
                        message = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("انتشار اعلان")
        }
    }

    message?.let {
        Spacer(Modifier.height(12.dp))
        InfoCard(
            title = "وضعیت",
            body = it,
            accent = Gold
        )
    }

    Spacer(Modifier.height(20.dp))

    OutlinedButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("خروج از حساب مدیریت")
    }
}


@Composable
private fun OnlinePaymentAdminCard(
    item: AdminOnlinePayment
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(15.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    item.chargeTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                StatusPill(
                    text = onlineStatus(item.status),
                    color =
                        when (item.status) {
                            "PAID" -> Good
                            "EXPIRED", "CANCELED" -> Bad
                            else -> Gold
                        }
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "${item.fullName} • ${unitLabel(item.block, item.unitNumber)}",
                color = Muted,
                fontSize = 12.sp
            )

            Text(
                "${money(item.amountToman)} • فاکتور ${item.invoiceId}",
                color = Muted,
                fontSize = 11.sp
            )

            if (item.receiptNo.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "رسید: ${item.receiptNo}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CompactRequestCard(
    item: AdminRequest
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface2
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(14.dp)
        ) {
            Text(
                item.title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${item.fullName} • ${unitLabel(item.block, item.unitNumber)}",
                color = Muted,
                fontSize = 11.sp
            )
            Text(
                requestStatus(item.status),
                color = Gold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MetricCard(
    emoji: String,
    title: String,
    value: Int,
    hint: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    emoji,
                    fontSize = 22.sp
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        hint,
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                value.toString(),
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
private fun MoneyMetricCard(
    title: String,
    value: Long,
    accent: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface2
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Muted,
                fontSize = 12.sp
            )

            Text(
                money(value),
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    accent: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface2
        ),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(14.dp)
        ) {
            Text(
                title,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                body,
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyState(
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface2
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            color = Muted,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun FormCard(
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(15.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(
                alpha = 0.15f
            )
        ),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp
            )
        )
    }
}

private fun signedNumberInput(
    value: String
): String {
    val trimmed = value.trim()

    if (trimmed.startsWith("-")) {
        return "-" +
            trimmed
                .drop(1)
                .filter(Char::isDigit)
    }

    return trimmed.filter(Char::isDigit)
}

private fun money(value: Long): String =
    "${
        NumberFormat.getNumberInstance(
            Locale("fa", "IR")
        ).format(value)
    } تومان"

private fun unitLabel(
    block: String,
    unit: String
): String =
    buildString {
        if (block.isNotBlank()) {
            append("بلوک ")
            append(block)
            append(" • ")
        }
        append("واحد ")
        append(
            unit.ifBlank { "—" }
        )
    }

private fun relationLabel(
    relation: String
): String =
    when (relation) {
        "owner" -> "مالک"
        "tenant" -> "مستأجر"
        "resident" -> "ساکن"
        "manager" -> "مدیر"
        else -> relation.ifBlank { "عضو" }
    }

private fun manualStatus(
    status: String
): String =
    when (status) {
        "approved" -> "تأیید شده"
        "rejected" -> "رد شده"
        else -> "در انتظار"
    }

private fun requestStatus(
    status: String
): String =
    when (status) {
        "in_progress" -> "درحال انجام"
        "done" -> "انجام شد"
        "closed" -> "بسته"
        else -> "جدید"
    }

private fun onlineStatus(
    status: String
): String =
    when (status) {
        "PAID" -> "پرداخت شد"
        "EXPIRED" -> "منقضی"
        "CANCELED" -> "لغو شد"
        else -> "در انتظار"
    }

package com.azhand.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection

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
                    AzhandApp()
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
private fun AzhandApp() {
    var selected by remember { mutableStateOf(AppTab.HOME) }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp
            ) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .padding(padding)
        ) {
            when (selected) {
                AppTab.HOME -> DashboardScreen()
                AppTab.FINANCE -> FinanceScreen()
                AppTab.SERVICES -> ServicesScreen()
                AppTab.NOTICES -> NoticesScreen()
                AppTab.ACCOUNT -> AccountScreen()
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp
        )
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
private fun DashboardScreen() = ScreenContainer(
    title = "آژند",
    subtitle = "مجتمع تجاری، مسکونی • نسخه ۰.۴.۱"
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("سلام 👋", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("واحد ۳۰۵", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text("مانده حساب", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "۳٬۰۰۰٬۰۰۰ تومان بدهکار",
                color = Danger,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Navy
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("پرداخت شارژ", fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniStat(
            modifier = Modifier.weight(1f),
            title = "شارژ شهریور",
            value = "۲٫۵ م",
            hint = "تومان"
        )
        MiniStat(
            modifier = Modifier.weight(1f),
            title = "درخواست باز",
            value = "۱",
            hint = "مورد"
        )
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("دسترسی سریع")

    QuickAction("صورتحساب و ریز هزینه‌ها", "مشاهده جزئیات مالی واحد")
    QuickAction("ثبت درخواست خدمات", "خرابی، نظافت، تأسیسات و سایر موارد")
    QuickAction("اعلانات ساختمان", "آخرین اطلاعیه‌های مدیریت")
    QuickAction("اطلاعات واحد", "مالک، ساکنین و اطلاعات تماس")

    Spacer(Modifier.height(18.dp))
    SectionTitle("آخرین اعلان")

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp)) {
            Text("جلسه هیئت‌مدیره", color = Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "پنجشنبه ساعت ۲۰ جلسه ماهانه مجتمع برگزار می‌شود.",
                color = TextPrimary,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("امروز • مدیریت مجتمع", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FinanceScreen() = ScreenContainer(
    title = "مالی",
    subtitle = "شارژ، پرداخت‌ها و شفافیت هزینه‌های ساختمان"
) {
    FinanceRow("شارژ شهریور", "۲٬۵۰۰٬۰۰۰ تومان", "پرداخت نشده", Danger)
    FinanceRow("بدهی قبلی", "۵۰۰٬۰۰۰ تومان", "باز", Danger)
    FinanceRow("پرداخت مرداد", "۲٬۳۰۰٬۰۰۰ تومان", "پرداخت شد", Success)

    Spacer(Modifier.height(18.dp))
    SectionTitle("هزینه‌های اخیر ساختمان")
    ExpenseRow("برق مشاعات", "۸٬۴۰۰٬۰۰۰")
    ExpenseRow("نظافت", "۱۲٬۰۰۰٬۰۰۰")
    ExpenseRow("تعمیر آسانسور", "۶٬۸۰۰٬۰۰۰")
}

@Composable
private fun ServicesScreen() = ScreenContainer(
    title = "خدمات",
    subtitle = "درخواست‌های واحد و خدمات مشترک"
) {
    QuickAction("ثبت درخواست جدید", "یک مشکل یا درخواست خدماتی ثبت کنید")
    QuickAction("درخواست‌های من", "پیگیری وضعیت درخواست‌های قبلی")
    QuickAction("رزرو امکانات", "این بخش در نسخه بعد فعال می‌شود")
    QuickAction("پارکینگ مهمان", "این بخش در نسخه بعد فعال می‌شود")
}

@Composable
private fun NoticesScreen() = ScreenContainer(
    title = "اعلانات",
    subtitle = "اطلاعیه‌های رسمی مجتمع آژند"
) {
    NoticeCard("جلسه هیئت‌مدیره", "پنجشنبه ساعت ۲۰ جلسه ماهانه برگزار می‌شود.", "امروز")
    NoticeCard("سرویس آسانسور", "آسانسور بلوک A فردا بین ساعت ۱۰ تا ۱۲ سرویس می‌شود.", "دیروز")
    NoticeCard("یادآوری شارژ", "مهلت پرداخت شارژ شهریور تا پایان هفته است.", "۳ روز پیش")
}

@Composable
private fun AccountScreen() = ScreenContainer(
    title = "حساب من",
    subtitle = "پروفایل و اطلاعات واحد"
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            ProfileLine("نام", "کاربر آژند")
            ProfileLine("واحد", "۳۰۵")
            ProfileLine("نوع عضویت", "مالک")
            ProfileLine("نسخه اپ", "۰.۴.۱")
        }
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier,
    title: String,
    value: String,
    hint: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(hint, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun QuickAction(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(Gold, RoundedCornerShape(99.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
            Text("‹", color = Gold, fontSize = 24.sp)
        }
    }
}

@Composable
private fun FinanceRow(title: String, amount: String, status: String, statusColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextPrimary)
        Text("$amount تومان", color = TextMuted)
    }
    HorizontalDivider(color = Color(0xFF20354F))
}

@Composable
private fun NoticeCard(title: String, body: String, time: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body, color = TextPrimary, lineHeight = 21.sp)
            Spacer(Modifier.height(9.dp))
            Text(time, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

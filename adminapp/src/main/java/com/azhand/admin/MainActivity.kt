package com.azhand.admin

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

private val Navy=Color(0xFF07111F); private val Surface=Color(0xFF0E1B2D); private val Gold=Color(0xFFE4B84B); private val Text=Color(0xFFF3F7FC); private val Muted=Color(0xFFAAB8CB); private val Bad=Color(0xFFFF7783)

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){MaterialTheme(colorScheme=darkColorScheme(primary=Gold,background=Navy,surface=Surface)){AdminRoot()}}}}}

enum class Tab(val label:String){HOME("خانه"),PAYMENTS("پرداخت‌ها"),REQUESTS("درخواست‌ها"),MEMBERS("ساکنین"),MORE("بیشتر")}

@Composable fun AdminRoot(){
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var token by remember{mutableStateOf(AdminSessionStore.get(context))}; var data by remember{mutableStateOf<AdminData?>(null)}; var error by remember{mutableStateOf<String?>(null)}; var refresh by remember{mutableIntStateOf(0)}; var updateInfo by remember{mutableStateOf<AdminUpdateInfo?>(null)}
    LaunchedEffect(Unit){ updateInfo = runCatching { AdminUpdateManager.check() }.getOrNull() }
    LaunchedEffect(token,refresh){val t=token?:return@LaunchedEffect;try{data=AdminApi.data(t);error=null}catch(e:AdminApiException){if(e.statusCode==401){AdminSessionStore.clear(context);token=null}else error=e.message}catch(e:Exception){error=e.message}}
    if(token==null){Login{key,done->scope.launch{try{val t=AdminApi.login(key);AdminSessionStore.set(context,t);token=t;done(null)}catch(e:Exception){done(e.message)}}}}else AdminApp(data,error,token!!,{refresh++},{val t=token;AdminSessionStore.clear(context);token=null;if(t!=null)scope.launch{AdminApi.logout(t)}})
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest={updateInfo=null},
            title={Text("نسخه جدید مدیریت آژند")},
            text={Text("نسخه ${info.versionName} آماده نصب است.")},
            confirmButton={TextButton(onClick={scope.launch{runCatching{val apk=AdminUpdateManager.download(context,info);AdminUpdateManager.install(context,apk)}}}){Text("دانلود و نصب")}},
            dismissButton={TextButton(onClick={updateInfo=null}){Text("بعداً")}},
            containerColor=Surface
        )
    }
}

@Composable
fun Login(
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
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "مدیریت آژند",
            color = Gold,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "اپلیکیشن مدیر مجتمع • نسخه ۰.۸.۲",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(22.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Setup Admin Key") },
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

                Spacer(Modifier.height(10.dp))

                Button(
                    enabled = !busy && key.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        onLogin(key) { message ->
                            error = message
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (busy) "در حال ورود..."
                        else "ورود"
                    )
                }
            }
        }
    }
}

@Composable fun AdminApp(data:AdminData?,error:String?,token:String,onChanged:()->Unit,onLogout:()->Unit){var tab by remember{mutableStateOf(Tab.HOME)};Scaffold(containerColor=Navy,bottomBar={NavigationBar(containerColor=Surface){Tab.entries.forEach{NavigationBarItem(selected=tab==it,onClick={tab=it},icon={Text("●")},label={Text(it.label,fontSize=10.sp)})}}}){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("مدیریت آژند",color=Text,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("نسخه ${BuildConfig.VERSION_NAME}",color=Muted,fontSize=11.sp)};TextButton(onClick=onChanged){Text("بروزرسانی")}};error?.let{Text(it,color=Bad)};Spacer(Modifier.height(12.dp));when(tab){Tab.HOME->Home(data);Tab.PAYMENTS->Payments(data,token,onChanged);Tab.REQUESTS->Requests(data,token,onChanged);Tab.MEMBERS->Members(data,token);Tab.MORE->More(token,onChanged,onLogout)}}}}

@Composable fun Home(data:AdminData?){val s=data?.summary;Stat("اعضا",s?.members?:0);Stat("واحدها",s?.units?:0);Stat("واریزهای منتظر",s?.pendingPayments?:0);Stat("درخواست‌های باز",s?.openRequests?:0)}
@Composable fun Stat(label:String,value:Int){Card(colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted);Text(value.toString(),color=Gold,fontWeight=FontWeight.Bold)}}}

@Composable fun Payments(data:AdminData?,token:String,onChanged:()->Unit){val scope=rememberCoroutineScope();Text("واریزهای دستی",color=Text,fontSize=20.sp,fontWeight=FontWeight.Bold);data?.payments.orEmpty().forEach{p->Card(colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Column(Modifier.padding(14.dp)){Text(p.fullName,color=Text,fontWeight=FontWeight.Bold);Text("${p.chargeTitle} • ${p.referenceId}",color=Muted,fontSize=12.sp);if(p.status=="pending")Row{Button(onClick={scope.launch{AdminApi.reviewPayment(token,p.id,"approved");onChanged()}}){Text("تأیید")};OutlinedButton(onClick={scope.launch{AdminApi.reviewPayment(token,p.id,"rejected");onChanged()}}){Text("رد")}}}}}}

@Composable fun Requests(data:AdminData?,token:String,onChanged:()->Unit){val scope=rememberCoroutineScope();Text("درخواست‌های خدمات",color=Text,fontSize=20.sp,fontWeight=FontWeight.Bold);data?.requests.orEmpty().forEach{r->Card(colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Column(Modifier.padding(14.dp)){Text(r.title,color=Text,fontWeight=FontWeight.Bold);Text("${r.fullName} • واحد ${r.unitNumber} • ${r.status}",color=Muted,fontSize=12.sp);Row{TextButton(onClick={scope.launch{AdminApi.setRequestStatus(token,r.id,"in_progress");onChanged()}}){Text("درحال انجام")};TextButton(onClick={scope.launch{AdminApi.setRequestStatus(token,r.id,"done");onChanged()}}){Text("انجام شد")}}}}}}

@Composable fun Members(data:AdminData?,token:String){val scope=rememberCoroutineScope();var code by remember{mutableStateOf<String?>(null)};Text("ساکنین",color=Text,fontSize=20.sp,fontWeight=FontWeight.Bold);code?.let{Text("کد جدید: $it",color=Gold,fontWeight=FontWeight.Bold)};data?.members.orEmpty().forEach{m->Card(colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Column(Modifier.padding(14.dp)){Text(m.fullName,color=Text,fontWeight=FontWeight.Bold);Text("${m.mobile} • واحد ${m.unitNumber}",color=Muted,fontSize=12.sp);OutlinedButton(onClick={scope.launch{code=AdminApi.resetAccessCode(token,m.id)}}){Text("صدور کد ورود جدید")}}}}}

@Composable
fun More(
    token: String,
    onChanged: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var integration by remember { mutableStateOf<BlupalIntegration?>(null) }
    var key by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    var expenseTitle by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var expenseDate by remember { mutableStateOf("") }

    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        integration = runCatching {
            AdminApi.blupal(token)
        }.getOrNull()
    }

    Text(
        "درگاه بلوپال",
        color = Text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (integration?.configured == true) {
                    "فعال (${integration?.mode})"
                } else {
                    "تنظیم نشده"
                },
                color = Gold
            )

            Text(
                "API Key داخل APK ذخیره نمی‌شود و به Secret ورکر منتقل می‌شود.",
                color = Muted,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text("Blupal API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = key.startsWith("blu_test_") || key.startsWith("blu_live_"),
                onClick = {
                    scope.launch {
                        try {
                            AdminApi.configureBlupal(token, key)
                            message = "کلید ذخیره شد."
                            key = ""
                            integration = runCatching {
                                AdminApi.blupal(token)
                            }.getOrNull()
                        } catch (e: Exception) {
                            message = e.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ذخیره امن کلید")
            }

            integration?.webhookUrl
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        "Webhook اختیاری: $it",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "ثبت هزینه",
        color = Text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )

    OutlinedTextField(
        value = expenseTitle,
        onValueChange = { expenseTitle = it },
        label = { Text("عنوان") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = expenseCategory,
        onValueChange = { expenseCategory = it },
        label = { Text("دسته‌بندی") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = expenseAmount,
        onValueChange = { expenseAmount = it.filter(Char::isDigit) },
        label = { Text("مبلغ تومان") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = expenseDate,
        onValueChange = { expenseDate = it },
        label = { Text("YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = {
            val parsedAmount = expenseAmount.toLongOrNull()
            if (parsedAmount == null) {
                message = "مبلغ معتبر وارد کن."
            } else {
                scope.launch {
                    try {
                        AdminApi.createExpense(
                            token,
                            expenseTitle,
                            expenseCategory,
                            parsedAmount,
                            expenseDate
                        )
                        message = "هزینه ثبت شد."
                        onChanged()
                    } catch (e: Exception) {
                        message = e.message
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("ثبت هزینه")
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "اعلان",
        color = Text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )

    OutlinedTextField(
        value = annTitle,
        onValueChange = { annTitle = it },
        label = { Text("عنوان") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = annBody,
        onValueChange = { annBody = it },
        label = { Text("متن") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = {
            scope.launch {
                try {
                    AdminApi.createAnnouncement(token, annTitle, annBody)
                    message = "اعلان منتشر شد."
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

    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = Gold, fontSize = 12.sp)
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("خروج")
    }
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.wordpop.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Username and password for the control panel. Only a salted hash is stored, never the password itself. */
object AdminAuth {
    private const val PREFS = "wordpop_admin"
    private const val ITER = 20000
    private const val MAX_TRIES = 5
    private const val LOCK_MS = 5 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAccount(ctx: Context) = prefs(ctx).getString("hash", null) != null

    fun username(ctx: Context): String = prefs(ctx).getString("user", "") ?: ""

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun hash(pass: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pass.toCharArray(), salt, ITER, 256)).encoded

    fun save(ctx: Context, user: String, pass: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit()
            .putString("user", user.trim())
            .putString("salt", hex(salt))
            .putString("hash", hex(hash(pass, salt)))
            .putInt("fails", 0).putLong("lockUntil", 0L)
            .apply()
    }

    /** Seconds left in the lock, or 0 if login is allowed. */
    fun lockedFor(ctx: Context): Int {
        val left = prefs(ctx).getLong("lockUntil", 0L) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun verify(ctx: Context, user: String, pass: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString("salt", null) ?: return false
        val stored = p.getString("hash", null) ?: return false
        val okPass = MessageDigest.isEqual(hash(pass, unhex(salt)), unhex(stored))
        val okUser = user.trim().equals(p.getString("user", ""), ignoreCase = true)
        if (okPass && okUser) {
            p.edit().putInt("fails", 0).putLong("lockUntil", 0L).apply()
            return true
        }
        val fails = p.getInt("fails", 0) + 1
        val e = p.edit()
        if (fails >= MAX_TRIES) e.putInt("fails", 0).putLong("lockUntil", System.currentTimeMillis() + LOCK_MS)
        else e.putInt("fails", fails)
        e.apply()
        return false
    }

    fun triesLeft(ctx: Context) = MAX_TRIES - prefs(ctx).getInt("fails", 0)
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text)
    )
}

@Composable
fun AdminScreen(
    store: SettingsStore,
    overlayGranted: Boolean,
    exactAlarmGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotif: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("لوحة التحكم", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Night, modifier = Modifier.weight(1f))
            Chip("خروج", false) { onClose() }
        }
        if (!unlocked) {
            AdminLogin(onOk = { unlocked = true })
        } else {
            AdminPanel(store, overlayGranted, exactAlarmGranted, onRequestOverlay, onRequestNotif, onRequestExactAlarm, onLock = { unlocked = false })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AdminLogin(onOk: () -> Unit) {
    val ctx = LocalContext.current
    val exists = remember { AdminAuth.hasAccount(ctx) }
    var user by remember { mutableStateOf(if (exists) "" else "") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    Section(if (exists) "سجّل دخولك" else "اعمل حساب اللوحة") {
        if (!exists) Text("اختار اسم مستخدم ورقم سري (6 حروف أو أرقام على الأقل). هتحتاجهم كل مرة تفتح اللوحة.", fontSize = 15.sp, color = Muted)
        Field(user, { user = it }, "اسم المستخدم")
        Field(pass, { pass = it }, "الرقم السري", secret = true)
        if (!exists) Field(pass2, { pass2 = it }, "أكّد الرقم السري", secret = true)
        if (msg.isNotEmpty()) Text(msg, fontSize = 15.sp, color = Danger)
        PrimaryButton(if (exists) "دخول" else "إنشاء الحساب", Modifier.fillMaxWidth()) {
            if (exists) {
                val wait = AdminAuth.lockedFor(ctx)
                if (wait > 0) { msg = "جربت كتير. استنى ${(wait + 59) / 60} دقيقة."; return@PrimaryButton }
                if (AdminAuth.verify(ctx, user, pass)) { onOk() } else {
                    val w = AdminAuth.lockedFor(ctx)
                    msg = if (w > 0) "اتقفلت 5 دقايق بعد محاولات كتير." else "اسم المستخدم أو الرقم السري غلط. باقي ${AdminAuth.triesLeft(ctx)} محاولات."
                }
            } else {
                when {
                    user.trim().length < 3 -> msg = "اسم المستخدم 3 حروف على الأقل."
                    pass.length < 6 -> msg = "الرقم السري 6 على الأقل."
                    pass != pass2 -> msg = "الرقمين السريين مش متطابقين."
                    else -> { AdminAuth.save(ctx, user, pass); onOk() }
                }
            }
        }
        if (exists) Text(
            "نسيت الرقم السري؟ مسح بيانات التطبيق من إعدادات الأندرويد بيرجع اللوحة من الأول، وبيمسح بياناتك كلها.",
            fontSize = 13.sp, color = Muted
        )
    }
}

@Composable
private fun AdminPanel(
    store: SettingsStore,
    overlayGranted: Boolean,
    exactAlarmGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotif: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onLock: () -> Unit
) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var interval by remember { mutableIntStateOf(store.intervalMin) }
    var startH by remember { mutableIntStateOf(store.startHour) }
    var endH by remember { mutableIntStateOf(store.endHour) }
    var content by remember { mutableIntStateOf(store.content) }
    var level by remember { mutableIntStateOf(store.level) }
    var displaySec by remember { mutableIntStateOf(store.displaySec) }
    var sound by remember { mutableStateOf(store.sound) }
    var autoSpeak by remember { mutableStateOf(store.autoSpeak) }
    var skipCalls by remember { mutableStateOf(store.skipCalls) }
    var skipGames by remember { mutableStateOf(store.skipGames) }
    var pos by remember { mutableIntStateOf(store.bannerPos) }
    var size by remember { mutableIntStateOf(store.bannerSize) }
    var theme by remember { mutableIntStateOf(store.bannerTheme) }
    var opacity by remember { mutableFloatStateOf(store.opacity.toFloat()) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var note by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(store.pausedUntil > System.currentTimeMillis()) }

    fun refresh() { if (enabled) sendToService(ctx, OverlayService.ACTION_START) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            note = try {
                val o = JSONObject().put("app", "wordpop").put("v", 1)
                    .put("prefs", store.exportAll()).put("custom", CustomWords.exportRaw(ctx))
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
                "اتحفظت النسخة الاحتياطية."
            } catch (e: Exception) { "مقدرتش أحفظ الملف." }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            note = try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val o = JSONObject(text)
                if (o.optString("app") != "wordpop") "الملف ده مش نسخة من ووردبوب."
                else if (!store.importAll(o.getString("prefs"))) "الملف تالف."
                else {
                    CustomWords.importRaw(ctx, o.optString("custom", "[]"))
                    WordRepo.init(ctx)
                    "اتسترجعت البيانات. اقفل التطبيق وافتحه تاني."
                }
            } catch (e: Exception) { "مقدرتش أقرأ الملف." }
        }
    }

    // ---- overview ----
    val customCount = tick.let { WordRepo.all.count { it.id >= CustomWords.FIRST_ID } }
    Section("نظرة عامة") {
        val rows = listOf(
            "كل الكلمات" to WordRepo.all.size,
            "كلماتي المستوردة" to customCount,
            "الكلمات اللي عرفتها" to store.learned.size,
            "الكلمات الصعبة" to store.hard.size,
            "الكلمات المفضلة" to store.favWords.size,
            "الجمل المفضلة" to store.favSentences.size,
            "القصص المحفوظة" to StoryApi.load(store).size,
            "جولات التهجئة" to store.stat("spellRounds"),
            "جولات بناء الجمل" to store.stat("buildRounds")
        )
        tick.let { }
        rows.forEach { (k, v) ->
            Row { Text(k, fontSize = 16.sp, color = Muted, modifier = Modifier.weight(1f)); Text("$v", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Night) }
        }
    }

    // ---- service ----
    Section("التشغيل") {
        SwitchRow("ظهور الكلمات", enabled) { on ->
            if (on) {
                if (!overlayGranted) { onRequestOverlay(); return@SwitchRow }
                if (!exactAlarmGranted) { store.enabled = true; enabled = true; onRequestExactAlarm(); return@SwitchRow }
                onRequestNotif()
                store.enabled = true; enabled = true
                sendToService(ctx, OverlayService.ACTION_START)
            } else {
                store.enabled = false; enabled = false
                Scheduler.cancel(ctx)
                ctx.stopService(Intent(ctx, OverlayService::class.java))
            }
        }
        if (!exactAlarmGranted) {
            Text("التنبيهات الدقيقة مطلوبة لتشغيل الكلمات تلقائيًا في الخلفية.", fontSize = 14.sp, color = Danger)
            SecondaryButton("افتح إعدادات التنبيهات الدقيقة", Modifier.fillMaxWidth()) { onRequestExactAlarm() }
        }
        Text("كل قد إيه", fontSize = 15.sp, color = Muted)
        ChipRow { listOf(5, 15, 30, 60, 120, 180, 360).forEach { m -> Chip(intervalLabel(m), interval == m) { interval = m; store.intervalMin = m; refresh() } } }
        HourStepper("من", startH) { startH = it; store.startHour = it; refresh() }
        HourStepper("إلى", endH) { endH = it; store.endHour = it; refresh() }
        ChipRow {
            Chip("أوقف ساعة", false) { store.pausedUntil = System.currentTimeMillis() + 3600_000L; paused = true; refresh() }
            Chip("أوقف 3 ساعات", false) { store.pausedUntil = System.currentTimeMillis() + 3 * 3600_000L; paused = true; refresh() }
            Chip("كمّل", false) { store.pausedUntil = 0L; paused = false; refresh() }
        }
        if (paused) Text("الظهور موقوف مؤقتًا.", fontSize = 14.sp, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("اعرض كلمة دلوقتي", false) { sendToService(ctx, OverlayService.ACTION_SHOW_NOW) }
        }
    }

    // ---- content ----
    Section("المحتوى") {
        ChipRow { listOf(0 to "كلمات وعبارات", 1 to "كلمات بس", 2 to "عبارات بس").forEach { (v, l) -> Chip(l, content == v) { content = v; store.content = v } } }
        ChipRow { listOf(0 to "كل المستويات", 1 to "أساسي", 2 to "B1", 3 to "B2").forEach { (v, l) -> Chip(l, level == v) { level = v; store.level = v } } }
    }

    // ---- window ----
    Section("النافذة العايمة") {
        Text("المكان", fontSize = 15.sp, color = Muted)
        ChipRow { listOf(0 to "فوق", 1 to "النص", 2 to "تحت").forEach { (v, l) -> Chip(l, pos == v) { pos = v; store.bannerPos = v; store.bannerY = -1 } } }
        Text("الحجم", fontSize = 15.sp, color = Muted)
        ChipRow { listOf(0 to "صغير", 1 to "وسط", 2 to "كبير").forEach { (v, l) -> Chip(l, size == v) { size = v; store.bannerSize = v } } }
        Text("اللون", fontSize = 15.sp, color = Muted)
        ChipRow { BannerThemes.all.forEachIndexed { i, t -> Chip(t.name, theme == i) { theme = i; store.bannerTheme = i } } }
        Text("الشفافية: ${opacity.toInt()}%", fontSize = 15.sp, color = Muted)
        Slider(
            value = opacity, onValueChange = { opacity = it },
            onValueChangeFinished = { store.opacity = opacity.toInt() },
            valueRange = 40f..100f,
            colors = SliderDefaults.colors(thumbColor = Teal, activeTrackColor = Teal, inactiveTrackColor = Line)
        )
        Text("مدة الظهور: $displaySec ثانية", fontSize = 15.sp, color = Muted)
        ChipRow { listOf(5, 10, 15, 20, 30).forEach { v -> Chip("$v ث", displaySec == v) { displaySec = v; store.displaySec = v } } }
        SwitchRow("صوت تنبيه", sound) { sound = it; store.sound = it }
        SwitchRow("نطق الكلمة تلقائيًا", autoSpeak) { autoSpeak = it; store.autoSpeak = it }
        SwitchRow("لا تظهر أثناء المكالمات", skipCalls) { skipCalls = it; store.skipCalls = it }
        SwitchRow("لا تظهر أثناء الألعاب والفيديو", skipGames) { skipGames = it; store.skipGames = it }
        SecondaryButton("رجّع مكان النافذة الافتراضي", Modifier.fillMaxWidth()) { store.bannerY = -1; store.bannerX = 0; note = "اتظبط المكان." }
    }

    // ---- data ----
    Section("البيانات") {
        Text("كل زرار هنا بيطلب تأكيد قبل ما يمسح.", fontSize = 14.sp, color = Muted)
        fun ask(text: String, act: () -> Unit) { confirm = text to act }
        SecondaryButton("امسح الكلمات اللي عرفتها", Modifier.fillMaxWidth()) { ask("تمسح قايمة الكلمات اللي عرفتها؟") { store.remove("learned"); tick++ } }
        SecondaryButton("امسح الكلمات الصعبة", Modifier.fillMaxWidth()) { ask("تمسح قايمة الكلمات الصعبة؟") { store.remove("hard"); tick++ } }
        SecondaryButton("امسح المفضلة (كلمات وجمل)", Modifier.fillMaxWidth()) { ask("تمسح كل المفضلة؟") { store.remove("favWords", "favSentences"); tick++ } }
        SecondaryButton("امسح القصص", Modifier.fillMaxWidth()) { ask("تمسح كل القصص المحفوظة؟") { store.storiesJson = "[]"; tick++ } }
        SecondaryButton("امسح إحصائيات الكتابة", Modifier.fillMaxWidth()) {
            ask("تمسح نتايج التهجئة وبناء الجمل؟") {
                store.remove("spellRounds", "spellBest", "buildRounds", "buildBest", "spellMiss", "spellOk", "buildMiss", "buildOk"); tick++
            }
        }
        SecondaryButton("امسح الكلمات المستوردة", Modifier.fillMaxWidth()) { ask("تمسح كل الكلمات اللي استوردتها؟") { WordRepo.clearCustom(ctx); tick++ } }
        SecondaryButton("امسح مفتاح OpenRouter", Modifier.fillMaxWidth()) { ask("تمسح المفتاح المحفوظ؟") { store.orKey = ""; tick++ } }
    }

    // ---- backup ----
    Section("نسخة احتياطية") {
        Text("احفظ إعداداتك وتقدمك وكلماتك في ملف، وارجعه بعدين أو على موبايل تاني. مفتاح OpenRouter وبيانات دخول لوحة التحكم لا يتم تضمينهما؛ أدخل المفتاح وأنشئ حساب اللوحة مرة أخرى على الجهاز الجديد.", fontSize = 14.sp, color = Muted)
        PrimaryButton("احفظ نسخة", Modifier.fillMaxWidth()) { exportLauncher.launch("wordpop-backup.json") }
        SecondaryButton("استرجع من ملف", Modifier.fillMaxWidth()) {
            confirm = "الاسترجاع هيستبدل كل بياناتك الحالية. متأكد؟" to { importLauncher.launch(arrayOf("*/*")) }
        }
        if (note.isNotEmpty()) Text(note, fontSize = 15.sp, color = TealDark)
    }

    // ---- security ----
    var showChange by remember { mutableStateOf(false) }
    Section("الأمان") {
        Text("اسم المستخدم: ${AdminAuth.username(ctx)}", fontSize = 15.sp, color = Muted)
        if (!showChange) {
            SecondaryButton("غيّر اسم المستخدم أو الرقم السري", Modifier.fillMaxWidth()) { showChange = true }
        } else {
            var u by remember { mutableStateOf(AdminAuth.username(ctx)) }
            var p1 by remember { mutableStateOf("") }
            var p2 by remember { mutableStateOf("") }
            var err by remember { mutableStateOf("") }
            Field(u, { u = it }, "اسم المستخدم الجديد")
            Field(p1, { p1 = it }, "الرقم السري الجديد", secret = true)
            Field(p2, { p2 = it }, "أكّد الرقم السري", secret = true)
            if (err.isNotEmpty()) Text(err, fontSize = 15.sp, color = Danger)
            PrimaryButton("حفظ", Modifier.fillMaxWidth()) {
                when {
                    u.trim().length < 3 -> err = "اسم المستخدم 3 حروف على الأقل."
                    p1.length < 6 -> err = "الرقم السري 6 على الأقل."
                    p1 != p2 -> err = "الرقمين مش متطابقين."
                    else -> { AdminAuth.save(ctx, u, p1); showChange = false; note = "اتغيرت بيانات الدخول." }
                }
            }
        }
        PrimaryButton("اقفل اللوحة", Modifier.fillMaxWidth(), container = Night) { onLock() }
    }

    confirm?.let { (text, act) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("تأكيد") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { act(); confirm = null }) { Text("أيوه") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("لأ") } }
        )
    }
}

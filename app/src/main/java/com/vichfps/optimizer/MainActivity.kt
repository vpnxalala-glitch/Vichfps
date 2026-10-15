package com.vichfps.optimizer

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF050713)
private val Panel = Color(0xFF10172A)
private val Accent = Color(0xFF00E5FF)
private val Purple = Color(0xFFB42BFF)
private val Muted = Color(0xFF9BA8BD)
private const val RELEASES_URL = "https://github.com/vpnxalala-glitch/Vichfps/releases/latest"
private const val USAGE_PERMISSION = "android:get_usage_stats"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OptimizerScreen(this) }
    }
}

private data class Stats(val total: Long, val free: Long, val totalRam: Long, val freeRam: Long, val battery: Int, val temp: Float)
private data class AppInfo(val label: String, val pkg: String, val cache: Long, val data: Long, val usedMs: Long)
private data class Scan(val apps: List<AppInfo>, val ownCache: Long, val skipped: Int)
private fun readStats(c: Context): Stats {
    val fs = StatFs(Environment.getDataDirectory().path)
    val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mem)
    val b = c.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return Stats(fs.totalBytes, fs.availableBytes, mem.totalMem, mem.availMem,
        if (level >= 0 && scale > 0) level * 100 / scale else 0,
        (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f)
}
private fun hasUsage(c: Context): Boolean {
    val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    return ops.checkOpNoThrow(USAGE_PERMISSION, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
}
private fun scanApps(c: Context): Scan {
    val pm = c.packageManager
    val sm = c.getSystemService(Context.STORAGE_SERVICE) as StorageStatsManager
    val usage = if (hasUsage(c)) (c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
        .queryAndAggregateUsageStats(System.currentTimeMillis() - 86400000L, System.currentTimeMillis()) else emptyMap()
    var skipped = 0
    val result = pm.getInstalledApplications(0).mapNotNull { app ->
        if (app.packageName == c.packageName) return@mapNotNull null
        try {
            val size = sm.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, android.os.UserHandle.getUserHandleForUid(app.uid))
            AppInfo(pm.getApplicationLabel(app).toString(), app.packageName, size.cacheBytes, size.appBytes + size.dataBytes,
                usage[app.packageName]?.totalTimeInForeground ?: 0L)
        } catch (_: Exception) { skipped++; null }
    }.sortedByDescending { it.cache }
    val own = c.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    return Scan(result, own, skipped)
}
private fun fmt(n: Long): String {
    val mb = n.coerceAtLeast(0L) / 1048576.0
    return if (mb >= 1024) String.format(Locale.getDefault(), "%.1f ГБ", mb / 1024)
    else String.format(Locale.getDefault(), "%.0f МБ", mb)
}

@Composable
private fun OptimizerScreen(activity: MainActivity) {
    var stats by remember { mutableStateOf(readStats(activity)) }
    var scan by remember { mutableStateOf<Scan?>(null) }
    var status by remember { mutableStateOf("Сканирование ещё не запускалось") }
    var busy by remember { mutableStateOf(false) }
    var autoMonitor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Purple, background = Bg, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("VICH", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("OPTIMIZER / REAL SCAN", color = Accent, fontSize = 10.sp)
                    }
                    Box(Modifier.size(58.dp).background(Color(0xFF070A18)), contentAlignment = Alignment.Center) {
                        Text("V", color = Accent, fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text("🚀", Modifier.align(Alignment.TopEnd), fontSize = 19.sp)
                    }
                }
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF172647), Panel))).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (scan == null) "АНАЛИЗ НЕ ЗАПУЩЕН" else "СКАНИРОВАНИЕ ЗАВЕРШЕНО", color = Accent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = Muted, fontSize = 12.sp)
                        if (scan != null) {
                            Text("Измеряемый кэш сторонних приложений: " + fmt(scan!!.apps.sumOf { it.cache }), color = Accent)
                            Text("Кэш самого оптимизатора: " + fmt(scan!!.ownCache), color = Purple)
                            Text("Прочитано приложений: ${scan!!.apps.size}; пропущено: ${scan!!.skipped}", color = Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true; status = "Сканируем статистику приложений…"
                                val res = withContext(Dispatchers.IO) { runCatching { scanApps(activity) } }
                                res.onSuccess { scan = it; status = "Сканирование завершено. Список ниже отсортирован по кэшу." }
                                    .onFailure { status = "Ошибка сканирования: " + (it.message ?: "неизвестно") }
                                stats = readStats(activity); busy = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Speed, null); Spacer(Modifier.width(8.dp)); Text("СКАНИРОВАТЬ ТЕЛЕФОН") }
                    }
                }
                if (!hasUsage(activity)) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Доступ к статистике использования", color = Accent, fontWeight = FontWeight.Bold)
                            Text("Необязательный специальный доступ, чтобы измерять время использования за 24 часа. Android показывает системный экран разрешения.", color = Muted, fontSize = 12.sp)
                            Button(onClick = { activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Выдать разрешение") }
                        }
                    }
                }
                Text("МОНИТОРИНГ", color = Accent, fontWeight = FontWeight.Bold)
                Metric("Внутренняя память", fmt(stats.total - stats.free) + " занято · " + fmt(stats.free) + " свободно", stats.total - stats.free, stats.total, Icons.Default.Storage)
                Metric("Оперативная память", fmt(stats.totalRam - stats.freeRam) + " используется · " + fmt(stats.freeRam) + " доступно", stats.totalRam - stats.freeRam, stats.totalRam, Icons.Default.Memory)
                Metric("Батарея", stats.battery.toString() + "% · температура " + if (stats.temp > 0) String.format(Locale.getDefault(), "%.1f°C", stats.temp) else "н/д", 100L - stats.battery.toLong(), 100L, Icons.Default.BatteryChargingFull)
                Text("ПРИЛОЖЕНИЯ · КЭШ ПО УБЫВАНИЮ", color = Accent, fontWeight = FontWeight.Bold)
                if (scan == null) Text("Сначала запусти сканирование.", color = Muted, fontSize = 12.sp)
                scan?.apps?.take(80)?.forEach { app ->
                    Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(app.pkg, color = Muted, fontSize = 10.sp)
                                }
                                Text(fmt(app.cache), color = Accent, fontWeight = FontWeight.Bold)
                            }
                            Text("Данные приложения: " + fmt(app.data), color = Muted, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))) }
                                        .onFailure { status = "Не удалось открыть страницу приложения" }
                                }, modifier = Modifier.weight(1f)) { Text("Настройки приложения", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                Text("ДЕЙСТВИЯ", color = Accent, fontWeight = FontWeight.Bold)
                Action("Очистить кэш Vich Optimizer", "Удаляет временные файлы только этого приложения", Icons.Default.Storage, !busy) {
                    scope.launch {
                        busy = true
                        val before = withContext(Dispatchers.IO) { activity.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
                        withContext(Dispatchers.IO) { activity.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                        val after = withContext(Dispatchers.IO) { activity.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
                        scan = null; status = "Удалено из собственного кэша: " + fmt((before - after).coerceAtLeast(0))
                        stats = readStats(activity); busy = false
                    }
                }
                Action("Освободить ОЗУ", "Измерить доступную память до и после запроса завершения фоновых процессов", Icons.Default.Memory, !busy) {
                    scope.launch {
                        busy = true
                        val before = withContext(Dispatchers.IO) { readStats(activity).freeRam }
                        val apps = scan?.apps ?: withContext(Dispatchers.IO) { runCatching { scanApps(activity).apps }.getOrDefault(emptyList()) }
                        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        withContext(Dispatchers.IO) {
                            apps.take(40).forEach { runCatching { am.killBackgroundProcesses(it.pkg) } }
                            activity.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            Thread.sleep(700)
                        }
                        stats = readStats(activity)
                        status = "Доступная ОЗУ до: " + fmt(before) + "; после: " + fmt(stats.freeRam) + "; изменение: " + fmt(stats.freeRam - before) + ". Android может перезапустить процессы."
                        busy = false
                    }
                }
                Action("Анализ батареи", "Точные BatteryStats чужих приложений закрыты Android без системных прав", Icons.Default.BatteryChargingFull, true) {
                    status = "BatteryStats других приложений недоступны обычному приложению. Доступен только общий уровень/температура батареи. Точный анализ отмечен как требующий root/системной привилегии."
                }
                Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171B29))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("BatteryStats других приложений", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("ТРЕБУЕТСЯ ROOT / СИСТЕМНАЯ ПРИВИЛЕГИЯ", color = Color.Gray, fontSize = 10.sp)
                    }
                }
                Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171B29))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Удаление кэша других приложений", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("ТРЕБУЕТСЯ ROOT / СИСТЕМНАЯ ПРИВИЛЕГИЯ. StorageStatsManager только измеряет размер.", color = Color.Gray, fontSize = 10.sp)
                    }
                }
                Text("ФАЙЛЫ ОБЩЕЙ ПАПКИ", color = Accent, fontWeight = FontWeight.Bold)
                Text("Android 11+ ограничивает массовое удаление файлов в Downloads. Без широкого доступа или подтверждения владельца приложение не может молча удалять файлы. Безопасная реализация должна показывать список найденных кандидатов и запрашивать подтверждение Android перед удалением. Автоматическое удаление не включено, чтобы не потерять пользовательские файлы.", color = Muted, fontSize = 12.sp)
                Text("ФОНОВЫЙ МОНИТОРИНГ", color = Accent, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Проверять память раз в 6 часов", fontWeight = FontWeight.SemiBold)
                        Text("Уведомление при заполнении внутренней памяти более чем на 90%", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = autoMonitor, onCheckedChange = { enabled ->
                        autoMonitor = enabled
                        val wm = WorkManager.getInstance(activity)
                        if (enabled) {
                            val request = PeriodicWorkRequestBuilder<StorageMonitorWorker>(6, TimeUnit.HOURS).build()
                            wm.enqueueUniquePeriodicWork("vich_storage_monitor", ExistingPeriodicWorkPolicy.UPDATE, request)
                            status = "Фоновая проверка включена (интервал не чаще 6 часов; Android управляет временем запуска)."
                        } else {
                            wm.cancelUniqueWork("vich_storage_monitor")
                            status = "Фоновая проверка отключена."
                        }
                    })
                }
                Action("Обновить приложение", "Открыть страницу релизов; для установки поверх нужна та же подпись", Icons.Default.SystemUpdate, true) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                }
                Text("Фоновая проверка уведомляет о нехватке места, но не удаляет файлы автоматически. Без root нельзя удалить чужой кэш или получить точные BatteryStats. Сканирование размеров может пропустить приложения, для которых Android отказал в доступе.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
@Composable
private fun Metric(title: String, detail: String, used: Long, total: Long, icon: ImageVector) {
    val p = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Accent); Spacer(Modifier.width(10.dp))
                Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Color(0xFF293246))
        }
    }
}
@Composable
private fun Action(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 12.sp) }
            Text("›", color = Accent, fontSize = 25.sp)
        }
    }
}

class StorageMonitorWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val fs = StatFs(Environment.getDataDirectory().path)
        val usedRatio = if (fs.totalBytes > 0) (fs.totalBytes - fs.availableBytes).toDouble() / fs.totalBytes else 0.0
        if (usedRatio >= 0.90) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(android.app.NotificationChannel("vich_storage", "Vich storage alerts", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            }
            val notification = if (android.os.Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(applicationContext, "vich_storage") else @Suppress("DEPRECATION") android.app.Notification.Builder(applicationContext)
            val built = notification.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Vich Optimizer: мало места")
                .setContentText("Внутренняя память заполнена более чем на 90%. Открой приложение для анализа.")
                .setAutoCancel(true).build()
            if (android.os.Build.VERSION.SDK_INT < 33 || applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(1001, built)
            }
        }
        return Result.success()
    }
}

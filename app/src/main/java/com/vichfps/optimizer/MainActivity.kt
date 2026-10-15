package com.vichfps.optimizer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Bg = Color(0xFF050713)
private val Panel = Color(0xFF10172A)
private val Accent = Color(0xFF00E5FF)
private val Mint = Color(0xFFB42BFF)
private val Muted = Color(0xFF9BA8BD)
private const val RELEASES_URL = "https://github.com/vpnxalala-glitch/Vichfps/releases/latest"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OptimizerScreen(this) }
    }
}

private data class DeviceStats(
    val totalStorage: Long, val freeStorage: Long, val totalRam: Long,
    val freeRam: Long, val battery: Int, val batteryTemp: Float
)

private fun readStats(context: Context): DeviceStats {
    val fs = StatFs(Environment.getDataDirectory().path)
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memory)
    val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val temp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    return DeviceStats(fs.totalBytes, fs.availableBytes, memory.totalMem, memory.availMem,
        if (level >= 0 && scale > 0) level * 100 / scale else 0, temp)
}

private fun bytes(value: Long): String {
    val safeValue = value.coerceAtLeast(0L)
    val gb = safeValue / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) String.format(Locale.getDefault(), "%.1f ГБ", gb)
    else String.format(Locale.getDefault(), "%.0f МБ", safeValue / (1024.0 * 1024.0))
}

@Composable
private fun OptimizerScreen(activity: ComponentActivity) {
    var stats by remember { mutableStateOf(readStats(activity)) }
    var message by remember { mutableStateOf("Устройство готово к проверке") }
    var scanning by remember { mutableStateOf(false) }
    val storageUsed = if (stats.totalStorage > 0) ((stats.totalStorage - stats.freeStorage).toFloat() / stats.totalStorage).coerceIn(0f, 1f) else 0f
    val ramUsed = if (stats.totalRam > 0) ((stats.totalRam - stats.freeRam).toFloat() / stats.totalRam).coerceIn(0f, 1f) else 0f
    val animatedStorage by animateFloatAsState(storageUsed, label = "storage")
    val animatedRam by animateFloatAsState(ramUsed, label = "ram")

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Mint, background = Bg, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("VICH", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("OPTIMIZER  /  SMART CONTROL", color = Accent, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    // Square, non-rounded brand tile.
                    Box(Modifier.size(58.dp).background(Color(0xFF070A18)), contentAlignment = Alignment.Center) {
                        Text("V", color = Accent, fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text("🚀", modifier = Modifier.align(Alignment.TopEnd).padding(1.dp), fontSize = 20.sp)
                    }
                }
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF172647), Color(0xFF10172A)))).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("СОСТОЯНИЕ УСТРОЙСТВА", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(14.dp))
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(142.dp)) {
                            CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize(), color = Color(0xFF24314D), strokeWidth = 8.dp)
                            CircularProgressIndicator(progress = { 1f - storageUsed }, modifier = Modifier.fillMaxSize(), color = Accent, strokeWidth = 8.dp, trackColor = Color.Transparent)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Speed, null, tint = Mint, modifier = Modifier.size(27.dp))
                                Text("ГОТОВ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("к проверке", color = Muted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(message, color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = {
                            scanning = true
                            stats = readStats(activity)
                            message = "Данные обновлены. Файлы не удалялись."
                            scanning = false
                        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Icon(Icons.Default.Speed, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ПРОВЕРИТЬ ТЕЛЕФОН", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("МОНИТОРИНГ", color = Accent, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                MetricCard("Внутренняя память", bytes(stats.totalStorage - stats.freeStorage) + " занято", bytes(stats.freeStorage) + " свободно", animatedStorage, Icons.Default.Storage, Accent)
                MetricCard("Оперативная память", bytes(stats.totalRam - stats.freeRam) + " используется", bytes(stats.freeRam) + " доступно", animatedRam, Icons.Default.Memory, Mint)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallMetric("Батарея", stats.battery.toString() + "%", Icons.Default.BatteryChargingFull, Accent, Modifier.weight(1f))
                    SmallMetric("Температура", if (stats.batteryTemp > 0) String.format(Locale.getDefault(), "%.1f°C", stats.batteryTemp) else "Нет данных", Icons.Default.BatteryChargingFull, Mint, Modifier.weight(1f))
                }
                Text("ИНСТРУМЕНТЫ", color = Accent, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                ToolCard("Обновить приложение", "Открыть последнюю версию Vich Optimizer", Icons.Default.SystemUpdate) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                }
                ToolCard("Освободить место", "Открыть системное управление хранилищем", Icons.Default.Storage) {
                    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                    activity.startActivity(if (intent.resolveActivity(activity.packageManager) != null) intent else Intent(Settings.ACTION_SETTINGS))
                }
                ToolCard("Управление приложениями", "Проверить приложения и очистить данные вручную", Icons.Default.Settings) {
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                }
                ToolCard("Экономия батареи", "Открыть системные настройки энергосбережения", Icons.Default.BatteryChargingFull) {
                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    activity.startActivity(if (intent.resolveActivity(activity.packageManager) != null) intent else Intent(Settings.ACTION_SETTINGS))
                }
                Text("Обновления устанавливаются поверх текущей версии, если APK подписан тем же ключом. Android ограничивает автоматическую очистку чужих кэшей; удаление файлов выполняется только через системные инструменты.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, used: String, free: String, progress: Float, icon: ImageVector, color: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(used, color = Muted, fontSize = 12.sp)
                }
                Text((progress * 100).toInt().toString() + "%", color = color, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = color, trackColor = Color(0xFF293246))
            Text(free, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SmallMetric(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text("›", color = Accent, fontSize = 28.sp)
        }
    }
}

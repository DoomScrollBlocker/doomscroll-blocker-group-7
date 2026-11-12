package com.qiell.doomscrollblocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qiell.doomscrollblocker.engine.BlockerSettings
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private const val MAX_ICONS = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onOpenPicker: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.engineState.collectAsState()
    val snapshot = rememberEngineSnapshot(viewModel)

    val accessibilityEnabled = rememberAccessibilityEnabled(context)
    val miuiAllowed = rememberMiuiBackgroundStartAllowed(context)
    val needsXiaomiPermission = accessibilityEnabled && OemPermissions.isXiaomi() && miuiAllowed != true
    val batteryExempt = rememberBatteryExempt(context)
    val needsBattery = accessibilityEnabled && !batteryExempt

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doomscroll Blocker") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Pengaturan waktu")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!accessibilityEnabled) DisclosureCard(context)
            if (needsBattery) {
                SetupCard(
                    title = "Pengecualian baterai belum aktif",
                    body = "Beberapa merek ponsel mematikan layanan di latar belakang. Izinkan " +
                        "aplikasi ini dikecualikan dari penghemat baterai supaya blokir tetap jalan.",
                    button = "Minta pengecualian baterai",
                    onClick = { requestBatteryExemption(context) },
                )
            }
            if (needsXiaomiPermission) {
                SetupCard(
                    title = "Izin Xiaomi belum aktif",
                    body = "MIUI menolak layar blokir muncul dari latar belakang. Buka Izin lainnya " +
                        "lalu aktifkan \"Tampilkan jendela pop-up saat berjalan di latar belakang\" " +
                        "dan \"Tampilkan di layar kunci\". Tanpa itu aplikasi tetap dikirim ke " +
                        "Home, tetapi layar blokir tidak terlihat.",
                    button = "Buka izin Xiaomi",
                    onClick = { OemPermissions.openPermissionEditor(context) },
                )
            }

            StatusCard(
                settings = settings,
                snapshot = snapshot,
                onOpenSettings = onOpenSettings,
            )

            AppsCard(settings = settings, onOpenPicker = onOpenPicker)

            AppCard {
                Text("Break-glass", style = MaterialTheme.typography.titleMedium)
                if (state.breakGlassLog.isEmpty()) {
                    Text(
                        "Belum pernah dipakai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Sudah dipakai ${state.breakGlassLog.size} kali. Terakhir:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.breakGlassLog.takeLast(5).reversed().forEach { epoch ->
                        Text(
                            "• " + DateFormat.getDateTimeInstance().format(Date(epoch)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Kartu status utama: tiga keadaan (boleh dipakai, terkunci, kelonggaran) dengan warna berbeda. */
@Composable
private fun StatusCard(settings: BlockerSettings, snapshot: EngineSnapshot, onOpenSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val blocked = snapshot.blockedRemainingMs > 0L && !snapshot.graceActive

    val container: Color
    val content: Color
    when {
        snapshot.graceActive -> { container = scheme.tertiaryContainer; content = scheme.onTertiaryContainer }
        blocked -> { container = scheme.errorContainer; content = scheme.onErrorContainer }
        else -> { container = scheme.primaryContainer; content = scheme.onPrimaryContainer }
    }

    AppCard(containerColor = container, contentColor = content) {
        when {
            snapshot.graceActive -> {
                Text("Kelonggaran break-glass aktif", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aplikasi yang dibatasi bisa dipakai sebentar. Cooldown tetap berjalan di belakang.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            blocked -> {
                Text("Terkunci", style = MaterialTheme.typography.labelLarge)
                Text(
                    formatDuration(snapshot.blockedRemainingMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val reopenAt = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(Date(System.currentTimeMillis() + snapshot.blockedRemainingMs))
                Text(
                    "Aplikasi yang dibatasi terbuka lagi sekitar pukul $reopenAt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                val remaining = (settings.allowanceMs - snapshot.usedMs).coerceAtLeast(0L)
                val fraction = if (settings.allowanceMs > 0) {
                    (snapshot.usedMs.toFloat() / settings.allowanceMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Text("Boleh dipakai", style = MaterialTheme.typography.labelLarge)
                Text(
                    formatDuration(remaining),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("sisa jatah waktu", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = content,
                    trackColor = content.copy(alpha = 0.18f),
                )
                Text(
                    "Terpakai ${formatDuration(snapshot.usedMs)} dari ${formatSpanMs(settings.allowanceMs)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HorizontalDivider(color = content.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Jatah ${formatSpanMs(settings.allowanceMs)} • Cooldown ${formatSpanMs(settings.cooldownMs)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text("Ubah", color = content) }
        }
    }
}

@Composable
private fun AppsCard(settings: BlockerSettings, onOpenPicker: () -> Unit) {
    val packages = settings.blockedPackages.toList()
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Aplikasi dibatasi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${packages.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (packages.isEmpty()) {
            Text(
                "Belum ada aplikasi yang dipilih, jadi belum ada yang diblokir. Pilih aplikasi " +
                    "yang sering membuatmu scroll tanpa henti.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) { Text("Pilih aplikasi") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                packages.take(MAX_ICONS).forEach { AppIcon(it, size = 40.dp) }
                if (packages.size > MAX_ICONS) {
                    Text(
                        "+${packages.size - MAX_ICONS}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Semuanya berbagi satu jatah waktu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) { Text("Kelola aplikasi") }
        }
    }
}

/** Kartu langkah persiapan (izin) dengan satu tombol aksi. */
@Composable
private fun SetupCard(title: String, body: String, button: String, onClick: () -> Unit) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(button) }
    }
}

/**
 * Pengungkapan menonjol WAJIB sebelum mengarahkan pengguna ke pengaturan
 * aksesibilitas (persyaratan Play Store, dan memang hal yang jujur untuk dilakukan):
 * menjelaskan persis data apa yang dilihat aplikasi dan apa yang tidak.
 */
@Composable
private fun DisclosureCard(context: Context) {
    AppCard {
        Text("Izin aksesibilitas diperlukan", style = MaterialTheme.typography.titleMedium)
        Text(
            "Doomscroll Blocker mendeteksi aplikasi mana yang terlihat di layar " +
                "(termasuk jendela mengambang) untuk menegakkan batas waktu yang kamu " +
                "atur sendiri. Aplikasi hanya membaca nama paket aplikasi itu. Android " +
                "menamai izinnya \"melihat isi layar\" karena daftar jendela termasuk " +
                "kategori itu, tetapi teks, ketikan, pesan, dan kata sandi tidak pernah " +
                "dibaca atau disimpan.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Tanpa izin ini aplikasi tidak bisa bekerja. Kamu bisa mencabutnya kapan " +
                "saja dari Pengaturan Android, dan mencopot aplikasi selalu bisa dilakukan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
            Text("Buka pengaturan aksesibilitas")
        }
    }
}

@Composable
private fun rememberAccessibilityEnabled(context: Context): Boolean {
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val services = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabled = services.contains(context.packageName)
            delay(2_000L)
        }
    }
    return enabled
}

/** Status izin "tampil di latar belakang" MIUI, diperiksa ulang tiap 2 detik (null = tidak terbaca). */
@Composable
private fun rememberMiuiBackgroundStartAllowed(context: Context): Boolean? {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        if (!OemPermissions.isXiaomi()) return@LaunchedEffect
        while (true) {
            allowed = OemPermissions.isBackgroundStartAllowed(context)
            delay(2_000L)
        }
    }
    return allowed
}

@Composable
private fun rememberBatteryExempt(context: Context): Boolean {
    // Dibaca ulang tiap 2 detik supaya kartu hilang begitu izin diberikan.
    var ignoring by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val pm = context.getSystemService(PowerManager::class.java)
        while (true) {
            ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            delay(2_000L)
        }
    }
    return ignoring
}

private fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

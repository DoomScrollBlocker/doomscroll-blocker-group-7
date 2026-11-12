package com.qiell.doomscrollblocker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qiell.doomscrollblocker.engine.BlockerSettings
import kotlinx.coroutines.launch

private val AllowancePresets = listOf(1, 5, 10, 15, 30)
private val CooldownPresets = listOf(15, 30, 60, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val snapshot = rememberEngineSnapshot(viewModel)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val allowanceMin = (settings.allowanceMs / 60_000L).toInt().coerceAtLeast(1)
    val cooldownMin = (settings.cooldownMs / 60_000L).toInt().coerceAtLeast(1)
    val isDefault = settings.allowanceMs == BlockerSettings.DEFAULT_ALLOWANCE_MS &&
        settings.cooldownMs == BlockerSettings.DEFAULT_COOLDOWN_MS &&
        settings.lastCustomAllowanceMinutes == null &&
        settings.lastCustomCooldownMinutes == null

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan waktu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Ringkasan aturan yang sedang berlaku, dalam kalimat biasa.
            AppCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text("Aturan saat ini", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(value = formatSpan(allowanceMin.toLong()), caption = "jatah waktu")
                    Stat(value = formatSpan(cooldownMin.toLong()), caption = "terkunci")
                }
                Text(
                    "Setelah ${formatSpan(allowanceMin.toLong())} memakai aplikasi yang dibatasi, " +
                        "semuanya terkunci ${formatSpan(cooldownMin.toLong())}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (snapshot.blockedRemainingMs > 0L) {
                AppCard(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text("Cooldown sedang berjalan", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sisa ${formatDuration(snapshot.blockedRemainingMs)}. Perubahan di sini tidak " +
                            "memendekkan atau memanjangkan cooldown ini; nilai baru berlaku di siklus berikutnya.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            AppCard {
                SectionHeader("Jatah waktu", formatSpan(allowanceMin.toLong()))
                Text(
                    "Total waktu yang boleh dipakai di semua aplikasi yang dibatasi sebelum terkunci.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DurationPicker(
                    valueMinutes = allowanceMin,
                    presets = AllowancePresets,
                    lastCustomMinutes = settings.lastCustomAllowanceMinutes,
                    maxMinutes = BlockerSettings.MAX_ALLOWANCE_MINUTES,
                    onPreset = { viewModel.setAllowanceMinutes(it, custom = false) },
                    onSaveCustom = {
                        viewModel.setAllowanceMinutes(it, custom = true)
                        toast("Jatah waktu disimpan: ${formatSpan(it.toLong())}")
                    },
                )
            }

            AppCard {
                SectionHeader("Cooldown", formatSpan(cooldownMin.toLong()))
                Text(
                    "Lama terkunci setelah jatah habis. Menjauh dari semua aplikasi yang dibatasi " +
                        "selama waktu ini juga mengembalikan jatah ke penuh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DurationPicker(
                    valueMinutes = cooldownMin,
                    presets = CooldownPresets,
                    lastCustomMinutes = settings.lastCustomCooldownMinutes,
                    maxMinutes = BlockerSettings.MAX_COOLDOWN_MINUTES,
                    onPreset = { viewModel.setCooldownMinutes(it, custom = false) },
                    onSaveCustom = {
                        viewModel.setCooldownMinutes(it, custom = true)
                        toast("Cooldown disimpan: ${formatSpan(it.toLong())}")
                    },
                )
            }

            AppCard {
                Text("Kembalikan ke bawaan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Jatah ${formatSpanMs(BlockerSettings.DEFAULT_ALLOWANCE_MS)} dan cooldown " +
                        "${formatSpanMs(BlockerSettings.DEFAULT_COOLDOWN_MS)}. Daftar aplikasi " +
                        "yang dibatasi tidak berubah.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { confirmReset = true },
                    enabled = !isDefault,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Text(if (isDefault) "Sudah pengaturan bawaan" else "Reset pengaturan waktu", Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset pengaturan waktu?") },
            text = {
                Text(
                    "Jatah menjadi ${formatSpanMs(BlockerSettings.DEFAULT_ALLOWANCE_MS)} dan cooldown " +
                        "menjadi ${formatSpanMs(BlockerSettings.DEFAULT_COOLDOWN_MS)}. Waktu khusus yang " +
                        "tersimpan dihapus. Daftar aplikasi dan cooldown yang sedang berjalan tidak berubah."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetTimeSettings()
                    toast("Pengaturan dikembalikan ke bawaan")
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun Stat(value: String, caption: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(caption, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionHeader(title: String, current: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            current,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Pemilih durasi: chip preset + chip "Khusus" yang membuka editor.
 *
 * Perbaikan UX: kolom khusus tidak pernah kosong. Isinya nilai khusus yang aktif;
 * atau, bila sekarang memakai preset, nilai khusus terakhir yang pernah disimpan.
 * Nilai khusus baru HANYA disimpan lewat tombol Simpan — mengetik "15" tidak
 * sempat menyimpan "1" lebih dulu (yang bisa memicu blokir).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPicker(
    valueMinutes: Int,
    presets: List<Int>,
    lastCustomMinutes: Int?,
    maxMinutes: Int,
    onPreset: (Int) -> Unit,
    onSaveCustom: (Int) -> Unit,
) {
    val focus = LocalFocusManager.current
    val valueIsCustom = valueMinutes !in presets
    var customOpen by rememberSaveable { mutableStateOf(valueIsCustom) }

    // Nilai kembali ke preset dari luar (mis. setelah reset): tutup editor khusus.
    LaunchedEffect(valueIsCustom) { customOpen = valueIsCustom }

    val initialDraft = if (valueIsCustom) valueMinutes else (lastCustomMinutes ?: valueMinutes)
    var draft by rememberSaveable(customOpen, valueMinutes, lastCustomMinutes) {
        mutableStateOf(initialDraft.toString())
    }
    val parsed = draft.toIntOrNull()
    val valid = parsed != null && parsed in 1..maxMinutes
    val changed = parsed != valueMinutes

    fun save() {
        val minutes = parsed ?: return
        if (!valid) return
        onSaveCustom(minutes)
        if (minutes in presets) customOpen = false
        focus.clearFocus()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { minutes ->
                FilterChip(
                    selected = !customOpen && valueMinutes == minutes,
                    onClick = {
                        customOpen = false
                        onPreset(minutes)
                        focus.clearFocus()
                    },
                    label = { Text(formatSpanShort(minutes)) },
                )
            }
            FilterChip(
                selected = customOpen || valueIsCustom,
                onClick = { if (!valueIsCustom) customOpen = !customOpen },
                label = { Text(if (valueIsCustom) "Khusus · ${formatSpanShort(valueMinutes)}" else "Khusus") },
            )
        }

        AnimatedVisibility(visible = customOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = {
                        draft = ((parsed ?: 1) - 1).coerceAtLeast(1).toString()
                    }) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi satu menit") }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        suffix = { Text("menit") },
                        isError = draft.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (changed) save() else focus.clearFocus() }),
                        modifier = Modifier.width(140.dp),
                    )
                    FilledTonalIconButton(onClick = {
                        draft = ((parsed ?: 0) + 1).coerceAtMost(maxMinutes).toString()
                    }) { Icon(Icons.Filled.Add, contentDescription = "Tambah satu menit") }
                }
                val hint = when {
                    !valid -> "Isi angka 1 sampai $maxMinutes menit."
                    !valueIsCustom && lastCustomMinutes != null && parsed == lastCustomMinutes ->
                        "Nilai khusus terakhir kamu: ${formatSpan(lastCustomMinutes.toLong())}."
                    parsed >= 60 -> "Sama dengan ${formatSpan(parsed.toLong())}."
                    else -> null
                }
                hint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { save() },
                    enabled = valid && changed,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (valid && !changed) "Tersimpan" else "Simpan") }
            }
        }
    }
}

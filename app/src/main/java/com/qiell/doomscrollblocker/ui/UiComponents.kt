package com.qiell.doomscrollblocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.qiell.doomscrollblocker.engine.AndroidClock
import com.qiell.doomscrollblocker.engine.RulesEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Kartu standar: sudut membulat, isi tersusun vertikal dengan jarak seragam. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Ikon aplikasi dimuat di latar belakang; kotak abu-abu selama belum siap atau tidak ditemukan. */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Ringkasan engine yang diperbarui tiap detik, supaya tampilan selalu mengikuti jam perangkat. */
data class EngineSnapshot(
    val usedMs: Long = 0L,
    val blockedRemainingMs: Long = 0L,
    val graceActive: Boolean = false,
)

@Composable
fun rememberEngineSnapshot(viewModel: MainViewModel): EngineSnapshot {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.engineState.collectAsState()
    var snapshot by remember { mutableStateOf(EngineSnapshot()) }
    LaunchedEffect(settings, state) {
        while (true) {
            val engine = RulesEngine(
                AndroidClock(context),
                state,
                settings.copy(whitelistedPackages = viewModel.whitelistedPackages)
            )
            snapshot = EngineSnapshot(
                usedMs = engine.currentUsedMs(),
                blockedRemainingMs = engine.blockedRemainingMs(),
                graceActive = engine.isGraceActive(),
            )
            delay(1_000L)
        }
    }
    return snapshot
}

/** "15 menit", "1 jam", "1 jam 30 menit". Untuk nilai pengaturan (bukan hitung mundur). */
fun formatSpan(minutes: Long): String = when {
    minutes < 60L -> "$minutes menit"
    minutes % 60L == 0L -> "${minutes / 60L} jam"
    else -> "${minutes / 60L} jam ${minutes % 60L} menit"
}

fun formatSpanMs(ms: Long): String = formatSpan(ms / 60_000L)

/** Versi pendek untuk chip: "15 mnt", "1 jam", "1 j 30 m". */
fun formatSpanShort(minutes: Int): String = when {
    minutes < 60 -> "$minutes mnt"
    minutes % 60 == 0 -> "${minutes / 60} jam"
    else -> "${minutes / 60} j ${minutes % 60} m"
}

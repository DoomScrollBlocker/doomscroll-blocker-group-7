package com.qiell.doomscrollblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.qiell.doomscrollblocker.DoomscrollApplication
import com.qiell.doomscrollblocker.R
import com.qiell.doomscrollblocker.engine.AndroidClock
import com.qiell.doomscrollblocker.engine.Decision
import com.qiell.doomscrollblocker.engine.RulesEngine
import com.qiell.doomscrollblocker.ui.BlockActivity
import com.qiell.doomscrollblocker.whitelist.WhitelistResolver
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Deteksi aplikasi foreground lewat jendela aplikasi yang terlihat.
 *
 * Tanggung jawab service ini sengaja tipis — semua keputusan ada di
 * [RulesEngine] yang murni Kotlin:
 * 1. Baca daftar jendela aplikasi yang terlihat (hanya nama paket dari jendela
 *    bertipe aplikasi; isi layar tidak pernah dibaca). Dengan begitu panel
 *    volume, bayangan notifikasi, dan jendela mengambang tidak dianggap
 *    "pindah aplikasi" — lihat [ForegroundPicker].
 * 2. Teruskan paket terpilih ke engine, simpan state hasilnya ke DataStore.
 * 3. Jadwalkan timer untuk sisa allowance (scroll tidak memicu event baru).
 * 4. Apabila engine memutuskan Block: tampilkan BlockActivity, lalu kirim
 *    pengguna ke Home lewat GLOBAL_ACTION_HOME. Bila sistem menolak layar
 *    blokir (mis. MIUI), langsung ke Home dan jaga sampai aplikasi hilang.
 * 5. Jeda/lanjutkan timer saat layar mati/menyala.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    // Semua akses engine lewat satu thread: event datang beruntun dan engine
    // tidak thread-safe.
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + engineExecutor.asCoroutineDispatcher())
    private val handler = Handler(Looper.getMainLooper())

    private var engine: RulesEngine? = null
    private var limitTimer: Runnable? = null

    @Volatile
    private var currentForeground: String? = null

    /** Hanya diakses dari thread utama: daftar jendela terakhir yang diteruskan ke engine. */
    private var lastVisiblePackages: List<String>? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> runEngine { it.onScreenOff(); Decision.Allow }
                Intent.ACTION_SCREEN_ON -> runEngine {
                    // onScreenOn wajib dipanggil: tanpa itu screenOn tetap false
                    // dan timer tidak pernah menghitung lagi.
                    it.onScreenOn()
                    it.onForegroundChanged(currentForeground)
                }
            }
        }
    }

    /** Mengulang blokir selama aplikasi terdaftar masih terlihat saat cooldown (mis. jendela mengambang). */
    private val blockGuard = Runnable {
        val visible = visibleAppPackages() ?: lastVisiblePackages ?: emptyList()
        scope.launch {
            val e = ensureEngine()
            if (e.isBlocked() && visible.any(e::isListed)) {
                showBlockScreen(e.blockedRemainingMs())
                scheduleBlockGuard()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
        scope.launch { ensureEngine() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // Utama: jendela aplikasi yang terlihat. Cadangan (daftar jendela tidak
        // tersedia): paket event, setelah disaring dari derau.
        val visible = visibleAppPackages()
            ?: event.packageName?.toString()?.takeUnless(::isNoisePackage)?.let { listOf(it) }
            ?: return
        if (visible == lastVisiblePackages) return
        lastVisiblePackages = visible

        runEngine { e ->
            val pkg = ForegroundPicker.pick(visible, e::isListed)
            Log.d(TAG, "foreground=$pkg visible=$visible")
            currentForeground = pkg
            e.onForegroundChanged(pkg)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        cancelLimitTimer()
        handler.removeCallbacks(blockGuard)
        scope.cancel()
        engineExecutor.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ engine

    private suspend fun ensureEngine(): RulesEngine {
        engine?.let { return it }
        val app = applicationContext as DoomscrollApplication
        val settings = app.repository.loadSettings().copy(
            whitelistedPackages = WhitelistResolver.resolve(this)
        )
        val created = RulesEngine(AndroidClock(this), app.repository.loadState(), settings)
        engine = created
        return created
    }

    private fun runEngine(block: (RulesEngine) -> Decision) {
        scope.launch {
            val e = ensureEngine()
            // Pengaturan bisa berubah dari UI saat service hidup: selalu muat ulang
            // (engine membekukan cooldown berjalan, jadi ini aman).
            val app = applicationContext as DoomscrollApplication
            e.updateSettings(
                app.repository.loadSettings().copy(
                    whitelistedPackages = WhitelistResolver.resolve(this@AppBlockerAccessibilityService)
                )
            )
            val decision = block(e)
            app.repository.saveState(e.state)
            handleDecision(decision)
        }
    }

    private fun handleDecision(decision: Decision) {
        when (decision) {
            is Decision.Block -> {
                cancelLimitTimer()
                showBlockScreen(decision.remainingMs)
                scheduleBlockGuard()
            }
            is Decision.LimitIn -> scheduleLimitTimer(decision.limitInMs)
            Decision.Allow -> cancelLimitTimer()
        }
    }

    /** Timer batas: menembak onTick tepat saat allowance habis, tanpa event jendela baru. */
    private fun scheduleLimitTimer(delayMs: Long) {
        cancelLimitTimer()
        val runnable = Runnable { runEngine { it.onTick() } }
        limitTimer = runnable
        handler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
    }

    private fun cancelLimitTimer() {
        limitTimer?.let { handler.removeCallbacks(it) }
        limitTimer = null
    }

    // ------------------------------------------------------------------- block

    private fun showBlockScreen(remainingMs: Long) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockActivity.EXTRA_REMAINING_MS, remainingMs)
        }
        runCatching { startActivity(intent) }
        handler.postDelayed({ goHomeAfterBlockScreen() }, BLOCK_SCREEN_CHECK_MS)
    }

    private fun goHomeAfterBlockScreen() {
        if (BlockActivity.isVisible) {
            // Layar blokir tampil: biarkan terbaca sebentar, lalu kirim ke Home.
            handler.postDelayed(
                { performGlobalAction(GLOBAL_ACTION_HOME) },
                BLOCK_SCREEN_HOME_DELAY_MS
            )
        } else {
            // Sistem menolak layar blokir (mis. MIUI tanpa izin "tampil di latar
            // belakang"). Jangan biarkan aplikasi tetap bisa dipakai: langsung ke Home.
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, R.string.block_fallback_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleBlockGuard() {
        handler.removeCallbacks(blockGuard)
        handler.postDelayed(blockGuard, BLOCK_GUARD_INTERVAL_MS)
    }

    // ----------------------------------------------------------------- windows

    /**
     * Paket jendela aplikasi yang terlihat, dari atas ke bawah. Null apabila
     * daftar jendela tidak tersedia (mis. izin belum aktif).
     *
     * Hanya jendela bertipe aplikasi yang dihitung: panel volume, bayangan
     * notifikasi, dan keyboard bertipe lain, jadi tidak menggeser foreground.
     * Dari tiap jendela hanya nama paket root yang dibaca.
     */
    @Suppress("DEPRECATION")
    private fun visibleAppPackages(): List<String>? {
        val all = windows
        if (all.isNullOrEmpty()) return null
        val result = mutableListOf<String>()
        for (window in all) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root
                val pkg = root?.packageName?.toString()
                root?.recycle()
                if (pkg != null && !isNoisePackage(pkg) && pkg !in result) result += pkg
            }
            window.recycle()
        }
        return result
    }

    // ------------------------------------------------------------------- noise

    /**
     * Paket yang tidak boleh dianggap sebagai aplikasi di depan: SystemUI,
     * keyboard yang sedang aktif, dialog izin, dan aplikasi ini sendiri
     * (termasuk BlockActivity).
     */
    private fun isNoisePackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui") return true
        if (pkg == "com.android.permissioncontroller" ||
            pkg == "com.google.android.permissioncontroller"
        ) return true
        val currentIme = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/') ?: return false
        return pkg == currentIme
    }

    private companion object {
        const val TAG = "DoomscrollService"

        /** Tunggu sebentar setelah startActivity untuk melihat apakah layar blokir benar-benar tampil. */
        const val BLOCK_SCREEN_CHECK_MS = 600L

        /** Dengan BLOCK_SCREEN_CHECK_MS, layar blokir terlihat ±2,5 detik sebelum ke Home. */
        const val BLOCK_SCREEN_HOME_DELAY_MS = 1_900L

        /** Jeda pemeriksaan ulang apakah aplikasi terdaftar masih terlihat saat cooldown. */
        const val BLOCK_GUARD_INTERVAL_MS = 3_500L
    }
}

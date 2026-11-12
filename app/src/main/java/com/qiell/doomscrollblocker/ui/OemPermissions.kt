package com.qiell.doomscrollblocker.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Izin khusus Xiaomi (MIUI/HyperOS). Tanpa izin "tampil di latar belakang",
 * MIUI menolak startActivity dari service sehingga layar blokir tidak pernah tampil.
 */
object OemPermissions {

    /** Kode app-op MIUI untuk "Tampilkan jendela pop-up saat berjalan di latar belakang". */
    private const val MIUI_OP_BACKGROUND_START = 10021

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)

    /** True/false bila statusnya terbaca; null bila tidak bisa dibaca (anggap belum pasti). */
    fun isBackgroundStartAllowed(context: Context): Boolean? = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val check = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val mode = check.invoke(ops, MIUI_OP_BACKGROUND_START, Process.myUid(), context.packageName) as Int
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrNull()

    /** Buka halaman izin MIUI untuk aplikasi ini; cadangan: halaman info aplikasi biasa. */
    fun openPermissionEditor(context: Context) {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        }
        val fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(miui) }
            .recoverCatching { context.startActivity(fallback) }
    }
}

package com.qiell.doomscrollblocker.service

/**
 * Memilih paket "foreground" untuk engine dari daftar jendela aplikasi yang
 * sedang terlihat. Kotlin murni supaya bisa diuji di JVM.
 *
 * Kenapa tidak cukup memakai paket dari event terakhir:
 * - Panel volume, bayangan notifikasi, dan kontrol jendela mengambang memancarkan
 *   event dengan paket lain. Bila dianggap "berpindah aplikasi", timer berhenti
 *   padahal aplikasi terdaftar masih terbuka di layar.
 * - Pada jendela mengambang / split-screen, beberapa aplikasi terlihat sekaligus.
 *
 * Aturan: apabila ada aplikasi terdaftar di antara jendela yang terlihat,
 * aplikasi itulah yang dihitung (walaupun bukan yang paling atas).
 */
object ForegroundPicker {

    /**
     * @param visibleAppPackages paket dari jendela aplikasi yang terlihat, sudah
     *   disaring dari derau, urut dari jendela paling atas.
     * @return paket terdaftar bila ada; kalau tidak, jendela paling atas; null
     *   bila tidak ada jendela aplikasi sama sekali.
     */
    fun pick(visibleAppPackages: List<String>, isListed: (String) -> Boolean): String? =
        visibleAppPackages.firstOrNull(isListed) ?: visibleAppPackages.firstOrNull()
}

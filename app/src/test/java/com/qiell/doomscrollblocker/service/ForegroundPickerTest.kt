package com.qiell.doomscrollblocker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundPickerTest {

    private val reddit = "com.reddit.frontpage"
    private val launcher = "com.miui.home"
    private val listed = setOf(reddit)

    private fun pick(vararg visible: String) =
        ForegroundPicker.pick(visible.toList()) { it in listed }

    @Test
    fun `aplikasi terdaftar di jendela mengambang tetap dipilih walau launcher di atas`() {
        assertEquals(reddit, pick(launcher, reddit))
    }

    @Test
    fun `aplikasi terdaftar dipilih bila sendirian`() {
        assertEquals(reddit, pick(reddit))
    }

    @Test
    fun `tanpa aplikasi terdaftar dipilih jendela paling atas`() {
        assertEquals(launcher, pick(launcher, "com.example.other"))
    }

    @Test
    fun `tanpa jendela aplikasi hasilnya null`() {
        assertNull(pick())
    }
}

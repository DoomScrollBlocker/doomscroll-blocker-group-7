package com.qiell.doomscrollblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class MainScreen { HOME, PICKER, SETTINGS }

/** Akar UI utama: berpindah antar layar dengan state sederhana (tanpa library navigasi). */
@Composable
fun MainRoot(viewModel: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(MainScreen.HOME) }

    // Tombol Back dari layar dalam kembali ke beranda, bukan menutup aplikasi.
    BackHandler(enabled = screen != MainScreen.HOME) { screen = MainScreen.HOME }

    when (screen) {
        MainScreen.HOME -> HomeScreen(
            viewModel = viewModel,
            onOpenPicker = { screen = MainScreen.PICKER },
            onOpenSettings = { screen = MainScreen.SETTINGS }
        )
        MainScreen.PICKER -> AppPickerScreen(
            viewModel = viewModel,
            onBack = { screen = MainScreen.HOME }
        )
        MainScreen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = MainScreen.HOME }
        )
    }
}

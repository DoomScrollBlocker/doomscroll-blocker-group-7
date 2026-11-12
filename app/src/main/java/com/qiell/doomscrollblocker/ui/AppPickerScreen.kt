package com.qiell.doomscrollblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    val apps = remember { viewModel.loadInstalledApps() }
    val selected = settings.blockedPackages

    val visible = apps.filter { app ->
        (!onlySelected || app.packageName in selected) &&
            (query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pilih aplikasi")
                        Text(
                            if (selected.isEmpty()) "Belum ada yang dipilih" else "${selected.size} dipilih",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                "Semua aplikasi yang dipilih berbagi SATU jatah waktu. Telepon, pesan, dan " +
                    "pengaturan tidak ditampilkan karena tidak pernah diblokir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari aplikasi") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = onlySelected,
                    onClick = { onlySelected = !onlySelected },
                    label = { Text("Hanya yang dipilih (${selected.size})") },
                )
            }

            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(visible, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { viewModel.setPackageBlocked(app.packageName, it) },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            if (onlySelected && query.isBlank()) "Belum ada aplikasi yang dipilih."
                            else "Tidak ada aplikasi yang cocok.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

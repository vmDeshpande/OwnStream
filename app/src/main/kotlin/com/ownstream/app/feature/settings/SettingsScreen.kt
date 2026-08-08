package com.ownstream.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ownstream.app.feature.onboarding.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val identity by viewModel.localIdentity.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ProfileSection(identity?.username ?: "Unknown", identity?.id ?: "No ID")
            }
            item { Divider() }
            item {
                SettingsItem("Privacy & Security", Icons.Default.Security, "App Lock, Identity Verification")
            }
            item {
                SettingsItem("Storage", Icons.Default.Storage, "Default Storage, Migration")
            }
            item {
                SettingsItem("Appearance", Icons.Default.Palette, "Theme, Colors")
            }
            item { Divider() }
            item {
                SettingsItem("About OwnStream", Icons.Default.Info, "Version 0.1.0 (MVP)")
            }
        }
    }
}

@Composable
fun ProfileSection(username: String, id: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(username.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(username, style = MaterialTheme.typography.titleLarge)
            Text(id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, subtitle: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

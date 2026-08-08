package com.ownstream.app.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSelectionScreen(onStorageSelected: () -> Unit) {
    val options = listOf(
        StorageOption(
            "Device",
            "Stored locally on this phone. No external storage required.",
            Icons.Default.Storage,
            isEnabled = true,
            isDefault = true
        ),
        StorageOption(
            "My Server",
            "Store encrypted conversation data on infrastructure you control.",
            Icons.Default.DeviceHub,
            isEnabled = false
        ),
        StorageOption(
            "Cloud",
            "Use a managed OwnStream-compatible storage provider.",
            Icons.Default.Cloud,
            isEnabled = false
        ),
        StorageOption(
            "Advanced",
            "Connect another compatible storage provider.",
            Icons.Default.Settings,
            isEnabled = false
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Where should your chats live?") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "OwnStream gives you control over your data. You can change this later.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(options) { option ->
                    StorageOptionItem(option) {
                        if (option.isEnabled) onStorageSelected()
                    }
                }
            }
            Button(
                onClick = onStorageSelected,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Confirm Selection")
            }
        }
    }
}

@Composable
fun StorageOptionItem(option: StorageOption, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = option.isEnabled) { onClick() },
        colors = if (option.isEnabled) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                option.icon,
                contentDescription = null,
                tint = if (option.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (option.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!option.isEnabled) {
                    Text(
                        "Coming soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (option.isDefault) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Default", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

data class StorageOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isEnabled: Boolean,
    val isDefault: Boolean = false
)

package com.ownstream.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ownstream.app.domain.model.StorageProviderType

@Composable
fun StorageBadge(
    providerType: StorageProviderType,
    modifier: Modifier = Modifier
) {
    val (icon, label) = when (providerType) {
        StorageProviderType.LOCAL -> Icons.Default.Storage to "🔒 Local Device"
        StorageProviderType.SELF_HOSTED -> Icons.Default.DeviceHub to "🖥 My Server"
        StorageProviderType.CLOUD -> Icons.Default.Cloud to "☁ Cloud"
        StorageProviderType.ADVANCED -> Icons.Default.Storage to "🛠 Advanced"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

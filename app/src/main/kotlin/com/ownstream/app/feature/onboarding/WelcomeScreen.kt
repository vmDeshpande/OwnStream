package com.ownstream.app.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "OwnStream",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your chats. Your data. Your choice.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "A privacy-first messenger where you control where your conversations are stored.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Get Started")
            }
            TextButton(onClick = { /* Learn more logic */ }) {
                Text("Learn more")
            }
        }
    }
}

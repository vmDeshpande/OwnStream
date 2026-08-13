package com.ownstream.app.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("relay_config", Context.MODE_PRIVATE)
    
    private val _baseUrlFlow = MutableStateFlow(prefs.getString("base_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080")
    val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    var baseUrl: String
        get() = _baseUrlFlow.value
        set(value) {
            prefs.edit().putString("base_url", value).apply()
            _baseUrlFlow.value = value
        }

    val wsUrl: String
        get() {
            val url = baseUrl
            val host = url.removePrefix("http://").removePrefix("https://")
            return if (url.startsWith("https")) "wss://$host/v1/ws" else "ws://$host/v1/ws"
        }
}

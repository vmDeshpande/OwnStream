package com.ownstream.app.core.network

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayConfig @Inject constructor() {
    // In a real app, these would come from BuildConfig or a remote config service
    val baseUrl: String = "http://10.0.2.2:8080"
    val wsUrl: String = "ws://10.0.2.2:8080/v1/ws"
}

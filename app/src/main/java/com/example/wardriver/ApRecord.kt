package com.example.wardriver

data class ApRecord(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val capabilities: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

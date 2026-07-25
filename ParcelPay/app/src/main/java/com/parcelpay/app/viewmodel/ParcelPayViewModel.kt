package com.parcelpay.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ParcelPayViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = "https://yogqmiqpvuhssgwnnupe.supabase.co/rest/v1/parcels?select=id"
            val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlvZ3FtaXFwdnVoc3Nnd25udXBlIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NDYxOTg5NywiZXhwIjoyMTAwMTk1ODk3fQ.8F-ob7ahbI7LEcEssA_VuoLs8fPCKZf2MC_lSey45Ns"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "count=exact")
                .addHeader("Range-Unit", "items")
                .head() // Use HEAD request to just get headers
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentRange = response.header("content-range")
                        if (contentRange != null && contentRange.contains("/")) {
                            val countStr = contentRange.substringAfter("/").trim()
                            if (countStr != "*") {
                                _totalCount.value = countStr.toIntOrNull() ?: 0
                            }
                        }
                    } else {
                        Log.e("ParcelPayViewModel", "Supabase error: ${response.code}")
                    }
                    Unit
                }
            } catch (e: IOException) {
                Log.e("ParcelPayViewModel", "Network error", e)
            }
        }
    }
}

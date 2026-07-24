package com.parcelpay.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _qrImagePath = MutableStateFlow<String?>(null)
    val qrImagePath: StateFlow<String?> = _qrImagePath.asStateFlow()

    private val QR_IMAGE_KEY = stringPreferencesKey("qr_image_path")

    init {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.map { preferences ->
                preferences[QR_IMAGE_KEY]
            }.collect { path ->
                _qrImagePath.value = path
            }
        }
    }

    fun saveQrImage(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "payment_qr.jpg")
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            val savedPath = file.absolutePath
            context.dataStore.edit { preferences ->
                preferences[QR_IMAGE_KEY] = savedPath
            }
        }
    }
}

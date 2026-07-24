package com.parcelpay.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parcelpay.app.data.ParcelDatabase
import com.parcelpay.app.data.ParcelEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ParcelDatabase.getDatabase(application).parcelDao()

    val history: StateFlow<List<ParcelEntity>> = dao.getAllParcels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addParcel(photoPath: String, phoneNumber: String) {
        viewModelScope.launch {
            try {
                dao.insertParcel(ParcelEntity(photoPath = photoPath, phoneNumber = phoneNumber))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

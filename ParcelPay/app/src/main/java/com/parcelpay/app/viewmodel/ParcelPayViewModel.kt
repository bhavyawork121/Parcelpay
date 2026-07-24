package com.parcelpay.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ParcelPayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ParcelPayUiState())
    val uiState: StateFlow<ParcelPayUiState> = _uiState.asStateFlow()

    // Example methods
    fun updateOcrResult(result: String) {
        _uiState.value = _uiState.value.copy(ocrResult = result)
    }
}

data class ParcelPayUiState(
    val ocrResult: String = "",
    val isLoading: Boolean = false
)

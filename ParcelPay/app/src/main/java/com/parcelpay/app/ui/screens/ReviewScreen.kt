package com.parcelpay.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.parcelpay.app.viewmodel.ReviewViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    photoPath: String?,
    viewModel: ReviewViewModel,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(photoPath) {
        if (photoPath != null) {
            viewModel.processImage(context, photoPath)
        }
    }

    if (showSuccess) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Opening WhatsApp...", style = MaterialTheme.typography.titleLarge)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Review Parcel") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (photoPath != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                ) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "Captured label",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Text("No Photo Provided")
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isProcessing || uiState.isAiFallback) {
                CircularProgressIndicator()
                val loadingText = if (uiState.isAiFallback) "Asking AI to read the label…" else "Reading number…"
                Text(loadingText, modifier = Modifier.padding(top = 8.dp))
            } else {
                if (uiState.candidates.size > 1) {
                    Text("Multiple numbers found. Select one:", style = MaterialTheme.typography.bodyMedium)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.candidates) { candidate ->
                            FilterChip(
                                selected = uiState.enteredNumber == candidate,
                                onClick = { viewModel.updateEnteredNumber(candidate) },
                                label = { Text(candidate) }
                            )
                        }
                    }
                } else if (uiState.candidates.isEmpty() && uiState.enteredNumber.isEmpty()) {
                    TextButton(onClick = { if (photoPath != null) viewModel.processImage(context, photoPath, true) }) {
                        Text("No numbers found. Enhance image?")
                    }
                }

                OutlinedTextField(
                    value = uiState.enteredNumber,
                    onValueChange = { viewModel.updateEnteredNumber(it) },
                    label = { Text("Phone Number") },
                    trailingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Number")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                val isValidFormat = uiState.enteredNumber.length == 10
                if (uiState.enteredNumber.isNotEmpty() && !isValidFormat) {
                    Text(
                        text = "Warning: Phone number should be exactly 10 digits.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                
                if (uiState.recipientName != null || uiState.address != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("AI Extracted Info (Sanity Check):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (uiState.recipientName != null) {
                                Text("Name: ${uiState.recipientName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (uiState.address != null) {
                                Text("Address: ${uiState.address}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { 
                        if (isValidFormat && photoPath != null) {
                            coroutineScope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSuccess = true
                                
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://api.whatsapp.com/send?phone=91${uiState.enteredNumber}")
                                    ).apply {
                                        setPackage("com.whatsapp")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("WhatsApp", "Share failed", e)
                                    val fbIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW, 
                                        android.net.Uri.parse("https://api.whatsapp.com/send?phone=91${uiState.enteredNumber}")
                                    )
                                    context.startActivity(fbIntent)
                                }
                                
                                delay(1000)
                                onSend(uiState.enteredNumber) 
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = uiState.enteredNumber.isNotEmpty() && isValidFormat,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Send to WhatsApp", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

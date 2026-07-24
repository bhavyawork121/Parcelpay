package com.parcelpay.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.parcelpay.app.data.ParcelEntity
import com.parcelpay.app.viewmodel.HistoryViewModel
import com.parcelpay.app.viewmodel.SettingsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val qrImagePath by settingsViewModel.qrImagePath.collectAsState(initial = null)
    var selectedParcel by remember { mutableStateOf<ParcelEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedParcel != null) {
            ParcelDetailView(
                parcel = selectedParcel!!,
                qrImagePath = qrImagePath,
                onClose = { selectedParcel = null },
                onSend = { sendViaWhatsApp(context, selectedParcel!!.photoPath, qrImagePath, selectedParcel!!.phoneNumber) }
            )
        } else {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty History",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No parcels sent yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Your history will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history) { parcel ->
                        HistoryItem(parcel) {
                            selectedParcel = parcel
                        }
                        if (history.last() != parcel) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(parcel: ParcelEntity, onClick: () -> Unit) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        parcel.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(56.dp)
            ) {
                AsyncImage(
                    model = File(parcel.photoPath),
                    contentDescription = "Parcel thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(parcel.phoneNumber, style = MaterialTheme.typography.titleMedium)
                Text(relativeTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ParcelDetailView(parcel: ParcelEntity, qrImagePath: String?, onClose: () -> Unit, onSend: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Phone: ${parcel.phoneNumber}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        AsyncImage(
            model = File(parcel.photoPath),
            contentDescription = "Parcel details",
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        var chatOpened by remember { mutableStateOf(false) }
        val context = LocalContext.current
        
        if (!chatOpened) {
            Button(
                onClick = { 
                    if (parcel.phoneNumber.length == 10) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=91${parcel.phoneNumber}"))
                        context.startActivity(intent)
                        chatOpened = true
                    } else {
                        Toast.makeText(context, "Phone number must be exactly 10 digits", Toast.LENGTH_SHORT).show()
                    }
                }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send again")
            }
        } else {
            Text(
                "Chat opened — tap to attach & share the photos", 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSend()
                    chatOpened = false
                }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share Images")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun sendViaWhatsApp(context: Context, photoPath: String, qrPath: String?, phoneNumber: String) {
    try {
        val imageUris = ArrayList<Uri>()
        
        val photoFile = File(photoPath)
        if (photoFile.exists()) {
            imageUris.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile))
        }
        
        if (qrPath != null) {
            val qrFile = File(qrPath)
            if (qrFile.exists()) {
                imageUris.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", qrFile))
            }
        }
        
        if (imageUris.isEmpty()) {
            Toast.makeText(context, "No images found to send", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
            setPackage("com.whatsapp")
            putExtra("jid", "91$phoneNumber@s.whatsapp.net") // Standard WA URL format for direct messages
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

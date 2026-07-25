package com.parcelpay.app.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.parcelpay.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class ReviewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val okHttpClient = OkHttpClient()

    fun processImage(context: Context, imagePath: String, forceFallback: Boolean = false) {
        if (_uiState.value.isProcessing || _uiState.value.isAiFallback) return
        
        if (forceFallback) {
            _uiState.value = _uiState.value.copy(isAiFallback = true, error = null)
        } else {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(isProcessing = false, isAiFallback = false, error = "File not found")
                    return@launch
                }

                if (forceFallback) {
                    runAiFallback(file.absolutePath)
                    return@launch
                }
                
                var tfliteText = ""
                var mlkitText = ""
                
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    val originalImage = InputImage.fromBitmap(bitmap, 0)
                    
                    val visionText = textRecognizer.process(originalImage).await()
                    mlkitText = visionText.text
                    
                    val tfliteClient = com.parcelpay.app.utils.TfLiteOcrClient(context)
                    
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox
                            if (box != null) {
                                // Crop the line from the bitmap
                                try {
                                    val safeLeft = Math.max(0, box.left)
                                    val safeTop = Math.max(0, box.top)
                                    val safeWidth = Math.min(bitmap.width - safeLeft, box.width())
                                    val safeHeight = Math.min(bitmap.height - safeTop, box.height())
                                    
                                    if (safeWidth > 0 && safeHeight > 0) {
                                        val croppedBitmap = android.graphics.Bitmap.createBitmap(
                                            bitmap, safeLeft, safeTop, safeWidth, safeHeight
                                        )
                                        val customRead = tfliteClient.extractTextFromBitmap(croppedBitmap)
                                        tfliteText += customRead + " \n"
                                    }
                                } catch (e: Exception) {
                                    Log.e("ReviewViewModel", "Failed to crop box", e)
                                }
                            }
                        }
                    }
                    tfliteClient.close()
                } catch (e: Exception) {
                    Log.e("ReviewViewModel", "TFLite OCR failed", e)
                }

                // Try extracting from both our custom model AND ML Kit's baseline to be extra safe
                val combinedText = tfliteText + "\n" + mlkitText
                val candidatesList = extractPhoneNumbers(combinedText)
                if (candidatesList.isEmpty()) {
                    // Fallback to AI API if custom model fails to find a number
                    runAiFallback(file.absolutePath)
                } else {
                    val defaultNumber = if (candidatesList.size == 1) candidatesList.first() else ""
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        rawText = tfliteText,
                        candidates = candidatesList,
                        enteredNumber = defaultNumber
                    )
                }
            } catch (e: Exception) {
                Log.e("ReviewViewModel", "Image processing failed", e)
                _uiState.value = _uiState.value.copy(isProcessing = false, isAiFallback = false, error = e.message)
            }
        }
    }

    private fun runAiFallback(imagePath: String) {
        _uiState.value = _uiState.value.copy(isProcessing = false, isAiFallback = true)
        try {
            val file = File(imagePath)
            val bytes = file.readBytes()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            val prompt = """
                Return ONLY a JSON object with fields: 
                - phone_number (10-digit Indian number if found, else null)
                - recipient_name
                - address
                - confidence ("high" or "low")
                - raw_text_seen
                Do not return any prose or markdown fences. Just valid JSON.
            """.trimIndent()
            
            val jsonPayload = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
            }
            
            val apiKey = BuildConfig.GEMINI_API_KEY
            // NOTE: If this app is ever shared beyond one device, the API key should move to a small backend proxy instead of living inside the app directly!
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .addHeader("content-type", "application/json")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val jsonResponse = JSONObject(body)
                val candidatesArray = jsonResponse.getJSONArray("candidates")
                val textContent = candidatesArray.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                
                val cleanJsonStr = textContent.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val resultObj = JSONObject(cleanJsonStr)
                
                var phoneNumber = ""
                if (resultObj.has("phone_number") && !resultObj.isNull("phone_number")) {
                    val rawNum = resultObj.getString("phone_number")
                    val digitsOnly = rawNum.replace(Regex("[^0-9]"), "")
                    if (digitsOnly.length == 10) phoneNumber = digitsOnly
                    else if (digitsOnly.length == 12 && digitsOnly.startsWith("91")) phoneNumber = digitsOnly.substring(2)
                }
                
                val recipientName = if (resultObj.has("recipient_name") && !resultObj.isNull("recipient_name")) resultObj.getString("recipient_name") else null
                val address = if (resultObj.has("address") && !resultObj.isNull("address")) resultObj.getString("address") else null
                
                val candidatesList = if (phoneNumber.isNotEmpty()) listOf(phoneNumber) else emptyList()
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isAiFallback = false,
                    candidates = candidatesList,
                    enteredNumber = phoneNumber,
                    recipientName = recipientName,
                    address = address
                )
            } else {
                Log.e("ReviewViewModel", "AI API error: ${response.code} ${response.body?.string()}")
                _uiState.value = _uiState.value.copy(isProcessing = false, isAiFallback = false)
            }
        } catch (e: Exception) {
            Log.e("ReviewViewModel", "AI fallback failed", e)
            _uiState.value = _uiState.value.copy(isProcessing = false, isAiFallback = false)
        }
    }

    private fun extractPhoneNumbers(text: String): List<String> {
        val regex = Regex("""(?:\+91|91|0)?\s*[6-9]\d{9}""")
        val matches = regex.findAll(text.replace(Regex("""[- .]"""), ""))
        val candidates = mutableSetOf<String>()
        
        for (match in matches) {
            var number = match.value
            // Strip prefixes
            if (number.startsWith("+91")) number = number.substring(3)
            else if (number.length == 12 && number.startsWith("91")) number = number.substring(2)
            else if (number.length == 11 && number.startsWith("0")) number = number.substring(1)
            
            if (number.length == 10) {
                candidates.add(number)
            }
        }
        return candidates.toList()
    }

    fun updateEnteredNumber(number: String) {
        _uiState.value = _uiState.value.copy(enteredNumber = number)
    }

    fun saveParcelToSupabase(phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://yogqmiqpvuhssgwnnupe.supabase.co/rest/v1/parcels"
                val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlvZ3FtaXFwdnVoc3Nnd25udXBlIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NDYxOTg5NywiZXhwIjoyMTAwMTk1ODk3fQ.8F-ob7ahbI7LEcEssA_VuoLs8fPCKZf2MC_lSey45Ns"
                
                val jsonPayload = JSONObject().apply {
                    put("phone", phoneNumber)
                }
                
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                    
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("ReviewViewModel", "Supabase insert error: ${response.code} ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ReviewViewModel", "Failed to insert into Supabase", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }
}

data class ReviewUiState(
    val isProcessing: Boolean = false,
    val isAiFallback: Boolean = false,
    val rawText: String = "",
    val candidates: List<String> = emptyList(),
    val enteredNumber: String = "",
    val recipientName: String? = null,
    val address: String? = null,
    val error: String? = null
)

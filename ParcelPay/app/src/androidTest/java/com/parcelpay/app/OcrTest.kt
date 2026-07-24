package com.parcelpay.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OcrTest {

    @Test
    fun testOcrExtractsIndianPhoneNumber() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        
        val bitmap = android.graphics.BitmapFactory.decodeStream(testContext.assets.open("test_label.jpg"))
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        val visionText = recognizer.process(inputImage).await()
        val text = visionText.text
        
        // Use the same regex from ReviewViewModel
        val regex = Regex("""(?:\+91|91|0)?\s*[6-9]\d{9}""")
        val matches = regex.findAll(text.replace(Regex("""[- .]"""), ""))
        val candidates = mutableSetOf<String>()
        
        for (match in matches) {
            var number = match.value
            if (number.startsWith("+91")) number = number.substring(3)
            else if (number.length == 12 && number.startsWith("91")) number = number.substring(2)
            else if (number.length == 11 && number.startsWith("0")) number = number.substring(1)
            
            if (number.length == 10) {
                candidates.add(number)
            }
        }
        
        assertEquals(listOf("9876543210"), candidates.toList())
    }
}

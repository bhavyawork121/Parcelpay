package com.parcelpay.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfLiteOcrClient(context: Context) {

    private var interpreter: Interpreter? = null
    private val imgWidth = 128
    private val imgHeight = 32

    // Vocabulary from the Python StringLookup.
    // Index 0 = [UNK] (keras default for StringLookup)
    // Indices 1..13 = characters
    private val charMap = listOf("[UNK]", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "D", "Z", "X")

    init {
        try {
            val assetFileDescriptor = context.assets.openFd("crnn_model.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val mappedByteBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options()
            options.setNumThreads(2)
            interpreter = Interpreter(mappedByteBuffer, options)
            Log.d("TfLiteOcrClient", "Model loaded successfully.")
        } catch (e: Exception) {
            Log.e("TfLiteOcrClient", "Error loading model", e)
        }
    }

    fun extractTextFromBitmap(bitmap: Bitmap): String {
        if (interpreter == null) return ""

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, imgWidth, imgHeight, true)

        // The python code resizes to (32, 128) then transposes to (128, 32, 1).
        // This means width is the outer dimension, height is the inner dimension.
        val byteBuffer = ByteBuffer.allocateDirect(1 * imgWidth * imgHeight * 1 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (x in 0 until imgWidth) {
            for (y in 0 until imgHeight) {
                val pixel = scaledBitmap.getPixel(x, y)
                // Grayscale conversion
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                byteBuffer.putFloat(gray)
            }
        }

        // Output shape of the CRNN is usually [1, time_steps, num_classes]
        // From python we know dense2 has len(vocab) + 1 classes. len(vocab) = 14. +1 = 15.
        // Time steps is usually imgWidth / 4 = 128 / 4 = 32.
        val timeSteps = 32
        val numClasses = 15
        val output = Array(1) { Array(timeSteps) { FloatArray(numClasses) } }

        try {
            interpreter?.run(byteBuffer, output)
        } catch (e: Exception) {
            Log.e("TfLiteOcrClient", "Inference failed", e)
            return ""
        }

        return ctcDecode(output[0])
    }

    private fun ctcDecode(predictions: Array<FloatArray>): String {
        val result = StringBuilder()
        var prevIndex = -1

        for (t in predictions.indices) {
            val probs = predictions[t]
            var maxProb = -1f
            var maxIndex = -1

            for (i in probs.indices) {
                if (probs[i] > maxProb) {
                    maxProb = probs[i]
                    maxIndex = i
                }
            }

            // Keras CTC puts the blank label at the last index (numClasses - 1 = 14)
            // If it's not blank and not a repeated character, add it to the result
            if (maxIndex != 14 && maxIndex != prevIndex) {
                if (maxIndex > 0 && maxIndex < charMap.size) {
                    val char = charMap[maxIndex]
                    if (char != "[UNK]") {
                        result.append(char)
                    }
                }
            }
            prevIndex = maxIndex
        }

        return result.toString()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

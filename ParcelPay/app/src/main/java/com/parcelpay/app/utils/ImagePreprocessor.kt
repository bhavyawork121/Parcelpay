package com.parcelpay.app.utils

import android.graphics.Bitmap
import android.graphics.Color

object ImagePreprocessor {
    /**
     * Applies Bradley-Roth adaptive thresholding to enhance handwritten and low-contrast labels.
     */
    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val intImg = IntArray(width * height)
        
        // Step 1: Grayscale and Integral Image
        for (y in 0 until height) {
            var sum = 0
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                sum += luma
                if (y == 0) {
                    intImg[index] = sum
                } else {
                    intImg[index] = intImg[(y - 1) * width + x] + sum
                }
            }
        }

        // Step 2: Adaptive thresholding
        // Window size (s) is a fraction of image width.
        val s = width / 8
        val s2 = s / 2
        val t = 0.15f // 15% darker than average

        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = (x - s2).coerceAtLeast(0)
                val x2 = (x + s2).coerceAtMost(width - 1)
                val y1 = (y - s2).coerceAtLeast(0)
                val y2 = (y + s2).coerceAtMost(height - 1)

                val count = (x2 - x1) * (y2 - y1)
                var sum = intImg[y2 * width + x2] - intImg[y1 * width + x2] - intImg[y2 * width + x1] + intImg[y1 * width + x1]

                val index = y * width + x
                val color = pixels[index]
                val luma = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()

                if (luma * count <= sum * (1.0f - t)) {
                    pixels[index] = Color.BLACK
                } else {
                    pixels[index] = Color.WHITE
                }
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}

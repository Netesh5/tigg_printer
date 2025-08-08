

package com.example.tigg_printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.RemoteException
import android.util.Base64
import android.util.Log
import androidx.annotation.NonNull
import com.example.clientapp.AppService
import com.example.clientapp.utils.BaseUtils
import acquire.client_connection.IPaymentCallback
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** TiggPrinterPlugin */

class TiggPrinterPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "tigg_printer")
        channel.setMethodCallHandler(this)
        

        Thread {
            try {
                Log.i("TiggPrinter", "Initializing AppService in background...")
                AppService.me().init(context)
                AppService.me().setPackageName("com.fewapay.cplus")
                
                
                Log.i("TiggPrinter", "Attempting initial service bind...")
                val bindResult = AppService.me().bindService()
                Log.i("TiggPrinter", "Initial service bind result: $bindResult")

                Thread.sleep(1000)
                val isConnected = AppService.me()?.isServiceConnected() ?: false
                Log.i("TiggPrinter", "Initial service connection status: $isConnected")
                
                if (!isConnected) {
                    Log.w("TiggPrinter", "Service not connected after initial bind. Will retry on demand.")
                }
                
            } catch (e: Exception) {
                Log.e("TiggPrinter", "Failed to initialize AppService: ${e.message}", e)
            }
        }.start()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "isPrinterAvailable" -> {
                try {
                    // Check if AppService is initialized and service is connected
                    val isServiceConnected = AppService.me()?.isServiceConnected() ?: false
                    if (isServiceConnected) {
                        result.success(true)
                    } else {
                        result.error("SERVICE_UNAVAILABLE", "Printer service is not connected", null)
                    }
                } catch (e: Exception) {
                    result.error("SERVICE_UNAVAILABLE", "Printer service check failed: ${e.message}", null)
                }
            }
            "bindService" -> {
                // Execute binding in background to avoid blocking main thread
                Thread {
                    try {
                        Log.i("TiggPrinter", "Manual bind service requested")
                        
                        // First check if AppService is initialized
                        if (AppService.me() == null) {
                            Log.e("TiggPrinter", "AppService is not initialized")
                            context.mainExecutor.execute {
                                result.error("SERVICE_NOT_INITIALIZED", "AppService is not initialized", null)
                            }
                            return@Thread
                        }
                        
                        // Check current connection status
                        val currentStatus = AppService.me().isServiceConnected()
                        Log.i("TiggPrinter", "Current service connection status: $currentStatus")
                        
                        if (currentStatus) {
                            context.mainExecutor.execute {
                                result.success("Service is already connected")
                            }
                            return@Thread
                        }
                        
                        // Attempt to bind with timeout to prevent hanging
                        Log.i("TiggPrinter", "Attempting service bind...")
                        val bindResult = AppService.me().bindService()
                        Log.i("TiggPrinter", "Bind service result: $bindResult")
                        
                        // Short wait for connection - reduced from 2s to 1s
                        Thread.sleep(1000)
                        val finalStatus = AppService.me()?.isServiceConnected() ?: false
                        Log.i("TiggPrinter", "Service connection status after bind: $finalStatus")
                        
                        // Return result on main thread
                        context.mainExecutor.execute {
                            if (finalStatus) {
                                result.success("Service bound and connected successfully")
                            } else {
                                result.error("BIND_FAILED", "Service bind initiated but connection failed. TiggPrinter service may not be running.", null)
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.e("TiggPrinter", "Failed to bind service", e)
                        context.mainExecutor.execute {
                            result.error("BIND_ERROR", "Error during service binding: ${e.message}", null)
                        }
                    }
                }.start()
            }
            "isServiceConnected" -> {
                try {
                    val isConnected = AppService.me()?.isServiceConnected() ?: false
                    Log.i("TiggPrinter", "Service connection check: $isConnected")
                    result.success(isConnected)
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "Failed to check service connection", e)
                    result.error("CONNECTION_CHECK_FAILED", "Failed to check service connection: ${e.message}", null)
                }
            }
            "printBase64Image" -> {
                val base64Image = call.argument<String>("base64Image")
                val textSize = call.argument<Int>("textSize") ?: 24

                if (base64Image.isNullOrEmpty()) {
                    result.error("INVALID_INPUT", "Base64 image data is required", null)
                    return
                }

                if (textSize <= 0 || textSize > 100) {
                    result.error("INVALID_INPUT", "Text size must be between 1 and 100", null)
                    return
                }

                if (!BaseUtils.isValidBase64(base64Image)) {
                    result.error("INVALID_BASE64", "Invalid Base64 image data format", null)
                    return
                }

                try {
                    // Check if service is available and connected
                    if (AppService.me() == null) {
                        result.error("SERVICE_UNAVAILABLE", "Printer service is not initialized", null)
                        return
                    }
                    
                    if (!AppService.me().isServiceConnected()) {
                        result.error("SERVICE_NOT_CONNECTED", "Printer service is not connected. Please bind service first.", null)
                        return
                    }

                    // Convert base64 to bitmap
                    val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    
                    if (bitmap == null) {
                        result.error("INVALID_IMAGE", "Could not decode base64 image data", null)
                        return
                    }

                    // Use the startPrinting method from AppService for bitmap printing
                    AppService.me().startPrinting(bitmap, true, object : IPaymentCallback.Stub() {
                        override fun onSuccess(success: Boolean, message: String?) {
                            // Ensure callback runs on main thread
                            context.mainExecutor.execute {
                                if (success) {
                                    result.success(mapOf(
                                        "success" to true,
                                        "message" to "Image printed successfully"
                                    ))
                                } else {
                                    result.error("PRINT_FAILED", message ?: "Print operation failed", null)
                                }
                            }
                        }
                        
                        override fun onResponse(response: Bundle?) {
                            // Handle any additional response data if needed
                            Log.d("TiggPrinter", "Print response received: $response")
                        }
                    })
                } catch (e: RemoteException) {
                    result.error("REMOTE_EXCEPTION", "Printer service communication error: ${e.message}", null)
                } catch (e: IllegalArgumentException) {
                    result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
                } catch (e: OutOfMemoryError) {
                    result.error("MEMORY_ERROR", "Not enough memory to process image: ${e.message}", null)
                } catch (e: Exception) {
                    result.error("PRINT_EXCEPTION", "Unexpected error during printing: ${e.message}", null)
                }
            }
            // "printText" -> {
            //     val text = call.argument<String>("text")
            //     val textSize = call.argument<Int>("textSize") ?: 24
            //     val paperWidth = call.argument<Int>("paperWidth") ?: 384 // Default to 58mm paper

            //     if (text.isNullOrEmpty()) {
            //         result.error("INVALID_INPUT", "Text is required", null)
            //         return
            //     }

            //     if (textSize <= 0 || textSize > 100) {
            //         result.error("INVALID_INPUT", "Text size must be between 1 and 100", null)
            //         return
            //     }

            //     if (paperWidth <= 0 || paperWidth > 1000) {
            //         result.error("INVALID_INPUT", "Paper width must be between 1 and 1000 pixels", null)
            //         return
            //     }

            //     try {
            //         // Check if service is available and connected
            //         if (AppService.me() == null) {
            //             result.error("SERVICE_UNAVAILABLE", "Printer service is not initialized", null)
            //             return
            //         }
                    
            //         if (!AppService.me().isServiceConnected()) {
            //             result.error("SERVICE_NOT_CONNECTED", "Printer service is not connected. Please bind service first.", null)
            //             return
            //         }

            //         // Use bitmap method directly with header flag set to false
            //         Log.d("TiggPrinter", "Creating text bitmap for printing...")
            //         val bitmap = createTextBitmap(text, textSize, paperWidth)
                    
            //         if (bitmap == null) {
            //             result.error("TEXT_RENDER_ERROR", "Could not create text bitmap", null)
            //             return
            //         }

            //         // Use bitmap printing with boolean false (no header)
            //         Log.d("TiggPrinter", "Printing text as bitmap with no header...")
                    
            //         AppService.me().startPrinting(bitmap, false, object : IPaymentCallback.Stub() {
            //             override fun onSuccess(success: Boolean, message: String?) {
            //                 Log.d("TiggPrinter", "Print callback - onSuccess: success=$success, message=$message")
            //                 context.mainExecutor.execute {
            //                     if (success) {
            //                         result.success(mapOf(
            //                             "success" to true,
            //                             "message" to "Invoice printed successfully"
            //                         ))
            //                     } else {
            //                         result.error("PRINT_FAILED", message ?: "Print operation failed", null)
            //                     }
            //                 }
            //             }
                        
            //             override fun onResponse(response: Bundle?) {
            //                 Log.d("TiggPrinter", "Print callback - onResponse: $response")
            //                 // onResponse is for additional data, onSuccess handles the completion
            //             }
            //         })

            //     } catch (e: RemoteException) {
            //         result.error("REMOTE_EXCEPTION", "Printer service communication error: ${e.message}", null)
            //     } catch (e: IllegalArgumentException) {
            //         result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
            //     } catch (e: Exception) {
            //         result.error("PRINT_EXCEPTION", "Unexpected error during printing: ${e.message}", null)
            //     }
            // }
            "printText" -> {
    val text = call.argument<String>("text")
    val textSize = call.argument<Int>("textSize") ?: 24
    val paperWidth = call.argument<Int>("paperWidth") ?: 384 // Default to 58mm paper

    if (text.isNullOrEmpty()) {
        result.error("INVALID_INPUT", "Text is required", null)
        return
    }

    if (textSize <= 0 || textSize > 100) {
        result.error("INVALID_INPUT", "Text size must be between 1 and 100", null)
        return
    }

    if (paperWidth <= 0 || paperWidth > 1000) {
        result.error("INVALID_INPUT", "Paper width must be between 1 and 1000 pixels", null)
        return
    }

    try {
        if (AppService.me() == null) {
            result.error("SERVICE_UNAVAILABLE", "Printer service is not initialized", null)
            return
        }

        // Attempt re-bind if not connected
        if (!AppService.me().isServiceConnected()) {
            Log.w("TiggPrinter", "Service not connected. Attempting re-bind before printing...")
            AppService.me().bindService()
            Thread.sleep(1000) // Wait for binding
        }

        if (!AppService.me().isServiceConnected()) {
            result.error("SERVICE_NOT_CONNECTED", "Printer service is still not connected after retry", null)
            return
        }

        val bitmap = createTextBitmap(text, textSize, paperWidth)
        if (bitmap == null) {
            result.error("TEXT_RENDER_ERROR", "Could not create text bitmap", null)
            return
        }

        Log.d("TiggPrinter", "Starting print job...")

        AppService.me().startPrinting(bitmap, false, object : IPaymentCallback.Stub() {
            override fun onSuccess(success: Boolean, message: String?) {
                Log.d("TiggPrinter", "Print callback - onSuccess: success=$success, message=$message")
                context.mainExecutor.execute {
                    if (success) {
                        val response = mapOf<String, Any?>(
                            "success" to true,
                            "message" to (message ?: "Printed successfully")
                        )
                        result.success(response)
                    } else {
                        result.error("PRINT_FAILED", message ?: "Print operation failed", null)
                    }
                }
            }

            override fun onResponse(response: Bundle?) {
                
                Log.d("TiggPrinter", "Print callback - onResponse: $response")
            }
        })

    } catch (e: RemoteException) {
        result.error("REMOTE_EXCEPTION", "Printer service communication error: ${e.message}", null)
    } catch (e: IllegalArgumentException) {
        result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
    } catch (e: Exception) {
        result.error("PRINT_EXCEPTION", "Unexpected error during printing: ${e.message}", null)
    }
}
            "printRawBytes" -> {
                val bytes = call.argument<List<Int>>("bytes")
                val useDirectString = call.argument<Boolean>("useDirectString") ?: false
                val textSize = call.argument<Int>("textSize") ?: 0
                val paperWidth = call.argument<Int>("paperWidth") ?: 384 // Default to 58mm paper

                if (bytes.isNullOrEmpty()) {
                    result.error("INVALID_INPUT", "Bytes array is required", null)
                    return
                }

                // Validate paper width
                if (paperWidth <= 0 || paperWidth > 1000) {
                    result.error("INVALID_INPUT", "Paper width must be between 1 and 1000 pixels", null)
                    return
                }

                // Validate byte values
                for (i in bytes.indices) {
                    val byteValue = bytes[i]
                    if (byteValue < 0 || byteValue > 255) {
                        result.error("INVALID_INPUT", "Invalid byte value at index $i: $byteValue. Bytes must be 0-255.", null)
                        return
                    }
                }

                try {
                    if (AppService.me() == null) {
                        result.error("SERVICE_UNAVAILABLE", "Printer service is not initialized", null)
                        return
                    }

                    // Attempt re-bind if not connected
                    if (!AppService.me().isServiceConnected()) {
                        Log.w("TiggPrinter", "Service not connected. Attempting re-bind before printing...")
                        AppService.me().bindService()
                        Thread.sleep(1000) // Wait for binding
                    }

                    if (!AppService.me().isServiceConnected()) {
                        result.error("SERVICE_NOT_CONNECTED", "Printer service is still not connected after retry", null)
                        return
                    }

                    // Convert List<Int> to ByteArray
                    val byteArray = bytes.map { it.toByte() }.toByteArray()
                    val escPosString = String(byteArray, Charsets.ISO_8859_1)
                    
                    Log.d("TiggPrinter", "Printing raw ESC/POS bytes, length: ${byteArray.size}, useDirectString: $useDirectString")

                    if (useDirectString) {
                        // Method 1: Direct string approach (may show default header)
                        AppService.me().startPrinting(escPosString, textSize, object : IPaymentCallback.Stub() {
                            override fun onSuccess(success: Boolean, message: String?) {
                                Log.d("TiggPrinter", "Raw bytes (string) print callback - onSuccess: success=$success, message=$message")
                                context.mainExecutor.execute {
                                    if (success) {
                                        result.success(mapOf(
                                            "success" to true,
                                            "message" to "Raw bytes printed successfully (string method)"
                                        ))
                                    } else {
                                        result.error("PRINT_FAILED", message ?: "Raw bytes print operation failed", null)
                                    }
                                }
                            }

                            override fun onResponse(response: Bundle?) {
                                Log.d("TiggPrinter", "Raw bytes (string) print callback - onResponse: $response")
                            }
                        })
                    } else {
                        // Method 2: Create minimal bitmap to avoid default header
                        val rawBitmap = createMinimalEscPosBitmap(escPosString,paperWidth)
                        if (rawBitmap == null) {
                            result.error("RAW_DATA_ERROR", "Could not process ESC/POS data", null)
                            return
                        }
                        
                        AppService.me().startPrinting(rawBitmap, false, object : IPaymentCallback.Stub() {
                            override fun onSuccess(success: Boolean, message: String?) {
                                Log.d("TiggPrinter", "Raw bytes (bitmap) print callback - onSuccess: success=$success, message=$message")
                                context.mainExecutor.execute {
                                    if (success) {
                                        result.success(mapOf(
                                            "success" to true,
                                            "message" to "Raw bytes printed successfully (bitmap method)"
                                        ))
                                    } else {
                                        result.error("PRINT_FAILED", message ?: "Raw bytes print operation failed", null)
                                    }
                                }
                            }

                            override fun onResponse(response: Bundle?) {
                                Log.d("TiggPrinter", "Raw bytes (bitmap) print callback - onResponse: $response")
                            }
                        })
                    }

                } catch (e: RemoteException) {
                    result.error("REMOTE_EXCEPTION", "Printer service communication error: ${e.message}", null)
                } catch (e: IllegalArgumentException) {
                    result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
                } catch (e: Exception) {
                    result.error("PRINT_EXCEPTION", "Unexpected error during raw bytes printing: ${e.message}", null)
                }
            }

            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun createTextBitmap(text: String, textSize: Int, paperWidth: Int): Bitmap? {
        return try {
            // 58mm paper: ~384 pixels, 80mm paper: ~576 pixels
            val padding = 8 // Better padding for balanced left/right spacing
            val usableWidth = paperWidth - (padding * 2) 
            
            // Create paint object for text
            val paint = Paint().apply {
                color = Color.BLACK
                this.textSize = textSize.toFloat()
                typeface = Typeface.DEFAULT
                isAntiAlias = true
                textAlign = Paint.Align.LEFT // Use left align but position correctly
            }

            // Split text into lines and handle word wrapping
            val lines = mutableListOf<String>()
            text.split("\n").forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    lines.add("")
                } else {
                    // Word wrap long lines
                    val words = paragraph.split(" ")
                    var currentLine = ""
                    
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val testWidth = paint.measureText(testLine)
                        
                        if (testWidth <= usableWidth) {
                            currentLine = testLine
                        } else {
                            if (currentLine.isNotEmpty()) {
                                lines.add(currentLine)
                                currentLine = word
                            } else {
                                // Single word is too long, add it anyway
                                lines.add(word)
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                    }
                }
            }
            
            val lineHeight = paint.textSize + 8 // Increase line spacing
            val bottomFeedSpace = 150 // Add extra space at bottom for paper feed effect
            val totalHeight = (lines.size * lineHeight + padding * 2 + bottomFeedSpace).toInt()
            
            // Create bitmap with proper dimensions
            val bitmap = Bitmap.createBitmap(
                paperWidth,
                totalHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            
            // Fill background with white
            canvas.drawColor(Color.WHITE)
            
            // Draw each line of text with proper left padding
            lines.forEachIndexed { index, line ->
                val x = padding.toFloat() // Start from padding distance from left edge
                val y = padding + (index + 1) * lineHeight - (paint.descent() / 2) // Better vertical alignment
                canvas.drawText(line, x, y, paint)
            }
            
            Log.d("TiggPrinter", "Created text bitmap: ${bitmap.width}x${bitmap.height}, lines: ${lines.size}, padding: ${padding}px")
            bitmap
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error creating text bitmap: ${e.message}", e)
            null
        }
    }

    private fun createMinimalEscPosBitmap(escPosString: String, paperSize: Int): Bitmap? {
        return try {
            Log.d("TiggPrinter", "Creating ESC/POS bitmap, paperSize: $paperSize, dataLength: ${escPosString.length}")
            
            // Log the raw bytes for debugging
            val bytes = escPosString.toByteArray(Charsets.ISO_8859_1)
            val hexString = bytes.take(50).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            Log.d("TiggPrinter", "First 50 bytes: $hexString")
            
            // Parse ESC/POS commands and extract formatted content
            val formattedLines = parseEscPosWithFormatting(escPosString, paperSize)
            Log.d("TiggPrinter", "Parsed ${formattedLines.size} formatted lines")
            
            for ((index, line) in formattedLines.withIndex()) {
                Log.d("TiggPrinter", "Line $index: '${line.text}' (align=${line.alignment}, bold=${line.isBold}, double=${line.isDoubleSize})")
            }
            
            if (formattedLines.isEmpty()) {
                Log.w("TiggPrinter", "No content extracted from ESC/POS data")
                // Create a simple test bitmap to ensure something prints
                val bitmap = Bitmap.createBitmap(paperSize, 100, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 8.0f
                    typeface = Typeface.MONOSPACE
                }
                canvas.drawText("ESC/POS Data Received", 8f, 30f, paint)
                canvas.drawText("${escPosString.length} bytes", 8f, 60f, paint)
                
                Log.d("TiggPrinter", "Created fallback bitmap: ${paperSize}x100")
                return bitmap
            }
            
            // Calculate total height needed
            val baseTextSize = 6.0f // Much smaller for better fitting
            val lineSpacing = 1.1f // Even tighter spacing
            var totalHeight = 10f // Top padding
            
            for (line in formattedLines) {
                val textSize = when {
                    line.isDoubleSize -> baseTextSize * 1.5f
                    else -> baseTextSize
                }
                totalHeight += textSize * lineSpacing
            }
            totalHeight += 30f // Bottom padding
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(paperSize, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            // Draw each line with proper formatting
            var y = baseTextSize * lineSpacing + 10f
            for (line in formattedLines) {
                if (line.text.isNotBlank()) {
                    val paint = Paint().apply {
                        color = Color.BLACK
                        textSize = when {
                            line.isDoubleSize -> baseTextSize * 1.5f
                            else -> baseTextSize
                        }
                        typeface = if (line.isBold) Typeface.DEFAULT_BOLD else Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    
                    // Wrap text to fit paper width with better margins
                    val wrappedLines = wrapText(line.text, paint, paperSize - 12f) // 6px padding each side
                    
                    for (wrappedLine in wrappedLines) {
                        if (wrappedLine.isNotBlank()) { // Extra check to prevent empty lines
                            val x = when (line.alignment) {
                            1 -> { // Center
                                val textWidth = paint.measureText(wrappedLine)
                                (paperSize - textWidth) / 2f
                            }
                            2 -> { // Right
                                val textWidth = paint.measureText(wrappedLine)
                                paperSize - textWidth - 6f
                            }
                            else -> 6f // Left
                            }
                            
                            canvas.drawText(wrappedLine, x, y, paint)
                            y += paint.textSize * lineSpacing
                        }
                    }
                } else {
                    // Empty line - add spacing
                    y += baseTextSize * 0.5f
                }
            }
            
            Log.d("TiggPrinter", "Created ESC/POS bitmap: ${paperSize}x${totalHeight.toInt()}, ${formattedLines.size} lines")
            bitmap
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error creating ESC/POS bitmap: ${e.message}", e)
            
            // Create emergency fallback that definitely works
            try {
                Log.d("TiggPrinter", "Creating emergency fallback bitmap")
                val bitmap = Bitmap.createBitmap(paperSize, 200, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 8.0f // Much smaller fallback font
                    typeface = Typeface.MONOSPACE
                    isAntiAlias = true
                }
                
                // Extract just printable text as simple fallback, filter out dots
                val simpleText = escPosString.toByteArray(Charsets.ISO_8859_1)
                    .filter { (it.toInt() and 0xFF) in 32..126 }
                    .map { it.toInt().toChar() }
                    .joinToString("")
                    .replace(Regex("\\s+"), " ")
                    .replace(".", "") // Remove dots that might be causing issues
                    .trim()
                
                Log.d("TiggPrinter", "Fallback extracted text: '$simpleText'")
                
                if (simpleText.isNotEmpty()) {
                    // Split into lines that fit
                    val maxCharsPerLine = (paperSize / (paint.textSize * 0.6)).toInt()
                    val words = simpleText.split(" ")
                    val lines = mutableListOf<String>()
                    var currentLine = ""
                    
                    for (word in words) {
                        if ((currentLine + " " + word).length <= maxCharsPerLine) {
                            currentLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        } else {
                            if (currentLine.isNotEmpty()) lines.add(currentLine)
                            currentLine = word
                        }
                    }
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    
                    var y = 40f
                    for (line in lines.take(6)) { // Max 6 lines
                        canvas.drawText(line, 8f, y, paint)
                        y += 30f
                    }
                } else {
                    canvas.drawText("No readable text found", 8f, 40f, paint)
                    canvas.drawText("Data length: ${escPosString.length}", 8f, 80f, paint)
                }
                
                Log.d("TiggPrinter", "Created emergency fallback bitmap successfully")
                bitmap
            } catch (fallbackError: Exception) {
                Log.e("TiggPrinter", "Failed to create fallback bitmap: ${fallbackError.message}")
                null
            }
        }
    }
    
    private data class FormattedLine(
        val text: String,
        val alignment: Int = 0, // 0=left, 1=center, 2=right
        val isBold: Boolean = false,
        val isDoubleSize: Boolean = false
    )
    
    private fun parseEscPosWithFormatting(escPosString: String, paperWidth: Int): List<FormattedLine> {
        val lines = mutableListOf<FormattedLine>()
        val bytes = escPosString.toByteArray(Charsets.ISO_8859_1)
        
        var i = 0
        var currentAlignment = 0
        var currentBold = false
        var currentDoubleSize = false
        val textBuffer = StringBuilder()
        
        while (i < bytes.size) {
            val byte = bytes[i].toInt() and 0xFF
            
            when (byte) {
                0x1B -> { // ESC command
                    if (i + 1 < bytes.size) {
                        when (bytes[i + 1].toInt() and 0xFF) {
                            0x40 -> { // ESC @ - Initialize printer
                                i += 2
                                continue
                            }
                            0x61 -> { // ESC a - Alignment
                                if (i + 2 < bytes.size) {
                                    // Flush current text if any
                                    if (textBuffer.isNotEmpty()) {
                                        lines.add(FormattedLine(textBuffer.toString().trim(), currentAlignment, currentBold, currentDoubleSize))
                                        textBuffer.clear()
                                    }
                                    currentAlignment = bytes[i + 2].toInt() and 0xFF
                                    i += 3
                                    continue
                                }
                            }
                            0x45 -> { // ESC E - Bold
                                if (i + 2 < bytes.size) {
                                    currentBold = (bytes[i + 2].toInt() and 0xFF) == 1
                                    i += 3
                                    continue
                                }
                            }
                        }
                    }
                    // Skip unrecognized ESC commands
                    i += if (i + 1 < bytes.size) 2 else 1
                }
                0x1D -> { // GS command
                    if (i + 1 < bytes.size) {
                        when (bytes[i + 1].toInt() and 0xFF) {
                            0x21 -> { // GS ! - Character size
                                if (i + 2 < bytes.size) {
                                    val sizeCmd = bytes[i + 2].toInt() and 0xFF
                                    currentDoubleSize = (sizeCmd and 0x11) != 0
                                    i += 3
                                    continue
                                }
                            }
                            0x56 -> { // GS V - Cut paper
                                i += if (i + 3 < bytes.size) 4 else 2
                                continue
                            }
                        }
                    }
                    // Skip unrecognized GS commands
                    i += if (i + 1 < bytes.size) 2 else 1
                }
                0x0A -> { // Line feed
                    val lineText = textBuffer.toString().trim()
                    // Only add lines with meaningful content (no just dots or spaces)
                    if (lineText.isNotEmpty() && lineText != "." && lineText.length > 1) {
                        lines.add(FormattedLine(lineText, currentAlignment, currentBold, currentDoubleSize))
                        Log.d("TiggPrinter", "Added line: '$lineText'")
                    } else if (lineText.isNotEmpty()) {
                        Log.d("TiggPrinter", "Skipped line: '$lineText' (too short or just dots)")
                    }
                    textBuffer.clear()
                    i++
                }
                0x0D -> { // Carriage return - skip
                    i++
                }
                in 0x20..0x7E -> { // Printable ASCII
                    textBuffer.append(byte.toChar())
                    i++
                }
                else -> {
                    // Skip non-printable characters
                    i++
                }
            }
        }
        
        // Add any remaining text
        if (textBuffer.isNotEmpty()) {
            val lineText = textBuffer.toString().trim()
            if (lineText.isNotEmpty() && lineText != "." && lineText.length > 1) {
                lines.add(FormattedLine(lineText, currentAlignment, currentBold, currentDoubleSize))
                Log.d("TiggPrinter", "Added final line: '$lineText'")
            }
        }
        
        return lines.filter { it.text.isNotEmpty() } // Only keep lines with actual content
    }
    
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList() // Return empty list instead of list with empty string
        
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val textWidth = paint.measureText(testLine)
            
            if (textWidth <= maxWidth || currentLine.isEmpty()) {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return if (lines.isEmpty()) emptyList() else lines // Return empty list if no lines
    }
    
    private fun extractSimpleTextFromEscPos(escPosString: String): String {
        val result = StringBuilder()
        val bytes = escPosString.toByteArray(Charsets.ISO_8859_1)
        
        var i = 0
        while (i < bytes.size) {
            val byte = bytes[i].toInt() and 0xFF
            
            when (byte) {
                0x1B, 0x1D -> { // ESC or GS commands - skip them
                    // Skip command and its parameters
                    if (i + 1 < bytes.size) {
                        val cmd = bytes[i + 1].toInt() and 0xFF
                        when (cmd) {
                            0x40, 0x61, 0x45, 0x21 -> { // Commands with 1 parameter
                                i += if (i + 2 < bytes.size) 3 else 2
                            }
                            else -> i += 2 // Skip command
                        }
                    } else {
                        i++
                    }
                }
                0x0A -> { // Line feed
                    result.append('\n')
                    i++
                }
                0x0D -> { // Carriage return
                    i++ // Skip
                }
                in 0x20..0x7E -> { // Printable ASCII
                    result.append(byte.toChar())
                    i++
                }
                else -> i++ // Skip non-printable
            }
        }
        
        return result.toString()
    }
}

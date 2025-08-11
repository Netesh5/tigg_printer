

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
                        Log.d("TiggPrinter", "*** USING STRING METHOD (may show default header) ***")
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
                        Log.d("TiggPrinter", "*** USING BITMAP METHOD (clean, no header) ***")
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
            val formattedLines = parseEscPosWithFormatting(escPosString, paperSize).toMutableList()
            Log.d("TiggPrinter", "Parsed ${formattedLines.size} formatted lines")
            
            // Process lines to detect and merge table structures based on ESC/POS row/column layout
            val processedLines = mutableListOf<FormattedLine>()
            var i = 0
            while (i < formattedLines.size) {
                val currentLine = formattedLines[i]
                val currentText = currentLine.text.trim()
                
                // Detect table header pattern: "Particular" followed by "Amount" on separate lines
                // This represents: ticket.row([PosColumn(width: 6, "Particular"), PosColumn(width: 6, "Amount", right)])
                if (currentText == "Particular8" || currentText == "Particular") {
                    if (i + 1 < formattedLines.size) {
                        val nextLine = formattedLines[i + 1]
                        if (nextLine.text.trim() == "Amount") {
                            // Simple merge with adequate spacing (like the original working version)
                            val tableHeader = "Particular              Amount"
                            processedLines.add(FormattedLine(tableHeader, 0, currentLine.isBold, currentLine.isDoubleSize))
                            Log.d("TiggPrinter", "Merged table header: '$tableHeader'")
                            i += 2
                            continue
                        }
                    }
                }
                
                // Enhanced product detection for ESC/POS row structure
                if (!currentText.contains("---") && 
                    currentText.isNotEmpty() &&
                    currentText != "Amount" &&
                    currentText != "Particular" &&
                    currentText != "Particular8" &&
                    !currentText.contains("Total") &&
                    !currentText.contains("VAT") &&
                    !currentText.contains("Discount") &&
                    !currentText.contains("Change") &&
                    !currentText.contains("Service") &&
                    !currentText.contains("Taxable") &&
                    !currentText.contains("Non-Taxable") &&
                    !currentText.matches(Regex(".*Thank you.*")) &&
                    !currentText.matches(Regex(".*Good Bye.*")) &&
                    !currentText.matches(Regex(".*visit.*")) &&
                    !currentText.matches(Regex("\\d{2}-\\d{2}-\\d{4}.*")) &&
                    !currentText.matches(Regex(".*Invoice.*")) &&
                    !currentText.matches(Regex(".*Bill No.*")) &&
                    !currentText.matches(Regex(".*Customer.*")) &&
                    !currentText.matches(Regex(".*Mode.*")) &&
                    !currentText.matches(Regex(".*PAN.*")) &&
                    !currentText.matches(Regex(".*Phone.*"))) {
                    
                    // Look ahead for amount in next few lines (representing separate PosColumns)
                    var foundAmount = false
                    var amountLine = ""
                    var amountIndex = -1
                    
                    for (j in i + 1..minOf(i + 4, formattedLines.size - 1)) {
                        val checkLine = formattedLines[j].text.trim()
                        
                        // Detect numeric amounts (right-aligned PosColumn)
                        if (checkLine.matches(Regex("\\d+\\.\\d{2}")) ||
                            checkLine.matches(Regex("\\(\\d+\\.\\d{2}\\)")) ||
                            checkLine.matches(Regex("\\d+\\.\\d+"))) {
                            amountLine = checkLine
                            amountIndex = j
                            foundAmount = true
                            break
                        }
                    }
                    
                    if (foundAmount) {
                        // Collect all product lines between current and amount
                        val productLines = mutableListOf<String>()
                        for (k in i until amountIndex) {
                            val lineText = formattedLines[k].text.trim()
                            if (lineText.isNotEmpty() && !lineText.contains("---")) {
                                productLines.add(lineText)
                            }
                        }
                        
                        if (productLines.isNotEmpty()) {
                            // Add all product lines except the last one normally
                            for (k in 0 until productLines.size - 1) {
                                processedLines.add(FormattedLine(productLines[k], 0, currentLine.isBold, currentLine.isDoubleSize))
                                Log.d("TiggPrinter", "Added product detail: '${productLines[k]}'")
                            }
                            
                            // For the last product line, create a proper row with right-aligned amount
                            val lastProductLine = productLines.last()
                            // Use a special format that we can detect later for right alignment
                            val mergedLine = lastProductLine + "|||" + amountLine  // ||| as separator
                            
                            // Use alignment=3 to indicate this needs special right-alignment processing
                            processedLines.add(FormattedLine(mergedLine, 3, currentLine.isBold, currentLine.isDoubleSize))
                            Log.d("TiggPrinter", "Added product row with right-aligned amount: '$lastProductLine' -> '$amountLine'")
                            i = amountIndex + 1
                            continue
                        }
                    }
                }
                
                // Add line normally if no special processing (preserves original alignment!)
                processedLines.add(currentLine)
                i++
            }
            
            // Use processed lines for rendering
            val finalLines = processedLines
            
            for ((index, line) in finalLines.withIndex()) {
                Log.d("TiggPrinter", "Final Line $index: '${line.text}' (align=${line.alignment}, bold=${line.isBold}, double=${line.isDoubleSize})")
            }
            
            if (finalLines.isEmpty()) {
                Log.w("TiggPrinter", "No content extracted from ESC/POS data - using simple extraction")
                // Create a simple fallback line to ensure something prints
                finalLines.add(FormattedLine("ESC/POS Data (${escPosString.length} bytes)", 1, false, false))
                Log.d("TiggPrinter", "Using minimal fallback content")
            }
            
            // NEVER use the old fallback - always use our small font rendering
            Log.d("TiggPrinter", "Using main rendering path with ${finalLines.size} lines")
            
            // Calculate total height needed
            val baseTextSize = 20.0f // Back to 20px as requested
            Log.d("TiggPrinter", "*** USING READABLE FONT SIZE: ${baseTextSize}px ***")
            val lineSpacing = 1.2f // Slightly more spacing for larger font
            var totalHeight = 10f // Reduced top padding
            
            for (line in finalLines) {
                val textSize = when {
                    line.isDoubleSize -> baseTextSize * 1.5f
                    else -> baseTextSize
                }
                // Normal height calculation without extra wrapping space
                totalHeight += textSize * lineSpacing
            }
            totalHeight += 100f // Increased bottom padding for easier paper handling
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(paperSize, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            // Draw each line with exact formatting - no modifications
            var y = baseTextSize * lineSpacing + 10f // Reduced initial Y position
            for (line in finalLines) {
                if (line.text.isNotEmpty()) {
                    val paint = Paint().apply {
                        color = Color.BLACK
                        textSize = when {
                            line.isDoubleSize -> baseTextSize * 1.8f
                            else -> baseTextSize
                        }
                        typeface = if (line.isBold) {
                            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        } else {
                            Typeface.MONOSPACE
                        }
                        isAntiAlias = true
                        isFakeBoldText = line.isBold
                    }
                    
                    Log.d("TiggPrinter", "Rendering: '${line.text}' (align=${line.alignment}, bold=${line.isBold}, double=${line.isDoubleSize})")
                    
                    // Special handling for product rows with right-aligned amounts (alignment=3)
                    if (line.alignment == 3 && line.text.contains("|||")) {
                        val parts = line.text.split("|||")
                        if (parts.size == 2) {
                            val productText = parts[0].trim()
                            val amountText = parts[1].trim()
                            
                            // Draw product text on left
                            canvas.drawText(productText, 4f, y, paint)
                            
                            // Draw amount on right
                            val amountWidth = paint.measureText(amountText)
                            val amountX = paperSize - amountWidth - 4f
                            canvas.drawText(amountText, maxOf(4f, amountX), y, paint)
                            
                            y += paint.textSize * lineSpacing
                            Log.d("TiggPrinter", "Drew product row: '$productText' (left) and '$amountText' (right)")
                            continue
                        }
                    }
                    
                    // Special handling for HR lines (dashes) to prevent wrapping
                    if (line.text.trim().matches(Regex("-{20,}"))) {
                        val availableWidth = paperSize - 8f // Account for margins
                        val dashChar = "-"
                        val singleDashWidth = paint.measureText(dashChar)
                        val maxDashes = (availableWidth / singleDashWidth).toInt()
                        val adjustedHrLine = dashChar.repeat(maxDashes)
                        
                        val x = when (line.alignment) {
                            1 -> { // Center alignment
                                val hrWidth = paint.measureText(adjustedHrLine)
                                val centerX = (paperSize - hrWidth) / 2f
                                maxOf(4f, centerX)
                            }
                            2 -> { // Right alignment
                                val hrWidth = paint.measureText(adjustedHrLine)
                                val rightX = paperSize - hrWidth - 4f
                                maxOf(4f, rightX)
                            }
                            else -> { // Left alignment
                                4f
                            }
                        }
                        
                        canvas.drawText(adjustedHrLine, x, y, paint)
                        y += paint.textSize * lineSpacing
                        Log.d("TiggPrinter", "Drew HR line: '$adjustedHrLine' at x=$x, y=$y (${maxDashes} dashes)")
                        continue
                    }
                    
                    // Handle text wrapping for long text that doesn't fit
                    val textWidth = paint.measureText(line.text)
                    val maxTextWidth = paperSize - 8f // 4px margin on each side for balanced padding
                    
                    if (textWidth <= maxTextWidth) {
                        // Text fits on one line
                        val x = when (line.alignment) {
                            1 -> { // Center alignment
                                val centerX = (paperSize - textWidth) / 2f
                                Log.d("TiggPrinter", "CENTER: '${line.text}' -> x=$centerX (paperSize=$paperSize, textWidth=$textWidth)")
                                maxOf(4f, centerX) // Balanced margin
                            }
                            2 -> { // Right alignment
                                val rightX = paperSize - textWidth - 4f // Balanced margin
                                Log.d("TiggPrinter", "RIGHT: '${line.text}' -> x=$rightX")
                                maxOf(4f, rightX)
                            }
                            else -> { // Left alignment
                                Log.d("TiggPrinter", "LEFT: '${line.text}' -> x=4")
                                4f // Balanced left margin
                            }
                        }
                        
                        canvas.drawText(line.text, x, y, paint)
                        y += paint.textSize * lineSpacing
                        Log.d("TiggPrinter", "Drew: '${line.text}' at x=$x, y=$y, align=${line.alignment}")
                    } else {
                        // Text needs wrapping
                        Log.d("TiggPrinter", "Text too long, wrapping: '${line.text}' (width=$textWidth, max=$maxTextWidth)")
                        val wrappedLines = wrapText(line.text, paint, maxTextWidth)
                        
                        for (wrappedLine in wrappedLines) {
                            val wrappedTextWidth = paint.measureText(wrappedLine)
                            val x = when (line.alignment) {
                                1 -> { // Center alignment
                                    val centerX = (paperSize - wrappedTextWidth) / 2f
                                    maxOf(4f, centerX) // Balanced margin
                                }
                                2 -> { // Right alignment
                                    val rightX = paperSize - wrappedTextWidth - 4f // Balanced margin
                                    maxOf(4f, rightX)
                                }
                                else -> { // Left alignment
                                    4f // Balanced margin
                                }
                            }
                            
                            canvas.drawText(wrappedLine, x, y, paint)
                            y += paint.textSize * lineSpacing
                            Log.d("TiggPrinter", "Drew wrapped: '$wrappedLine' at x=$x, y=$y, align=${line.alignment}")
                        }
                    }
                } else {
                    // Only add spacing for explicit empty lines in the data
                    y += baseTextSize * 0.6f
                    Log.d("TiggPrinter", "Empty line spacing")
                }
            }
            
            Log.d("TiggPrinter", "Created ESC/POS bitmap: ${paperSize}x${totalHeight.toInt()}, ${finalLines.size} lines")
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
                    textSize = 20.0f // Match main font size
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
                    // Use proper text wrapping based on actual text width
                    val maxLineWidth = paperSize - 8f // 4px margin each side (balanced)
                    val wrappedLines = wrapText(simpleText, paint, maxLineWidth)
                    
                    var y = 40f
                    for (line in wrappedLines.take(8)) { // Max 8 lines for fallback
                        canvas.drawText(line, 4f, y, paint) // Balanced margin
                        y += paint.textSize * 1.2f
                    }
                } else {
                    canvas.drawText("No readable text found", 4f, 40f, paint)
                    canvas.drawText("Data length: ${escPosString.length}", 4f, 80f, paint)
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
        var currentAlignment = 0  // 0=left, 1=center, 2=right
        var currentBold = false
        var currentDoubleHeight = false
        var currentDoubleWidth = false
        var currentUnderline = false
        val textBuffer = StringBuilder()
        
        Log.d("TiggPrinter", "Starting comprehensive ESC/POS parsing, total bytes: ${bytes.size}")
        
        fun flushTextBuffer() {
            if (textBuffer.isNotEmpty()) {
                val text = textBuffer.toString()
                    .replace("€", "") // Remove Euro symbol specifically
                    .replace("£", "") // Remove Pound symbol
                    .replace("¥", "") // Remove Yen symbol
                    .replace("¢", "") // Remove Cent symbol
                    .replace("₹", "") // Remove Rupee symbol
                    .replace("\u0080", "") // Remove Euro symbol (Windows-1252)
                    .replace("\u0000", "") // Remove null characters
                    .replace("\ufffd", "") // Remove replacement characters (boxes)
                
                if (text.isNotEmpty()) {
                    val isDoubleSize = currentDoubleHeight || currentDoubleWidth
                    
                    // Don't override alignment for any text - respect ESC/POS commands exactly
                    lines.add(FormattedLine(text, currentAlignment, currentBold, isDoubleSize))
                    Log.d("TiggPrinter", "Flushed: '$text' (align=$currentAlignment, bold=$currentBold, double=$isDoubleSize)")
                }
                textBuffer.clear()
            }
        }
        
        while (i < bytes.size) {
            val byte = bytes[i].toInt() and 0xFF
            
            when (byte) {
                // ESC commands (0x1B)
                0x1B -> {
                    if (i + 1 < bytes.size) {
                        val cmd = bytes[i + 1].toInt() and 0xFF
                        when (cmd) {
                            0x40 -> { // ESC @ - Initialize printer
                                flushTextBuffer()
                                currentAlignment = 0
                                currentBold = false
                                currentDoubleHeight = false
                                currentDoubleWidth = false
                                currentUnderline = false
                                Log.d("TiggPrinter", "ESC @ - Initialize printer")
                                i += 2
                            }
                            0x61 -> { // ESC a n - Set alignment
                                if (i + 2 < bytes.size) {
                                    flushTextBuffer()
                                    val alignValue = bytes[i + 2].toInt() and 0xFF
                                    // ESC/POS alignment: 0=left, 1=center, 2=right
                                    // But sometimes we get ASCII values, so convert properly
                                    currentAlignment = when (alignValue) {
                                        48, 0 -> 0 // '0' (ASCII 48) or 0 = left
                                        49, 1 -> 1 // '1' (ASCII 49) or 1 = center  
                                        50, 2 -> 2 // '2' (ASCII 50) or 2 = right
                                        else -> alignValue // Keep original for debugging
                                    }
                                    Log.d("TiggPrinter", "ESC a - Set alignment: raw=$alignValue -> mapped=$currentAlignment")
                                    i += 3
                                } else i += 2
                            }
                            0x45 -> { // ESC E n - Bold on/off
                                if (i + 2 < bytes.size) {
                                    currentBold = (bytes[i + 2].toInt() and 0xFF) != 0
                                    Log.d("TiggPrinter", "ESC E - Bold: $currentBold")
                                    i += 3
                                } else {
                                    currentBold = true
                                    Log.d("TiggPrinter", "ESC E - Bold on")
                                    i += 2
                                }
                            }
                            0x46 -> { // ESC F - Bold off
                                currentBold = false
                                Log.d("TiggPrinter", "ESC F - Bold off")
                                i += 2
                            }
                            0x2D -> { // ESC - n - Underline on/off
                                if (i + 2 < bytes.size) {
                                    currentUnderline = (bytes[i + 2].toInt() and 0xFF) != 0
                                    Log.d("TiggPrinter", "ESC - - Underline: $currentUnderline")
                                    i += 3
                                } else i += 2
                            }
                            0x21 -> { // ESC ! n - Character font and style
                                if (i + 2 < bytes.size) {
                                    val style = bytes[i + 2].toInt() and 0xFF
                                    currentBold = (style and 0x08) != 0
                                    currentDoubleHeight = (style and 0x10) != 0
                                    currentDoubleWidth = (style and 0x20) != 0
                                    currentUnderline = (style and 0x80) != 0
                                    Log.d("TiggPrinter", "ESC ! - Style: bold=$currentBold, dh=$currentDoubleHeight, dw=$currentDoubleWidth")
                                    i += 3
                                } else i += 2
                            }
                            0x32 -> { // ESC 2 - Default line spacing
                                Log.d("TiggPrinter", "ESC 2 - Default line spacing")
                                i += 2
                            }
                            0x33 -> { // ESC 3 n - Set line spacing
                                if (i + 2 < bytes.size) {
                                    Log.d("TiggPrinter", "ESC 3 - Line spacing: ${bytes[i + 2].toInt() and 0xFF}")
                                    i += 3
                                } else i += 2
                            }
                            0x64 -> { // ESC d n - Print and feed n lines
                                if (i + 2 < bytes.size) {
                                    flushTextBuffer()
                                    // Don't add manual feed lines, let the original data handle spacing
                                    Log.d("TiggPrinter", "ESC d - Feed command (not adding manual lines)")
                                    i += 3
                                } else i += 2
                            }
                            else -> {
                                Log.d("TiggPrinter", "Unknown ESC command: 0x${cmd.toString(16)}")
                                i += 2
                            }
                        }
                    } else {
                        i++
                    }
                }
                
                // GS commands (0x1D)
                0x1D -> {
                    if (i + 1 < bytes.size) {
                        val cmd = bytes[i + 1].toInt() and 0xFF
                        when (cmd) {
                            0x21 -> { // GS ! n - Character size
                                if (i + 2 < bytes.size) {
                                    val size = bytes[i + 2].toInt() and 0xFF
                                    currentDoubleWidth = (size and 0x0F) != 0
                                    currentDoubleHeight = (size and 0xF0) != 0
                                    Log.d("TiggPrinter", "GS ! - Size: 0x${size.toString(16)}, dw=$currentDoubleWidth, dh=$currentDoubleHeight")
                                    i += 3
                                } else i += 2
                            }
                            0x42 -> { // GS B n - Bold on/off
                                if (i + 2 < bytes.size) {
                                    currentBold = (bytes[i + 2].toInt() and 0xFF) != 0
                                    Log.d("TiggPrinter", "GS B - Bold: $currentBold")
                                    i += 3
                                } else i += 2
                            }
                            0x56 -> { // GS V - Cut paper
                                flushTextBuffer()
                                Log.d("TiggPrinter", "GS V - Cut paper")
                                i += if (i + 2 < bytes.size) 3 else 2
                            }
                            0x4C -> { // GS L - Set left margin
                                if (i + 3 < bytes.size) {
                                    Log.d("TiggPrinter", "GS L - Set left margin")
                                    i += 4
                                } else i += 2
                            }
                            0x57 -> { // GS W - Set print area width
                                if (i + 3 < bytes.size) {
                                    Log.d("TiggPrinter", "GS W - Set print area width")
                                    i += 4
                                } else i += 2
                            }
                            else -> {
                                Log.d("TiggPrinter", "Unknown GS command: 0x${cmd.toString(16)}")
                                i += 2
                            }
                        }
                    } else {
                        i++
                    }
                }
                
                // FS commands (0x1C)
                0x1C -> {
                    if (i + 1 < bytes.size) {
                        val cmd = bytes[i + 1].toInt() and 0xFF
                        Log.d("TiggPrinter", "FS command: 0x${cmd.toString(16)}")
                        i += 2
                    } else {
                        i++
                    }
                }
                
                // Control characters
                0x0A -> { // LF - Line feed
                    flushTextBuffer()
                    // Don't add automatic empty lines - only if explicitly in the data
                    Log.d("TiggPrinter", "LF - Line feed (no automatic empty line)")
                    i++
                }
                0x0D -> { // CR - Carriage return
                    flushTextBuffer()
                    Log.d("TiggPrinter", "CR - Carriage return")
                    i++
                }
                0x09 -> { // HT - Horizontal tab
                    textBuffer.append("    ") // Add 4 spaces for tab
                    i++
                }
                0x0C -> { // FF - Form feed
                    flushTextBuffer()
                    // Add empty line for form feeds to preserve formatting
                    lines.add(FormattedLine("", currentAlignment, false, false))
                    Log.d("TiggPrinter", "FF - Form feed")
                    i++
                }
                
                // Printable ASCII characters
                in 0x20..0x7E -> {
                    textBuffer.append(byte.toChar())
                    i++
                }
                
                // Extended ASCII (for international characters) - restore but filter specific problematic chars
                in 0x80..0xFF -> {
                    val char = byte.toChar()
                    val byteValue = byte.toInt() and 0xFF
                    // Filter out currency symbols and problematic characters
                    if (char != '€' && char != '£' && char != '¥' && char != '¢' && char != '₹' && 
                        byteValue != 0x80 && // Euro in Windows-1252
                        byteValue != 0x9C && // Pound-like symbol
                        byteValue != 0xA2 && // Cent symbol
                        byteValue != 0xA3 && // Pound symbol
                        byteValue != 0xA4 && // Generic currency symbol
                        byteValue != 0xA5) { // Yen symbol
                        textBuffer.append(char)
                    }
                    i++
                }
                
                // Other control characters - skip
                else -> {
                    if (byte != 0) { // Don't log null bytes
                        Log.d("TiggPrinter", "Skipping control char: 0x${byte.toString(16)}")
                    }
                    i++
                }
            }
        }
        
        // Flush any remaining text
        flushTextBuffer()
        
        Log.d("TiggPrinter", "ESC/POS parsing complete. Generated ${lines.size} lines")
        return lines
    }
    
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        
        // For very long single words or lines without spaces, split by character
        if (!text.contains(" ") && paint.measureText(text) > maxWidth) {
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()
            
            for (char in text) {
                val testLine = currentLine.toString() + char
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine.append(char)
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder(char.toString())
                    } else {
                        // Even single character is too wide, force it
                        lines.add(char.toString())
                    }
                }
            }
            
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
            
            return lines
        }
        
        // Normal word-based wrapping
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
                
                // If even single word is too long, break it by character
                if (paint.measureText(word) > maxWidth) {
                    val brokenLines = wrapText(word, paint, maxWidth)
                    lines.addAll(brokenLines.dropLast(1)) // Add all but last
                    currentLine = StringBuilder(brokenLines.last()) // Keep last as current
                }
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return if (lines.isEmpty()) listOf("") else lines
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
    
    private fun isLikelyNewProduct(lineText: String, allLines: List<FormattedLine>, currentIndex: Int): Boolean {
        // A line is likely a new product if it's not empty, doesn't contain system info,
        // and is followed by product-related information
        if (lineText.isEmpty() || 
            lineText.contains("Total") ||
            lineText.contains("VAT") ||
            lineText.contains("Discount") ||
            lineText.matches(Regex("\\d+\\.\\d{2}")) ||
            lineText.contains("HS Code")) {
            return false
        }
        
        // Check if next line might be HS Code or quantity info
        if (currentIndex + 1 < allLines.size) {
            val nextLine = allLines[currentIndex + 1].text.trim()
            if (nextLine.contains("HS Code") || nextLine.matches(Regex("\\d+\\s+\\w+\\s+x.*"))) {
                return true
            }
        }
        
        return false
    }
}

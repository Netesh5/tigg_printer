

package com.example.tigg_printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                            if (success) {
                                result.success("Image printed successfully")
                            } else {
                                result.error("PRINT_FAILED", message ?: "Print operation failed", null)
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
            "printText" -> {
                val text = call.argument<String>("text")
                val textSize = call.argument<Int>("textSize") ?: 24

                if (text.isNullOrEmpty()) {
                    result.error("INVALID_INPUT", "Text is required", null)
                    return
                }

                if (textSize <= 0 || textSize > 100) {
                    result.error("INVALID_INPUT", "Text size must be between 1 and 100", null)
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

                    // Use the startPrinting method from AppService for text printing
                    AppService.me().startPrinting(text, textSize, object : IPaymentCallback.Stub() {
                        override fun onSuccess(success: Boolean, message: String?) {
                            if (success) {
                                result.success("Text printed successfully")
                            } else {
                                result.error("PRINT_FAILED", message ?: "Print operation failed", null)
                            }
                        }
                        
                        override fun onResponse(response: Bundle?) {
                            Log.d("TiggPrinter", "Print response received: $response")
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
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}

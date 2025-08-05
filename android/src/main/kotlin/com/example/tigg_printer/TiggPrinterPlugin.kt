

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
        
        try {
            Log.i("TiggPrinter", "Initializing AppService...")
            AppService.me().init(context)
            AppService.me().setPackageName(context.packageName)
            
            Log.i("TiggPrinter", "Attempting to bind service...")
            val bindResult = AppService.me().bindService()
            Log.i("TiggPrinter", "Service bind result: $bindResult")
            
            // Give some time for the service to connect
            Thread {
                Thread.sleep(1000)
                val isConnected = AppService.me()?.isServiceConnected() ?: false
                Log.i("TiggPrinter", "Service connection status after 1s: $isConnected")
            }.start()
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to initialize AppService: ${e.message}", e)
        }
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
                try {
                    Log.i("TiggPrinter", "Manual bind service requested")
                    
                    // First check if AppService is initialized
                    if (AppService.me() == null) {
                        Log.e("TiggPrinter", "AppService is not initialized")
                        result.error("SERVICE_NOT_INITIALIZED", "AppService is not initialized", null)
                        return
                    }
                    
                    // Check current connection status
                    val currentStatus = AppService.me().isServiceConnected()
                    Log.i("TiggPrinter", "Current service connection status: $currentStatus")
                    
                    if (currentStatus) {
                        result.success("Service is already connected")
                        return
                    }
                    
                    // Attempt to bind
                    val bindResult = AppService.me().bindService()
                    Log.i("TiggPrinter", "Bind service result: $bindResult")
                    
                    // Wait a moment and check connection status
                    Thread {
                        try {
                            Thread.sleep(2000) // Wait 2 seconds for connection
                            val finalStatus = AppService.me()?.isServiceConnected() ?: false
                            Log.i("TiggPrinter", "Service connection status after bind: $finalStatus")
                            
                            // Return result on main thread
                            context.mainExecutor.execute {
                                if (finalStatus) {
                                    result.success("Service bound and connected successfully")
                                } else {
                                    result.error("BIND_FAILED", "Service bind initiated but connection failed. Check if TiggPrinter device is available.", null)
                                }
                            }
                        } catch (e: Exception) {
                            context.mainExecutor.execute {
                                result.error("BIND_ERROR", "Error during service binding: ${e.message}", null)
                            }
                        }
                    }.start()
                    
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "Failed to bind service", e)
                    result.error("BIND_FAILED", "Failed to bind service: ${e.message}", null)
                }
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
            "getServiceDiagnostics" -> {
                try {
                    val diagnostics = mutableMapOf<String, Any>()
                    
                    // Check if AppService is initialized
                    val appService = AppService.me()
                    diagnostics["appServiceInitialized"] = (appService != null)
                    diagnostics["packageName"] = context.packageName
                    diagnostics["contextValid"] = this::context.isInitialized
                    
                    if (appService != null) {
                        try {
                            diagnostics["serviceConnected"] = appService.isServiceConnected()
                            
                            // Try to get more service details
                            try {
                                // Attempt to rebind and get detailed status
                                Log.i("TiggPrinter", "Diagnostics: Attempting service rebind...")
                                appService.bindService()
                                diagnostics["rebindAttemptResult"] = "Rebind attempt completed"
                                
                                // Wait a moment and check again
                                Thread.sleep(1000)
                                val connectionAfterRebind = appService.isServiceConnected()
                                diagnostics["connectionAfterRebind"] = connectionAfterRebind
                                
                                Log.i("TiggPrinter", "Diagnostics: Rebind completed, Connected after rebind=$connectionAfterRebind")
                                
                            } catch (rebindException: Exception) {
                                diagnostics["rebindError"] = rebindException.message ?: "Unknown rebind error"
                                Log.e("TiggPrinter", "Diagnostics: Rebind failed", rebindException)
                            }
                            
                        } catch (serviceException: Exception) {
                            diagnostics["serviceError"] = serviceException.message ?: "Unknown service error"
                            diagnostics["serviceConnected"] = false
                            Log.e("TiggPrinter", "Diagnostics: Service check failed", serviceException)
                        }
                    } else {
                        diagnostics["serviceConnected"] = false
                        diagnostics["error"] = "AppService not initialized"
                        
                        // Try to initialize again
                        try {
                            Log.i("TiggPrinter", "Diagnostics: Attempting to reinitialize AppService...")
                            AppService.me().init(context)
                            AppService.me().setPackageName(context.packageName)
                            diagnostics["reinitializationAttempted"] = true
                            
                            AppService.me().bindService()
                            diagnostics["postReinitBindResult"] = "Post-reinit bind attempt completed"
                            
                        } catch (initException: Exception) {
                            diagnostics["reinitializationError"] = initException.message ?: "Unknown initialization error"
                            Log.e("TiggPrinter", "Diagnostics: Reinitialization failed", initException)
                        }
                    }
                    
                    result.success(diagnostics)
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "Failed to get diagnostics", e)
                    result.error("DIAGNOSTICS_FAILED", "Failed to get service diagnostics: ${e.message}", null)
                }
            }
            "checkSystemServices" -> {
                try {
                    val systemInfo = mutableMapOf<String, Any>()
                    
                    // Check running processes for TiggPrinter related services
                    try {
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        val runningServices = activityManager.getRunningServices(1000)
                        
                        val tiggServices = runningServices.filter { service ->
                            service.service.className.contains("tigg", ignoreCase = true) ||
                            service.service.packageName.contains("tigg", ignoreCase = true) ||
                            service.service.className.contains("printer", ignoreCase = true) ||
                            service.service.packageName.contains("printer", ignoreCase = true)
                        }
                        
                        systemInfo["totalRunningServices"] = runningServices.size
                        systemInfo["tiggRelatedServices"] = tiggServices.map { 
                            mapOf(
                                "className" to it.service.className,
                                "packageName" to it.service.packageName,
                                "pid" to it.pid,
                                "foreground" to it.foreground
                            )
                        }
                        
                        Log.i("TiggPrinter", "Found ${tiggServices.size} Tigg-related services")
                        
                    } catch (serviceException: Exception) {
                        systemInfo["serviceCheckError"] = serviceException.message ?: "Unknown service check error"
                        Log.e("TiggPrinter", "Failed to check running services", serviceException)
                    }
                    
                    // Check installed packages for TiggPrinter
                    try {
                        val packageManager = context.packageManager
                        val installedPackages = packageManager.getInstalledPackages(0)
                        
                        val tiggPackages = installedPackages.filter { pkg ->
                            pkg.packageName.contains("tigg", ignoreCase = true) ||
                            pkg.packageName.contains("printer", ignoreCase = true)
                        }
                        
                        systemInfo["tiggRelatedPackages"] = tiggPackages.map {
                            mapOf(
                                "packageName" to it.packageName,
                                "versionName" to it.versionName,
                                "versionCode" to it.versionCode
                            )
                        }
                        
                        Log.i("TiggPrinter", "Found ${tiggPackages.size} Tigg-related packages")
                        
                    } catch (packageException: Exception) {
                        systemInfo["packageCheckError"] = packageException.message ?: "Unknown package check error"
                        Log.e("TiggPrinter", "Failed to check installed packages", packageException)
                    }
                    
                    result.success(systemInfo)
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "Failed to check system services", e)
                    result.error("SYSTEM_CHECK_FAILED", "Failed to check system services: ${e.message}", null)
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

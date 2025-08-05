

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
            AppService.me().init(context)
            AppService.me().setPackageName(context.packageName)
            AppService.me().bindService()
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to initialize AppService: ${e.message}")
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "printBase64Image" -> {
                val base64Image = call.argument<String>("base64Image") ?: ""

                if (BaseUtils.isValidBase64(base64Image)) {
                    try {
                        // Convert base64 to bitmap
                        val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        
                        if (bitmap != null) {
                            // Use the startPrinting method from AppService for bitmap printing
                            AppService.me().startPrinting(bitmap, true, object : IPaymentCallback.Stub() {
                                override fun onSuccess(success: Boolean, message: String?) {
                                    if (success) {
                                        result.success("Print completed successfully")
                                    } else {
                                        result.error("PRINT_ERROR", message ?: "Print failed", null)
                                    }
                                }
                                
                                override fun onResponse(response: Bundle?) {
                                    // Handle any additional response data if needed
                                    Log.d("TiggPrinter", "Print response received: $response")
                                }
                            })
                        } else {
                            result.error("INVALID_IMAGE", "Could not decode base64 image", null)
                        }
                    } catch (e: RemoteException) {
                        result.error("REMOTE_EXCEPTION", "Printer service error: ${e.message}", null)
                    } catch (e: Exception) {
                        result.error("PRINT_EXCEPTION", "Print error: ${e.message}", null)
                    }
                } else {
                    result.error("INVALID_BASE64", "Invalid Base64 image data", null)
                }
            }
            "printText" -> {
                val text = call.argument<String>("text") ?: ""
                val textSize = call.argument<Int>("textSize") ?: 24

                if (text.isNotEmpty()) {
                    try {
                        // Use the startPrinting method from AppService for text printing
                        AppService.me().startPrinting(text, textSize, object : IPaymentCallback.Stub() {
                            override fun onSuccess(success: Boolean, message: String?) {
                                if (success) {
                                    result.success("Text printed successfully")
                                } else {
                                    result.error("PRINT_ERROR", message ?: "Print failed", null)
                                }
                            }
                            
                            override fun onResponse(response: Bundle?) {
                                Log.d("TiggPrinter", "Print response received: $response")
                            }
                        })
                    } catch (e: RemoteException) {
                        result.error("REMOTE_EXCEPTION", "Printer service error: ${e.message}", null)
                    } catch (e: Exception) {
                        result.error("PRINT_EXCEPTION", "Print error: ${e.message}", null)
                    }
                } else {
                    result.error("EMPTY_TEXT", "Text cannot be empty", null)
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}

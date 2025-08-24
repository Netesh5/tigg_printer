

package com.example.tigg_printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Base64
import android.util.Log
import androidx.annotation.NonNull
import com.example.clientapp.AppService
import com.example.clientapp.utils.BaseUtils
import acquire.client_connection.IPaymentCallback
// Tactilion SDK imports
import com.basewin.printService.PrintService
import com.basewin.printService.PrintParams
import com.basewin.aidl.OnPrinterListener
import com.basewin.services.DeviceInfoBinder
// ServiceManager API imports (proper Tactilion API)
import com.basewin.services.ServiceManager
import com.basewin.services.PrinterBinder
import com.basewin.aidl.OnPrinterListener as BasewinPrinterListener
import com.basewin.models.TextPrintLine
import com.basewin.models.BitmapPrintLine
import com.basewin.models.PrintLine
import com.basewin.define.PrinterInfo
import com.basewin.interfaces.OnInitListener
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** TiggPrinterPlugin */

enum class DeviceType {
    FEWAPOS,
    TACTILION,
    UNKNOWN
}

class TiggPrinterPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var detectedDeviceType: DeviceType = DeviceType.UNKNOWN
    
    // Tactilion SDK instances
    private var tactilionPrintService: PrintService? = null
    private var deviceInfoBinder: DeviceInfoBinder? = null
    // ServiceManager API instances (proper Tactilion API)
    private var serviceManager: ServiceManager? = null
    private var printerBinder: PrinterBinder? = null
    private var isServiceManagerInitialized = false

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "tigg_printer")
        channel.setMethodCallHandler(this)
        
        // Detect device type asynchronously
        Thread {
            try {
                detectedDeviceType = detectDeviceType()
                Log.i("TiggPrinter", "Detected device type: $detectedDeviceType")
                
                when (detectedDeviceType) {
                    DeviceType.FEWAPOS -> {
                        initializeFewaPos()
                    }
                    DeviceType.TACTILION -> {
                        // Initialize Tactilion in background thread to prevent blocking
                        Thread {
                            initializeTactilion()
                        }.start()
                    }
                    DeviceType.UNKNOWN -> {
                        Log.w("TiggPrinter", "Unknown device type. Attempting FewaPos initialization as fallback.")
                        initializeFewaPos()
                    }
                }
            } catch (e: Exception) {
                Log.e("TiggPrinter", "Failed to initialize printer service: ${e.message}", e)
            }
        }.start()
    }
    
    private fun detectDeviceType(): DeviceType {
        try {
            // Method 1: Check device manufacturer and model
            val manufacturer = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()
            val product = Build.PRODUCT.lowercase()
            
            Log.d("TiggPrinter", "Device info - Manufacturer: $manufacturer, Model: $model, Product: $product")
            
            // Check for Tactilion device patterns
            if (manufacturer.contains("tactilion") || 
                model.contains("tactilion") || 
                product.contains("tactilion") ||
                manufacturer.contains("basewin") ||
                model.contains("basewin")) {
                Log.d("TiggPrinter", "Tactilion device detected by manufacturer/model")
                return DeviceType.TACTILION
            }
            
            // Method 2: Try to instantiate Tactilion SDK classes (without initializing PrintService)
            try {
                // Try DeviceInfoBinder first as it's less likely to have permission issues
                val testDeviceInfoBinder = DeviceInfoBinder(context)
                try {
                    val deviceType = testDeviceInfoBinder.getDeviceType()
                    Log.d("TiggPrinter", "Tactilion DeviceInfoBinder successful, device type: $deviceType")
                    return DeviceType.TACTILION
                } catch (e: SecurityException) {
                    Log.d("TiggPrinter", "Tactilion DeviceInfoBinder available but permission denied: ${e.message}")
                    // Device is Tactilion but permissions are missing
                    return DeviceType.TACTILION
                } catch (e: Exception) {
                    Log.d("TiggPrinter", "Tactilion DeviceInfoBinder failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.d("TiggPrinter", "Tactilion SDK classes not available: ${e.message}")
            }
            
            // Method 3: Check for FewaPos specific packages/classes
            try {
                AppService.me().init(context)
                Log.d("TiggPrinter", "FewaPos AppService available")
                return DeviceType.FEWAPOS
            } catch (e: Exception) {
                Log.d("TiggPrinter", "FewaPos SDK not available: ${e.message}")
            }
            
            return DeviceType.UNKNOWN
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error detecting device type: ${e.message}", e)
            return DeviceType.UNKNOWN
        }
    }
    
    private fun initializeFewaPos() {
        try {
            Log.i("TiggPrinter", "Initializing FewaPos SDK...")
            AppService.me().init(context)
            AppService.me().setPackageName("com.fewapay.cplus")
            
            Log.i("TiggPrinter", "Attempting initial FewaPos service bind...")
            val bindResult = AppService.me().bindService()
            Log.i("TiggPrinter", "Initial FewaPos service bind result: $bindResult")

            Thread.sleep(1000)
            val isConnected = AppService.me()?.isServiceConnected() ?: false
            Log.i("TiggPrinter", "Initial FewaPos service connection status: $isConnected")
            
            if (!isConnected) {
                Log.w("TiggPrinter", "FewaPos service not connected after initial bind. Will retry on demand.")
            }
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to initialize FewaPos: ${e.message}", e)
        }
    }
    
    private fun initializeTactilion() {
        try {
            Log.i("TiggPrinter", "Initializing Tactilion SDK...")
            
            // Initialize DeviceInfoBinder first (less likely to have permission issues)
            try {
                deviceInfoBinder = DeviceInfoBinder(context)
                Log.i("TiggPrinter", "Tactilion DeviceInfoBinder initialized successfully")
            } catch (securityException: SecurityException) {
                Log.w("TiggPrinter", "DeviceInfoBinder permission issue: ${securityException.message}")
                deviceInfoBinder = null
            } catch (e: Exception) {
                Log.w("TiggPrinter", "DeviceInfoBinder initialization failed: ${e.message}")
                deviceInfoBinder = null
            }
            
            // For now, skip ServiceManager initialization completely due to permission issues
            // The ServiceManager requires system-level permissions that we don't have
            Log.w("TiggPrinter", "Skipping ServiceManager initialization due to known permission issues")
            Log.w("TiggPrinter", "Tactilion device detected but SDK requires system-level permissions")
            Log.w("TiggPrinter", "App may need to be signed with Tactilion certificates or installed as system app")
            
            serviceManager = null
            printerBinder = null
            isServiceManagerInitialized = false
            
            // Try the old PrintService approach as fallback
            try {
                Log.d("TiggPrinter", "Attempting PrintService initialization as fallback...")
                tactilionPrintService = PrintService.getInstance()
                tactilionPrintService?.cleanCache()
                Log.i("TiggPrinter", "Tactilion PrintService fallback initialized successfully")
            } catch (securityException: SecurityException) {
                Log.e("TiggPrinter", "PrintService also requires permissions: ${securityException.message}")
                tactilionPrintService = null
            } catch (e: Exception) {
                Log.e("TiggPrinter", "PrintService fallback failed: ${e.message}")
                tactilionPrintService = null
            }
            
            Log.i("TiggPrinter", "Tactilion SDK initialization completed with limited functionality")
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to initialize Tactilion: ${e.message}", e)
            tactilionPrintService = null
            deviceInfoBinder = null
            serviceManager = null
            printerBinder = null
            isServiceManagerInitialized = false
        }
    }
    
    private fun initializeServiceManager() {
        try {
            Log.d("TiggPrinter", "Attempting ServiceManager initialization...")
            
            // Try to get ServiceManager instance first
            serviceManager = ServiceManager.getInstence()
            
            if (serviceManager == null) {
                Log.e("TiggPrinter", "ServiceManager.getInstence() returned null")
                isServiceManagerInitialized = false
                return
            }
            
            // Try the simple init first (less likely to have permission issues)
            try {
                Log.d("TiggPrinter", "Trying ServiceManager.init() method...")
                serviceManager?.init(context)
                isServiceManagerInitialized = true
                Log.i("TiggPrinter", "ServiceManager initialized successfully with init() method")
                
                // Try to initialize printer after successful init
                try {
                    initializePrinter()
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "Failed to initialize printer after ServiceManager init: ${e.message}")
                }
                return
                
            } catch (securityException: SecurityException) {
                Log.w("TiggPrinter", "ServiceManager.init() failed with security exception: ${securityException.message}")
                // Fall through to try forceinit
            } catch (e: Exception) {
                Log.w("TiggPrinter", "ServiceManager.init() failed: ${e.message}")
                // Fall through to try forceinit
            }
            
            // If init() failed, try forceinit in a background thread with timeout
            Log.d("TiggPrinter", "Trying ServiceManager.forceinit() method...")
            
            // Run forceinit in a separate thread with timeout to prevent blocking
            val initThread = Thread {
                try {
                    serviceManager?.forceinit(context, object : OnInitListener {
                        override fun onSucc() {
                            Log.i("TiggPrinter", "ServiceManager forceinit successful")
                            isServiceManagerInitialized = true
                            
                            // Initialize printer after successful service init
                            try {
                                initializePrinter()
                            } catch (e: Exception) {
                                Log.e("TiggPrinter", "Failed to initialize printer after ServiceManager forceinit: ${e.message}")
                            }
                        }
                        
                        override fun onFailed() {
                            Log.e("TiggPrinter", "ServiceManager forceinit failed")
                            isServiceManagerInitialized = false
                        }
                    })
                } catch (securityException: SecurityException) {
                    Log.e("TiggPrinter", "ServiceManager forceinit security exception: ${securityException.message}")
                    isServiceManagerInitialized = false
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "ServiceManager forceinit exception: ${e.message}")
                    isServiceManagerInitialized = false
                }
            }
            
            initThread.start()
            
            // Wait for a reasonable time for initialization
            try {
                initThread.join(3000) // Wait up to 3 seconds
                if (initThread.isAlive) {
                    Log.w("TiggPrinter", "ServiceManager initialization timed out")
                    initThread.interrupt()
                }
            } catch (e: InterruptedException) {
                Log.w("TiggPrinter", "ServiceManager initialization interrupted")
            }
            
        } catch (securityException: SecurityException) {
            Log.e("TiggPrinter", "Security permission error during ServiceManager initialization: ${securityException.message}")
            Log.e("TiggPrinter", "This indicates the app lacks system-level permissions required by Tactilion SDK")
            serviceManager = null
            isServiceManagerInitialized = false
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to initialize ServiceManager: ${e.message}", e)
            serviceManager = null
            isServiceManagerInitialized = false
        }
    }
    
    private fun initializePrinter() {
        try {
            Log.d("TiggPrinter", "Initializing Tactilion printer...")
            
            if (serviceManager == null) {
                Log.e("TiggPrinter", "ServiceManager is null, cannot initialize printer")
                return
            }
            
            // Get printer binder
            printerBinder = serviceManager?.getPrinter()
            
            if (printerBinder != null) {
                Log.d("TiggPrinter", "PrinterBinder obtained successfully")
                
                // Configure printer settings based on the sample code
                printerBinder?.setPrintGray(1000)
                printerBinder?.setLineSpace(25)
                printerBinder?.setPrintTypesettingType(1) // PRINTERLAYOUT_TYPESETTING
                printerBinder?.cleanCache()
                
                // Set default font if available
                try {
                    printerBinder?.setPrintFontByAsserts("arial.ttf")
                } catch (e: Exception) {
                    Log.w("TiggPrinter", "Could not set custom font: ${e.message}")
                }
                
                Log.i("TiggPrinter", "Tactilion printer initialized successfully")
                
                // Get available printers info
                val printerInfo = printerBinder?.getPrinterInfo()
                Log.d("TiggPrinter", "Available printers: ${printerInfo?.size ?: 0}")
                printerInfo?.forEachIndexed { index, info ->
                    Log.d("TiggPrinter", "Printer $index: ${info}")
                }
                
            } else {
                Log.e("TiggPrinter", "Failed to get PrinterBinder from ServiceManager")
            }
            
        } catch (securityException: SecurityException) {
            Log.e("TiggPrinter", "Security permission error during printer initialization: ${securityException.message}")
            printerBinder = null
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to initialize printer: ${e.message}", e)
            printerBinder = null
        }
    }
    
    private fun ensureTactilionPrintService(): Boolean {
        // First check if we have any working service
        if (isServiceManagerInitialized && printerBinder != null) {
            return true
        }
        
        if (tactilionPrintService != null) {
            return true
        }
        
        // Try PrintService approach only (avoid ServiceManager due to permission issues)
        if (tactilionPrintService == null) {
            try {
                Log.d("TiggPrinter", "Attempting PrintService initialization...")
                tactilionPrintService = PrintService.getInstance()
                tactilionPrintService?.cleanCache()
                Log.d("TiggPrinter", "Tactilion PrintService initialized successfully")
                return true
            } catch (securityException: SecurityException) {
                Log.e("TiggPrinter", "Security permission error during Tactilion PrintService initialization: ${securityException.message}")
                Log.e("TiggPrinter", "This device appears to be a Tactilion device but lacks the required system-level permissions.")
                Log.e("TiggPrinter", "The app may need to be signed with Tactilion's certificates or installed as a system app.")
                tactilionPrintService = null
                return false
            } catch (e: Exception) {
                Log.e("TiggPrinter", "Failed to initialize Tactilion PrintService: ${e.message}", e)
                tactilionPrintService = null
                return false
            }
        }
        return tactilionPrintService != null
    }
    
    // Manual ServiceManager initialization method (can be called explicitly if needed)
    private fun tryServiceManagerInitialization(): Boolean {
        if (isServiceManagerInitialized && printerBinder != null) {
            return true
        }
        
        try {
            Log.d("TiggPrinter", "Manual ServiceManager initialization attempt...")
            initializeServiceManager()
            return isServiceManagerInitialized && printerBinder != null
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Manual ServiceManager initialization failed: ${e.message}")
            return false
        }
    }
    
    private fun bindFewaPos(result: MethodChannel.Result) {
        try {
            // First check if AppService is initialized
            if (AppService.me() == null) {
                Log.e("TiggPrinter", "FewaPos AppService is not initialized")
                context.mainExecutor.execute {
                    result.error("SERVICE_NOT_INITIALIZED", "FewaPos AppService is not initialized", null)
                }
                return
            }
            
            // Check current connection status
            val currentStatus = AppService.me().isServiceConnected()
            Log.i("TiggPrinter", "Current FewaPos service connection status: $currentStatus")
            
            if (currentStatus) {
                context.mainExecutor.execute {
                    result.success("FewaPos service is already connected")
                }
                return
            }
            
            // Attempt to bind with timeout to prevent hanging
            Log.i("TiggPrinter", "Attempting FewaPos service bind...")
            val bindResult = AppService.me().bindService()
            Log.i("TiggPrinter", "FewaPos bind service result: $bindResult")
            
            // Short wait for connection
            Thread.sleep(1000)
            val finalStatus = AppService.me()?.isServiceConnected() ?: false
            Log.i("TiggPrinter", "FewaPos service connection status after bind: $finalStatus")
            
            // Return result on main thread
            context.mainExecutor.execute {
                if (finalStatus) {
                    result.success("FewaPos service bound and connected successfully")
                } else {
                    result.error("BIND_FAILED", "FewaPos service bind initiated but connection failed. TiggPrinter service may not be running.", null)
                }
            }
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to bind FewaPos service", e)
            context.mainExecutor.execute {
                result.error("BIND_ERROR", "Error during FewaPos service binding: ${getErrorMessage(e)}", null)
            }
        }
    }
    
    private fun bindTactilion(result: MethodChannel.Result) {
        try {
            // Tactilion doesn't require a separate bind operation
            // The PrintService.getInstance() call during initialization is sufficient
            if (tactilionPrintService != null) {
                context.mainExecutor.execute {
                    result.success("Tactilion service is ready")
                }
            } else {
                // Try to reinitialize
                try {
                    tactilionPrintService = PrintService.getInstance()
                    if (tactilionPrintService != null) {
                        context.mainExecutor.execute {
                            result.success("Tactilion service initialized successfully")
                        }
                    } else {
                        context.mainExecutor.execute {
                            result.error("BIND_FAILED", "Failed to initialize Tactilion print service", null)
                        }
                    }
                } catch (securityException: SecurityException) {
                    Log.e("TiggPrinter", "Security permission error for Tactilion SDK: ${securityException.message}")
                    context.mainExecutor.execute {
                        result.error("PERMISSION_DENIED", "Missing required permissions for Tactilion SDK. Please add com.pos.permission.SECURITY and other required permissions to AndroidManifest.xml", securityException.message)
                    }
                } catch (e: Exception) {
                    Log.e("TiggPrinter", "Failed to initialize Tactilion print service: ${e.message}", e)
                    context.mainExecutor.execute {
                        result.error("INIT_FAILED", "Failed to initialize Tactilion print service: ${getErrorMessage(e)}", null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Failed to bind Tactilion service", e)
            context.mainExecutor.execute {
                result.error("BIND_ERROR", "Error during Tactilion service binding: ${getErrorMessage(e)}", null)
            }
        }
    }
    
    private fun printBase64ImageFewaPos(base64Image: String, textSize: Int, result: MethodChannel.Result) {
        try {
            if (!BaseUtils.isValidBase64(base64Image)) {
                result.error("INVALID_BASE64", "Invalid Base64 image data format", null)
                return
            }

            // Check if service is available and connected
            if (AppService.me() == null) {
                result.error("SERVICE_UNAVAILABLE", "FewaPos printer service is not initialized", null)
                return
            }
            
            if (!AppService.me().isServiceConnected()) {
                result.error("SERVICE_NOT_CONNECTED", "FewaPos printer service is not connected. Please bind service first.", null)
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
                                "message" to "Image printed successfully with FewaPos"
                            ))
                        } else {
                            result.error("PRINT_FAILED", message ?: "FewaPos print operation failed", null)
                        }
                    }
                }
                
                override fun onResponse(response: Bundle?) {
                    // Handle any additional response data if needed
                    Log.d("TiggPrinter", "FewaPos print response received: $response")
                }
            })
        } catch (e: RemoteException) {
            result.error("REMOTE_EXCEPTION", "FewaPos printer service communication error: ${e.message}", null)
        } catch (e: IllegalArgumentException) {
            result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
        } catch (e: OutOfMemoryError) {
            result.error("MEMORY_ERROR", "Not enough memory to process image: ${e.message}", null)
        } catch (e: Exception) {
            result.error("PRINT_EXCEPTION", "Unexpected error during FewaPos printing: ${e.message}", null)
        }
    }
    
    private fun printBase64ImageTactilion(base64Image: String, textSize: Int, result: MethodChannel.Result) {
        try {
            // Ensure Tactilion service is initialized and available
            if (!ensureTactilionPrintService()) {
                result.error("SERVICE_UNAVAILABLE", "Tactilion printer service failed to initialize. This may be due to missing permissions or incorrect device configuration.", null)
                return
            }

            // Convert base64 to bitmap
            val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            
            if (bitmap == null) {
                result.error("INVALID_IMAGE", "Could not decode base64 image data", null)
                return
            }

            // Try ServiceManager approach first (preferred)
            if (isServiceManagerInitialized && printerBinder != null) {
                printImageWithServiceManager(bitmap, result)
                return
            }
            
            // Fallback to old PrintService approach
            if (tactilionPrintService != null) {
                printImageWithPrintService(bitmap, result)
                return
            }
            
            result.error("SERVICE_UNAVAILABLE", "No Tactilion printing service available", null)
            
        } catch (securityException: SecurityException) {
            Log.e("TiggPrinter", "Security permission error during Tactilion image printing: ${securityException.message}")
            result.error("PERMISSION_DENIED", "Permission denied for Tactilion printing. Required permissions may not be granted at system level.", securityException.message)
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Unexpected error during Tactilion image printing: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "Unexpected error during Tactilion printing: ${e.message}", null)
        }
    }
    
    private fun printImageWithServiceManager(bitmap: Bitmap, result: MethodChannel.Result) {
        try {
            Log.d("TiggPrinter", "Printing image with ServiceManager")
            
            // Clean cache before printing
            printerBinder?.cleanCache()
            
            // Create bitmap print line using ServiceManager API
            val bitmapPrintLine = BitmapPrintLine().apply {
                setType(PrintLine.BITMAP)
                setPosition(PrintLine.CENTER) // Center the image
                setBitmap(bitmap)
            }
            
            // Add print line to printer
            printerBinder?.addPrintLine(bitmapPrintLine)
            
            // Begin printing with callback
            printerBinder?.beginPrint(object : BasewinPrinterListener {
                override fun onStart() {
                    Log.d("TiggPrinter", "ServiceManager image print started")
                }
                
                override fun onFinish() {
                    Log.d("TiggPrinter", "ServiceManager image print finished successfully")
                    context.mainExecutor.execute {
                        result.success(mapOf(
                            "success" to true,
                            "message" to "Image printed successfully with Tactilion ServiceManager"
                        ))
                    }
                }
                
                override fun onError(errorCode: Int, errorMessage: String?) {
                    Log.e("TiggPrinter", "ServiceManager image print error: $errorCode - $errorMessage")
                    context.mainExecutor.execute {
                        result.error("PRINT_FAILED", "Tactilion ServiceManager image print failed: $errorMessage (Code: $errorCode)", null)
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error in printImageWithServiceManager: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "ServiceManager image printing error: ${e.message}", null)
        }
    }
    
    private fun printImageWithPrintService(bitmap: Bitmap, result: MethodChannel.Result) {
        try {
            Log.d("TiggPrinter", "Printing image with old PrintService")
            
            // Create print parameters for Tactilion
            val printParams = PrintParams(0, 0, 0) // Default parameters, can be customized
            
            // Add bitmap to cache and print
            tactilionPrintService?.addBmpToCurCache(bitmap, printParams)
            tactilionPrintService?.print(object : OnPrinterListener {
                override fun onStart() {
                    Log.d("TiggPrinter", "PrintService image print started")
                }
                
                override fun onFinish() {
                    Log.d("TiggPrinter", "PrintService image print finished successfully")
                    context.mainExecutor.execute {
                        result.success(mapOf(
                            "success" to true,
                            "message" to "Image printed successfully with Tactilion PrintService"
                        ))
                    }
                }
                
                override fun onError(errorCode: Int, errorMessage: String?) {
                    Log.e("TiggPrinter", "PrintService image print error: $errorCode - $errorMessage")
                    context.mainExecutor.execute {
                        result.error("PRINT_FAILED", "Tactilion PrintService image print failed: $errorMessage (Code: $errorCode)", null)
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error in printImageWithPrintService: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "PrintService image printing error: ${e.message}", null)
        }
    }
    
    private fun printTextFewaPos(text: String, textSize: Int, paperWidth: Int, result: MethodChannel.Result) {
        try {
            if (AppService.me() == null) {
                result.error("SERVICE_UNAVAILABLE", "FewaPos printer service is not initialized", null)
                return
            }

            // Attempt re-bind if not connected
            if (!AppService.me().isServiceConnected()) {
                Log.w("TiggPrinter", "FewaPos service not connected. Attempting re-bind before printing...")
                AppService.me().bindService()
                Thread.sleep(1000) // Wait for binding
            }

            if (!AppService.me().isServiceConnected()) {
                result.error("SERVICE_NOT_CONNECTED", "FewaPos printer service is still not connected after retry", null)
                return
            }

            val bitmap = createTextBitmap(text, textSize, paperWidth)
            if (bitmap == null) {
                result.error("TEXT_RENDER_ERROR", "Could not create text bitmap", null)
                return
            }

            Log.d("TiggPrinter", "Starting FewaPos print job...")

            AppService.me().startPrinting(bitmap, false, object : IPaymentCallback.Stub() {
                override fun onSuccess(success: Boolean, message: String?) {
                    Log.d("TiggPrinter", "FewaPos print callback - onSuccess: success=$success, message=$message")
                    context.mainExecutor.execute {
                        if (success) {
                            val response = mapOf<String, Any?>(
                                "success" to true,
                                "message" to (message ?: "Text printed successfully with FewaPos")
                            )
                            result.success(response)
                        } else {
                            result.error("PRINT_FAILED", message ?: "FewaPos print operation failed", null)
                        }
                    }
                }

                override fun onResponse(response: Bundle?) {
                    Log.d("TiggPrinter", "FewaPos print callback - onResponse: $response")
                }
            })

        } catch (e: RemoteException) {
            result.error("REMOTE_EXCEPTION", "FewaPos printer service communication error: ${e.message}", null)
        } catch (e: IllegalArgumentException) {
            result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
        } catch (e: Exception) {
            result.error("PRINT_EXCEPTION", "Unexpected error during FewaPos printing: ${e.message}", null)
        }
    }
    
    private fun printTextTactilion(text: String, textSize: Int, paperWidth: Int, result: MethodChannel.Result) {
        try {
            // Ensure Tactilion service is initialized and available
            if (!ensureTactilionPrintService()) {
                result.error("SERVICE_UNAVAILABLE", "Tactilion printer service failed to initialize. This may be due to missing permissions or incorrect device configuration.", null)
                return
            }

            // Try ServiceManager approach first (preferred)
            if (isServiceManagerInitialized && printerBinder != null) {
                printTextWithServiceManager(text, textSize, result)
                return
            }
            
            // Fallback to old PrintService approach
            if (tactilionPrintService != null) {
                printTextWithPrintService(text, textSize, result)
                return
            }
            
            result.error("SERVICE_UNAVAILABLE", "No Tactilion printing service available", null)
            
        } catch (securityException: SecurityException) {
            Log.e("TiggPrinter", "Security permission error during Tactilion printing: ${securityException.message}")
            result.error("PERMISSION_DENIED", "Permission denied for Tactilion printing. Required permissions may not be granted at system level.", securityException.message)
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Unexpected error during Tactilion text printing: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "Unexpected error during Tactilion text printing: ${e.message}", null)
        }
    }
    
    private fun printTextWithServiceManager(text: String, textSize: Int, result: MethodChannel.Result) {
        try {
            Log.d("TiggPrinter", "Printing text with ServiceManager: $text")
            
            // Clean cache before printing
            printerBinder?.cleanCache()
            
            // Create text print line using ServiceManager API
            val textPrintLine = TextPrintLine().apply {
                setType(PrintLine.TEXT)
                setPosition(PrintLine.LEFT) // Default to left alignment
                setBold(true)
                setSize(when {
                    textSize <= 12 -> TextPrintLine.FONT_SMALL
                    textSize <= 18 -> TextPrintLine.FONT_NORMAL
                    else -> TextPrintLine.FONT_LARGE
                })
                setContent(text)
            }
            
            // Add print line to printer
            printerBinder?.addPrintLine(textPrintLine)
            
            // Begin printing with callback
            printerBinder?.beginPrint(object : BasewinPrinterListener {
                override fun onStart() {
                    Log.d("TiggPrinter", "ServiceManager text print started")
                }
                
                override fun onFinish() {
                    Log.d("TiggPrinter", "ServiceManager text print finished successfully")
                    context.mainExecutor.execute {
                        result.success(mapOf(
                            "success" to true,
                            "message" to "Text printed successfully with Tactilion ServiceManager"
                        ))
                    }
                }
                
                override fun onError(errorCode: Int, errorMessage: String?) {
                    Log.e("TiggPrinter", "ServiceManager text print error: $errorCode - $errorMessage")
                    context.mainExecutor.execute {
                        result.error("PRINT_FAILED", "Tactilion ServiceManager print failed: $errorMessage (Code: $errorCode)", null)
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error in printTextWithServiceManager: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "ServiceManager printing error: ${e.message}", null)
        }
    }
    
    private fun printTextWithPrintService(text: String, textSize: Int, result: MethodChannel.Result) {
        try {
            Log.d("TiggPrinter", "Printing text with old PrintService: $text")
            
            // Create print parameters for Tactilion
            val printParams = PrintParams(0, 0, 0) // Default parameters
            
            // Add text to cache and print
            tactilionPrintService?.addTextToCurCache(text, printParams)
            tactilionPrintService?.print(object : OnPrinterListener {
                override fun onStart() {
                    Log.d("TiggPrinter", "PrintService text print started")
                }
                
                override fun onFinish() {
                    Log.d("TiggPrinter", "PrintService text print finished successfully")
                    context.mainExecutor.execute {
                        result.success(mapOf(
                            "success" to true,
                            "message" to "Text printed successfully with Tactilion PrintService"
                        ))
                    }
                }
                
                override fun onError(errorCode: Int, errorMessage: String?) {
                    Log.e("TiggPrinter", "PrintService text print error: $errorCode - $errorMessage")
                    context.mainExecutor.execute {
                        result.error("PRINT_FAILED", "Tactilion PrintService print failed: $errorMessage (Code: $errorCode)", null)
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error in printTextWithPrintService: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "PrintService printing error: ${e.message}", null)
        }
    }
    
    private fun printRawBytesFewaPos(bytes: List<Int>, useDirectString: Boolean, textSize: Int, paperWidth: Int, result: MethodChannel.Result) {
        try {
            if (AppService.me() == null) {
                result.error("SERVICE_UNAVAILABLE", "FewaPos printer service is not initialized", null)
                return
            }

            // Attempt re-bind if not connected
            if (!AppService.me().isServiceConnected()) {
                Log.w("TiggPrinter", "FewaPos service not connected. Attempting re-bind before printing...")
                AppService.me().bindService()
                Thread.sleep(1000) // Wait for binding
            }

            if (!AppService.me().isServiceConnected()) {
                result.error("SERVICE_NOT_CONNECTED", "FewaPos printer service is still not connected after retry", null)
                return
            }

            // Convert List<Int> to ByteArray
            val byteArray = bytes.map { it.toByte() }.toByteArray()
            val escPosString = String(byteArray, Charsets.ISO_8859_1)
            
            Log.d("TiggPrinter", "Printing raw ESC/POS bytes with FewaPos, length: ${byteArray.size}, useDirectString: $useDirectString")

            if (useDirectString) {
                // Method 1: Direct string approach (may show default header)
                Log.d("TiggPrinter", "*** USING FewaPos STRING METHOD (may show default header) ***")
                AppService.me().startPrinting(escPosString, textSize, object : IPaymentCallback.Stub() {
                    override fun onSuccess(success: Boolean, message: String?) {
                        Log.d("TiggPrinter", "FewaPos raw bytes (string) print callback - onSuccess: success=$success, message=$message")
                        context.mainExecutor.execute {
                            if (success) {
                                result.success(mapOf(
                                    "success" to true,
                                    "message" to "Raw bytes printed successfully with FewaPos (string method)"
                                ))
                            } else {
                                result.error("PRINT_FAILED", message ?: "FewaPos raw bytes print operation failed", null)
                            }
                        }
                    }

                    override fun onResponse(response: Bundle?) {
                        Log.d("TiggPrinter", "FewaPos raw bytes (string) print callback - onResponse: $response")
                    }
                })
            } else {
                // Method 2: Create minimal bitmap to avoid default header
                Log.d("TiggPrinter", "*** USING FewaPos BITMAP METHOD (clean, no header) ***")
                val rawBitmap = createMinimalEscPosBitmap(escPosString, paperWidth)
                if (rawBitmap == null) {
                    result.error("RAW_DATA_ERROR", "Could not process ESC/POS data", null)
                    return
                }
                
                AppService.me().startPrinting(rawBitmap, false, object : IPaymentCallback.Stub() {
                    override fun onSuccess(success: Boolean, message: String?) {
                        Log.d("TiggPrinter", "FewaPos raw bytes (bitmap) print callback - onSuccess: success=$success, message=$message")
                        context.mainExecutor.execute {
                            if (success) {
                                result.success(mapOf(
                                    "success" to true,
                                    "message" to "Raw bytes printed successfully with FewaPos (bitmap method)"
                                ))
                            } else {
                                result.error("PRINT_FAILED", message ?: "FewaPos raw bytes print operation failed", null)
                            }
                        }
                    }

                    override fun onResponse(response: Bundle?) {
                        Log.d("TiggPrinter", "FewaPos raw bytes (bitmap) print callback - onResponse: $response")
                    }
                })
            }

        } catch (e: RemoteException) {
            result.error("REMOTE_EXCEPTION", "FewaPos printer service communication error: ${e.message}", null)
        } catch (e: IllegalArgumentException) {
            result.error("INVALID_INPUT", "Invalid input parameters: ${e.message}", null)
        } catch (e: Exception) {
            result.error("PRINT_EXCEPTION", "Unexpected error during FewaPos raw bytes printing: ${e.message}", null)
        }
    }
    
    private fun printRawBytesTactilion(bytes: List<Int>, useDirectString: Boolean, textSize: Int, paperWidth: Int, result: MethodChannel.Result) {
        try {
            // Ensure Tactilion service is initialized and available
            if (!ensureTactilionPrintService()) {
                result.error("SERVICE_UNAVAILABLE", "Tactilion printer service failed to initialize. This may be due to missing permissions or incorrect device configuration.", null)
                return
            }

            // Convert List<Int> to ByteArray
            val byteArray = bytes.map { it.toByte() }.toByteArray()
            val escPosString = String(byteArray, Charsets.ISO_8859_1)
            
            Log.d("TiggPrinter", "Printing raw ESC/POS bytes with Tactilion, length: ${byteArray.size}")

            // For Tactilion, we can try to render the ESC/POS data as text or bitmap
            // Since Tactilion SDK might not directly support ESC/POS commands,
            // we'll convert the data to a bitmap for consistent printing
            
            val rawBitmap = createMinimalEscPosBitmap(escPosString, paperWidth)
            if (rawBitmap == null) {
                result.error("RAW_DATA_ERROR", "Could not process ESC/POS data for Tactilion", null)
                return
            }
            
            val printParams = PrintParams(0, 0, 0) // Default parameters
            
            // Add bitmap to cache and print
            tactilionPrintService?.addBmpToCurCache(rawBitmap, printParams)
            tactilionPrintService?.print(object : OnPrinterListener {
                override fun onStart() {
                    Log.d("TiggPrinter", "Tactilion raw bytes print started")
                }
                
                override fun onFinish() {
                    Log.d("TiggPrinter", "Tactilion raw bytes print finished successfully")
                    context.mainExecutor.execute {
                        result.success(mapOf(
                            "success" to true,
                            "message" to "Raw bytes printed successfully with Tactilion"
                        ))
                    }
                }
                
                override fun onError(errorCode: Int, errorMessage: String?) {
                    Log.e("TiggPrinter", "Tactilion raw bytes print error: $errorCode - $errorMessage")
                    context.mainExecutor.execute {
                        result.error("PRINT_FAILED", "Tactilion raw bytes print failed: $errorMessage (Code: $errorCode)", null)
                    }
                }
            })
            
        } catch (securityException: SecurityException) {
            Log.e("TiggPrinter", "Security permission error during Tactilion raw bytes printing: ${securityException.message}")
            result.error("PERMISSION_DENIED", "Permission denied for Tactilion printing. Required permissions may not be granted at system level.", securityException.message)
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Unexpected error during Tactilion raw bytes printing: ${e.message}", e)
            result.error("PRINT_EXCEPTION", "Unexpected error during Tactilion raw bytes printing: ${e.message}", null)
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getDeviceType" -> {
                val deviceTypeString = when (detectedDeviceType) {
                    DeviceType.FEWAPOS -> "fewapos"
                    DeviceType.TACTILION -> "tactilion"
                    DeviceType.UNKNOWN -> "unknown"
                }
                result.success(deviceTypeString)
            }
            "isPrinterAvailable" -> {
                try {
                    when (detectedDeviceType) {
                        DeviceType.FEWAPOS -> {
                            val isServiceConnected = AppService.me()?.isServiceConnected() ?: false
                            if (isServiceConnected) {
                                result.success(true)
                            } else {
                                result.error("SERVICE_UNAVAILABLE", "FewaPos printer service is not connected", null)
                            }
                        }
                        DeviceType.TACTILION -> {
                            // For Tactilion, check both ServiceManager and fallback PrintService
                            val serviceManagerStatus = isServiceManagerInitialized && printerBinder != null
                            val printServiceStatus = tactilionPrintService != null
                            
                            if (serviceManagerStatus || printServiceStatus) {
                                Log.i("TiggPrinter", "Tactilion printer check - ServiceManager: $serviceManagerStatus, PrintService: $printServiceStatus")
                                result.success(true)
                            } else {
                                Log.w("TiggPrinter", "Tactilion printer services not available - ServiceManager: $serviceManagerStatus, PrintService: $printServiceStatus")
                                result.error("SERVICE_UNAVAILABLE", "Tactilion printer services are not available. This may be due to missing system-level permissions.", null)
                            }
                        }
                        DeviceType.UNKNOWN -> {
                            result.error("SERVICE_UNAVAILABLE", "Unknown device type - printer service unavailable", null)
                        }
                    }
                } catch (e: Exception) {
                    result.error("SERVICE_UNAVAILABLE", "Printer service check failed: ${getErrorMessage(e)}", null)
                }
            }
            "bindService" -> {
                // Execute binding in background to avoid blocking main thread
                Thread {
                    try {
                        Log.i("TiggPrinter", "Manual bind service requested for device type: $detectedDeviceType")
                        
                        when (detectedDeviceType) {
                            DeviceType.FEWAPOS -> {
                                bindFewaPos(result)
                            }
                            DeviceType.TACTILION -> {
                                bindTactilion(result)
                            }
                            DeviceType.UNKNOWN -> {
                                context.mainExecutor.execute {
                                    result.error("UNKNOWN_DEVICE", "Cannot bind service for unknown device type", null)
                                }
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
                    when (detectedDeviceType) {
                        DeviceType.FEWAPOS -> {
                            val isConnected = AppService.me()?.isServiceConnected() ?: false
                            Log.i("TiggPrinter", "FewaPos service connection check: $isConnected")
                            result.success(isConnected)
                        }
                        DeviceType.TACTILION -> {
                            // For Tactilion, check both ServiceManager and PrintService availability
                            val serviceManagerConnected = isServiceManagerInitialized && printerBinder != null
                            val printServiceConnected = tactilionPrintService != null
                            val isConnected = serviceManagerConnected || printServiceConnected
                            
                            Log.i("TiggPrinter", "Tactilion service connection check - ServiceManager: $serviceManagerConnected, PrintService: $printServiceConnected, Overall: $isConnected")
                            result.success(isConnected)
                        }
                        DeviceType.UNKNOWN -> {
                            Log.i("TiggPrinter", "Unknown device - service connection: false")
                            result.success(false)
                        }
                    }
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

                try {
                    when (detectedDeviceType) {
                        DeviceType.FEWAPOS -> {
                            printBase64ImageFewaPos(base64Image, textSize, result)
                        }
                        DeviceType.TACTILION -> {
                            printBase64ImageTactilion(base64Image, textSize, result)
                        }
                        DeviceType.UNKNOWN -> {
                            result.error("UNKNOWN_DEVICE", "Cannot print - unknown device type", null)
                        }
                    }
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
                    when (detectedDeviceType) {
                        DeviceType.FEWAPOS -> {
                            printTextFewaPos(text, textSize, paperWidth, result)
                        }
                        DeviceType.TACTILION -> {
                            printTextTactilion(text, textSize, paperWidth, result)
                        }
                        DeviceType.UNKNOWN -> {
                            result.error("UNKNOWN_DEVICE", "Cannot print - unknown device type", null)
                        }
                    }
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
                    when (detectedDeviceType) {
                        DeviceType.FEWAPOS -> {
                            printRawBytesFewaPos(bytes, useDirectString, textSize, paperWidth, result)
                        }
                        DeviceType.TACTILION -> {
                            printRawBytesTactilion(bytes, useDirectString, textSize, paperWidth, result)
                        }
                        DeviceType.UNKNOWN -> {
                            result.error("UNKNOWN_DEVICE", "Cannot print - unknown device type", null)
                        }
                    }
                } catch (e: Exception) {
                    result.error("PRINT_EXCEPTION", "Unexpected error during raw bytes printing: ${e.message}", null)
                }
            }

            "getAvailablePrinters" -> {
                getAvailablePrinters(result)
            }
            "tryServiceManagerInit" -> {
                // Manual ServiceManager initialization for testing
                Thread {
                    try {
                        val success = tryServiceManagerInitialization()
                        context.mainExecutor.execute {
                            if (success) {
                                result.success(mapOf(
                                    "success" to true,
                                    "message" to "ServiceManager initialized successfully"
                                ))
                            } else {
                                result.success(mapOf(
                                    "success" to false,
                                    "message" to "ServiceManager initialization failed"
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        context.mainExecutor.execute {
                            result.error("INIT_ERROR", "ServiceManager initialization error: ${e.message}", null)
                        }
                    }
                }.start()
            }
            "selectPrinter" -> {
                selectPrinter(call, result)
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // Helper function to safely get error message from exceptions
    private fun getErrorMessage(e: Exception): String {
        return e.message ?: "Unknown error"
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
            val bottomFeedSpace = 100 // Reduced bottom space since ESC/POS uses empty lines
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
            
            // Debug: Log each parsed line to identify parsing issues
            for ((index, line) in formattedLines.withIndex()) {
                Log.d("TiggPrinter", "Parsed Line $index: '${line.text}' (align=${line.alignment}, bold=${line.isBold}, double=${line.isDoubleSize})")
            }
            
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
                                .replace(Regex("\\d+\\.\\d+[A-Z]"), { matchResult ->
                                    // Remove trailing letters from numeric values (like 88.49D -> 88.49)
                                    matchResult.value.replace(Regex("[A-Z]+$"), "")
                                })
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
            
            // Filter out standalone "0.00" lines that don't add value
            val filteredLines = finalLines.filter { line ->
                val trimmedText = line.text.trim()
                if (trimmedText == "0.00") {
                    Log.d("TiggPrinter", "Filtering out standalone '0.00' line")
                    false
                } else {
                    true
                }
            }.toMutableList()
            
            for ((index, line) in filteredLines.withIndex()) {
                Log.d("TiggPrinter", "Final Line $index: '${line.text}' (align=${line.alignment}, bold=${line.isBold}, double=${line.isDoubleSize})")
            }
            
            if (filteredLines.isEmpty()) {
                Log.w("TiggPrinter", "No content extracted from ESC/POS data - using simple extraction")
                // Create a simple fallback line to ensure something prints
                filteredLines.add(FormattedLine("ESC/POS Data (${escPosString.length} bytes)", 1, false, false))
                Log.d("TiggPrinter", "Using minimal fallback content")
            }
            
            // Add 3 empty lines at the bottom for easier paper handling
            filteredLines.add(FormattedLine("", 0, false, false))
            filteredLines.add(FormattedLine("", 0, false, false))
            filteredLines.add(FormattedLine("", 0, false, false))
           
            
            // NEVER use the old fallback - always use our small font rendering
            Log.d("TiggPrinter", "Using main rendering path with ${filteredLines.size} lines")
            
            // Calculate total height needed
            val baseTextSize = 20.0f // Back to 20px as requested
            Log.d("TiggPrinter", "*** USING READABLE FONT SIZE: ${baseTextSize}px ***")
            val lineSpacing = 1.2f // Slightly more spacing for larger font
            var totalHeight = 10f // Reduced top padding
            
            for (line in filteredLines) {
                val textSize = when {
                    line.isDoubleSize -> baseTextSize * 1.5f
                    else -> baseTextSize
                }
                // Normal height calculation without extra wrapping space
                totalHeight += textSize * lineSpacing
            }
             totalHeight += 80f // Reduced bottom padding since we have empty lines for paper handling
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(paperSize, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            // Draw each line with exact formatting - no modifications
            var y = baseTextSize * lineSpacing + 10f // Reduced initial Y position
            for (line in filteredLines) {
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
                            var productText = parts[0].trim()
                            val amountText = parts[1].trim()
                            
                            // Clean up decimal formatting in product text (truncate to 2 decimals, don't round)
                            productText = productText.replace(Regex("(\\d+\\.\\d{3,})")) { matchResult ->
                                val numberStr = matchResult.value
                                val dotIndex = numberStr.indexOf('.')
                                if (dotIndex != -1 && dotIndex + 3 < numberStr.length) {
                                    // Truncate to 2 decimal places without rounding
                                    numberStr.substring(0, dotIndex + 3)
                                } else {
                                    numberStr
                                }
                            }
                            
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
                    .replace(Regex("(\\d+\\.\\d+)D"), "$1") // Remove 'D' after decimal numbers (e.g., "88.49D" -> "88.49")
                    .replace(Regex("(\\d+)D"), "$1") // Remove 'D' after whole numbers (e.g., "100D" -> "100")
                
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
                    Log.d("TiggPrinter", "LF - Line feed")
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
    
    private fun getAvailablePrinters(result: MethodChannel.Result) {
        try {
            when (detectedDeviceType) {
                DeviceType.FEWAPOS -> {
                    result.success(mapOf(
                        "success" to true,
                        "deviceType" to "fewapos",
                        "printers" to listOf(
                            mapOf(
                                "name" to "FewaPos Built-in Printer",
                                "status" to if (AppService.me()?.isServiceConnected() == true) "connected" else "disconnected"
                            )
                        )
                    ))
                }
                DeviceType.TACTILION -> {
                    // For Tactilion, provide detailed status information
                    val serviceManagerAvailable = isServiceManagerInitialized && printerBinder != null
                    val printServiceAvailable = tactilionPrintService != null
                    
                    if (serviceManagerAvailable) {
                        // Try to get detailed printer info from ServiceManager
                        try {
                            val printerInfoList = printerBinder?.getPrinterInfo()
                            val printerCount = printerBinder?.getPrinterNum() ?: 0
                            val currentPrinter = printerBinder?.getPrinterInfoForNow()
                            
                            val printersList = mutableListOf<Map<String, Any>>()
                            
                            if (printerInfoList != null && printerInfoList.isNotEmpty()) {
                                printerInfoList.forEachIndexed { index, info ->
                                    printersList.add(mapOf(
                                        "id" to info.getId(),
                                        "name" to (info.getName() ?: "Tactilion Printer $index"),
                                        "type" to info.getType(),
                                        "mac" to (info.getMac() ?: "N/A"),
                                        "status" to "available",
                                        "isCurrent" to (info.getId() == currentPrinter?.getId()),
                                        "source" to "ServiceManager",
                                        "description" to info.toNormalString()
                                    ))
                                }
                            } else {
                                printersList.add(mapOf(
                                    "id" to 0,
                                    "name" to "Tactilion Built-in Printer (ServiceManager)",
                                    "type" to "built-in",
                                    "status" to "available",
                                    "isCurrent" to true,
                                    "source" to "ServiceManager"
                                ))
                            }
                            
                            // Add device info if available
                            val deviceInfo = mutableMapOf<String, Any>()
                            try {
                                deviceInfoBinder?.let { binder ->
                                    deviceInfo["deviceType"] = binder.getDeviceType() ?: "unknown"
                                    deviceInfo["serialNumber"] = binder.getSN() ?: "N/A"
                                    deviceInfo["vid"] = binder.getVID() ?: "N/A"
                                    deviceInfo["vendorName"] = binder.getVName() ?: "N/A"
                                    deviceInfo["sdkVersion"] = binder.getSDKVersion() ?: "N/A"
                                    deviceInfo["systemVersion"] = binder.getSystemVersion() ?: "N/A"
                                    deviceInfo["ksn"] = binder.getKSN() ?: "N/A"
                                }
                            } catch (e: Exception) {
                                Log.w("TiggPrinter", "Could not get device info: ${e.message}")
                                deviceInfo["error"] = e.message ?: "Unknown error"
                            }
                            
                            result.success(mapOf(
                                "success" to true,
                                "deviceType" to "tactilion",
                                "printerCount" to printerCount,
                                "currentPrinter" to currentPrinter?.toNormalString(),
                                "currentPrinterId" to (currentPrinter?.getId() ?: -1),
                                "serviceManagerAvailable" to true,
                                "printServiceAvailable" to printServiceAvailable,
                                "deviceInfo" to deviceInfo,
                                "printers" to printersList
                            ))
                        } catch (e: Exception) {
                            Log.e("TiggPrinter", "Error getting ServiceManager printer info: ${e.message}")
                            // Fall through to alternative reporting
                        }
                    }
                    
                    // If ServiceManager not available or failed, provide basic status
                    if (!serviceManagerAvailable) {
                        val printersList = mutableListOf<Map<String, Any>>()
                        
                        if (printServiceAvailable) {
                            printersList.add(mapOf(
                                "id" to 0,
                                "name" to "Tactilion Built-in Printer (PrintService)",
                                "status" to "available",
                                "source" to "PrintService"
                            ))
                        } else {
                            printersList.add(mapOf(
                                "id" to 0,
                                "name" to "Tactilion Printer (Permission Denied)",
                                "status" to "permission_error",
                                "source" to "none",
                                "error" to "SDK requires system-level permissions"
                            ))
                        }
                        
                        result.success(mapOf(
                            "success" to true,
                            "deviceType" to "tactilion",
                            "serviceManagerAvailable" to false,
                            "printServiceAvailable" to printServiceAvailable,
                            "printers" to printersList,
                            "permissionIssue" to !printServiceAvailable,
                            "message" to if (!printServiceAvailable) 
                                "Tactilion device detected but SDK requires system-level permissions" 
                                else "Using PrintService fallback"
                        ))
                    }
                }
                DeviceType.UNKNOWN -> {
                    result.success(mapOf(
                        "success" to false,
                        "deviceType" to "unknown",
                        "printers" to emptyList<Map<String, Any>>(),
                        "error" to "Device type not detected"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error in getAvailablePrinters: ${e.message}", e)
            result.error("PRINTER_INFO_ERROR", "Failed to get printer information: ${e.message}", null)
        }
    }

    private fun selectPrinter(call: MethodCall, result: MethodChannel.Result) {
        try {
            val printerId = call.argument<Int>("printerId") ?: 0
            Log.i("TiggPrinter", "selectPrinter called with ID: $printerId for device type: $detectedDeviceType")

            when (detectedDeviceType) {
                DeviceType.FEWAPOS -> {
                    // FewaPos typically has a single built-in printer
                    result.success(mapOf(
                        "success" to true,
                        "deviceType" to "fewapos",
                        "message" to "FewaPos uses built-in printer (ID selection not applicable)",
                        "selectedPrinterId" to 0
                    ))
                }
                DeviceType.TACTILION -> {
                    // Use Tactilion ServiceManager to select printer
                    if (isServiceManagerInitialized && printerBinder != null) {
                        try {
                            val success = printerBinder?.selectPosPrinter(printerId)
                            if (success == true) {
                                // Get the now-selected printer info
                                val currentPrinter = printerBinder?.getPrinterInfoForNow()
                                
                                result.success(mapOf(
                                    "success" to true,
                                    "deviceType" to "tactilion",
                                    "message" to "Printer selected successfully",
                                    "selectedPrinterId" to printerId,
                                    "currentPrinter" to currentPrinter?.toNormalString(),
                                    "currentPrinterName" to (currentPrinter?.getName() ?: "Unknown"),
                                    "currentPrinterMac" to (currentPrinter?.getMac() ?: "N/A"),
                                    "source" to "ServiceManager"
                                ))
                                Log.i("TiggPrinter", "Successfully selected Tactilion printer ID: $printerId")
                            } else {
                                result.success(mapOf(
                                    "success" to false,
                                    "deviceType" to "tactilion",
                                    "error" to "Failed to select printer ID: $printerId",
                                    "message" to "selectPosPrinter returned false"
                                ))
                                Log.w("TiggPrinter", "Failed to select Tactilion printer ID: $printerId - selectPosPrinter returned false")
                            }
                        } catch (e: Exception) {
                            Log.e("TiggPrinter", "Exception during printer selection: ${e.message}", e)
                            result.success(mapOf(
                                "success" to false,
                                "deviceType" to "tactilion",
                                "error" to "Exception during printer selection: ${e.message}",
                                "selectedPrinterId" to printerId
                            ))
                        }
                    } else {
                        // ServiceManager not available, but we can still track the intended selection
                        result.success(mapOf(
                            "success" to false,
                            "deviceType" to "tactilion",
                            "error" to "ServiceManager not initialized",
                            "message" to "Cannot select printer - ServiceManager not available due to permission restrictions",
                            "selectedPrinterId" to printerId,
                            "permissionIssue" to true
                        ))
                        Log.w("TiggPrinter", "Cannot select printer - ServiceManager not available")
                    }
                }
                DeviceType.UNKNOWN -> {
                    result.success(mapOf(
                        "success" to false,
                        "deviceType" to "unknown",
                        "error" to "Cannot select printer for unknown device type"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("TiggPrinter", "Error in selectPrinter: ${e.message}", e)
            result.error("PRINTER_SELECT_ERROR", "Failed to select printer: ${e.message}", null)
        }
    }
}

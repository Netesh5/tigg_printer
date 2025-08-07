import 'tigg_printer_platform_interface.dart';

export 'tigg_printer_platform_interface.dart'
    show PrintResult, TiggPrinterException;

class TiggPrinter {
  TiggPrinter._();

  static DateTime? _lastBindAttempt;
  static const Duration _bindCooldown = Duration(seconds: 5);

  /// Get the platform version
  static Future<String?> getPlatformVersion() {
    return TiggPrinterPlatform.instance.getPlatformVersion();
  }

  /// Bind to printer service with intelligent retry logic
  static Future<void> bindServiceWithRetry({int maxRetries = 3}) async {
    // Check cooldown to prevent excessive binding attempts
    if (_lastBindAttempt != null) {
      final timeSinceLastAttempt = DateTime.now().difference(_lastBindAttempt!);
      if (timeSinceLastAttempt < _bindCooldown) {
        final remainingCooldown = _bindCooldown - timeSinceLastAttempt;
        throw TiggPrinterException(
          'BIND_COOLDOWN',
          'Please wait ${remainingCooldown.inSeconds} seconds before attempting to bind again.',
        );
      }
    }

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        _lastBindAttempt = DateTime.now();
        await TiggPrinterPlatform.instance.bindService();

        // Verify connection after binding
        await Future.delayed(const Duration(milliseconds: 500));
        final isConnected = await isServiceConnected();

        if (isConnected) {
          return; // Success
        } else if (attempt < maxRetries) {
          // Wait before retry
          await Future.delayed(Duration(seconds: attempt * 2));
        }
      } catch (e) {
        if (attempt == maxRetries) {
          rethrow; // Final attempt failed
        }
        // Wait before retry
        await Future.delayed(Duration(seconds: attempt * 2));
      }
    }

    throw const TiggPrinterException(
      'BIND_FAILED_AFTER_RETRIES',
      'Failed to bind service after multiple attempts. TiggPrinter service may not be available.',
    );
  }

  /// Print a base64 encoded image
  static Future<PrintResult> printBase64Image({
    required String base64Image,
    int textSize = 24,
  }) async {
    // Check service connection before printing
    try {
      final isConnected = await isServiceConnected();
      if (!isConnected) {
        throw const TiggPrinterException(
          'SERVICE_NOT_CONNECTED',
          'Printer service is not connected. Use bindServiceWithRetry() to establish connection.',
        );
      }
    } catch (e) {
      if (e is TiggPrinterException) rethrow;
      throw TiggPrinterException(
        'SERVICE_CHECK_FAILED',
        'Failed to check service status: ${e.toString()}',
      );
    }

    return TiggPrinterPlatform.instance.printBase64Image(
      base64Image: base64Image,
      textSize: textSize,
    );
  }

  /// Print plain text
  static Future<PrintResult> printText({
    required String text,
    int textSize = 24,
    int paperWidth = 384,
  }) async {
    // Check service connection before printing
    try {
      final isConnected = await isServiceConnected();
      if (!isConnected) {
        throw const TiggPrinterException(
          'SERVICE_NOT_CONNECTED',
          'Printer service is not connected. Use bindServiceWithRetry() to establish connection.',
        );
      }
    } catch (e) {
      if (e is TiggPrinterException) rethrow;
      throw TiggPrinterException(
        'SERVICE_CHECK_FAILED',
        'Failed to check service status: ${e.toString()}',
      );
    }

    return TiggPrinterPlatform.instance.printText(
      text: text,
      textSize: textSize,
      paperWidth: paperWidth,
    );
  }

  /// Print raw byte data (for ESC/POS commands from esc_pos_utils_plus)
  /// This allows you to use existing ESC/POS command bytes directly
  ///
  /// [useDirectString] - if true, uses string method (may show default header)
  /// [useDirectString] - if false, uses bitmap method (avoids default header)
  /// [textSize] - only used when useDirectString=true
  static Future<PrintResult> printRawBytes({
    required List<int> bytes,
    bool useDirectString = false,
    int textSize = 0,
  }) async {
    // Check service connection before printing
    try {
      final isConnected = await isServiceConnected();
      if (!isConnected) {
        throw const TiggPrinterException(
          'SERVICE_NOT_CONNECTED',
          'Printer service is not connected. Use bindServiceWithRetry() to establish connection.',
        );
      }
    } catch (e) {
      if (e is TiggPrinterException) rethrow;
      throw TiggPrinterException(
        'SERVICE_CHECK_FAILED',
        'Failed to check service status: ${e.toString()}',
      );
    }

    return TiggPrinterPlatform.instance.printRawBytes(
      bytes: bytes,
      useDirectString: useDirectString,
      textSize: textSize,
    );
  }

  /// Check if the printer service is available
  static Future<bool> isPrinterAvailable() {
    return TiggPrinterPlatform.instance.isPrinterAvailable();
  }

  /// Bind to the printer service (legacy method - use bindServiceWithRetry instead)
  static Future<void> bindService() {
    return TiggPrinterPlatform.instance.bindService();
  }

  /// Check if the service is connected
  static Future<bool> isServiceConnected() {
    return TiggPrinterPlatform.instance.isServiceConnected();
  }

  /// Get detailed service diagnostics
  static Future<Map<String, dynamic>> getServiceDiagnostics() {
    return TiggPrinterPlatform.instance.getServiceDiagnostics();
  }

  /// Check system-level printer services
  static Future<Map<String, dynamic>> checkSystemServices() {
    return TiggPrinterPlatform.instance.checkSystemServices();
  }
}

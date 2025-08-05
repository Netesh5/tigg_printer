import 'tigg_printer_platform_interface.dart';

export 'tigg_printer_platform_interface.dart'
    show PrintResult, TiggPrinterException;

class TiggPrinter {
  TiggPrinter._();

  /// Get the platform version
  static Future<String?> getPlatformVersion() {
    return TiggPrinterPlatform.instance.getPlatformVersion();
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
          'Printer service is not connected. Please bind service first using bindService().',
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
  }) async {
    // Check service connection before printing
    try {
      final isConnected = await isServiceConnected();
      if (!isConnected) {
        throw const TiggPrinterException(
          'SERVICE_NOT_CONNECTED',
          'Printer service is not connected. Please bind service first using bindService().',
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
    );
  }

  /// Check if the printer service is available
  static Future<bool> isPrinterAvailable() {
    return TiggPrinterPlatform.instance.isPrinterAvailable();
  }

  /// Bind the printer service
  /// This should be called when the app starts or before using printer functions
  static Future<void> bindService() {
    return TiggPrinterPlatform.instance.bindService();
  }

  /// Check if the printer service is connected
  static Future<bool> isServiceConnected() {
    return TiggPrinterPlatform.instance.isServiceConnected();
  }
}

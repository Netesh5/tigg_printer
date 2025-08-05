import 'package:flutter/services.dart';
import 'package:tigg_printer/core/exception/custom_exception.dart';

/// Exception thrown when printer operations fail

/// Result of a print operation
class PrintResult {
  final bool success;
  final String message;

  const PrintResult({required this.success, required this.message});

  @override
  String toString() => 'PrintResult(success: $success, message: $message)';
}

class TiggPrinter {
  static const MethodChannel _channel = MethodChannel('tigg_printer');

  /// Print a base64 encoded image
  static Future<PrintResult> printBase64Image({
    required String base64Image,
    int textSize = 24,
  }) async {
    // Input validation
    if (base64Image.isEmpty) {
      throw const TiggPrinterException(
        'INVALID_INPUT',
        'Base64 image data cannot be empty',
      );
    }

    if (textSize <= 0 || textSize > 100) {
      throw const TiggPrinterException(
        'INVALID_INPUT',
        'Text size must be between 1 and 100',
      );
    }

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

    try {
      final result = await _channel.invokeMethod('printBase64Image', {
        'base64Image': base64Image,
        'textSize': textSize,
      });

      return PrintResult(
        success: true,
        message: result as String? ?? 'Print completed successfully',
      );
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Unknown platform error',
        e.details,
      );
    } catch (e) {
      throw TiggPrinterException(
        'UNKNOWN_ERROR',
        'Unexpected error: ${e.toString()}',
      );
    }
  }

  /// Print plain text
  static Future<PrintResult> printText({
    required String text,
    int textSize = 24,
  }) async {
    // Input validation
    if (text.isEmpty) {
      throw const TiggPrinterException('INVALID_INPUT', 'Text cannot be empty');
    }

    if (textSize <= 0 || textSize > 100) {
      throw const TiggPrinterException(
        'INVALID_INPUT',
        'Text size must be between 1 and 100',
      );
    }

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

    try {
      final result = await _channel.invokeMethod('printText', {
        'text': text,
        'textSize': textSize,
      });

      return PrintResult(
        success: true,
        message: result as String? ?? 'Text printed successfully',
      );
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Unknown platform error',
        e.details,
      );
    } catch (e) {
      throw TiggPrinterException(
        'UNKNOWN_ERROR',
        'Unexpected error: ${e.toString()}',
      );
    }
  }

  /// Check if the printer service is available
  static Future<bool> isPrinterAvailable() async {
    try {
      await _channel.invokeMethod('isPrinterAvailable');
      return true;
    } on PlatformException catch (e) {
      if (e.code == 'SERVICE_UNAVAILABLE') {
        return false;
      }
      rethrow;
    }
  }

  /// Bind the printer service
  /// This should be called when the app starts or before using printer functions
  static Future<void> bindService() async {
    try {
      await _channel.invokeMethod('bindService');
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Failed to bind printer service',
        e.details,
      );
    }
  }

  /// Check if the printer service is connected
  static Future<bool> isServiceConnected() async {
    try {
      final result = await _channel.invokeMethod('isServiceConnected');
      return result as bool;
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Failed to check service connection',
        e.details,
      );
    }
  }
}

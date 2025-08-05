import 'package:flutter/services.dart';

/// Exception thrown when printer operations fail
class TiggPrinterException implements Exception {
  final String code;
  final String message;
  final dynamic details;

  const TiggPrinterException(this.code, this.message, [this.details]);

  @override
  String toString() => 'TiggPrinterException($code): $message';
}

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
}

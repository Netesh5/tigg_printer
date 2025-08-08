import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'tigg_printer_platform_interface.dart';

/// An implementation of [TiggPrinterPlatform] that uses method channels.
class MethodChannelTiggPrinter extends TiggPrinterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('tigg_printer');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<PrintResult> printBase64Image({
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
      final result = await methodChannel.invokeMethod('printBase64Image', {
        'base64Image': base64Image,
        'textSize': textSize,
      });

      // Handle both Map and String responses
      if (result is Map) {
        return PrintResult(
          success: result['success'] as bool? ?? true,
          message:
              result['message'] as String? ?? 'Print completed successfully',
        );
      } else {
        return PrintResult(
          success: true,
          message: result as String? ?? 'Print completed successfully',
        );
      }
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

  @override
  Future<PrintResult> printText({
    required String text,
    int textSize = 24,
    int paperWidth = 384,
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

    if (paperWidth <= 0 || paperWidth > 1000) {
      throw const TiggPrinterException(
        'INVALID_INPUT',
        'Paper width must be between 1 and 1000 pixels',
      );
    }

    try {
      final result = await methodChannel.invokeMethod('printText', {
        'text': text,
        'textSize': textSize,
        'paperWidth': paperWidth,
      });

      // Handle both Map and String responses
      if (result is Map) {
        return PrintResult(
          success: result['success'] as bool? ?? true,
          message: result['message'] as String? ?? 'Text printed successfully',
        );
      } else {
        return PrintResult(
          success: true,
          message: result as String? ?? 'Text printed successfully',
        );
      }
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

  @override
  Future<PrintResult> printRawBytes({
    required List<int> bytes,
    bool useDirectString = false,
    int textSize = 0,
    int paperWidth = 384,
  }) async {
    // Input validation
    if (bytes.isEmpty) {
      throw const TiggPrinterException(
        'INVALID_INPUT',
        'Bytes cannot be empty',
      );
    }

    // Validate byte values (0-255)
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] < 0 || bytes[i] > 255) {
        throw TiggPrinterException(
          'INVALID_INPUT',
          'Invalid byte value at index $i: ${bytes[i]}. Bytes must be 0-255.',
        );
      }
    }

    try {
      final result = await methodChannel.invokeMethod('printRawBytes', {
        'bytes': bytes,
        'useDirectString': useDirectString,
        'textSize': textSize,
        'paperWidth': paperWidth,
      });

      // Handle both Map and String responses
      if (result is Map) {
        return PrintResult(
          success: result['success'] as bool? ?? true,
          message:
              result['message'] as String? ?? 'Raw bytes printed successfully',
        );
      } else {
        return PrintResult(
          success: true,
          message: result as String? ?? 'Raw bytes printed successfully',
        );
      }
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

  @override
  Future<bool> isPrinterAvailable() async {
    try {
      await methodChannel.invokeMethod('isPrinterAvailable');
      return true;
    } on PlatformException catch (e) {
      if (e.code == 'SERVICE_UNAVAILABLE') {
        return false;
      }
      rethrow;
    }
  }

  @override
  Future<void> bindService() async {
    try {
      await methodChannel.invokeMethod('bindService');
    } on PlatformException catch (e) {
      // Handle specific binding failures
      if (e.code == 'BIND_FAILED' &&
          e.message?.contains('connection failed') == true) {
        // Wait a moment before potentially allowing retry
        await Future.delayed(const Duration(seconds: 2));
      }
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Failed to bind printer service',
        e.details,
      );
    }
  }

  @override
  Future<bool> isServiceConnected() async {
    try {
      final result = await methodChannel.invokeMethod('isServiceConnected');
      return result as bool;
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Failed to check service connection',
        e.details,
      );
    }
  }

  @override
  Future<Map<String, dynamic>> getServiceDiagnostics() async {
    try {
      final result = await methodChannel.invokeMethod('getServiceDiagnostics');
      return Map<String, dynamic>.from(result as Map);
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Failed to get service diagnostics',
        e.details,
      );
    }
  }

  @override
  Future<Map<String, dynamic>> checkSystemServices() async {
    try {
      final result = await methodChannel.invokeMethod('checkSystemServices');
      return Map<String, dynamic>.from(result as Map);
    } on PlatformException catch (e) {
      throw TiggPrinterException(
        e.code,
        e.message ?? 'Failed to check system services',
        e.details,
      );
    }
  }
}

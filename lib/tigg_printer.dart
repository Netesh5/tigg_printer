import 'package:flutter/services.dart';

class TiggPrinter {
  static const MethodChannel _channel = MethodChannel('tigg_printer');

  /// Print a base64 encoded image
  static Future<String> printBase64Image({
    required String base64Image,
    int textSize = 24,
  }) async {
    final result = await _channel.invokeMethod('printBase64Image', {
      'base64Image': base64Image,
      'textSize': textSize,
    });
    return result as String;
  }

  /// Print plain text
  static Future<String> printText({
    required String text,
    int textSize = 24,
  }) async {
    final result = await _channel.invokeMethod('printText', {
      'text': text,
      'textSize': textSize,
    });
    return result as String;
  }
}

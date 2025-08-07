import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'tigg_printer_method_channel.dart';

/// The result of a print operation
class PrintResult {
  final bool success;
  final String message;

  const PrintResult({required this.success, required this.message});

  @override
  String toString() => 'PrintResult(success: $success, message: $message)';
}

/// Exception thrown when printer operations fail
class TiggPrinterException implements Exception {
  final String code;
  final String message;
  final dynamic details;

  const TiggPrinterException(this.code, this.message, [this.details]);

  @override
  String toString() => 'TiggPrinterException($code): $message';
}

abstract class TiggPrinterPlatform extends PlatformInterface {
  /// Constructs a TiggPrinterPlatform.
  TiggPrinterPlatform() : super(token: _token);

  static final Object _token = Object();

  static TiggPrinterPlatform _instance = MethodChannelTiggPrinter();

  /// The default instance of [TiggPrinterPlatform] to use.
  ///
  /// Defaults to [MethodChannelTiggPrinter].
  static TiggPrinterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [TiggPrinterPlatform] when
  /// they register themselves.
  static set instance(TiggPrinterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Get the platform version (for debugging/info purposes)
  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  /// Print a base64 encoded image
  Future<PrintResult> printBase64Image({
    required String base64Image,
    int textSize = 24,
  }) {
    throw UnimplementedError('printBase64Image() has not been implemented.');
  }

  /// Print plain text
  Future<PrintResult> printText({
    required String text,
    int textSize = 24,
    int paperWidth = 384,
  }) {
    throw UnimplementedError('printText() has not been implemented.');
  }

  /// Print raw byte data (for ESC/POS commands)
  Future<PrintResult> printRawBytes({
    required List<int> bytes,
    bool useDirectString = false,
    int textSize = 0,
  }) {
    throw UnimplementedError('printRawBytes() has not been implemented.');
  }

  /// Check if the printer service is available
  Future<bool> isPrinterAvailable() {
    throw UnimplementedError('isPrinterAvailable() has not been implemented.');
  }

  /// Bind the printer service
  Future<void> bindService() {
    throw UnimplementedError('bindService() has not been implemented.');
  }

  /// Check if the printer service is connected
  Future<bool> isServiceConnected() {
    throw UnimplementedError('isServiceConnected() has not been implemented.');
  }

  /// Get detailed service diagnostics for debugging
  Future<Map<String, dynamic>> getServiceDiagnostics() {
    throw UnimplementedError(
      'getServiceDiagnostics() has not been implemented.',
    );
  }

  /// Check system services and packages related to TiggPrinter
  Future<Map<String, dynamic>> checkSystemServices() {
    throw UnimplementedError('checkSystemServices() has not been implemented.');
  }
}

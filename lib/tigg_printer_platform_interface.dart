import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'tigg_printer_method_channel.dart';

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

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}

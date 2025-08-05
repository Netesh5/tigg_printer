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
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}

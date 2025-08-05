import 'package:flutter_test/flutter_test.dart';
import 'package:tigg_printer/tigg_printer.dart';
import 'package:tigg_printer/tigg_printer_platform_interface.dart';
import 'package:tigg_printer/tigg_printer_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockTiggPrinterPlatform
    with MockPlatformInterfaceMixin
    implements TiggPrinterPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final TiggPrinterPlatform initialPlatform = TiggPrinterPlatform.instance;

  test('$MethodChannelTiggPrinter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelTiggPrinter>());
  });

  test('getPlatformVersion', () async {
    TiggPrinter tiggPrinterPlugin = TiggPrinter();
    MockTiggPrinterPlatform fakePlatform = MockTiggPrinterPlatform();
    TiggPrinterPlatform.instance = fakePlatform;

    expect(await tiggPrinterPlugin.getPlatformVersion(), '42');
  });
}

import 'dart:developer';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:tigg_printer/tigg_printer.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _printStatus = 'Ready to print';
  bool _isPrinting = false;
  bool _isServiceConnected = false;

  final String _exampleBase64Image =
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

  @override
  void initState() {
    super.initState();
    _checkServiceStatus();
  }

  Future<void> _checkServiceStatus() async {
    try {
      final isConnected = await TiggPrinter.isServiceConnected();
      setState(() {
        _isServiceConnected = isConnected;
        _printStatus = isConnected
            ? 'Service connected ✓'
            : 'Service not connected. Please bind service.';
      });
    } catch (e) {
      setState(() {
        _isServiceConnected = false;
        _printStatus = 'Service check failed: $e';
      });
    }
  }

  Future<void> _bindService() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Binding service...';
    });

    try {
      await TiggPrinter.bindService();
      await Future.delayed(const Duration(seconds: 1));
      await _checkServiceStatus();
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Bind failed (${e.code}): ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Bind error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  Future<void> _bindServiceWithRetry() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Smart binding with retry...';
    });

    try {
      await TiggPrinter.bindServiceWithRetry(maxRetries: 3);
      setState(() {
        _printStatus = 'Service bound successfully with retry logic!';
      });
      await _checkServiceStatus();
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Smart bind failed (${e.code}): ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Smart bind error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  // Future<void> _getDiagnostics() async {
  //   setState(() {
  //     _isPrinting = true;
  //     _printStatus = 'Getting diagnostics...';
  //   });

  //   try {
  //     final diagnostics = await TiggPrinter.getServiceDiagnostics();
  //     final buffer = StringBuffer('Service Diagnostics:\n');
  //     diagnostics.forEach((key, value) {
  //       buffer.writeln('  $key: $value');
  //       log(diagnostics.toString());
  //     });
  //     setState(() {
  //       _printStatus = buffer.toString();
  //     });
  //   } on TiggPrinterException catch (e) {
  //     setState(() {
  //       _printStatus = 'Diagnostics failed (${e.code}): ${e.message}';
  //     });
  //   } catch (e) {
  //     setState(() {
  //       _printStatus = 'Diagnostics error: $e';
  //     });
  //   } finally {
  //     setState(() {
  //       _isPrinting = false;
  //     });
  //   }
  // }

  // Future<void> _checkSystemServices() async {
  //   setState(() {
  //     _isPrinting = true;
  //     _printStatus = 'Checking system services...';
  //   });

  //   try {
  //     final systemInfo = await TiggPrinter.checkSystemServices();
  //     final buffer = StringBuffer('System Services:\n');
  //     systemInfo.forEach((key, value) {
  //       if (value is List) {
  //         buffer.writeln('  $key: ${value.length} items');
  //         for (var item in value) {
  //           if (item is Map) {
  //             buffer.writeln(
  //               '    - ${item['packageName'] ?? item['className'] ?? item}',
  //             );
  //           } else {
  //             buffer.writeln('    - $item');
  //           }
  //         }
  //       } else {
  //         buffer.writeln('  $key: $value');
  //       }
  //     });
  //     setState(() {
  //       _printStatus = buffer.toString();
  //     });
  //   } on TiggPrinterException catch (e) {
  //     setState(() {
  //       _printStatus = 'System check failed (${e.code}): ${e.message}';
  //     });
  //   } catch (e) {
  //     setState(() {
  //       _printStatus = 'System check error: $e';
  //     });
  //   } finally {
  //     setState(() {
  //       _isPrinting = false;
  //     });
  //   }
  // }

  Future<void> _printImage() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Printing image...';
    });

    try {
      final result = await TiggPrinter.printBase64Image(
        base64Image: _exampleBase64Image,
        textSize: 24,
      );
      setState(() {
        _printStatus = 'Success: ${result.message}';
      });
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Printer Error (${e.code}): ${e.message}';
      });
    } on PlatformException catch (e) {
      setState(() {
        _printStatus = 'Platform Error: ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Unexpected error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  Future<void> _printText() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Printing text...';
    });

    try {
      final result = await TiggPrinter.printText(
        text: 'Hello from Tigg Printer!\nThis is a test print.',
        textSize: 24,
      );
      setState(() {
        _printStatus = 'Success: ${result.message}';
      });
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Printer Error (${e.code}): ${e.message}';
      });
    } on PlatformException catch (e) {
      setState(() {
        _printStatus = 'Platform Error: ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Unexpected error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  Future<void> _checkPrinterAvailability() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Checking printer availability...';
    });

    try {
      final isAvailable = await TiggPrinter.isPrinterAvailable();
      setState(() {
        _printStatus = isAvailable
            ? 'Printer is available ✓'
            : 'Printer not available ✗';
      });
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Check failed (${e.code}): ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Check error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Tigg Printer Example')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Status: $_printStatus',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 18),
              ),
              const SizedBox(height: 10),
              // Service connection indicator
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: _isServiceConnected
                      ? Colors.green.shade100
                      : Colors.red.shade100,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _isServiceConnected ? Colors.green : Colors.red,
                    width: 1,
                  ),
                ),
                child: Text(
                  _isServiceConnected
                      ? 'Service Connected ✓'
                      : 'Service Disconnected ✗',
                  style: TextStyle(
                    color: _isServiceConnected
                        ? Colors.green.shade800
                        : Colors.red.shade800,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(height: 20),
              // Service management buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  ElevatedButton(
                    onPressed: _isPrinting ? null : _bindService,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.blue,
                    ),
                    child: const Text('Bind Service'),
                  ),
                  ElevatedButton(
                    onPressed: _isPrinting ? null : _checkServiceStatus,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.orange,
                    ),
                    child: const Text('Check Status'),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              // Smart retry button
              ElevatedButton(
                onPressed: _isPrinting ? null : _bindServiceWithRetry,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.green,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 12,
                  ),
                ),
                child: const Text(
                  '🔄 Smart Bind with Retry',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(height: 10),
              // ElevatedButton(
              //   onPressed: _isPrinting ? null : _getDiagnostics,
              //   style: ElevatedButton.styleFrom(backgroundColor: Colors.purple),
              //   child: const Text('Get Diagnostics'),
              // ),
              // const SizedBox(height: 10),
              // ElevatedButton(
              //   onPressed: _isPrinting ? null : _checkSystemServices,
              //   style: ElevatedButton.styleFrom(backgroundColor: Colors.teal),
              //   child: const Text('Check System Services'),
              // ),
              // const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _isPrinting ? null : _checkPrinterAvailability,
                style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                child: const Text('Check Printer Availability'),
              ),
              const SizedBox(height: 10),
              ElevatedButton(
                onPressed: _isPrinting ? null : _printImage,
                child: _isPrinting
                    ? const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                          SizedBox(width: 10),
                          Text('Printing...'),
                        ],
                      )
                    : const Text('Print Test Image'),
              ),
              const SizedBox(height: 10),
              ElevatedButton(
                onPressed: _isPrinting ? null : _printText,
                child: const Text('Print Test Text'),
              ),
              const SizedBox(height: 20),
              const Text(
                'This will print a test image or text using the Tigg Printer',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, color: Colors.grey),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

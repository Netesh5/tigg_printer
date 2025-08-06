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
  double _textSize = 24.0;
  int _paperWidth = 384; // Default to 58mm paper (384px)

  final String _exampleBase64Image =
      '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=';

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
      // Add timeout to prevent hanging
      final result =
          await TiggPrinter.printBase64Image(
            base64Image: _exampleBase64Image,
            textSize: _textSize.round(),
          ).timeout(
            const Duration(seconds: 30),
            onTimeout: () {
              throw TimeoutException(
                'Print operation timed out after 30 seconds',
              );
            },
          );
      setState(() {
        _printStatus = 'Success: ${result.message}';
      });
    } on TimeoutException catch (e) {
      setState(() {
        _printStatus = 'Timeout Error: ${e.message}';
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
    print('_printText called - isPrinting: $_isPrinting');

    setState(() {
      _isPrinting = true;
      _printStatus = 'Printing text...';
    });

    try {
      print('Starting text print operation...');

      // Try using a more comprehensive test text
      final result = await TiggPrinter.printText(
        text:
            'Hello World!\nThis is a test print with multiple lines.\n\nTesting word wrapping with a very long sentence that should wrap to multiple lines automatically.',
        textSize: _textSize.round(),
        paperWidth: _paperWidth,
      );
      print('Text print result: ${result.success}');
      setState(() {
        _printStatus = 'Success: ${result.message}';
      });
    } on TimeoutException catch (e) {
      print('TimeoutException: ${e.message}');
      setState(() {
        _printStatus = 'Timeout Error: ${e.message}';
      });
    } on TiggPrinterException catch (e) {
      print('TiggPrinterException: (${e.code}) ${e.message}');
      setState(() {
        _printStatus = 'Printer Error (${e.code}): ${e.message}';
      });
    } on PlatformException catch (e) {
      print('PlatformException: ${e.message}');
      setState(() {
        _printStatus = 'Platform Error: ${e.message}';
      });
    } catch (e) {
      print('Unexpected error: $e');
      setState(() {
        _printStatus = 'Unexpected error: $e';
      });
    } finally {
      print('Text print operation finished, setting isPrinting to false');
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

  void _resetState() {
    setState(() {
      _isPrinting = false;
      _printStatus = 'Ready to print';
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Tigg Printer Example')),
        body: Center(
          child: SingleChildScrollView(
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
                // Show reset button when printing is stuck
                if (_isPrinting)
                  ElevatedButton(
                    onPressed: _resetState,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red,
                    ),
                    child: const Text(
                      'Cancel/Reset',
                      style: TextStyle(color: Colors.white),
                    ),
                  ),
                ElevatedButton(
                  onPressed: _isPrinting ? null : _checkPrinterAvailability,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                  ),
                  child: const Text('Check Printer Availability'),
                ),
                const SizedBox(height: 20),
                // Text Size Control
                Card(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Text Size: ${_textSize.round()}px',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Slider(
                          value: _textSize,
                          min: 12.0,
                          max: 48.0,
                          divisions: 36,
                          onChanged: (value) {
                            setState(() {
                              _textSize = value;
                            });
                          },
                        ),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            TextButton(
                              onPressed: () => setState(() => _textSize = 12.0),
                              child: const Text('Small (12px)'),
                            ),
                            TextButton(
                              onPressed: () => setState(() => _textSize = 24.0),
                              child: const Text('Medium (24px)'),
                            ),
                            TextButton(
                              onPressed: () => setState(() => _textSize = 36.0),
                              child: const Text('Large (36px)'),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                // Paper Width Control
                Card(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Paper Width: ${_paperWidth}px',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 10),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            ElevatedButton(
                              onPressed: () =>
                                  setState(() => _paperWidth = 384),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: _paperWidth == 384
                                    ? Colors.blue
                                    : Colors.grey.shade300,
                              ),
                              child: Text(
                                '58mm\n(384px)',
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: _paperWidth == 384
                                      ? Colors.white
                                      : Colors.black,
                                ),
                              ),
                            ),
                            ElevatedButton(
                              onPressed: () =>
                                  setState(() => _paperWidth = 576),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: _paperWidth == 576
                                    ? Colors.blue
                                    : Colors.grey.shade300,
                              ),
                              child: Text(
                                '80mm\n(576px)',
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: _paperWidth == 576
                                      ? Colors.white
                                      : Colors.black,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            const Text('Custom: '),
                            Expanded(
                              child: TextFormField(
                                initialValue: _paperWidth.toString(),
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(
                                  hintText: 'Width in pixels',
                                  border: OutlineInputBorder(),
                                  contentPadding: EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 4,
                                  ),
                                ),
                                onChanged: (value) {
                                  final width = int.tryParse(value);
                                  if (width != null &&
                                      width > 0 &&
                                      width <= 1000) {
                                    setState(() {
                                      _paperWidth = width;
                                    });
                                  }
                                },
                              ),
                            ),
                            const Text(' px'),
                          ],
                        ),
                      ],
                    ),
                  ),
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
                  onPressed: _isPrinting
                      ? null
                      : () {
                          print('Print Text button tapped');
                          _printText();
                        },
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
      ),
    );
  }
}

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
      final result = await TiggPrinter.printBase64Image(
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

  Future<void> _printRawBytes() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Printing raw ESC/POS bytes (bitmap method)...';
    });

    try {
      // Example ESC/POS command bytes (preserves all formatting commands)
      final List<int> escPosBytes = [
        0x1B, 0x40, // ESC @ - Initialize printer

        // Center aligned title
        0x1B, 0x61, 0x01, // ESC a 1 - Center align
        0x1D, 0x21, 0x11, // GS ! 17 - Double height and width
        // Text: "Bitmap Method"
        0x42, 0x69, 0x74, 0x6D, 0x61, 0x70, 0x20, 0x4D, 0x65, 0x74, 0x68, 0x6F,
        0x64,
        0x0A, 0x0A, // Line feeds

        // Reset to normal and left align
        0x1B, 0x61, 0x00, // ESC a 0 - Left align
        0x1D, 0x21, 0x00, // GS ! 0 - Normal size

        // Bold text
        0x1B, 0x45, 0x01, // ESC E 1 - Bold ON
        // Text: "No Default Header"
        0x4E, 0x6F, 0x20, 0x44, 0x65, 0x66, 0x61, 0x75, 0x6C, 0x74, 0x20, 0x48,
        0x65, 0x61, 0x64, 0x65, 0x72,
        0x0A, // Line feed
        0x1B, 0x45, 0x00, // ESC E 0 - Bold OFF

        // Right aligned text
        0x1B, 0x61, 0x02, // ESC a 2 - Right align
        // Text: "Right Aligned"
        0x52, 0x69, 0x67, 0x68, 0x74, 0x20, 0x41, 0x6C, 0x69, 0x67, 0x6E, 0x65,
        0x64,
        0x0A, // Line feed

        // Back to left align
        0x1B, 0x61, 0x00, // ESC a 0 - Left align

        // Horizontal line (dashes)
        0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D,
        0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D,
        0x0A, 0x0A, 0x0A, // Line feeds for spacing
      ];

      // Use bitmap method (should avoid default header)
      final result = await TiggPrinter.printRawBytes(
        bytes: escPosBytes,
        useDirectString: false, // Use bitmap method to avoid header
        paperWidth: _paperWidth, // Use configurable paper width
      );

      setState(() {
        _printStatus =
            'Success: ${result.message}\n\n✅ Bitmap method - should avoid default header!\n🎯 All ESC/POS formatting preserved!';
      });
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Printer Error (${e.code}): ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Raw bytes error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  Future<void> _printRawBytesWithString() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Printing raw ESC/POS bytes (string method)...';
    });

    try {
      // Same ESC/POS commands but using string method
      final List<int> escPosBytes = [
        0x1B, 0x40, // ESC @ - Initialize printer
        0x1B, 0x61, 0x01, // ESC a 1 - Center align
        0x1D, 0x21, 0x11, // GS ! 17 - Double height and width
        // Text: "String Method"
        0x53, 0x74, 0x72, 0x69, 0x6E, 0x67, 0x20, 0x4D, 0x65, 0x74, 0x68, 0x6F,
        0x64,
        0x0A, 0x0A, // Line feeds
        0x1B, 0x61, 0x00, // ESC a 0 - Left align
        0x1D, 0x21, 0x00, // GS ! 0 - Normal size
        // Text: "May show header"
        0x4D, 0x61, 0x79, 0x20, 0x73, 0x68, 0x6F, 0x77, 0x20, 0x68, 0x65, 0x61,
        0x64, 0x65, 0x72,
        0x0A, 0x0A, 0x0A, // Line feeds for spacing
      ];

      // Use string method (may show default header)
      final result = await TiggPrinter.printRawBytes(
        bytes: escPosBytes,
        useDirectString: true, // Use string method
        textSize: 0, // Minimal text size
        paperWidth: _paperWidth, // Use configurable paper width
      );

      setState(() {
        _printStatus =
            'Success: ${result.message}\n\n⚠️ String method - may show default header\n🎯 ESC/POS formatting still preserved!';
      });
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Printer Error (${e.code}): ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Raw bytes error: $e';
      });
    } finally {
      setState(() {
        _isPrinting = false;
      });
    }
  }

  // ✅ CORRECT WAY: Handle ESC/POS data from esc_pos_utils_plus
  Future<void> _printEscPosFromPackage() async {
    setState(() {
      _isPrinting = true;
      _printStatus = 'Printing ESC/POS from esc_pos_utils_plus...';
    });

    try {
      await TiggPrinter.bindService();

      // Example: If you get List<int> from esc_pos_utils_plus
      List<int> escPosBytes = [
        // Your ESC/POS data from esc_pos_utils_plus package
        // Example data with proper formatting
        0x1B, 0x40, // ESC @ - Initialize printer
        0x1B, 0x61, 0x01, // ESC a 1 - Center align
        0x1D, 0x21, 0x11, // GS ! 17 - Double height and width
        // Text: "RECEIPT"
        0x52, 0x45, 0x43, 0x45, 0x49, 0x50, 0x54,
        0x0A, 0x0A, // Line feeds
        0x1B, 0x61, 0x00, // ESC a 0 - Left align
        0x1D, 0x21, 0x00, // GS ! 0 - Normal size
        // Text: "Item: Coffee      $5.00"
        0x49, 0x74, 0x65, 0x6D, 0x3A, 0x20, 0x43, 0x6F, 0x66, 0x66, 0x65, 0x65,
        0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x24, 0x35, 0x2E, 0x30, 0x30,
        0x0A, // Line feed
        // Text: "Total:           $5.00"
        0x54, 0x6F, 0x74, 0x61, 0x6C, 0x3A, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
        0x20, 0x20, 0x20, 0x20, 0x20, 0x24, 0x35, 0x2E, 0x30, 0x30,
        0x0A, 0x0A, 0x0A, // Line feeds for spacing
      ];

      // ✅ Method 1: Bitmap method (no header, all formatting preserved)
      final result = await TiggPrinter.printRawBytes(
        bytes: escPosBytes,
        useDirectString: false, // Use bitmap method
        paperWidth: _paperWidth,
      );

      setState(() {
        _printStatus =
            'Success: ${result.message}\n\n✅ ESC/POS data printed correctly!\n🎯 All formatting and alignment preserved!';
      });
    } on TiggPrinterException catch (e) {
      setState(() {
        _printStatus = 'Printer Error (${e.code}): ${e.message}';
      });
    } catch (e) {
      setState(() {
        _printStatus = 'Error: $e';
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
        _printStatus =
            isAvailable ? 'Printer is available ✓' : 'Printer not available ✗';
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
                const SizedBox(height: 10),
                ElevatedButton(
                  onPressed: _isPrinting
                      ? null
                      : () {
                          print('Print Raw Bytes button tapped');
                          _printRawBytes();
                        },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.purple,
                  ),
                  child: const Text(
                    'Print ESC/POS (Bitmap Method)',
                    style: TextStyle(color: Colors.white),
                  ),
                ),
                const SizedBox(height: 5),
                ElevatedButton(
                  onPressed: _isPrinting
                      ? null
                      : () {
                          print('Print Raw Bytes String button tapped');
                          _printRawBytesWithString();
                        },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.orange,
                  ),
                  child: const Text(
                    'Print ESC/POS (String Method)',
                    style: TextStyle(color: Colors.white),
                  ),
                ),
                const SizedBox(height: 5),
                ElevatedButton(
                  onPressed: _isPrinting
                      ? null
                      : () {
                          print('Print ESC/POS Package Data button tapped');
                          _printEscPosFromPackage();
                        },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                  ),
                  child: const Text(
                    '✅ Correct ESC/POS Method',
                    style: TextStyle(color: Colors.white),
                  ),
                ),
                const SizedBox(height: 20),
                const Text(
                  'This will print test content using the Tigg Printer:\n• Test Image: Base64 encoded image\n• Test Text: Formatted text with word wrapping\n• ESC/POS Raw Bytes: Direct printer commands (compatible with esc_pos_utils_plus)',
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

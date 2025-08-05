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

  final String _exampleBase64Image =
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

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
        _printStatus = result;
      });
    } on PlatformException catch (e) {
      setState(() {
        _printStatus = 'Print failed: ${e.message}';
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
        _printStatus = result;
      });
    } on PlatformException catch (e) {
      setState(() {
        _printStatus = 'Print failed: ${e.message}';
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
              const SizedBox(height: 20),
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

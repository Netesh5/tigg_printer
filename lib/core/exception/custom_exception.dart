class TiggPrinterException implements Exception {
  final String code;
  final String message;
  final dynamic details;

  const TiggPrinterException(this.code, this.message, [this.details]);

  @override
  String toString() => 'TiggPrinterException($code): $message';
}

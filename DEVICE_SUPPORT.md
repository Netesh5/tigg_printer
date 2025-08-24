# Device Support Documentation

## Overview

The Tigg Printer plugin now supports multiple device types with automatic device detection and conditional printing logic. This ensures that the plugin works seamlessly across different POS devices without requiring manual configuration.

## Supported Devices

### 1. FewaPos Devices
- **Detection**: Based on manufacturer info and AppService availability
- **SDK**: Uses existing FewaPos SDK (`printer_sdk.aar`)
- **Service**: AppService with IPaymentCallback
- **Features**: Full compatibility with existing implementation

### 2. Tactilion Devices
- **Detection**: Based on manufacturer info and Tactilion SDK availability
- **SDK**: Uses Tactilion SDK (`tactilion_sdk.aar`)
- **Service**: PrintService with OnPrinterListener
- **Features**: Native Tactilion printing support

### 3. Unknown Devices
- **Fallback**: Attempts FewaPos SDK as default
- **Detection**: When neither device type can be identified
- **Behavior**: May have limited functionality

## Device Detection Logic

The plugin automatically detects the device type during initialization using the following methods:

1. **Manufacturer/Model Detection**: Checks `Build.MANUFACTURER`, `Build.MODEL`, and `Build.PRODUCT`
2. **SDK Availability**: Attempts to instantiate SDK-specific classes
3. **Service Initialization**: Tests which services can be successfully initialized

```kotlin
private fun detectDeviceType(): DeviceType {
    // Check for Tactilion device patterns
    if (manufacturer.contains("tactilion") || model.contains("basewin")) {
        return DeviceType.TACTILION
    }
    
    // Try Tactilion SDK instantiation
    try {
        deviceInfoBinder = DeviceInfoBinder(context)
        return DeviceType.TACTILION
    } catch (e: Exception) { /* Continue */ }
    
    // Try FewaPos SDK
    try {
        AppService.me().init(context)
        return DeviceType.FEWAPOS
    } catch (e: Exception) { /* Continue */ }
    
    return DeviceType.UNKNOWN
}
```

## API Usage

### Getting Device Type

```dart
DeviceType deviceType = await TiggPrinter.getDeviceType();

switch (deviceType) {
  case DeviceType.fewaPos:
    print("Running on FewaPos device");
    break;
  case DeviceType.tactilion:
    print("Running on Tactilion device");
    break;
  case DeviceType.unknown:
    print("Unknown device type");
    break;
}
```

### Device-Aware Printing

All existing print methods automatically use the appropriate SDK based on detected device type:

```dart
// This will use FewaPos or Tactilion SDK automatically
await TiggPrinter.printText(
  text: "Hello World",
  textSize: 24,
);

await TiggPrinter.printBase64Image(
  base64Image: base64ImageString,
  textSize: 24,
);

await TiggPrinter.printRawBytes(
  bytes: escPosBytes,
  useDirectString: false,
);
```

### Service Binding

Service binding is also device-aware:

```dart
// Automatically binds the correct service based on device type
await TiggPrinter.bindServiceWithRetry();

// Check connection status
bool isConnected = await TiggPrinter.isServiceConnected();
```

## Implementation Details

### FewaPos Implementation
- Uses existing `AppService.me()` with `IPaymentCallback`
- Maintains backward compatibility
- Supports all original printing methods
- Service binding required before printing

### Tactilion Implementation
- Uses `PrintService.getInstance()` with `OnPrinterListener`
- Text printing: `addTextToCurCache()` + `print()`
- Image printing: `addBmpToCurCache()` + `print()`
- Raw bytes: Converted to bitmap for compatibility
- No separate service binding required

### Error Handling

Device-specific errors are properly handled and reported:

```dart
try {
  await TiggPrinter.printText(text: "Test");
} catch (e) {
  if (e.toString().contains("Tactilion")) {
    // Handle Tactilion-specific error
  } else if (e.toString().contains("FewaPos")) {
    // Handle FewaPos-specific error
  }
}
```

## Conditional Logic Examples

### Service Availability Check

```kotlin
when (detectedDeviceType) {
    DeviceType.FEWAPOS -> {
        val isServiceConnected = AppService.me()?.isServiceConnected() ?: false
        result.success(isServiceConnected)
    }
    DeviceType.TACTILION -> {
        val isConnected = tactilionPrintService != null
        result.success(isConnected)
    }
    DeviceType.UNKNOWN -> {
        result.success(false)
    }
}
```

### Device-Specific Printing

```kotlin
when (detectedDeviceType) {
    DeviceType.FEWAPOS -> {
        printTextFewaPos(text, textSize, paperWidth, result)
    }
    DeviceType.TACTILION -> {
        printTextTactilion(text, textSize, paperWidth, result)
    }
    DeviceType.UNKNOWN -> {
        result.error("UNKNOWN_DEVICE", "Cannot print - unknown device type", null)
    }
}
```

## Testing

The example app includes device type detection and displays:
- Current device type with appropriate icons and colors
- Device-specific information
- Connection status for the detected device type

Run the example app to test device detection and printing on your specific device.

## Migration Guide

### For Existing Users
- **No code changes required** - existing code will continue to work
- Device detection happens automatically
- All existing methods remain backward compatible

### For New Implementations
- Use `getDeviceType()` if you need device-specific logic
- All print methods work identically regardless of device type
- Device type information can be used for UI customization

## Troubleshooting

### Device Not Detected Correctly
1. Check device manufacturer/model information
2. Verify SDK AAR files are properly included
3. Check logs for device detection process

### Printing Issues
1. Ensure correct service is bound for device type
2. Check device-specific error messages
3. Verify SDK permissions in AndroidManifest.xml

### Unknown Device Type
1. Plugin will attempt FewaPos SDK as fallback
2. May have limited functionality
3. Contact support for device-specific integration

## Future Enhancements

- Additional device type support
- Custom device detection rules
- Enhanced error reporting
- Performance optimizations

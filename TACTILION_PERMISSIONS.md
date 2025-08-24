# Tactilion Device Permissions

## Required Permissions for Tactilion SDK

When your app is running on a Tactilion device, the following permissions must be added to your `android/app/src/main/AndroidManifest.xml` file:

### Standard Android Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.NFC" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.QUICKBOOT_POWERON" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### Tactilion/POS Specific Permissions
```xml
<uses-permission android:name="com.pos.permission.SECURITY" />
<uses-permission android:name="com.pos.permission.ACCESSORY_DATETIME" />
<uses-permission android:name="com.pos.permission.ACCESSORY_LED" />
<uses-permission android:name="com.pos.permission.ACCESSORY_BEEP" />
<uses-permission android:name="com.pos.permission.CARD_READER_ICC" />
<uses-permission android:name="com.pos.permission.CARD_READER_PICC" />
<uses-permission android:name="com.pos.permission.CARD_READER_MAG" />
<uses-permission android:name="com.pos.permission.COMMUNICATION" />
<uses-permission android:name="com.pos.permission.PRINTER" />
<uses-permission android:name="com.pos.permission.ACCESSORY_RFREGISTER" />
<uses-permission android:name="com.pos.permission.ACCESSORY_SCANNER_LED" />
<uses-permission android:name="com.pos.permission.EMVCORE" />
```

### Required Features
```xml
<uses-feature
    android:name="android.hardware.nfc"
    android:required="false" />
```

### Required Libraries
Add these inside the `<application>` tag:

```xml
<uses-library
    android:name="com.odm"
    android:required="false" />
<uses-library
    android:name="com.pos.sdk"
    android:required="false" />
```

## Error Messages

If you see errors like:
- `java.lang.SecurityException: Permission denied, requires com.pos.permission.SECURITY permission`

This means the required permissions are missing from your AndroidManifest.xml file.

## Complete Example

Here's a complete example of AndroidManifest.xml with all required permissions:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Standard Android Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.NFC" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.QUICKBOOT_POWERON" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    
    <!-- Tactilion/POS Specific Permissions -->
    <uses-permission android:name="com.pos.permission.SECURITY" />
    <uses-permission android:name="com.pos.permission.ACCESSORY_DATETIME" />
    <uses-permission android:name="com.pos.permission.ACCESSORY_LED" />
    <uses-permission android:name="com.pos.permission.ACCESSORY_BEEP" />
    <uses-permission android:name="com.pos.permission.CARD_READER_ICC" />
    <uses-permission android:name="com.pos.permission.CARD_READER_PICC" />
    <uses-permission android:name="com.pos.permission.CARD_READER_MAG" />
    <uses-permission android:name="com.pos.permission.COMMUNICATION" />
    <uses-permission android:name="com.pos.permission.PRINTER" />
    <uses-permission android:name="com.pos.permission.ACCESSORY_RFREGISTER" />
    <uses-permission android:name="com.pos.permission.ACCESSORY_SCANNER_LED" />
    <uses-permission android:name="com.pos.permission.EMVCORE" />

    <uses-feature
        android:name="android.hardware.nfc"
        android:required="false" />

    <application
        android:label="your_app_name"
        android:name="${applicationName}"
        android:icon="@mipmap/ic_launcher">
        
        <!-- Your activities here -->
        
        <!-- Required libraries for Tactilion SDK -->
        <uses-library
            android:name="com.odm"
            android:required="false" />
        <uses-library
            android:name="com.pos.sdk"
            android:required="false" />
    </application>
</manifest>
```

## Notes

- These permissions are only required when running on Tactilion devices
- The plugin will automatically detect the device type and only use Tactilion SDK on compatible devices
- FewaPos devices don't require these additional permissions
- Some permissions may require runtime permission requests in newer Android versions

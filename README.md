# Outdoor Mode

An LSPosed module that enables the **Outdoor brightness mode** toggle in Samsung Settings, currently tested on One UI 8.5.

## What it does

Forces `SecOutDoorModePreferenceController.isAvailable()` to return `true`, making the Outdoor mode brightness option visible in **Settings → Display** on Samsung devices that don't show it by default.

## Requirements

- Android device with Samsung One UI
- [LSPosed](https://github.com/LSPosed/LSPosed) (Zygisk)
- Root access

## Installation

1. Download and install the APK
2. Open **LSPosed** → **Modules**
3. Enable **Outdoor Brightness**
4. Set scope to **com.android.settings**
5. Reboot or force-stop Settings

## How to build

```bash
# Requires Android SDK build-tools and JDK
BUILD_TOOLS="$ANDROID_HOME/build-tools/37.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-37.0/android.jar"

# Compile Java
javac -source 1.8 -target 1.8 -cp "$ANDROID_JAR:XposedBridge.jar" \
    -d build/classes src/com/artisan/outdoormode/OutdoorHook.java

# Create dex
$BUILD_TOOLS/d8 --lib "$ANDROID_JAR" --min-api 24 \
    --output build/ build/classes/com/artisan/outdoormode/OutdoorHook*.class

# Package APK
$BUILD_TOOLS/aapt package -f -M AndroidManifest.xml -I $ANDROID_JAR \
    -S res -A assets -F build/module.unsigned.apk

# Add dex and sign
cd build && unzip -o module.unsigned.apk -d extracted
cp classes.dex extracted/
cd extracted && zip -0 -r ../module.withdex.apk .
cd .. && $BUILD_TOOLS/zipalign -f -p 4 module.withdex.apk module.aligned.apk
$BUILD_TOOLS/apksigner sign --ks debug.keystore \
    --out outdoor-brightness.apk module.aligned.apk
```

Thank you @salvogiangri for giving me this idea through your UN1CA project

## License

[GPLv3](LICENSE)

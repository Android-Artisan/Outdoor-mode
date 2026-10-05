# Outdoor Mode

An LSPosed module that enables the **Outdoor brightness mode** toggle in Samsung Settings and adds a **Quick Settings tile** to switch it without digging through Settings, currently tested on One UI 8.5.

## What it does

1. Forces `SecOutDoorModePreferenceController.isAvailable()` to return `true`, making the Outdoor mode brightness option visible in **Settings → Display** on Samsung devices that don't show it by default.
2. Adds an **Outdoor mode** Quick Settings tile that turns the mode on or off from the shade. Long pressing the tile opens **Settings → Display**, instead of the usual app info screen.

The tile flips the `display_outdoor_mode` system setting, the same value the Settings switch writes, so the framework applies the brightness change on its own.

### Why the module also hooks the settings provider

`SettingsProvider` only lets system/shell/root create settings that aren't in its `PUBLIC_SETTINGS` list. An app writing `display_outdoor_mode` normally fails with:

```
java.lang.IllegalArgumentException: You cannot keep your settings in the secure settings.
```

Granting the app `WRITE_SECURE_SETTINGS` does not help - the check runs before the permission is consulted. The provider lives in `system_server`, so the module lifts that restriction for this one key only, which lets the tile write it like any other setting. No root prompt is needed to toggle.

## Requirements

- Android device with Samsung One UI
- [LSPosed](https://github.com/LSPosed/LSPosed) (Zygisk)
- Root access (to install LSPosed and the module)

## Installation

1. Download and install the APK
2. Open **LSPosed** → **Modules** → **Outdoor Brightness**
3. Set the scope to **com.android.settings** and **System Framework**
4. Reboot (system_server is only injected at boot, so the provider hook needs it)
5. Add the **Outdoor mode** tile from the Quick Settings edit panel
6. Tap the tile once: the first tap asks for the **Modify system settings** access, after which toggling is instant

Tip: long press the tile to jump to the full Outdoor mode screen in Settings → Display.

If the tile does nothing, check that both scopes are enabled and that the app was granted *Modify system settings*.

## How to build

```bash
# Requires Android SDK build-tools and JDK
BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"

# Compile Java (aapt also generates R.java from the manifest and resources)
javac -source 1.8 -target 1.8 -cp "$ANDROID_JAR:XposedBridge.jar" \
    -d build/classes src/com/artisan/outdoormode/*.java build/gen/com/artisan/outdoormode/R.java

# Create dex
$BUILD_TOOLS/d8 --lib "$ANDROID_JAR" --min-api 24 \
    --output build/ build/classes/com/artisan/outdoormode/*.class

# Package APK (writes R.java into build/gen)
$BUILD_TOOLS/aapt package -f -m -J build/gen -M AndroidManifest.xml -I $ANDROID_JAR \
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

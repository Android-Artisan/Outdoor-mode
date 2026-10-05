package com.artisan.outdoormode;

import android.content.ContentProvider;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class OutdoorHook implements IXposedHookLoadPackage {

    private static final String TAG = "OutdoorBrightness";

    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String TARGET_CLASS = "com.samsung.android.settings.display.controller.SecOutDoorModePreferenceController";
    private static final String TARGET_METHOD = "isAvailable";

    /** SettingsProvider is hosted by system_server, so it shows up as package "android". */
    private static final String SYSTEM_SERVER_PACKAGE = "android";
    private static final String PROVIDER_CLASS_NAME = "com.android.providers.settings.SettingsProvider";
    private static final String ENFORCE_METHOD = "enforceRestrictedSystemSettingsMutationForCallingPackage";
    private static final String THROW_METHOD = "warnOrThrowForUndesiredSecureSettingsMutationForTargetSdk";

    private static final String OUTDOOR_KEY = "display_outdoor_mode";

    private boolean mProviderHooked;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (SETTINGS_PACKAGE.equals(lpparam.packageName)) {
            hookSettings(lpparam);
        } else if (SYSTEM_SERVER_PACKAGE.equals(lpparam.packageName)) {
            hookSettingsProvider(lpparam);
        }
    }

    private void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": Loaded in " + SETTINGS_PACKAGE);

        try {
            XposedHelpers.findAndHookMethod(
                TARGET_CLASS,
                lpparam.classLoader,
                TARGET_METHOD,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(true);
                        XposedBridge.log(TAG + ": Forced " + TARGET_METHOD + "() to return true");
                    }
                }
            );
            XposedBridge.log(TAG + ": Successfully hooked " + TARGET_CLASS + "." + TARGET_METHOD);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * The settings provider only lets system/shell/root write settings that are not in its
     * PUBLIC_SETTINGS list, which is why a normal app write of "display_outdoor_mode" is
     * refused. That class is loaded from the provider APK by its own classloader, so it
     * cannot be looked up by name from system_server; instead the class is taken from the
     * first provider instance that gets constructed and its checks are hooked there.
     *
     * Both hooks only ever touch the "display_outdoor_mode" key.
     */
    private void hookSettingsProvider(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": Loaded in " + SYSTEM_SERVER_PACKAGE);

        try {
            XposedBridge.hookAllConstructors(ContentProvider.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object provider = param.thisObject;
                    if (provider != null && PROVIDER_CLASS_NAME.equals(provider.getClass().getName())) {
                        hookSettingsProviderMethods(provider.getClass());
                    }
                }
            });
            XposedBridge.log(TAG + ": Watching for " + PROVIDER_CLASS_NAME);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to watch providers - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private synchronized void hookSettingsProviderMethods(Class<?> providerClass) {
        if (mProviderHooked) {
            return;
        }
        mProviderHooked = true;

        // Matching by name only keeps this working even if the parameter list differs.
        for (String methodName : new String[] { ENFORCE_METHOD, THROW_METHOD }) {
            try {
                XposedBridge.hookAllMethods(providerClass, methodName, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length >= 2 && OUTDOOR_KEY.equals(param.args[1])) {
                            param.setResult(null);
                        }
                    }
                });
                XposedBridge.log(TAG + ": Successfully hooked " + PROVIDER_CLASS_NAME + "." + methodName);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Failed to hook " + methodName + " - " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }
}

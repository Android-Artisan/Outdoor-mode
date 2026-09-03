package com.artisan.outdoormode;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class OutdoorHook implements IXposedHookLoadPackage {

    private static final String TAG = "OutdoorBrightness";
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String TARGET_CLASS = "com.samsung.android.settings.display.controller.SecOutDoorModePreferenceController";
    private static final String TARGET_METHOD = "isAvailable";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log(TAG + ": Loaded in " + TARGET_PACKAGE);

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
}
package com.artisan.outdoormode;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String SCENE_KT_CLASS =
        "com.android.systemui.samsung.quicksetting.ui.details.SecBrightnessDetailSceneKt";
    private static final String CONTENT_LAMBDA_CLASS =
        "com.android.systemui.samsung.quicksetting.ui.details.SecBrightnessDetailSceneKt$$ExternalSyntheticLambda0";
    private static final String ROW_METHOD = "BrightnessDetailMain$BrightnessRow";

    private static final String SEAD_TITLE_NAME = "brightness_detail_sead_title";
    private static final String OUTDOOR_TITLE_NAME = "sec_brightness_outdoor_mode_title";
    private static final String OUTDOOR_SUMMARY_NAME = "sec_brightness_outdoor_mode_summary";
    private static final int SEAD_TITLE_FALLBACK = 0x7e1303d1;
    private static final int OUTDOOR_TITLE_FALLBACK = 0x7e13145a;
    private static final int OUTDOOR_SUMMARY_FALLBACK = 0x7e131459;

    /** SettingsProvider is hosted by system_server, so it shows up as package "android". */
    private static final String SYSTEM_SERVER_PACKAGE = "android";
    private static final String PROVIDER_CLASS_NAME = "com.android.providers.settings.SettingsProvider";
    private static final String ENFORCE_METHOD = "enforceRestrictedSystemSettingsMutationForCallingPackage";
    private static final String THROW_METHOD = "warnOrThrowForUndesiredSecureSettingsMutationForTargetSdk";

    private static final String OUTDOOR_KEY = "display_outdoor_mode";

    private boolean mProviderHooked;

    private int mParentDepth;
    private boolean mUiSetupDone;
    private boolean mUiSetupFailed;
    private boolean mInjectLogged;
    private int mSeadTitleRes;
    private int mOutdoorTitleRes;
    private int mOutdoorSummaryRes;
    private Object mOutdoorState;
    private Method mStateGetValue;
    private Method mStateSetValue;
    private Object mToggle;
    private Object mElementKey;
    private Context mUiContext;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (SETTINGS_PACKAGE.equals(lpparam.packageName)) {
            hookSettings(lpparam);
        } else if (SYSTEM_SERVER_PACKAGE.equals(lpparam.packageName)) {
            hookSettingsProvider(lpparam);
        } else if (SYSTEMUI_PACKAGE.equals(lpparam.packageName)) {
            hookSystemUI(lpparam);
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

    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": Loaded in " + SYSTEMUI_PACKAGE);
        ClassLoader cl = lpparam.classLoader;

        try {
            Class<?> contentLambda = XposedHelpers.findClass(CONTENT_LAMBDA_CLASS, cl);
            XposedBridge.hookAllMethods(contentLambda, "invoke", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    mParentDepth++;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mParentDepth--;
                }
            });
            XposedBridge.log(TAG + ": Depth tracking on " + CONTENT_LAMBDA_CLASS);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook content lambda - " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return;
        }

        try {
            Class<?> sceneClass = XposedHelpers.findClass(SCENE_KT_CLASS, cl);
            XposedBridge.hookAllMethods(sceneClass, ROW_METHOD, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    injectOutdoorRow(param, cl);
                }
            });
            XposedBridge.log(TAG + ": Hooked " + SCENE_KT_CLASS + "." + ROW_METHOD);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook row method - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void injectOutdoorRow(XC_MethodHook.MethodHookParam param, ClassLoader cl) {
        try {
            if (param.hasThrowable() || mParentDepth <= 0) {
                return;
            }
            Object[] args = param.args;
            if (args == null || args.length != 11 || !(args[1] instanceof Context) || !(args[3] instanceof Integer)) {
                return;
            }
            if (!mUiSetupDone) {
                setupUi((Context) args[1], cl);
                if (!mUiSetupDone) {
                    return;
                }
            }
            if ((Integer) args[3] != mSeadTitleRes) {
                return;
            }
            Object[] newArgs = args.clone();
            newArgs[2] = mElementKey;
            newArgs[3] = mOutdoorTitleRes;
            newArgs[4] = mOutdoorSummaryRes;
            newArgs[5] = readOutdoorState();
            newArgs[6] = mToggle;
            ((Method) param.method).invoke(null, newArgs);
            if (!mInjectLogged) {
                mInjectLogged = true;
                XposedBridge.log(TAG + ": Injected QS outdoor row (sead=0x"
                    + Integer.toHexString(mSeadTitleRes) + " outdoor=0x"
                    + Integer.toHexString(mOutdoorTitleRes) + ")");
            }
        } catch (Throwable t) {
            if (!mInjectLogged) {
                mInjectLogged = true;
                XposedBridge.log(TAG + ": Row injection failed - " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private void setupUi(Context context, ClassLoader cl) {
        if (mUiSetupFailed) {
            return;
        }
        try {
            mUiContext = context.getApplicationContext();
            Resources res = context.getResources();
            String pkg = context.getPackageName();
            mSeadTitleRes = resolveRes(res, pkg, SEAD_TITLE_NAME, SEAD_TITLE_FALLBACK);
            mOutdoorTitleRes = resolveRes(res, pkg, OUTDOOR_TITLE_NAME, OUTDOOR_TITLE_FALLBACK);
            mOutdoorSummaryRes = resolveRes(res, pkg, OUTDOOR_SUMMARY_NAME, OUTDOOR_SUMMARY_FALLBACK);
            res.getResourceEntryName(mSeadTitleRes);
            res.getResourceEntryName(mOutdoorTitleRes);
            res.getResourceEntryName(mOutdoorSummaryRes);

            Class<?> snapshotStateKt = cl.loadClass("androidx.compose.runtime.SnapshotStateKt");
            Class<?> mutableStateCl = cl.loadClass("androidx.compose.runtime.MutableState");
            mStateGetValue = mutableStateCl.getMethod("getValue");
            mStateSetValue = mutableStateCl.getMethod("setValue", Object.class);
            mOutdoorState = createMutableState(snapshotStateKt, cl);

            Class<?> function1Cl = cl.loadClass("kotlin.jvm.functions.Function1");
            final Object unit = cl.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            mToggle = Proxy.newProxyInstance(cl, new Class<?>[] { function1Cl }, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] methodArgs) {
                    String name = method.getName();
                    if ("invoke".equals(name) && methodArgs != null && methodArgs.length == 1 && methodArgs[0] instanceof Boolean) {
                        applyOutdoor((Boolean) methodArgs[0]);
                        return unit;
                    }
                    if ("equals".equals(name) && methodArgs != null && methodArgs.length == 1) {
                        return proxy == methodArgs[0] ? Boolean.TRUE : Boolean.FALSE;
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "OutdoorToggle";
                    }
                    return null;
                }
            });

            Class<?> elementKeyCl = cl.loadClass("com.android.compose.animation.scene.ElementKey");
            Class<?> pickerCl = cl.loadClass("com.android.compose.animation.scene.ElementContentPicker");
            Class<?> markerCl = cl.loadClass("kotlin.jvm.internal.DefaultConstructorMarker");
            Constructor<?> elementKeyCtor = elementKeyCl.getConstructor(
                String.class, Object.class, pickerCl, boolean.class, int.class, markerCl);
            mElementKey = elementKeyCtor.newInstance("OutdoorModeRow", null, null, false, 14, null);

            ContentResolver cr = mUiContext.getContentResolver();
            cr.registerContentObserver(Settings.System.getUriFor(OUTDOOR_KEY), false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        syncOutdoorState();
                    }
                });
            mStateSetValue.invoke(mOutdoorState,
                Settings.System.getInt(cr, OUTDOOR_KEY, 0) != 0);

            mUiSetupDone = true;
            XposedBridge.log(TAG + ": SystemUI QS row setup complete");
        } catch (Throwable t) {
            mUiSetupFailed = true;
            XposedBridge.log(TAG + ": SystemUI QS row setup failed - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private Object createMutableState(Class<?> snapshotStateKt, ClassLoader cl) throws Throwable {
        try {
            return snapshotStateKt.getMethod("mutableStateOf$default", Object.class).invoke(null, Boolean.FALSE);
        } catch (NoSuchMethodException e) {
            Object policy = cl.loadClass("androidx.compose.runtime.StructuralEqualityPolicy").getField("INSTANCE").get(null);
            Class<?> policyCl = cl.loadClass("androidx.compose.runtime.SnapshotMutationPolicy");
            return snapshotStateKt.getMethod("mutableStateOf", Object.class, policyCl).invoke(null, Boolean.FALSE, policy);
        }
    }

    private int resolveRes(Resources res, String pkg, String name, int fallback) {
        int id = res.getIdentifier(name, "string", pkg);
        return id != 0 ? id : fallback;
    }

    private boolean readOutdoorState() {
        try {
            return Boolean.TRUE.equals(mStateGetValue.invoke(mOutdoorState));
        } catch (Throwable t) {
            return false;
        }
    }

    private void applyOutdoor(boolean value) {
        try {
            ContentResolver cr = mUiContext.getContentResolver();
            boolean ok = Settings.System.putInt(cr, OUTDOOR_KEY, value ? 1 : 0);
            XposedBridge.log(TAG + ": Outdoor write ok=" + ok + " value=" + value);
            if (ok) {
                mStateSetValue.invoke(mOutdoorState, value);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Outdoor write failed - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void syncOutdoorState() {
        try {
            boolean value = Settings.System.getInt(mUiContext.getContentResolver(), OUTDOOR_KEY, 0) != 0;
            mStateSetValue.invoke(mOutdoorState, value);
        } catch (Throwable ignored) {
        }
    }
}

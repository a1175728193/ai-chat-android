package com.aichat.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 接口配置的本地存储。App 不内置任何 Key，全部由用户在设置里填写。 */
public final class Prefs {

    private static final String FILE = "ai_chat_prefs";

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getBaseUrl(Context c) {
        return sp(c).getString("base_url", DEFAULT_BASE_URL);
    }

    public static void setBaseUrl(Context c, String v) {
        sp(c).edit().putString("base_url", v == null ? "" : v.trim()).apply();
    }

    public static String getApiKey(Context c) {
        return sp(c).getString("api_key", "");
    }

    public static void setApiKey(Context c, String v) {
        sp(c).edit().putString("api_key", v == null ? "" : v.trim()).apply();
    }

    public static String getModel(Context c) {
        return sp(c).getString("model", DEFAULT_MODEL);
    }

    public static void setModel(Context c, String v) {
        sp(c).edit().putString("model", v == null ? "" : v.trim()).apply();
    }

    public static boolean isStream(Context c) {
        return sp(c).getBoolean("stream", true);
    }

    public static void setStream(Context c, boolean v) {
        sp(c).edit().putBoolean("stream", v).apply();
    }

    public static boolean hasApiKey(Context c) {
        return getApiKey(c).length() > 0;
    }
}

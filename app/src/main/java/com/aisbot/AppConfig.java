package com.aisbot;

import android.content.Context;
import android.content.SharedPreferences;

public class AppConfig {

    private static final String PREFS = "aisbot_config";

    private static final String KEY_IP = "server_ip";
    private static final String KEY_MAC = "server_mac";
    private static final String KEY_BROADCAST = "server_broadcast";
    private static final String KEY_RULE = "context_rule";

    private final SharedPreferences prefs;

    public AppConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String ip, String mac, String broadcast) {
        prefs.edit()
                .putString(KEY_IP, ip)
                .putString(KEY_MAC, mac)
                .putString(KEY_BROADCAST, broadcast)
                .apply();
    }

    public String getIP() {
        return prefs.getString(KEY_IP, null);
    }

    public String getMAC() {
        return prefs.getString(KEY_MAC, null);
    }

    public String getBroadcast() {
        return prefs.getString(KEY_BROADCAST, null);
    }

    public boolean isConfigured() {
        return isValid(getIP()) && isValid(getMAC()) && isValid(getBroadcast());
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public void saveRule(int rule) {
        prefs.edit().putInt(KEY_RULE, rule).apply();
    }

    public int getRule() {
        return prefs.getInt(KEY_RULE, -1);
    }
}

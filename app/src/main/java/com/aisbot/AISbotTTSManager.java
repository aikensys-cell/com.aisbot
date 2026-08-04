package com.aisbot;

import android.content.Context;

public class AISbotTTSManager {

    private final Context context;
    private final AppConfig config;

    public AISbotTTSManager(Context ctx) {
        this.context = ctx;
        this.config = new AppConfig(ctx);
    }

    public void speak(String text) {
        SanbotSpeech.getInstance().speak(text);
    }
}

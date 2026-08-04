package com.aisbot;

import android.util.Log;

import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.interfaces.speech.SpeakListener;
import com.sanbot.opensdk.function.beans.speech.SpeakStatus;

public class SanbotSpeech {

    private static SanbotSpeech instance;
    private SpeechManager speechManager;

    private SanbotSpeech() {}

    public static synchronized SanbotSpeech getInstance() {
        if (instance == null) {
            instance = new SanbotSpeech();
        }
        return instance;
    }

    public void init(SpeechManager speechManager) {

        if (speechManager == null) {
            Log.e("SANBOT_SPEECH", "init() called with NULL SpeechManager");
            return;
        }

        this.speechManager = speechManager;

        speechManager.setOnSpeechListener(new SpeakListener() {

            @Override
            public void onSpeakStatus(SpeakStatus status) {

                float progress = status.getProgress();

                if (progress == 100f) {
                    RobotSpeechManager.getInstance().notifyRobotFinishedSpeaking();
                }
            }
        });
    }

    public void speak(final String text) {

        if (speechManager == null) {
            Log.e("SANBOT_SPEECH", "speak(): SpeechManager NULL");
            return;
        }

        if (text == null) return;

        String raw = text.trim();
        if (raw.isEmpty()) return;

        try {
            speechManager.startSpeak(raw);
        } catch (Exception e) {
            Log.e("SANBOT_SPEECH", "Error speaking: " + e.getMessage());
        }
    }
}

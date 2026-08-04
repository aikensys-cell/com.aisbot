package com.aisbot;

import android.util.Log;

import com.sanbot.opensdk.function.beans.speech.Grammar;
import com.sanbot.opensdk.function.beans.speech.RecognizeTextBean;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.interfaces.speech.RecognizeListener;

public class RobotSpeechManager {

    private static RobotSpeechManager instance;

    public static RobotSpeechManager getInstance() {
        return instance;
    }

    private final SpeechManager speechManager;
    private AISbotSpeechListener listener;

    private boolean wakeupInProgress = false;

    public RobotSpeechManager(SpeechManager speechManager) {
        this.speechManager = speechManager;
        instance = this;
    }

    public void attachSpeechListener(AISbotSpeechListener listener) {
        this.listener = listener;
    }

    public void initSpeech() {

        if (speechManager == null) return;

        speechManager.setOnSpeechListener(new RecognizeListener() {

            @Override
            public void onStartRecognize() {
            }

            @Override
            public void onRecognizeText(RecognizeTextBean recognizeTextBean) {
            }

            @Override
            public boolean onRecognizeResult(Grammar grammar) {

                wakeupInProgress = false;

                if (grammar != null && listener != null) {
                    String text = grammar.getText();
                    Log.e("AISBOT", "USER SAID: " + text);
                    listener.notifyText(text);
                }

                return true;
            }

            @Override
            public void onStopRecognize() {

                wakeupInProgress = false;

                MainActivity act = MainActivity.getInstance();
                if (act != null) act.onRobotSpeechError();
            }

            @Override
            public void onError(int i, int i1) {

                wakeupInProgress = false;

                MainActivity act = MainActivity.getInstance();
                if (act != null) act.onRobotSpeechError();
            }

            @Override
            public void onRecognizeVolume(int volume) {
            }
        });
    }

    public void startWakeUp() {

        if (speechManager == null) return;

        if (wakeupInProgress) {
            Log.e("AISBOT", "startWakeUp: ignorat, ja hi ha un wake-up en curs.");
            return;
        }

        wakeupInProgress = true;

        Log.e("AISBOT", ">>> WAKE-UP REQUESTED");

        try {
            speechManager.doWakeUp();
        } catch (Exception e) {
            Log.e("AISBOT", "WakeUp error: " + e.getMessage());
            wakeupInProgress = false;
        }
    }

    public void notifyRobotFinishedSpeaking() {
        MainActivity act = MainActivity.getInstance();
        if (act != null) act.onRobotFinishedSpeaking();
    }

    public boolean isWakeUpInProgress() {
        return wakeupInProgress;
    }
}

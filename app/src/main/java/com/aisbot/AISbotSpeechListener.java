package com.aisbot;

public class AISbotSpeechListener {

    public interface OnSpeechResultListener {
        void onSpeechResult(String text);
    }

    private OnSpeechResultListener listener;

    public AISbotSpeechListener(OnSpeechResultListener listener) {
        this.listener = listener;
    }

    public void notifyText(String text) {
        if (listener != null) {
            listener.onSpeechResult(text);
        }
    }

    public void startListening() {
    }

    public void stopListening() {
    }
}

package com.aisbot;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AISbotManager {

    private static final String TAG = "AISBOT";
    private static final String RULE_TAG = "AISBOT_RULE";

    private final Context context;
    private final TextView statusText;

    private final ConversationManager conversationManager = new ConversationManager();

    private int dynamicRule = 4;

    public AISbotManager(Context context, TextView statusText) {
        this.context = context;
        this.statusText = statusText;
        Log.d(RULE_TAG, "AISbotManager creat. Regla inicial = " + dynamicRule);
    }

    public void setDynamicRule(int value) {
        if (value < 2) value = 2;
        if (value > 9) value = 9;
        this.dynamicRule = value;
        Log.d(RULE_TAG, "setDynamicRule -> " + this.dynamicRule);
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public interface OnResponseListener {
        void onResponse(String responseText);
        void onNextChunk(String chunk);
        void onComplete();
    }

    public void processUserText(String userText, OnResponseListener listener) {

        final ServerWakeManager server = MainActivity.getInstance().getServer();
        final OnResponseListener finalListener = listener;

        try {
            conversationManager.addUserMessage(userText);

            List<ConversationManager.Message> full = conversationManager.getHistory();
            List<ConversationManager.Message> sliced = applyRuleSlidingTurns(full);

            JSONArray messages = new JSONArray();
            for (ConversationManager.Message m : sliced) {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                messages.put(o);
            }

            JSONObject payload = new JSONObject();
            payload.put("model", "sanbot");
            payload.put("messages", messages);
            payload.put("stream", true);

            Log.d(TAG, "PAYLOAD ENVIAT A OLLAMA: " + payload.toString());
            server.sendConversationPayload(payload);

        } catch (Exception e) {
            Log.e(TAG, "ERROR a processUserText", e);
        }

        server.setOnAIResponseListener(new ServerWakeManager.OnAIResponseListener() {
            @Override
            public void onAIResponse(String aiText) {
                if (finalListener != null) {
                    if (aiText != null && aiText.trim().length() > 0) {
                        finalListener.onNextChunk(aiText);
                    }
                    finalListener.onResponse(aiText);
                }
            }

            @Override
            public void onAICallbackChunk(String chunk) {
                if (finalListener != null) {
                    finalListener.onNextChunk(chunk);
                }
            }

            @Override
            public void onAICallbackComplete() {
                if (finalListener != null) {
                    finalListener.onComplete();
                }
            }
        });
    }

    private List<ConversationManager.Message> applyRuleSlidingTurns(List<ConversationManager.Message> full) {

        if (full.isEmpty()) return new ArrayList<>(full);

        Log.d(RULE_TAG, "EXECUTANT REGLA SLIDING TURNS, dynamicRule = " + dynamicRule);

        List<ConversationManager.Message> selected = new ArrayList<>();

        int turnsCollected = 0;
        int i = full.size() - 1;

        while (i >= 0 && turnsCollected < dynamicRule) {

            ConversationManager.Message current = full.get(i);

            if ("user".equals(current.role)) {

                selected.add(current);

                int j = i - 1;
                if (j >= 0) {
                    ConversationManager.Message maybeAssistant = full.get(j);
                    if ("assistant".equals(maybeAssistant.role)) {
                        selected.add(maybeAssistant);
                        i = j - 1;
                    } else {
                        i = j;
                    }
                } else {
                    i = j;
                }

                turnsCollected++;

            } else {
                i--;
            }
        }

        Collections.reverse(selected);
        return selected;
    }
}

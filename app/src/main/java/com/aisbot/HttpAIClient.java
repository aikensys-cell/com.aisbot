package com.aisbot;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpAIClient {

    public interface OnAIResponseListener {
        void onAIResponse(String text);
        void onAIError(Exception e);
    }

    private static final String TAG = "AISBOT_HTTP";

    private final String ip;
    private final String prompt;
    private final OnAIResponseListener listener;

    public static boolean STREAMING_MODE = true;

    public HttpAIClient(String ip, String prompt, OnAIResponseListener listener) {
        this.ip = ip;
        this.prompt = prompt;
        this.listener = listener;
    }

    public void start() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runRequest();
            }
        });
        t.start();
    }

    private void runRequest() {

        HttpURLConnection conn = null;

        try {
            String urlStr = "http://" + ip + ":11434/api/generate";
            Log.i(TAG, "Connecting to: " + urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            Log.e("AISBOT", "PROMPT ENVIAT A OLLAMA:\n" + prompt);

            String json = "{\"model\":\"sanbot\",\"prompt\":\"" + prompt + "\",\"stream\":true}";
            Log.i(TAG, "REQUEST JSON = " + json);
            conn.getOutputStream().write(json.getBytes("UTF-8"));

            int code = conn.getResponseCode();
            Log.i(TAG, "HTTP CODE = " + code);
            if (code != 200) {
                if (listener != null) listener.onAIError(new Exception("HTTP " + code));
                return;
            }

            InputStream in = conn.getInputStream();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, "UTF-8"));

            StringBuilder accumulator = new StringBuilder();
            StringBuilder raw = new StringBuilder();

            int depth = 0;
            StringBuilder current = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {

                raw.append(line);

                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);

                    if (c == '{') depth++;
                    if (depth > 0) current.append(c);
                    if (c == '}') {
                        depth--;
                        if (depth == 0) {

                            String jsonObj = current.toString();
                            current.setLength(0);

                            try {
                                JSONObject o = new JSONObject(jsonObj);

                                if (o.has("response")) {

                                    String fragment = o.getString("response");

                                    accumulator.append(fragment);
                                    String acc = accumulator.toString().trim();

                                    if (acc.length() < 8) continue;

                                    boolean endOfSentence =
                                            acc.endsWith(".") ||
                                                    acc.endsWith(",") ||
                                                    acc.endsWith("!") ||
                                                    acc.endsWith("?") ||
                                                    acc.endsWith("\n");

                                    boolean tooLong =
                                            acc.split(" ").length >= 20;

                                    if (endOfSentence || tooLong) {
                                        SanbotSpeech.getInstance().speak(acc);
                                        accumulator.setLength(0);
                                    }
                                }

                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            String finalText = raw.toString();

            StringBuilder full = new StringBuilder();
            int d2 = 0;
            StringBuilder cur2 = new StringBuilder();

            for (int i = 0; i < finalText.length(); i++) {
                char c = finalText.charAt(i);
                if (c == '{') d2++;
                if (d2 > 0) cur2.append(c);
                if (c == '}') {
                    d2--;
                    if (d2 == 0) {
                        try {
                            JSONObject o = new JSONObject(cur2.toString());
                            if (o.has("response")) {
                                full.append(o.getString("response"));
                            }
                        } catch (Exception ignored) {}
                        cur2.setLength(0);
                    }
                }
            }

            JSONObject wrapper = new JSONObject();
            JSONObject message = new JSONObject();
            message.put("content", full.toString().trim());
            wrapper.put("message", message);

            if (listener != null) {
                listener.onAIResponse(wrapper.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Timeout", e);
            if (listener != null) listener.onAIError(e);

        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }
}

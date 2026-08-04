package com.aisbot;

import android.widget.TextView;
import android.util.Log;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerWakeManager {

    public interface OnAIReadyListener { void onAIReady(); }

    public interface OnAIResponseListener {
        void onAIResponse(String text);
        void onAICallbackChunk(String chunk);
        void onAICallbackComplete();
    }

    private OnAIResponseListener aiResponseListener;
    public void setOnAIResponseListener(OnAIResponseListener l) { this.aiResponseListener = l; }

    private final String ip, mac, broadcast;
    private final TextView status;
    private final OnAIReadyListener aiReadyListener;

    private final MainActivity activity;

    private volatile boolean keepCheckingAI = false;
    private volatile boolean isHandlingError = false;
    private volatile boolean isShuttingDown = false;

    public ServerWakeManager(AppConfig config,
                             TextView status,
                             OnAIReadyListener aiReadyListener,
                             MainActivity activity) {

        this.ip = config.getIP();
        this.mac = config.getMAC();
        this.broadcast = config.getBroadcast();
        this.status = status;
        this.aiReadyListener = aiReadyListener;
        this.activity = activity;
    }

    private void setStatus(final String line) {
        status.post(new Runnable() {
            @Override
            public void run() { status.setText(line); }
        });
    }

    public void onLocalAIError() { unifiedError(); }

    private void unifiedError() {
        if (isHandlingError || isShuttingDown) return;
        isHandlingError = true;
        keepCheckingAI = false;

        setStatus(AISbotMessages.localAINotResponding());

        MainActivity.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.getInstance().enableAllMenus();
            }
        });

        status.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isShuttingDown) {
                    isHandlingError = false;
                    return;
                }
                isHandlingError = false;
                start();
            }
        }, 5000);
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (isShuttingDown) return;

                if (ping()) {
                    setStatus(AISbotMessages.serverUp());
                    try { Thread.sleep(1000); } catch (Exception ignored) {}

                    if (!isShuttingDown) {
                        runAISequence();
                    }
                    return;
                }

                final String base = AISbotMessages.checkingServer();
                final String[] frames = new String[]{base + " .", base + " ..", base + " ...", base};
                int frameIndex = 0;
                int wolCounter = 0;

                while (!isShuttingDown && !ping()) {
                    if (wolCounter % 5 == 0) {
                        try { wake(mac, broadcast); } catch (Exception ignored) {}
                    }

                    setStatus(frames[frameIndex]);
                    frameIndex = (frameIndex + 1) % frames.length;

                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                    wolCounter++;
                }

                if (isShuttingDown) return;

                setStatus(AISbotMessages.serverUp());
                try { Thread.sleep(1000); } catch (Exception ignored) {}

                if (!isShuttingDown) {
                    runAISequence();
                }
            }
        }).start();
    }

    public void runAISequence() {
        keepCheckingAI = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String base = AISbotMessages.checkingAI();
                final String[] frames = new String[]{base + " .", base + " ..", base + " ...", base};
                int frameIndex = 0;

                while (!isShuttingDown && keepCheckingAI) {
                    setStatus(frames[frameIndex]);
                    frameIndex = (frameIndex + 1) % frames.length;

                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isShuttingDown && keepCheckingAI) {
                    if (checkAI()) {
                        keepCheckingAI = false;
                        if (isShuttingDown) return;

                        setStatus(AISbotMessages.aiUp());

                        status.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (isShuttingDown) return;

                                setStatus("");

                                MainActivity.getInstance().revealSanbotButton();

                                if (aiReadyListener != null) aiReadyListener.onAIReady();
                            }
                        }, 1000);

                        return;
                    }

                    try { Thread.sleep(2000); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private boolean checkAI() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + ":11434/api/generate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1500);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String json = "{\"model\":\"sanbot\",\"prompt\":\"y\",\"stream\":true}";
            conn.getOutputStream().write(json.getBytes("UTF-8"));

            return conn.getResponseCode() == 200;

        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public boolean ping() {
        try {
            Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 1 " + ip);
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void wake(String macStr, String br) throws Exception {
        byte[] mac = new byte[6];
        String[] parts = macStr.split(":");

        for (int i = 0; i < 6; i++) mac[i] = (byte) Integer.parseInt(parts[i], 16);

        byte[] p = new byte[102];
        for (int i = 0; i < 6; i++) p[i] = (byte) 0xff;
        for (int i = 6; i < 102; i += 6) System.arraycopy(mac, 0, p, i, 6);

        DatagramSocket s = new DatagramSocket();
        s.send(new DatagramPacket(p, 102, InetAddress.getByName(br), 9));
        s.close();
    }

    public void handleUserText(final String prompt) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String body = "{\"model\":\"sanbot\",\"prompt\":\"" + prompt + "\",\"stream\":true}";
                callAIStreamingBody(body);
            }
        }).start();
    }

    public void sendConversationPayload(final JSONObject payload) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                callAIStreamingBody(payload.toString());
            }
        }).start();
    }

    private void callAIStreamingBody(String bodyJson) {
        HttpURLConnection conn = null;

        try {
            URL url = new URL("http://" + ip + ":11434/api/chat");
            conn = (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            Log.e("AISBOT_PAYLOAD", bodyJson);

            conn.getOutputStream().write(bodyJson.getBytes("UTF-8"));

            int code = conn.getResponseCode();
            Log.e("AISBOT_HTTP", "HTTP CODE = " + code);

            if (code != 200) {
                unifiedError();
                return;
            }

            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String l;

            while ((l = r.readLine()) != null) {
                if (isHandlingError || isShuttingDown) return;

                // 🔥 LOG RAW DEL SERVIDOR
                Log.e("AISBOT_RAW", l);

                int start = l.indexOf("\"content\":\"");
                if (start != -1) {
                    int end = l.indexOf("\"", start + 11);
                    if (end != -1) {
                        String chunk = l.substring(start + 11, end)
                                .replace("\\n", "\n")
                                .replace("\\\"", "\"");

                        // 🔥 LOG DEL CHUNK FINAL
                        Log.e("AISBOT_CHUNK", chunk);

                        if (aiResponseListener != null && !isHandlingError && !isShuttingDown) {
                            aiResponseListener.onAICallbackChunk(chunk);
                        }
                    }
                }
            }

            if (aiResponseListener != null && !isHandlingError && !isShuttingDown) {
                aiResponseListener.onAICallbackComplete();
            }

        } catch (Exception e) {
            Log.e("AISBOT_ERROR", "Exception: ", e);
            unifiedError();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public void stop() {
        isShuttingDown = true;
        keepCheckingAI = false;
        isHandlingError = false;
    }
}

package com.aisbot;

import android.app.Activity;
import android.widget.TextView;
import android.widget.Toast;

public class ChatConnectionManager {

    public static void showLocalAIError(final Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(
                        activity,
                        AISbotMessages.localAINotResponding(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    public static void handleCurlError(final Activity activity,
                                       final ServerWakeManager server) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                boolean pingOK = server.ping();

                if (!pingOK) {
                    try { Thread.sleep(4000); } catch (Exception ignored) {}
                    server.start();
                    return;
                }

                try { Thread.sleep(4000); } catch (Exception ignored) {}
                server.runAISequence();
            }
        }).start();
    }

    public static void initialize(Activity activity, TextView statusText) {
    }

    public static void retry(final Activity activity, final ServerWakeManager server) {
        handleCurlError(activity, server);
    }
}

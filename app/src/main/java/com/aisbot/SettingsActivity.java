package com.aisbot;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private boolean hasChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        final AppConfig config = new AppConfig(this);

        final EditText ip = findViewById(R.id.input_ip);
        final EditText mac = findViewById(R.id.input_mac);
        final EditText broadcast = findViewById(R.id.input_broadcast);

        final Button save = findViewById(R.id.btn_save);
        final Button quit = findViewById(R.id.btn_quit);

        final String oldIP = config.getIP();
        final String oldMAC = config.getMAC();
        final String oldBroadcast = config.getBroadcast();

        ip.setText(oldIP);
        mac.setText(oldMAC);
        broadcast.setText(oldBroadcast);

        save.setEnabled(false);
        quit.setEnabled(config.isConfigured());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean changed =
                        !ip.getText().toString().trim().equals(oldIP) ||
                                !mac.getText().toString().trim().equals(oldMAC) ||
                                !broadcast.getText().toString().trim().equals(oldBroadcast);

                hasChanges = changed;
                save.setEnabled(changed);
            }

            @Override public void afterTextChanged(Editable s) {}
        };

        ip.addTextChangedListener(watcher);
        mac.addTextChangedListener(watcher);
        broadcast.addTextChangedListener(watcher);

        quit.setText("CANCEL");
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!hasChanges) return;

                String ipValue = ip.getText().toString().trim();
                String macValue = mac.getText().toString().trim();
                String broadcastValue = broadcast.getText().toString().trim();

                if (ipValue.isEmpty() || macValue.isEmpty() || broadcastValue.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "All fields are required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidIP(ipValue)) {
                    Toast.makeText(SettingsActivity.this, "Invalid IP address.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidMAC(macValue)) {
                    Toast.makeText(SettingsActivity.this, "Invalid MAC address.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidIP(broadcastValue)) {
                    Toast.makeText(SettingsActivity.this, "Invalid broadcast address.", Toast.LENGTH_SHORT).show();
                    return;
                }

                config.save(ipValue, macValue, broadcastValue);

                Toast.makeText(SettingsActivity.this, AISbotMessages.configSaved(), Toast.LENGTH_SHORT).show();

                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private boolean isValidIP(String ip) {
        return ip != null && ip.matches(
                "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}" +
                        "(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
        );
    }

    private boolean isValidMAC(String mac) {
        return mac != null && mac.matches(
                "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"
        );
    }
}

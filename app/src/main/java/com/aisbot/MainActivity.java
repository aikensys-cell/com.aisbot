package com.aisbot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import android.support.v7.widget.Toolbar;

import com.sanbot.opensdk.base.TopBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SpeechManager;

import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends TopBaseActivity {

    private static MainActivity instance;

    private AISbotManager brain;
    private AISbotTTSManager tts;
    private AISbotSpeechListener speechListener;
    private RobotSpeechManager robotSpeech;
    private SpeechManager speechManager;
    private ServerWakeManager server;

    private AppearanceManager appearanceManager;

    private boolean aiReady = false;

    private final StringBuilder sentenceBuffer = new StringBuilder();
    private final StringBuilder fullResponseBuffer = new StringBuilder();

    private boolean isAiStreaming = false;

    private final Queue<String> speechQueue = new LinkedList<>();
    private boolean isRobotSpeakingNow = false;

    private Menu optionsMenu;

    private View sanbotButtonContainer;
    private View sanbotButton;
    private boolean sanbotButtonShown = false;

    private int wakeUpCount = 0;

    public MainActivity() {
        instance = this;
    }

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        register(MainActivity.class);
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        onCreateInternal();
    }

    private void onCreateInternal() {

        View mainLayout = findViewById(R.id.main_layout);
        appearanceManager = new AppearanceManager(this, mainLayout);
        appearanceManager.applySavedBackground();

        AppConfig config = new AppConfig(this);

        if (!config.isConfigured()) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, 1);
            return;
        }

        if (config.getRule() == -1) {
            showContextMemoryMenu();
            return;
        }

        if (brain != null) return;

        final TextView statusText = findViewById(R.id.main_text);

        updateTextVisibility();

        aiReady = false;
        isAiStreaming = false;
        isRobotSpeakingNow = false;
        wakeUpCount = 0;
        speechQueue.clear();
        sentenceBuffer.setLength(0);
        fullResponseBuffer.setLength(0);

        brain = new AISbotManager(this, statusText);
        tts   = new AISbotTTSManager(this);

        int savedRule = config.getRule();
        if (savedRule >= 2 && savedRule <= 9) {
            brain.setDynamicRule(savedRule);
        } else {
            config.saveRule(4);
            brain.setDynamicRule(4);
        }

        sanbotButtonContainer = findViewById(R.id.sanbot_button_container);
        sanbotButton = findViewById(R.id.sanbot_listen_button);

        if (sanbotButtonContainer != null) {
            sanbotButtonContainer.setVisibility(View.GONE);
        }
        sanbotButtonShown = false;

        if (sanbotButton != null) {
            sanbotButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideSanbotButton();
                    disableContextMemoryMenu();
                    disableServerSettings();
                    startSanbotListening();
                }
            });
        }

        ChatConnectionManager.initialize(this, statusText);

        server = new ServerWakeManager(
                config,
                statusText,
                new ServerWakeManager.OnAIReadyListener() {
                    @Override
                    public void onAIReady() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isAiStreaming = false;
                                speechQueue.clear();
                                isRobotSpeakingNow = false;
                                sentenceBuffer.setLength(0);
                                fullResponseBuffer.setLength(0);
                                aiReady = true;

                                showSanbotButton();
                            }
                        });
                    }
                },
                this
        );

        server.start();
    }

    private void updateTextVisibility() {
        final TextView statusText = findViewById(R.id.main_text);
        if (statusText != null && appearanceManager != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (appearanceManager.shouldShowText()) {
                        statusText.setVisibility(View.VISIBLE);
                    } else {
                        statusText.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private void disableContextMemoryMenu() {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(R.id.menu_context_memory);
            if (item != null) item.setEnabled(false);
        }
    }

    private void enableContextMemoryMenu() {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(R.id.menu_context_memory);
            if (item != null) item.setEnabled(true);
        }
    }

    public void disableServerSettings() {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(R.id.menu_settings);
            if (item != null) item.setEnabled(false);
        }
    }

    private void enableServerSettings() {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(R.id.menu_settings);
            if (item != null) item.setEnabled(true);
        }
    }

    public void enableAllMenus() {
        enableContextMemoryMenu();
        enableServerSettings();
    }

    private void addToSpeechQueue(String text) {

        if (text == null || text.trim().length() == 0) return;

        speechQueue.add(text);

        if (!isRobotSpeakingNow) {
            processNextInQueue();
        }
    }

    private void processNextInQueue() {
        String nextText = speechQueue.poll();
        if (nextText != null) {
            isRobotSpeakingNow = true;
            Log.e("AISBOT", ">>> ROBOT PARLANT: " + nextText);
            SanbotSpeech.getInstance().speak(nextText);
        } else {
            isRobotSpeakingNow = false;
        }
    }

    public void onRobotFinishedSpeaking() {
        Log.e("AISBOT", "FINISHED SPEAKING CALLBACK >>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.e("AISBOT", "STACK TRACE:", new Exception("TRACE"));

        new Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        processNextInQueue();
                        checkIfShouldWakeUp();
                    }
                },
                400
        );
    }

    private void resetWakeUpCycle() {
        wakeUpCount = 0;
        speechQueue.clear();
        isRobotSpeakingNow = false;
        isAiStreaming = false;

        final TextView statusText = findViewById(R.id.main_text);
        statusText.setText("");

        if (brain != null) {
            brain.getConversationManager().clear();

            AppConfig config = new AppConfig(this);
            int rule = config.getRule();
            if (rule >= 2 && rule <= 9) brain.setDynamicRule(rule);
        }

        showSanbotButton();

        enableAllMenus();
    }

    private void checkIfShouldWakeUp() {

        if (robotSpeech != null && robotSpeech.isWakeUpInProgress()) {
            Log.e("AISBOT", "checkIfShouldWakeUp: ja hi ha un wake-up en curs.");
            return;
        }

        if (isAiStreaming) {
            Log.e("AISBOT", "NO wake-up: AI encara està generant.");
            return;
        }

        if (speechQueue.isEmpty() && !isRobotSpeakingNow) {

            final TextView statusText = findViewById(R.id.main_text);

            statusText.postDelayed(new Runnable() {
                @Override
                public void run() {

                    if (aiReady &&
                            !isAiStreaming &&
                            speechQueue.isEmpty() &&
                            !isRobotSpeakingNow &&
                            (robotSpeech == null || !robotSpeech.isWakeUpInProgress())) {

                        wakeUpCount++;

                        if (wakeUpCount > 5) {
                            Log.e("AISBOT", ">>> 5 intents sense resposta. Finalitzant conversa.");
                            resetWakeUpCycle();
                            return;
                        }

                        Log.e("AISBOT", "checkIfShouldWakeUp: Obrint micro. Intent " + wakeUpCount);

                        if (robotSpeech != null) {
                            robotSpeech.initSpeech();
                            robotSpeech.startWakeUp();
                        }
                    }
                }
            }, 1200);
        }
    }

    public void onRobotSpeechError() {
        checkIfShouldWakeUp();
    }

    public void tryWakeUp() {
        checkIfShouldWakeUp();
    }

    @Override
    protected void onMainServiceConnected() {

        this.speechManager = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);

        speechListener = new AISbotSpeechListener(
                new AISbotSpeechListener.OnSpeechResultListener() {
                    @Override
                    public void onSpeechResult(final String userText) {
                        final TextView statusText = findViewById(R.id.main_text);

                        wakeUpCount = 0;

                        statusText.post(new Runnable() {
                            @Override
                            public void run() {
                                if (appearanceManager.shouldShowText()) {
                                    statusText.setText(userText);
                                }
                            }
                        });

                        isAiStreaming = true;
                        speechQueue.clear();
                        isRobotSpeakingNow = false;

                        sentenceBuffer.setLength(0);
                        fullResponseBuffer.setLength(0);

                        brain.processUserText(userText, new AISbotManager.OnResponseListener() {

                            @Override
                            public void onResponse(String responseText) {}

                            @Override
                            public void onNextChunk(String chunk) {
                                if (chunk == null) return;

                                fullResponseBuffer.append(chunk);
                                sentenceBuffer.append(chunk);

                                String currentText = sentenceBuffer.toString();

                                if (currentText.contains(".") ||
                                        currentText.contains("?") ||
                                        currentText.contains("!") ||
                                        currentText.contains("\n")) {

                                    addToSpeechQueue(currentText.trim());
                                    sentenceBuffer.setLength(0);
                                }
                            }

                            @Override
                            public void onComplete() {

                                String fullResponse = fullResponseBuffer.toString().trim();
                                fullResponseBuffer.setLength(0);

                                brain.getConversationManager().addAssistantMessage(fullResponse);

                                addToSpeechQueue(sentenceBuffer.toString().trim());
                                sentenceBuffer.setLength(0);

                                isAiStreaming = false;
                            }
                        });
                    }
                });

        if (robotSpeech == null && speechManager != null) {
            robotSpeech = new RobotSpeechManager(speechManager);
            robotSpeech.attachSpeechListener(speechListener);
        }

        if (robotSpeech != null) robotSpeech.initSpeech();
        if (speechManager != null) SanbotSpeech.getInstance().init(speechManager);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1 && resultCode == -1) {

            if (server != null) {
                server.stop();
            }

            brain = null;
            server = null;
            aiReady = false;
            isAiStreaming = false;
            isRobotSpeakingNow = false;
            wakeUpCount = 0;
            speechQueue.clear();
            sentenceBuffer.setLength(0);
            fullResponseBuffer.setLength(0);

            recreate();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;
        getMenuInflater().inflate(R.menu.main_menu, menu);

        enableContextMemoryMenu();
        enableServerSettings();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, 1);
            return true;
        }

        if (id == R.id.menu_background) {
            appearanceManager.showOptionsDialog(new Runnable() {
                @Override
                public void run() {
                    updateTextVisibility();
                }
            });
            return true;
        }

        if (id == R.id.menu_context_memory) {
            showContextMemoryMenu();
            return true;
        }

        return false;
    }

    public RobotSpeechManager getRobotSpeechManager() { return robotSpeech; }
    public SpeechManager getSpeechManager() { return speechManager; }
    public ServerWakeManager getServer() { return server; }

    public void revealSanbotButton() {
        showSanbotButton();
    }

    public void speakFromRobot(final String text) {
        addToSpeechQueue(text);
    }

    private void showSanbotButton() {
        if (sanbotButtonContainer != null && !sanbotButtonShown) {
            sanbotButtonShown = true;
            sanbotButtonContainer.setVisibility(View.VISIBLE);
        }
    }

    private void hideSanbotButton() {
        if (sanbotButtonContainer != null && sanbotButtonShown) {
            sanbotButtonShown = false;
            sanbotButtonContainer.setVisibility(View.GONE);
        }
    }

    private void startSanbotListening() {
        if (robotSpeech != null) {
            robotSpeech.initSpeech();
            robotSpeech.startWakeUp();
        }
    }

    private void showContextMemoryMenu() {

        final AppConfig config = new AppConfig(this);

        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.rule_input, null);

        final android.widget.EditText ruleInput = dialogView.findViewById(R.id.rule_input);
        final android.widget.TextView aiInfo = dialogView.findViewById(R.id.ai_info);
        final android.widget.TextView userInfo = dialogView.findViewById(R.id.user_info);

        aiInfo.setText("AI past prompts");
        userInfo.setText("User past prompts");

        int savedRule = config.getRule();
        if (savedRule > 0) {
            ruleInput.setText(String.valueOf(savedRule));
            ruleInput.setSelection(ruleInput.getText().length());   // << CURSOR A LA DRETA

            aiInfo.setText("AI past prompts " + savedRule);
            userInfo.setText("User past prompts " + savedRule);
        }

        builder.setView(dialogView);

        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("SAVE", null);

        final android.app.AlertDialog dialog = builder.create();
        dialog.show();

        final android.widget.Button saveButton =
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);

        saveButton.setEnabled(false);

        ruleInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String txt = ruleInput.getText().toString();

                if (!txt.matches("\\d+")) {
                    saveButton.setEnabled(false);
                    aiInfo.setText("AI past prompts");
                    userInfo.setText("User past prompts");
                    return;
                }

                int value = Integer.parseInt(txt);

                if (value < 2 || value > 9) {
                    saveButton.setEnabled(false);
                    aiInfo.setText("AI past prompts");
                    userInfo.setText("User past prompts");
                    return;
                }

                saveButton.setEnabled(true);
                aiInfo.setText("AI past prompts " + value);
                userInfo.setText("User past prompts " + value);
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String txt = ruleInput.getText().toString();
                int value = Integer.parseInt(txt);

                config.saveRule(value);
                if (brain != null) brain.setDynamicRule(value);

                dialog.dismiss();
                onCreateInternal();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (server != null) {
            server.stop();
        }
    }
}

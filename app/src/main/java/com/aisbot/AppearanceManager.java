package com.aisbot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;

public class AppearanceManager {

    private static final String PREFS_NAME = "AISbotAppearance";
    private static final String KEY_BG = "background_res_id";

    public static final int MODE_DEBUGGER = -1;

    private final String[] backgroundNames = {
            "Aberrantrealities",
            "Astralember",
            "Blendertimer maze",
            "Impermanent clouds",
            "Structure",
            "Shogun mountains",
            "Bokeh",
            "Debugger"
    };

    private final int[] backgroundRes = {
            R.drawable.aberrantrealities_ai_generated_8436635_1920_1200,
            R.drawable.astralember_ai_generated_8233733_1920_1200,
            R.drawable.blendertimer_maze_5768511_1920_1200,
            R.drawable.impermanent_clouds_7518259_1920_1200,
            R.drawable.michael_luenen_structure_1559179_1920_1200,
            R.drawable.shogun_mountains_8451480_1920_1200,
            R.drawable.tommyvideo_bokeh_1772963_1920_1200,
            MODE_DEBUGGER
    };

    private final Context context;
    private final View rootView;
    private int currentMode = MODE_DEBUGGER;

    public AppearanceManager(Context context, View rootView) {
        this.context = context;
        this.rootView = rootView;
    }

    public void applySavedBackground() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_BG, MODE_DEBUGGER);
        updateUI(currentMode);
    }

    public boolean shouldShowText() {
        return currentMode == MODE_DEBUGGER;
    }

    public void showOptionsDialog(final Runnable onChange) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Background");
        builder.setItems(backgroundNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentMode = backgroundRes[which];
                updateUI(currentMode);

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_BG, currentMode)
                        .apply();

                if (onChange != null) onChange.run();
            }
        });
        builder.show();
    }

    private void updateUI(int resId) {
        if (resId == MODE_DEBUGGER) {
            rootView.setBackgroundResource(0);
            rootView.setBackgroundColor(Color.WHITE);
        } else {
            try {
                rootView.setBackgroundResource(resId);
            } catch (android.content.res.Resources.NotFoundException e) {
                rootView.setBackgroundColor(Color.WHITE);
                currentMode = MODE_DEBUGGER;

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_BG, MODE_DEBUGGER)
                        .apply();
            }
        }
    }
}

/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2013, ParanoidAndroid Project.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.method.AllCapsTransformationMethod;
import android.text.method.TransformationMethod;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class PAWorldActivity extends Activity {
    FrameLayout mContent;
    int mCount;
    final Handler mHandler = new Handler();
    final static int SOLID_BGCOLOR = 0xFF000000;
    final static int CLEAR_BGCOLOR = 0xC0000000;
    final static int TEXT_COLOR = 0xFFFFFFFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

        mContent = new FrameLayout(this);
        mContent.setBackgroundColor(CLEAR_BGCOLOR);

        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;

        final ImageView logo = new ImageView(this);
        logo.setImageResource(com.android.internal.R.drawable.xpe_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setVisibility(View.INVISIBLE);

        final View bg = new View(this);
        bg.setBackgroundColor(SOLID_BGCOLOR);
        bg.setAlpha(0f);

        final TextView letter = new TextView(this);

        letter.setTypeface(bold);
        letter.setTextSize(200);
        letter.setTextColor(TEXT_COLOR);
        letter.setGravity(Gravity.CENTER);
        letter.setText("PA");

        final int p = (int)(4 * metrics.density);

        final TextView tv = new TextView(this);
        if (light != null) tv.setTypeface(light);
        tv.setTextSize(20);
        tv.setPadding(p, p, p, p);
        tv.setTextColor(TEXT_COLOR);
        tv.setGravity(Gravity.CENTER);
        tv.setTransformationMethod(new AllCapsTransformationMethod(this));
        String paVersion = SystemProperties.get("ro.xpe.version");
        paVersion = paVersion.replaceAll("([0-9\\.]+?)-.*", "$1");
        tv.setText("XPerience " + paVersion);
        tv.setVisibility(View.INVISIBLE);

        mContent.addView(bg);
        mContent.addView(letter, lp);
        mContent.addView(logo, lp);

        final FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(lp);
        lp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp2.bottomMargin = p;

        mContent.addView(tv, lp2);

        mContent.setOnClickListener(new View.OnClickListener() {
            int clicks;
            @Override
            public void onClick(View v) {
                clicks++;
                if (clicks >= 6) {
                    mContent.performLongClick();
                    return;
                }
                letter.animate().cancel();
                final float offset = (int)letter.getRotation() % 360;
                letter.animate()
                    .rotationBy((Math.random() > 0.5f ? 360 : -360) - offset)
                    .setInterpolator(new DecelerateInterpolator())
                    .setDuration(700).start();
            }
        });

        mContent.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (logo.getVisibility() != View.VISIBLE) {
                    bg.setScaleX(0.01f);
                    bg.animate().alpha(1f).scaleX(1f).setStartDelay(500).start();
                    letter.animate().alpha(0f).scaleY(0.5f).scaleX(0.5f)
                            .rotationBy(360)
                            .setInterpolator(new AccelerateInterpolator())
                            .setDuration(1000)
                            .start();
                    logo.setAlpha(0f);
                    logo.setVisibility(View.VISIBLE);
                    logo.setScaleX(0.5f);
                    logo.setScaleY(0.5f);
                    logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(1000).setStartDelay(500)
                        .setInterpolator(new AnticipateOvershootInterpolator())
                        .start();
                    tv.setAlpha(0f);
                    tv.setVisibility(View.VISIBLE);
                    tv.animate().alpha(1f).setDuration(1000).setStartDelay(1000).start();
                    return true;
                }
                return false;
            }
        });

        logo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.android.settings", "com.android.settings.BeanBag")
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getBaseContext().startActivityAsUser(intent,
                            new UserHandle(UserHandle.USER_CURRENT));
                } catch (ActivityNotFoundException ex) {
                    android.util.Log.e("XPerienceEasterEgg", "Couldn't catch a break.");
                }
                finish();
                return true;
            }
        });

        setContentView(mContent);
    }
}

/*
 * Copyright (C) 2014, ParanoidAndroid Project
 *
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

package com.android.systemui.statusbar.pie;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.PanelBar;
import com.android.systemui.statusbar.pie.PieController.OnNavButtonPressedListener;
import com.android.internal.statusbar.IStatusBarService;

/**
 * Pie control panel
 * Handles displaying pie and handling key codes
 * Must be initilized
 * On phones: Stores absolute gravity of Pie. All query methods return only
 *            relative gravity (depending on screen rotation).
 */
public class PieControlPanel extends FrameLayout implements OnNavButtonPressedListener {

    private boolean mShowing;
    private boolean mMenuButton;

    private int mInjectKeycode;
    private long mDownTime;
    private int mOrientation;
    private int mWidth;
    private int mHeight;

    private BaseStatusBar mStatusBar;
    private Rect mContentArea;
    private Context mContext;
    private Handler mHandler;
    private KeyguardManager mKeyguardManger;
    private ViewGroup mPieContentFrame;
    private PieController mPieController;
    private boolean mRelocatePieOnRotation;

    /* Analogous to NAVBAR_ALWAYS_AT_RIGHT */
    final static boolean PIE_ALWAYS_AT_RIGHT = true;

    public PieControlPanel(Context context) {
        this(context, null);
    }

    public PieControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mKeyguardManger = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mPieController = PieController.getInstance();
        mContentArea = new Rect();
        mOrientation = Gravity.BOTTOM;
        mMenuButton = false;
        mRelocatePieOnRotation = mContext.getResources().getBoolean(
                R.bool.config_relocatePieOnRotation);
    }

    public boolean currentAppUsesMenu() {
        return mMenuButton;
    }

    public void setMenu(boolean state) {
        mMenuButton = state;
    }

    private int convertAbsoluteToRelativeGravity(int gravity) {
        if (mRelocatePieOnRotation) {
            int rot = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getRotation();

            if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
                // only mess around with Pie in landscape
                if (PIE_ALWAYS_AT_RIGHT) {
                    // no questions asked if right is preferred
                    gravity = Gravity.RIGHT;
                } else if (gravity == Gravity.BOTTOM) {
                    // bottom is now right/left (depends on the direction of rotation)
                    gravity = rot == Surface.ROTATION_90 ? Gravity.RIGHT : Gravity.LEFT;
                } else {
                    // top can't be used so default to bottom
                    gravity = Gravity.BOTTOM;
                }
            }
        }

        return gravity;
    }

    private int convertRelativeToAbsoluteGravity(int gravity) {
        if (mRelocatePieOnRotation) {
            int rot = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getRotation();

            if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
                // only mess around with Pie in landscape
                if (PIE_ALWAYS_AT_RIGHT) {
                    // no questions asked if right is preferred
                    gravity = Gravity.RIGHT;
                } else {
                    // just stick to the edge when possible
                    switch (gravity) {
                    case Gravity.LEFT:
                        gravity = rot == Surface.ROTATION_90 ? Gravity.NO_GRAVITY : Gravity.BOTTOM;
                        break;
                    case Gravity.RIGHT:
                        gravity = rot == Surface.ROTATION_90 ? Gravity.BOTTOM : Gravity.NO_GRAVITY;
                        break;
                    case Gravity.BOTTOM:
                        gravity = rot == Surface.ROTATION_90 ? Gravity.LEFT : Gravity.RIGHT;
                        break;
                    }
                }
            }
        }

        return gravity;
    }

    public int getOrientation() {
        return convertAbsoluteToRelativeGravity(mOrientation);
    }

    public int getDegree() {
        switch(convertAbsoluteToRelativeGravity(mOrientation)) {
            case Gravity.RIGHT:
                return 0;
            case Gravity.BOTTOM:
                return 90;
            case Gravity.LEFT:
                return 180;
        }
        return 0;
    }

    /**
     * Check whether the requested relative gravity is possible. Portrait orientation is assumed to
     * return true for all gravities that might ever be possible on the device even if they are
     * unavailable at the exact moment due to the device being in landscape.
     *
     * @param gravity
     *            the Gravity value to check
     * @return whether the requested relative Gravity is possible
     * @see #isGravityPossible(int, boolean)
     */
    public boolean isGravityPossible(int gravity) {
        return isGravityPossible(gravity, true);
    }

    /**
     * Check whether the requested relative gravity is possible. If the task is to check whether a
     * gravity would ever be available, portrait orientation should be used for the checking instead
     * as some values might not be available on phones in landscape.
     *
     * @param gravity
     *            the Gravity value to check
     * @param forceAssumePortrait
     *            whether the natural orientation should be preferred instead of the actual
     *            orientation
     * @return whether the requested relative Gravity is possible
     * @see #isGravityPossible(int)
     */
    public boolean isGravityPossible(int gravity, boolean forceAssumePortrait) {
        if (mRelocatePieOnRotation) {
            int rot = forceAssumePortrait ? Surface.ROTATION_0 : ((WindowManager) mContext
                    .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

            if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
                if (PIE_ALWAYS_AT_RIGHT) return gravity == Gravity.RIGHT;
            }
        }

        return convertRelativeToAbsoluteGravity(gravity) != Gravity.NO_GRAVITY;
    }

    public BaseStatusBar getBar() {
        return mStatusBar;
    }

    public void init(Handler h, BaseStatusBar statusbar, int orientation) {
        mHandler = h;
        mStatusBar = statusbar;
        mOrientation = orientation;
    }

    public static int convertGravitytoPieGravity(int gravity) {
        switch(gravity) {
            case Gravity.LEFT:
                return 1;
            case Gravity.RIGHT:
                return 2;
            default:
                return 0;
        }
    }

    public static int convertPieGravitytoGravity(int gravity) {
        switch(gravity) {
            case 1:
                return Gravity.LEFT;
            case 2:
                return Gravity.RIGHT;
            default:
                return Gravity.BOTTOM;
        }
    }

    public void reorient(int orientation) {
        mOrientation = convertRelativeToAbsoluteGravity(orientation);
        show(mShowing);
        Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.PIE_GRAVITY,
                            convertGravitytoPieGravity(mOrientation));
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mPieContentFrame = (ViewGroup)findViewById(R.id.pie_content_frame);
        setWillNotDraw(false);
        mPieController.setControlPanel(this);
        show(false);
    }

    public boolean isShowing() {
        return mShowing;
    }

    public PointF getSize() {
        return new PointF(mWidth, mHeight);
    }

    public void show(boolean show) {
        mShowing = show;
        setVisibility(show ? View.VISIBLE : View.GONE);
        mPieController.show(show);
    }

    // we show pie always centered
    public void show() {
        mShowing = true;
        mStatusBar.preloadRecentApps();
        setVisibility(View.VISIBLE);
        Point outSize = new Point(0,0);
        WindowManager windowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(outSize);
        mWidth = outSize.x;
        mHeight = outSize.y;
        switch(getOrientation()) {
            case Gravity.LEFT:
                mPieController.setCenter(0, mHeight / 2);
                break;
            case Gravity.RIGHT:
                mPieController.setCenter(mWidth, mHeight / 2);
                break;
            default:
                mPieController.setCenter(mWidth / 2, mHeight);
                break;
        }
        mPieController.show(true);
    }

    public boolean isInContentArea(int x, int y) {
        mContentArea.left = mPieContentFrame.getLeft() + mPieContentFrame.getPaddingLeft();
        mContentArea.right = mPieContentFrame.getRight() - mPieContentFrame.getPaddingRight();
        mContentArea.bottom = mPieContentFrame.getBottom() - mPieContentFrame.getPaddingBottom();

        return mContentArea.contains(x, y);
    }

    public void onNavButtonPressed(String buttonName) {
        if (buttonName.equals(PieController.BACK_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_BACK);
        } else if (buttonName.equals(PieController.HOME_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_HOME);
        } else if (buttonName.equals(PieController.MENU_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_MENU);
        } else if (buttonName.equals(PieController.RECENT_BUTTON)) {
            mStatusBar.toggleRecentApps();
        } else if (buttonName.equals(PieController.CLEAR_ALL_BUTTON)) {
            mStatusBar.clearRecentApps();
        }
    }

    public void injectKeyDelayed(int keycode){
        mInjectKeycode = keycode;
        mDownTime = SystemClock.uptimeMillis();
        mHandler.removeCallbacks(onInjectKeyDelayed);
        mHandler.postDelayed(onInjectKeyDelayed, 100);
        mStatusBar.cancelPreloadRecentApps();
    }

    final Runnable onInjectKeyDelayed = new Runnable() {
        public void run() {
            final long eventTime = SystemClock.uptimeMillis();
            InputManager.getInstance().injectInputEvent(
                    new KeyEvent(mDownTime, eventTime - 100,
                            KeyEvent.ACTION_DOWN, mInjectKeycode, 0),
                                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            InputManager.getInstance().injectInputEvent(
                    new KeyEvent(mDownTime, eventTime - 50, KeyEvent.ACTION_UP, mInjectKeycode, 0),
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    public boolean isKeyguardSecureShowing() {
        return mKeyguardManger.isKeyguardLocked() && mKeyguardManger.isKeyguardSecure();
    }
}

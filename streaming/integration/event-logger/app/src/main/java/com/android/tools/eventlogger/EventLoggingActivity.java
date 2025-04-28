/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.eventlogger;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class EventLoggingActivity extends Activity {

  private static final String TAG = "EventLogger";
  private static final long POINTER_HIDE_DELAY_MILLIS = 5000;
  private final Runnable pointerHider = () -> this.crosshair.setVisibility(INVISIBLE);
  private FrameLayout layout;
  private ImageView crosshair;
  private Handler scheduler;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    scheduler = new Handler(Looper.getMainLooper());
    getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
    ActionBar actionBar = getActionBar();
    if (actionBar != null) {
      actionBar.hide();
    }
    layout = new FrameLayout(this);
    setContentView(layout);
    crosshair = new ImageView(this);
    crosshair.setImageResource(R.drawable.crosshair);
    crosshair.setScaleType(ImageView.ScaleType.CENTER);
    crosshair.setVisibility(INVISIBLE);
    layout.addView(crosshair);
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.i(TAG, "RESUMED");
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Log.d(TAG, event.toString());
    Log.i(TAG, "KEY DOWN: " + keyCode);
    return true;
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    Log.d(TAG, event.toString());
    Log.i(TAG, "KEY LONG PRESS: " + keyCode);
    return true;
  }

  @Override
  public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
    Log.d(TAG, event.toString());
    Log.i(TAG, "KEY MULTIPLE: " + keyCode + " (count=" + count + ")");
    return true;
  }

  @Override
  public boolean onKeyShortcut(int keyCode, KeyEvent event) {
    Log.d(TAG, event.toString());
    Log.i(TAG, "KEY SHORTCUT: " + keyCode);
    return true;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Log.d(TAG, event.toString());
    Log.i(TAG, "KEY UP: " + keyCode);
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    Log.d(TAG, event.toString());
    Log.i(TAG, "TOUCH EVENT: " + MotionEvent.actionToString(event.getAction()) + pointersToString(event));
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      crosshair.setImageResource(R.drawable.crosshair_filled);
    }
    if (event.getAction() == MotionEvent.ACTION_UP) {
      crosshair.setImageResource(R.drawable.crosshair);
    }
    // TODO: Show multiple pointers for multitouch events.
    showPointer(event.getX(0), event.getY(0));
    return true;
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    Log.d(TAG, event.toString());
    StringBuilder buf = new StringBuilder("MOTION EVENT: ");
    buf.append(MotionEvent.actionToString(event.getAction()));
    if (event.getAction() == MotionEvent.ACTION_SCROLL) {
      processScrollEvent(event, buf);
    }
    else {
      buf.append(pointersToString(event));
      showPointer(event.getX(0), event.getY(0));
    }
    Log.i(TAG, buf.toString());
    return true;
  }

  private void processScrollEvent(MotionEvent event, StringBuilder buf) {
    for (int i = 0; i < event.getPointerCount(); ++i) {
      buf
        // Coordinates of the pointer at the time of the scrolling.
        .append(" (").append(event.getX(i)).append(",").append(event.getY(i)).append(")")
        // Vertical scroll component.
        .append(" v=").append(event.getAxisValue(MotionEvent.AXIS_VSCROLL, i))
        // Horizontal scroll component.
        .append(" h=").append(event.getAxisValue(MotionEvent.AXIS_HSCROLL, i));
    }
  }

  private String pointersToString(MotionEvent event) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < event.getPointerCount(); ++i) {
      result.append(" (").append(event.getX(i)).append(",").append(event.getY(i)).append(")");
    }
    return result.toString();
  }

  private void showPointer(float x, float y) {
    scheduler.removeCallbacks(pointerHider);
    int[] offset = new int[2];
    layout.getLocationInWindow(offset);
    crosshair.setTranslationX(x - offset[0] - layout.getWidth() / 2F);
    crosshair.setTranslationY(y - offset[1] - layout.getHeight() / 2F);
    crosshair.setVisibility(VISIBLE);
    scheduler.postDelayed(pointerHider, POINTER_HIDE_DELAY_MILLIS);
  }
}
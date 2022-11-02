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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.FrameLayout;

public class EventLoggingActivity extends Activity {
  private static final String TAG = "EventLogger";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
    getActionBar().hide();
    FrameLayout frameLayout = new FrameLayout(this);
    setContentView(frameLayout);
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.i(TAG, "RESUMED");
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Log.i(TAG, "KEY DOWN: " + keyCode);
    return true;
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    Log.i(TAG, "KEY LONG PRESS: " + keyCode);
    return true;
  }

  @Override
  public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
    Log.i(TAG, "KEY MULTIPLE: " + keyCode + " (count=" + count + ")");
    return true;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Log.i(TAG, "KEY UP: " + keyCode);
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    Log.i(TAG, "TOUCH EVENT: " + MotionEvent.actionToString(event.getAction()) + " (" + event.getX() + "," + event.getY() + ")");
    return true;
  }
}
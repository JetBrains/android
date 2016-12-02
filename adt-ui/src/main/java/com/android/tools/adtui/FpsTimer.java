/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Timer which tries to tick at a rate to match the passed in fps. Each tick, it will report how
 * much time has passed since the last tick.
 */
public final class FpsTimer extends StopwatchTimer implements ActionListener {
  public static final float ONE_FRAME_IN_60_FPS = 1.0f / FpsTimer.DEFAULT_FPS;

  private static final int DEFAULT_FPS = 60;
  private static final float NANOSECONDS_IN_SECOND = 1000000000.0f;

  private final Timer mTimer;
  private float mFrameTime;

  public FpsTimer(int fps) {
    mTimer = new Timer(1000 / fps, this);
  }

  public FpsTimer() {
    this(DEFAULT_FPS);
  }

  @Override
  public void start() {
    if (!isRunning()) {
      mFrameTime = System.nanoTime();
      mTimer.start();
    }
  }

  @Override
  public boolean isRunning() {
    return mTimer.isRunning();
  }

  @Override
  public void stop() {
    if (isRunning()) {
      mTimer.stop();
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    long now = System.nanoTime();
    float frame = (now - mFrameTime) / NANOSECONDS_IN_SECOND;
    mFrameTime = now;

    tick(frame);
  }
}

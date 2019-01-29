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
package com.android.tools.adtui.model;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Timer which tries to tick at a rate to match the passed in fps. Each tick, it will report how
 * much time has passed since the last tick.
 */
public final class FpsTimer extends StopwatchTimer implements ActionListener {
  public static final long ONE_FRAME_IN_NS = 1000000000 / FpsTimer.DEFAULT_FPS;

  private static final int DEFAULT_FPS = 60;

  private final Timer myTimer;
  private long myFrameTime;

  public FpsTimer(int fps) {
    myTimer = new Timer(1000 / fps, this);
  }

  public FpsTimer() {
    this(DEFAULT_FPS);
  }

  @Override
  public void start() {
    if (!isRunning()) {
      myFrameTime = System.nanoTime();
      myTimer.start();
    }
  }

  @Override
  public boolean isRunning() {
    return myTimer.isRunning();
  }

  @Override
  public void stop() {
    if (isRunning()) {
      myTimer.stop();
    }
  }

  @Override
  public long getCurrentTimeNs() {
    return System.nanoTime();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    long now = System.nanoTime();
    long frame = now - myFrameTime;
    myFrameTime = now;

    tick(frame);
  }
}

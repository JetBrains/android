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

import java.util.concurrent.TimeUnit;

/**
 * Timer that is manually controlled, using {@link #step()}, which is useful for tests.
 */
public final class FakeTimer extends StopwatchTimer {

  public static long ONE_SECOND_IN_NS = TimeUnit.SECONDS.toNanos(1);

  private boolean myRunning;
  private long myCurrentTimeNs;

  @Override
  public void start() {
    myRunning = true;
  }

  @Override
  public boolean isRunning() {
    return myRunning;
  }

  @Override
  public void stop() {
    myRunning = false;
  }

  @Override
  public long getCurrentTimeNs() {
    return myCurrentTimeNs;
  }

  public void setCurrentTimeNs(long currentTimeNs) {
    myCurrentTimeNs = currentTimeNs;
  }

  public boolean step() {
    if (!isRunning()) {
      return false;
    }
    tick(FpsTimer.ONE_FRAME_IN_NS);
    return true;
  }
}

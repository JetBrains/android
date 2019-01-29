/*
 * Copyright (C) 2018 The Android Open Source Project
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

import org.jetbrains.annotations.NotNull;

public class Stopwatch {
  private long myTotalRunningTimeNs;
  private long myStartTimeNs;
  private long myLastDeltaTimeNs;

  @NotNull
  public Stopwatch start() {
    myStartTimeNs = System.nanoTime();
    return this;
  }

  @NotNull
  public Stopwatch stop() {
    myTotalRunningTimeNs = System.nanoTime() - myStartTimeNs;
    myStartTimeNs = 0;
    return this;
  }

  /**
   * @return the amount of time that has elapsed since the last time this function was called, or the last time {@link #start()} was called.
   */
  public long getElapsedSinceLastDeltaNs() {
    long currentTime = System.nanoTime();
    if (myStartTimeNs == 0) {
      myStartTimeNs = currentTime;
    }
    long elapsed = currentTime - Math.max(myStartTimeNs, myLastDeltaTimeNs);
    myLastDeltaTimeNs = currentTime;
    return elapsed;
  }

  /**
   * @return the amount of time that this {@link Stopwatch} has been running for.
   */
  public long getTotalRunningTimeNs() {
    return myTotalRunningTimeNs;
  }
}

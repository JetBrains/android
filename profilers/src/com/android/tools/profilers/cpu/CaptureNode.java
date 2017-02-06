/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.HNode;
import com.android.tools.perflib.vmtrace.ClockType;
import org.jetbrains.annotations.NotNull;

public class CaptureNode extends HNode<MethodModel> {

  /**
   * Start time with GLOBAL clock.
   */
  private long myStartGlobal;

  /**
   * End time with GLOBAL clock.
   */
  private long myEndGlobal;

  /**
   * Start time with THREAD clock.
   */
  private long myStartThread;

  /**
   * End time with THREAD clock.
   */
  private long myEndThread;

  @NotNull
  private ClockType myClockType;

  public CaptureNode() {
    super();
    myClockType = ClockType.GLOBAL;
  }

  @Override
  public long getStart() {
    return myClockType == ClockType.THREAD ? myStartThread : myStartGlobal;
  }

  @Override
  public long getEnd() {
    return myClockType == ClockType.THREAD ? myEndThread : myEndGlobal;
  }

  @Override
  public long duration() {
    return getEnd() - getStart();
  }

  public void setStartGlobal(long startGlobal) {
    myStartGlobal = startGlobal;
  }

  public long getStartGlobal() {
    return myStartGlobal;
  }

  public void setEndGlobal(long endGlobal) {
    myEndGlobal = endGlobal;
  }

  public long getEndGlobal() {
    return myEndGlobal;
  }

  public void setStartThread(long startThread) {
    myStartThread = startThread;
  }

  public long getStartThread() {
    return myStartThread;
  }

  public void setEndThread(long endThread) {
    myEndThread = endThread;
  }

  public long getEndThread() {
    return myEndThread;
  }

  public void setClockType(@NotNull ClockType clockType) {
    myClockType = clockType;
  }

  /**
   * Returns the proportion of time the method was using CPU relative to the total (wall-clock) time that passed.
   */
  public double threadGlobalRatio() {
    long durationThread = myEndThread - myStartThread;
    long durationGlobal = myEndGlobal - myStartGlobal;
    return (double)durationThread / durationGlobal;
  }

  @NotNull
  public ClockType getClockType() {
    return myClockType;
  }
}

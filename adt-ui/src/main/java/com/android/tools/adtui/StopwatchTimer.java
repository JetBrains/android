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

import com.android.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for a timer that can be started and stopped. While running, it regularly fires a tick
 * event which can be listened to by a {@link TickHandler}.
 *
 * Child classes are responsible for calling {@link #tick(float)} to drive the timer.
 */
public abstract class StopwatchTimer {
  public interface TickHandler {
    void onTick(float elapsed);
  }

  @Nullable
  private TickHandler myHandler;

  public final void setHandler(@NotNull TickHandler handler) {
    myHandler = handler;
  }

  public abstract void start();
  public abstract boolean isRunning();
  public abstract void stop();

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  public final void tick(float elapsed) {
    if (myHandler != null) {
      myHandler.onTick(elapsed);
    }
  }
}

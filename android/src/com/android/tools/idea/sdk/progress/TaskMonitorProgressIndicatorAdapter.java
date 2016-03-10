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
package com.android.tools.idea.sdk.progress;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.tools.idea.sdk.legacy.remote.internal.ITaskMonitor;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ITaskMonitor} implementation that wraps a {@link ProgressIndicator}, for interacting with legacy code.
 */
public class TaskMonitorProgressIndicatorAdapter implements ITaskMonitor {
  /**
   * The wrapped ProgressIndicator
   */
  private final ProgressIndicator myProgressIndicator;

  /**
   * ITaskMonitor records progress in integer ticks from 0-myProgressMax, whereas ProgressIndicator stores progress as a double from 0 to 1.
   */
  private int myProgressMax;

  /**
   * The current progress for this monitor. The equivalent progress indicator progress is myProgress/myProgressMax.
   */
  private int myProgress;

  public TaskMonitorProgressIndicatorAdapter(@NotNull ProgressIndicator progress) {
    myProgressIndicator = progress;
  }

  @Override
  public void setDescription(@NotNull String format, Object... args) {
    myProgressIndicator.setText(String.format(format, args));
  }

  @Override
  public void log(@NotNull String format, Object... args) {
    myProgressIndicator.logInfo(String.format(format, args));
  }

  @Override
  public void logError(@NotNull String format, Object... args) {
    myProgressIndicator.logError(String.format(format, args));
  }

  @Override
  public void logVerbose(@NotNull String format, Object... args) {
    // ProgressIndicator doesn't support verbose logging
  }

  @Override
  public void setProgressMax(int max) {
    myProgressMax = max;
  }

  @Override
  public int getProgressMax() {
    return myProgressMax;
  }

  @Override
  public void incProgress(int delta) {
    myProgress += delta;
    myProgressIndicator.setFraction((float)myProgress/(float)myProgressMax);
  }

  @Override
  public int getProgress() {
    return myProgress;
  }

  @Override
  public ITaskMonitor createSubMonitor(final int tickCount) {
    final ITaskMonitor parent = this;

    return new TaskMonitorProgressIndicatorAdapter(myProgressIndicator) {
      @Override
      public void incProgress(int delta) {
        myProgress += delta;
        parent.incProgress((int)((float)delta * (float)tickCount / (float)myProgressMax));
      }
    };
  }

  @Override
  public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
    myProgressIndicator.logError(msgFormat == null ? "" : String.format(msgFormat, args), t);
  }

  @Override
  public void warning(@NonNull String msgFormat, Object... args) {
    myProgressIndicator.logWarning(String.format(msgFormat, args));
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    myProgressIndicator.logInfo(String.format(msgFormat, args));
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
    // ProgressIndicator doesn't support verbose logging
  }
}

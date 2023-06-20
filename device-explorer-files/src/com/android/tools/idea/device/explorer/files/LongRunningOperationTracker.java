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
package com.android.tools.idea.device.explorer.files;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class of all long-running operations that need to be tracked with
 * the progress panel of a {@link DeviceFileExplorerView} instance.
 */
abstract class LongRunningOperationTracker implements Disposable {
  @Nullable private DeviceFileExplorerView myView;
  private final boolean myBackgroundable;
  @NotNull private final DeviceExplorerViewProgressListener myProgressListener = () -> myIsCancelled = true;
  private boolean myIsCancelled;
  private long myStartNanoTime;

  public LongRunningOperationTracker(@NotNull DeviceFileExplorerView view, boolean backgroundable) {
    myView = view;
    myBackgroundable = backgroundable;
  }

  public void cancel() {
    myIsCancelled = true;
  }

  public boolean isCancelled() {
    return myIsCancelled;
  }

  protected boolean isBackgroundable() {
    return myBackgroundable;
  }

  public long getDurationMillis() {
    return (System.nanoTime() - myStartNanoTime) / 1_000_000;
  }

  public void start() {
    if (myView == null) {
      throw new UnsupportedOperationException("A background operation cannot be started again");
    }
    myView.addProgressListener(myProgressListener);
    myView.startProgress();
    myStartNanoTime = System.nanoTime();
    myIsCancelled = false;
  }

  public void stop() {
    if (myView != null) {
      myView.removeProgressListener(myProgressListener);
      myView.stopProgress();
    }
  }

  public void moveToBackground() {
    if (!myBackgroundable) {
      throw new UnsupportedOperationException("This operation is not backgroundable");
    }
    if (myView == null) {
      throw new IllegalStateException("This operation is already in background");
    }
    myView.removeProgressListener(myProgressListener);
    myView.stopProgress();
    myView.stopTreeBusyIndicator();
    myView = null;
  }

  public boolean isInForeground() {
    return myView != null;
  }

  @Override
  public void dispose() {
    stop();
  }

  public void setIndeterminate(boolean indeterminate) {
    if (myView != null) {
      myView.setProgressIndeterminate(indeterminate);
    }
  }

  public void setProgress(double fraction) {
    if (myView != null) {
      myView.setProgressValue(fraction);
    }
  }

  public void setStatusText(@NotNull String text) {
    if (myView != null) {
      myView.setProgressText(text);
    }
  }

  public void setWarningColor() {
    if (myView != null) {
      myView.setProgressWarningColor();
    }
  }
}

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
package com.android.tools.idea.explorer;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * Base class of all long running operations that need to be tracked with the
 * progress panel of a {@link DeviceExplorerView} instance.
 */
abstract class LongRunningOperationTracker implements Disposable {
  @NotNull private DeviceExplorerView myView;
  @NotNull private final DeviceExplorerViewProgressListener myProgressListener;
  private boolean myIsCancelled;
  private long myStartNanoTime;

  public LongRunningOperationTracker(@NotNull DeviceExplorerView view) {
    myView = view;
    myProgressListener = () -> myIsCancelled = true;
  }

  public void cancel() {
    myIsCancelled = true;
  }

  public boolean isCancelled() {
    return myIsCancelled;
  }

  public long getDurationMillis() {
    return (System.nanoTime() - myStartNanoTime) / 1_000_000;
  }

  public void start() {
    myView.addProgressListener(myProgressListener);
    myView.startProgress();
    myStartNanoTime = System.nanoTime();
    myIsCancelled = false;
  }

  public void stop() {
    myView.removeProgressListener(myProgressListener);
    myView.stopProgress();
  }

  @Override
  public void dispose() {
    stop();
  }

  public void setIndeterminate(boolean indeterminate) {
    myView.setProgressIndeterminate(indeterminate);
  }

  public void setProgress(double fraction) {
    myView.setProgressValue(fraction);
  }

  public void setStatusText(@NotNull String text) {
    myView.setProgressText(text);
  }

  public void setWarningColor() {
    myView.setProgressWarningColor();
  }
}

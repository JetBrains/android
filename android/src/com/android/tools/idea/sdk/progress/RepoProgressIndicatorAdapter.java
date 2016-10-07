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

import com.android.repository.api.ProgressIndicator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.NonCancelableSection;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link ProgressIndicator} that wraps a {@link com.intellij.openapi.progress.ProgressIndicator} and a {@link Logger}.
 */
public class RepoProgressIndicatorAdapter implements ProgressIndicator {
  private com.intellij.openapi.progress.ProgressIndicator myWrappedIndicator;
  private Logger myLogger = Logger.getInstance(RepoProgressIndicatorAdapter.class);

  public RepoProgressIndicatorAdapter(@NotNull com.intellij.openapi.progress.ProgressIndicator p) {
    myWrappedIndicator = p;
  }

  @Override
  public void setText(String s) {
    myWrappedIndicator.setText(s);
  }

  @Override
  public boolean isCanceled() {
    return myWrappedIndicator.isCanceled();
  }

  @Override
  public void cancel() {
    myWrappedIndicator.cancel();
  }

  @Override
  public void setCancellable(boolean cancellable) {
  }

  @Override
  public boolean isCancellable() {
    // This isn't actually accurate, but it doesn't seem like there's a general way to do it.
    return !(myWrappedIndicator instanceof NonCancelableSection);
  }

  @Override
  public void setFraction(double v) {
    myWrappedIndicator.setFraction(v);
  }

  @Override
  public double getFraction() {
    return myWrappedIndicator.getFraction();
  }

  @Override
  public void setSecondaryText(String s) {
    myWrappedIndicator.setText2(s);
  }

  @Override
  public void logWarning(String s) {
    myLogger.warn(s);
  }

  @Override
  public void logWarning(String s, Throwable e) {
    myLogger.warn(s, e);
  }

  @Override
  public void logError(String s) {
    myLogger.error(s);
  }

  @Override
  public void logError(String s, Throwable e) {
    myLogger.error(s, e);
  }

  @Override
  public void logInfo(String s) {
    myLogger.info(s);
  }

  @Override
  public boolean isIndeterminate() {
    return myWrappedIndicator.isIndeterminate();
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    myWrappedIndicator.setIndeterminate(indeterminate);
  }
}

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
package com.android.tools.idea.progress;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

/**
 * An IntelliJ {@link ProgressIndicator} that wraps an Android SDK {@link com.android.repository.api.ProgressIndicator}.
 * This allows a method that accepts an IntelliJ ProgressIndicator to provide updates to an SDK ProgressIndicator.
 */
public class StudioProgressIndicatorAdapter implements ProgressIndicator {
  private static final double UPDATE_THRESHOLD = 0.01;

  private final com.android.repository.api.ProgressIndicator myWrapped;

  public StudioProgressIndicatorAdapter(com.android.repository.api.ProgressIndicator wrapped) {
    myWrapped = wrapped;
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  public boolean isRunning() {
    // TODO: Right now ProgressIndicator doesn't have a way to indicate that it has completed successfully. If needed, that case should also
    //       be checked here.
    return !myWrapped.isCanceled();
  }

  @Override
  public void cancel() {
    myWrapped.cancel();
  }

  @Override
  public boolean isCanceled() {
    return myWrapped.isCanceled();
  }

  @Override
  public void setText(String text) {
    myWrapped.setText(text);
  }

  @Override
  public String getText() {
    // TODO: ProgressIndicator currently has no way to retrieve its text and other state. If it's important to be able to retrieve that
    //       state once set, support needs to be added and it should be hooked up here.
    return "";
  }

  @Override
  public void setText2(String text) {
    myWrapped.setSecondaryText(text);

  }

  @Override
  public String getText2() {
    // TODO: ProgressIndicator currently has no way to retrieve its text and other state. If it's important to be able to retrieve that
    //       state once set, support needs to be added and it should be hooked up here.
    return "";
  }

  @Override
  public double getFraction() {
    return myWrapped.getFraction();
  }

  @Override
  public void setFraction(double fraction) {
    if (Math.abs(myWrapped.getFraction() - fraction) < UPDATE_THRESHOLD) {
      return;
    }
    myWrapped.setFraction(fraction);
  }

  @Override
  public void pushState() {
  }

  @Override
  public void popState() {
  }

  @Override
  public boolean isModal() {
    // TODO: ProgressIndicator currently has no way to retrieve its state. If it's important to be able to retrieve that state once set,
    //       support needs to be added and it should be hooked up here.
    return true;
  }

  @NotNull
  @Override
  public ModalityState getModalityState() {
    // IJ concept; ProgressIndicator doesn't know about ModalityState.
    return ModalityState.defaultModalityState();
  }

  @Override
  public void setModalityProgress(ProgressIndicator modalityProgress) {
  }

  @Override
  public boolean isIndeterminate() {
    return myWrapped.isIndeterminate();
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    myWrapped.setIndeterminate(indeterminate);
  }

  @Override
  public void checkCanceled() throws ProcessCanceledException {
    ProgressManager.checkCanceled();
    if (myWrapped.isCanceled() && myWrapped.isCancellable()) {
      throw new ProcessCanceledException();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    return false;
  }

  @Override
  public boolean isShowing() {
    return true;
  }
}

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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

/**
 * A Studio {@link ProgressIndicator} that wraps a {@link com.android.repository.api.ProgressIndicator} and another existing
 * {@link ProgressIndicator}. This allows an invocation that requires a {@link com.android.repository.api.ProgressIndicator} to provides
 * updates to an existing {@link ProgressIndicator}.
 *
 * TODOs below will be addressed as need becomes clear during adoption.
 */
public class StudioProgressIndicatorAdapter implements ProgressIndicator {
  private static final double UPDATE_THRESHOLD = 0.01;

  private final com.android.repository.api.ProgressIndicator myWrapped;
  private final ProgressIndicator myExistingIndicator;

  public StudioProgressIndicatorAdapter(com.android.repository.api.ProgressIndicator wrapped) {
    this(wrapped, ProgressManager.getInstance().getProgressIndicator());
  }

  public StudioProgressIndicatorAdapter(com.android.repository.api.ProgressIndicator wrapped,
                                        ProgressIndicator existing) {
    myWrapped = wrapped;
    myExistingIndicator = existing;
  }

  @Override
  public void start() {
    if (myExistingIndicator != null) {
      myExistingIndicator.start();
    }
  }

  @Override
  public void stop() {
    if (myExistingIndicator != null) {
      myExistingIndicator.stop();
    }
  }

  @Override
  public boolean isRunning() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.isRunning();
    }
    // TODO: Right now ProgressIndicator doesn't have a way to indicate that it has completed successfully. If needed, that case should also
    //       be checked here.
    return !myWrapped.isCanceled();
  }

  @Override
  public void cancel() {
    if (myExistingIndicator != null) {
      myExistingIndicator.cancel();
    }
    myWrapped.cancel();
  }

  @Override
  public boolean isCanceled() {
    if (myExistingIndicator != null) {
      if (myExistingIndicator.isCanceled()) {
        myWrapped.cancel();
      }
      return myExistingIndicator.isCanceled();
    }
    return myWrapped.isCanceled();
  }

  @Override
  public void setText(String text) {
    if (myExistingIndicator != null) {
      myExistingIndicator.setText(text);
    }
    myWrapped.setText(text);
  }

  @Override
  public String getText() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.getText();
    }
    // TODO: ProgressIndicator currently has no way to retrieve its text and other state. If it's important to be able to retrieve that
    //       state once set, support needs to be added and it should be hooked up here.
    return "";
  }

  @Override
  public void setText2(String text) {
    if (myExistingIndicator != null) {
      myExistingIndicator.setText2(text);
    }
    myWrapped.setSecondaryText(text);

  }

  @Override
  public String getText2() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.getText2();
    }
    // TODO: ProgressIndicator currently has no way to retrieve its text and other state. If it's important to be able to retrieve that
    //       state once set, support needs to be added and it should be hooked up here.
    return "";
  }

  @Override
  public double getFraction() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.getFraction();
    }
    // TODO: ProgressIndicator currently has no way to retrieve its progress and other state. If it's important to be able to retrieve that
    //       state once set, support needs to be added and it should be hooked up here.
    return 0;
  }

  @Override
  public void setFraction(double fraction) {
    if (Math.abs(myWrapped.getFraction() - fraction) < UPDATE_THRESHOLD) {
      return;
    }
    if (myExistingIndicator != null) {
      myExistingIndicator.setFraction(fraction);
    }
    myWrapped.setFraction(fraction);
  }

  @Override
  public void pushState() {
    if (myExistingIndicator != null) {
      myExistingIndicator.pushState();
    }
  }

  @Override
  public void popState() {
    if (myExistingIndicator != null) {
      myExistingIndicator.popState();
    }
  }

  @Override
  public void startNonCancelableSection() {
    if (myExistingIndicator != null) {
      myExistingIndicator.startNonCancelableSection();
    }
  }

  @Override
  public void finishNonCancelableSection() {
    if (myExistingIndicator != null) {
      myExistingIndicator.finishNonCancelableSection();
    }
  }

  @Override
  public boolean isModal() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.isModal();
    }
    // TODO: ProgressIndicator currently has no way to retrieve its state. If it's important to be able to retrieve that state once set,
    //       support needs to be added and it should be hooked up here.
    return true;
  }

  @NotNull
  @Override
  public ModalityState getModalityState() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.getModalityState();
    }
    // IJ concept; ProgressIndicator doesn't know about ModalityState.
    return ModalityState.defaultModalityState();
  }

  @Override
  public void setModalityProgress(ProgressIndicator modalityProgress) {
    if (myExistingIndicator != null) {
      myExistingIndicator.setModalityProgress(modalityProgress);
    }
  }

  @Override
  public boolean isIndeterminate() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.isIndeterminate();
    }
    return myWrapped.isIndeterminate();
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    if (myExistingIndicator != null) {
      myExistingIndicator.setIndeterminate(indeterminate);
    }
    myWrapped.setIndeterminate(indeterminate);
  }

  @Override
  public void checkCanceled() throws ProcessCanceledException {
    if ((myExistingIndicator != null && myExistingIndicator.isCanceled()) || myWrapped.isCanceled()) {
      throw new ProcessCanceledException();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.isPopupWasShown();
    }
    return false;
  }

  @Override
  public boolean isShowing() {
    if (myExistingIndicator != null) {
      return myExistingIndicator.isShowing();
    }
    return true;
  }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Operation that is executed prior to installing the application.</p>
 *
 * <p>Type argument specifies the type of the operation return value</p>
 */
public abstract class PreinstallOperation<T> {
  protected final InstallContext myContext;
  private final double myProgressRatio;

  protected PreinstallOperation(InstallContext context, double progressRatio) {
    myContext = context;
    myProgressRatio = progressRatio;
  }

  /**
   * Performs the actual logic
   */
  protected abstract T perform() throws WizardException;

  /**
   * Runs the operation under progress indicator that only gives access to progress portion.
   */
  @Nullable
  public final T execute() throws WizardException {
    if (myContext.isCanceled()) {
      return null;
    }
    return myContext.run(new ThrowableComputable<T, WizardException>() {
      @Override
      public T compute() throws WizardException {
        return perform();
      }
    }, myProgressRatio);
  }

  /**
   * Shows a retry prompt. Throws an exception to stop the setup process if the user preses cancel or returns normally otherwise.
   */
  protected final void promptToRetry(final String prompt, String failureDescription, Exception e) throws WizardException {
    final AtomicBoolean response = new AtomicBoolean(false);
    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        int i = Messages.showYesNoDialog(null, prompt, "Android Studio Setup", "Retry", "Cancel", Messages.getErrorIcon());
        response.set(i == Messages.YES);
      }
    }, application.getAnyModalityState());
    if (!response.get()) {
      throw new WizardException(failureDescription, e);
    }
    else {
      myContext.print(failureDescription + "\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
  }
}

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
package com.android.tools.idea.welcome.install;

import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Keeps installation process state.
 */
public class InstallContext {
  private final File myTempDirectory;
  @Nullable private final ProgressStep myProgressStep;

  public InstallContext(@NotNull File tempDirectory) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myTempDirectory = tempDirectory;
    myProgressStep = null;
  }

  @SuppressWarnings("NullableProblems")
  public InstallContext(@NotNull File tempDirectory, @NotNull ProgressStep progressStep) {
    myTempDirectory = tempDirectory;
    myProgressStep = progressStep;
  }

  public File getTempDirectory() {
    return myTempDirectory;
  }

  public void checkCanceled() throws InstallationCancelledException {
    if (myProgressStep != null && myProgressStep.isCanceled()) {
      throw new InstallationCancelledException();
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void print(String message, ConsoleViewContentType contentType) {
    if (myProgressStep != null) {
      myProgressStep.print(message, contentType);
    }
    else {
      if (contentType == ConsoleViewContentType.ERROR_OUTPUT) {
        System.err.println(message);
      }
      else {
        System.out.println(message);
      }
    }
  }

  public <R, E extends Exception> R run(ThrowableComputable<R, E> operation, double progressRatio) throws E {
    Wrapper<R, E> wrapper = new Wrapper<R, E>(operation);
    if (myProgressStep != null) {
      myProgressStep.run(wrapper, progressRatio);
    }
    else {
      ProgressManager.getInstance().executeProcessUnderProgress(wrapper, new TestingProgressIndicator());
    }
    return wrapper.getResult();
  }

  public void advance(double progress) {
    if (myProgressStep != null) {
      myProgressStep.advance(progress);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static class TestingProgressIndicator extends ProgressIndicatorBase {
    private int previous = 0;

    @Override
    public void setText(String text) {
      System.out.println(text);
    }

    @Override
    public void setText2(String text) {
      System.out.println("Text2: " + text);
    }

    @Override
    public void setFraction(double fraction) {
      int p = (int)Math.floor(fraction * 20);
      if (p > previous) {
        previous = p;
        System.out.print(".");
      }
    }
  }

  private static class Wrapper<R, E extends Exception> implements Runnable {
    private final ThrowableComputable<R, E> myRunnable;
    private volatile R myResult;
    private volatile E myException;

    public Wrapper(ThrowableComputable<R, E> runnable) {
      myRunnable = runnable;
    }

    @Override
    public void run() {
      try {
        myResult = myRunnable.compute();
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        // We know this is not a runtime exception, hence it is the exception from the callable prototype
        //noinspection unchecked
        myException = (E)e;
      }
    }

    private R getResult() throws E {
      if (myException != null) {
        throw myException;
      }
      else {
        return myResult;
      }
    }

  }
}

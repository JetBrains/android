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

import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Keeps installation process state.
 */
public class InstallContext {
  @NotNull private final ProgressStep myProgressStep;

  public InstallContext(@NotNull File tempDirectory, @NotNull ProgressStep progressStep) {
    myProgressStep = progressStep;
  }

  public void checkCanceled() throws InstallationCancelledException {
    if (myProgressStep.isCanceled()) {
      throw new InstallationCancelledException();
    }
  }

  public void print(String message, ConsoleViewContentType contentType) {
    myProgressStep.print(message, contentType);
  }

  public <R, E extends Exception> R run(ThrowableComputable<R, E> operation, double progressRatio) throws E {
    Wrapper<R, E> wrapper = new Wrapper<>(operation);
    myProgressStep.run(wrapper, progressRatio);

    return wrapper.getResult();
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

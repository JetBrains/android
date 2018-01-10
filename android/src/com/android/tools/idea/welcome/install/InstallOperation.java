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

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Operation that is executed when installing the operation.</p>
 * <p/>
 * <p>Type argument specifies the type of the operation return value and the argument</p>
 */
public abstract class InstallOperation<Return, Argument> {
  protected final InstallContext myContext;
  // TODO: replace with ProgressIndicator.createSubProgress
  private final double myProgressRatio;

  protected InstallOperation(InstallContext context, double progressRatio) {
    myContext = context;
    myProgressRatio = progressRatio;
  }

  public static <Return, Argument> InstallOperation<Return, Argument> wrap(@NotNull InstallContext context,
                                                                           @NotNull Function<Argument, Return> function,
                                                                           double progressShare) {
    return new FunctionWrapper<>(context, function, progressShare);
  }

  /**
   * Performs the actual logic
   */
  @NotNull
  protected abstract Return perform(@NotNull ProgressIndicator indicator, @NotNull Argument argument) throws WizardException,
                                                                                                             InstallationCancelledException;

  /**
   * Runs the operation under progress indicator that only gives access to progress portion.
   */
  @NotNull
  public final Return execute(@NotNull final Argument argument) throws WizardException, InstallationCancelledException {
    myContext.checkCanceled();
    if (myProgressRatio == 0) {
      return perform(new EmptyProgressIndicator(), argument);
    }
    else {
      try {
        return myContext.run(new ThrowableComputable<Return, Exception>() {
          @Override
          @Nullable
          public Return compute() throws Exception {
            ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
            if (indicator == null) {
              indicator = new EmptyProgressIndicator();
            }
            return perform(indicator, argument);
          }
        }, myProgressRatio);
      }
      catch (ProcessCanceledException e) {
        throw new InstallationCancelledException();
      }
      catch (Exception e) {
        Throwables.propagateIfPossible(e, WizardException.class, InstallationCancelledException.class);
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Shows a retry prompt. Throws an exception to stop the setup process if the user preses cancel or returns normally otherwise.
   */
  protected final void promptToRetry(@NotNull final String prompt, @NotNull String failureDescription, @Nullable Exception e) throws WizardException {
    final AtomicBoolean response = new AtomicBoolean(false);
    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> {
      String wrappedPrompt = prompt.replaceAll("(\\p{Print}{30,50}([\\h\\n]|$))", "$1\n");
      String wrappedFailure = failureDescription
        .replaceAll("(\\p{Print}{30,50}([\\h\\n]|$))", "$1\n")
        .replaceAll("(\\p{Print}{30,50}/)", "$1\n");
      int i = Messages.showDialog(null, wrappedPrompt, "Android Studio Setup", wrappedFailure, new String[] {"Retry", "Cancel"}, 0, 0,
                                  Messages.getErrorIcon());
      response.set(i == Messages.YES);
    }, application.getAnyModalityState());
    if (!response.get()) {
      if (e != null) {
        Throwables.throwIfInstanceOf(e, WizardException.class);
      }
      throw new WizardException(failureDescription, e);
    }
    else {
      myContext.print(failureDescription + "\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  public abstract void cleanup(@NotNull Return result);

  /**
   * This allows combining a sequence of operations into one, assisting with the cleanup.
   */
  public final <FinalResult> InstallOperation<FinalResult, Argument> then(@NotNull final InstallOperation<FinalResult, Return> next) {
    return new OperationChain<>(this, next);
  }

  /**
   * <p>Adds a function to a sequence, wrapping it into InstallOperation.</p>
   *
   * <p>Note that currently it is expected that the function is fast and there is no progress to report.
   * Another option is to manage progress manually.</p>
   */
  public final <FinalResult> InstallOperation<FinalResult, Argument> then(@NotNull Function<Return, FinalResult> next) {
    return then(wrap(myContext, next, 0));
  }

  private static class OperationChain<FinalResult, Argument, Return> extends InstallOperation<FinalResult, Argument> {
    private final InstallOperation<Return, Argument> myFirst;
    private final InstallOperation<FinalResult, Return> mySecond;

    public OperationChain(InstallOperation<Return, Argument> first, InstallOperation<FinalResult, Return> second) {
      super(first.myContext, 0);
      myFirst = first;
      mySecond = second;
    }

    @Override
    @NotNull
    protected FinalResult perform(@NotNull ProgressIndicator indicator, @NotNull Argument argument)
      throws WizardException, InstallationCancelledException {
      Return execute = myFirst.execute(argument);
      try {
        return mySecond.execute(execute);
      }
      finally {
        myFirst.cleanup(execute);
      }
    }

    @Override
    public void cleanup(@NotNull FinalResult result) {
      mySecond.cleanup(result);
    }
  }

  private static class FunctionWrapper<Return, Argument> extends InstallOperation<Return, Argument> {
    @NotNull private final Function<Argument, Return> myRunnable;

    public FunctionWrapper(@NotNull InstallContext context, @NotNull Function<Argument, Return> runnable, double progressShare) {
      super(context, progressShare);
      myRunnable = runnable;
    }

    @NotNull
    @Override
    protected Return perform(@NotNull ProgressIndicator indicator, @NotNull Argument arg) {
      indicator.start();
      try {
        Return value = myRunnable.apply(arg);
        assert value != null;
        return value;
      }
      finally {
        indicator.setFraction(1.0);
      }
    }

    @Override
    public void cleanup(@NotNull Return result) {
      // Do nothing
    }
  }
}

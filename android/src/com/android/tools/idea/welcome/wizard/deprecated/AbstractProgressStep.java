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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.annotations.concurrency.AnyThread;
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard step with progress bar and "more details" button.
 *
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.AbstractProgressStep}
 */
@Deprecated
public abstract class AbstractProgressStep extends FirstRunWizardStep implements ProgressStep {
  private final ProgressStepForm myForm;
  private ProgressIndicator myProgressIndicator;

  public AbstractProgressStep(
    @NotNull Disposable parentDisposable,
    @NotNull String name,
    @NotNull FirstRunWizardTracker tracker) {
    super(name, tracker);
    myForm = new ProgressStepForm();
    setComponent(myForm.getRoot());
    Disposer.register(parentDisposable, myForm);
  }

  @Override
  public void init() {
    // Do nothing
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    ApplicationManager.getApplication().invokeLater(this::execute);
  }

  protected abstract void execute();

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myForm.getShowDetailsButton();
  }

  /**
   * Returns the progress indicator that will report the progress to this wizard step.
   */
  @AnyThread
  @Override
  @NotNull
  public synchronized ProgressIndicator getProgressIndicator() {
    if (myProgressIndicator == null) {
      myProgressIndicator = new com.android.tools.idea.welcome.wizard.AbstractProgressStep.ProgressIndicatorIntegration(myForm);
    }
    return myProgressIndicator;
  }

  /**
   * Output text to the console pane.
   *
   * @param s The text to print
   * @param contentType Attributes of the text to output
   */
  @AnyThread
  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    myForm.print(s, contentType);
  }

  /**
   * Will output process standard in and out to the console view.
   * <p/>
   * Note: current version does not support collecting user input. We may
   * reconsider this at a later point.
   *
   * @param processHandler process to track
   */
  @AnyThread
  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    myForm.attachToProcess(processHandler);
  }

  /**
   * Returns true if the operation associated with this progress step has been cancelled.
   */
  @AnyThread
  @Override
  public boolean isCanceled() {
    return getProgressIndicator().isCanceled();
  }

  /**
   * Displays console widget if one was not visible already
   */
  public void showConsole() {
    myForm.showConsole();
  }

  /**
   * Executes a runnable under a progress indicator, allocating a specific portion of the
   * overall progress to this runnable.
   *
   * @param runnable The code to execute.
   * @param progressPortion The fraction of the overall progress bar to allocate to this runnable
   *   (between 0.0 and 1.0).
   */
  @AnyThread
  @Override
  public void run(final @NotNull Runnable runnable, double progressPortion) {
    ProgressIndicator progress =
      new com.android.tools.idea.welcome.wizard.AbstractProgressStep.ProgressPortionReporter(getProgressIndicator(), myForm.getFraction(), progressPortion);
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, progress);
  }
}

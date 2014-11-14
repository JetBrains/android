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

import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.wizard.AndroidStudioWizardPath;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardHost;
import com.android.tools.idea.wizard.SingleStepPath;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wizard to setup Android Studio before the first run
 */
public class FirstRunWizard extends DynamicWizard {
  public static final String WIZARD_TITLE = "Android Studio Setup";
  @NotNull private final FirstRunWizardMode myMode;
  /**
   * On the first user click on finish button, we show progress step & perform setup.
   * Second attempt will close the wizard.
   */
  private final AtomicInteger myFinishClicks = new AtomicInteger(0);
  private final SetupJdkPath myJdkPath = new SetupJdkPath();
  private InstallComponentsPath myComponentsPath;

  public FirstRunWizard(@NotNull DynamicWizardHost host, @NotNull FirstRunWizardMode mode) {
    super(null, null, WIZARD_TITLE, host);
    myMode = mode;
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    SetupProgressStep progressStep = new SetupProgressStep();
    myComponentsPath = new InstallComponentsPath(progressStep, myMode);
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      addPath(new SingleStepPath(new FirstRunWelcomeStep()));
    }
    addPath(myJdkPath);
    addPath(myComponentsPath);
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      addPath(new SingleStepPath(new LicenseAgreementStep(getDisposable())));
    }
    addPath(new SingleStepPath(progressStep));
    super.init();
  }

  // We need to show progress page before proceeding closing the wizard.
  @Override
  public void doFinishAction() {
    if (myFinishClicks.incrementAndGet() == 1) {
      doNextAction();
    }
    else {
      assert myFinishClicks.get() <= 2; // Should not take more then 2 clicks
      super.doFinishAction();
    }
  }

  private void doLongRunningOperation(@NotNull final ProgressStep progressStep) throws WizardException {
    for (AndroidStudioWizardPath path : myPaths) {
      if (progressStep.isCanceled()) {
        break;
      }
      if (path instanceof LongRunningOperationPath) {
        ((LongRunningOperationPath)path).runLongOperation();
      }
    }
  }

  @Override
  public void performFinishingActions() {
    // Nothing
  }

  @Override
  protected String getWizardActionDescription() {
    return "Android Studio Setup";
  }

  private class SetupProgressStep extends ProgressStep {
    public SetupProgressStep() {
      super(FirstRunWizard.this.getDisposable());
    }

    @Override
    protected void execute() {
      myHost.runSensitiveOperation(getProgressIndicator(), true, new Runnable() {
        @Override
        public void run() {
          try {
            doLongRunningOperation(SetupProgressStep.this);
          }
          catch (WizardException e) {
            Logger.getInstance(getClass()).error(e);
            showConsole();
            print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
          }
        }
      });
    }

    @Override
    public boolean canGoPrevious() {
      return false;
    }

    /**
     * The goal is not to show this step until the user completes the wizard. So this page is
     * only shown once, after the user clicks finish for the first time.
     */
    @Override
    public boolean isStepVisible() {
      return myFinishClicks.get() == 1 || (!(myJdkPath.showsStep() || myComponentsPath.showsStep()));
    }
  }
}

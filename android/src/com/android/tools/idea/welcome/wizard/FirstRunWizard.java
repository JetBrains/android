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
package com.android.tools.idea.welcome.wizard;

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.wizard.AndroidStudioWizardPath;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardHost;
import com.android.tools.idea.wizard.SingleStepPath;
import com.android.utils.NullLogger;
import com.google.common.collect.Multimap;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wizard to setup Android Studio before the first run
 */
public class FirstRunWizard extends DynamicWizard {
  public static final String WIZARD_TITLE = "Android Studio Setup Wizard";
  @NotNull private final FirstRunWizardMode myMode;
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;
  /**
   * On the first user click on finish button, we show progress step & perform setup.
   * Second attempt will close the wizard.
   */
  private final AtomicInteger myFinishClicks = new AtomicInteger(0);
  private final SetupJdkPath myJdkPath;
  private InstallComponentsPath myComponentsPath;

  public FirstRunWizard(@NotNull DynamicWizardHost host, @NotNull FirstRunWizardMode mode,
                        @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    super(null, null, WIZARD_TITLE, host);
    myMode = mode;
    myJdkPath = new SetupJdkPath(mode);
    myRemotePackages = remotePackages;
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    File initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(myMode);
    SetupProgressStep progressStep = new SetupProgressStep();
    myComponentsPath = new InstallComponentsPath(progressStep, myMode, initialSdkLocation, myRemotePackages);
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      boolean sdkExists = initialSdkLocation.isDirectory() &&
                          SdkManager.createManager(initialSdkLocation.getAbsolutePath(), new NullLogger()) != null;
      addPath(new SingleStepPath(new FirstRunWelcomeStep(sdkExists)));
    }
    addPath(myJdkPath);
    addPath(myComponentsPath);
    if (myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      addPath(new SingleStepPath(new LicenseAgreementStep(getDisposable())));
    }
    addPath(new SingleStepPath(progressStep));
    super.init();
  }

  @Override
  public void doCancelAction() {
    ConfirmFirstRunWizardCloseDialog.Result result = new ConfirmFirstRunWizardCloseDialog().open();
    switch (result) {
      case Skip:
        AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myMode.getInstallerTimestamp());
        // Fallthrough
      case Rerun:
        myHost.close(DynamicWizardHost.CloseAction.CANCEL);
        break;
      case DoNotClose:
        break; // Do nothing
    }

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

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Finishing setup...";
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
    return "Android Studio Setup Wizard";
  }

  public class SetupProgressStep extends ProgressStep {
    private final AtomicBoolean myIsBusy = new AtomicBoolean(false);

    public SetupProgressStep() {
      super(FirstRunWizard.this.getDisposable());
    }

    @Override
    public boolean canGoNext() {
      return super.canGoNext() && !myIsBusy.get();
    }

    @Override
    protected void execute() {
      myIsBusy.set(true);
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
          finally {
            myIsBusy.set(false);
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

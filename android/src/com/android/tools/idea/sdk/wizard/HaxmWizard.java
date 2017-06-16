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
package com.android.tools.idea.sdk.wizard;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.install.*;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.*;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wizard that downloads (if necessary), configures, and installs HAXM.
 */
public class HaxmWizard extends DynamicWizard {
  private static final String SDK_PACKAGE_CLEANUP_FAILED =
    "HAXM installer cleanup failed. The status of the package in the SDK manager may " +
    "be reflected incorrectly. Reinstalling the package may solve the issue" +
    (SystemInfo.isWindows ? " (is the SDK folder opened in another program?)" : ".");

  HaxmPath myHaxmPath;
  boolean myInvokedToUninstall;

  public HaxmWizard(boolean invokedToUninstall) {
    super(null, null, "HAXM");
    myInvokedToUninstall = invokedToUninstall;
    myHaxmPath = new HaxmPath();
    addPath(myHaxmPath);
  }

  @Override
  public void performFinishingActions() {
    // Nothing. Handled by SetupProgressStep.
  }

  @Override
  public void doCancelAction() {
    if (myHaxmPath.canPerformFinishingActions()) {
      doFinishAction();
      return;
    }

    // The wizard was invoked to install, but installer invocation failed or was cancelled.
    // Have to ensure the SDK package is removed
    if (!myInvokedToUninstall) {
      try {
        AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
        ComponentInstaller componentInstaller = new ComponentInstaller(sdkHandler);
        ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
        sdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        componentInstaller.ensureSdkPackagesUninstalled(myHaxmPath.myHaxm.getRequiredSdkPackages(), progress);
      }
      catch (Exception e) {
        Messages.showErrorDialog(SDK_PACKAGE_CLEANUP_FAILED, "Cleanup Error");
        LOG.warn("Failed to make sure HAXM SDK package is uninstalled after HAXM wizard was cancelled", e);
      }
    }
    super.doCancelAction();
  }

  @Override
  public void doFinishAction() {
    if (!myHaxmPath.canPerformFinishingActions()) {
      doCancelAction();
      return;
    }
    super.doFinishAction();
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Finishing install...";
  }

  @Override
  protected String getWizardActionDescription() {
    return "HAXM Installation";
  }

  private static class SetupProgressStep extends ProgressStep {
    private Haxm myHaxm;
    private final AtomicBoolean myIsSuccessfullyCompleted = new AtomicBoolean(false);
    private DynamicWizardHost myHost;
    private StudioLoggerProgressIndicator myProgressIndicator;

    public SetupProgressStep(Disposable parentDisposable, Haxm haxm, DynamicWizardHost host) {
      super(parentDisposable, "Invoking installer");
      myHaxm = haxm;
      myHost = host;
      myProgressIndicator = new StudioLoggerProgressIndicator(getClass());
    }

    @Override
    public boolean canGoNext() {
      return false;
    }

    public boolean isSuccessfullyCompleted() { return myIsSuccessfullyCompleted.get(); }

    @Override
    protected void execute() {
      myHost.runSensitiveOperation(getProgressIndicator(), true, new Runnable() {
        @Override
        public void run() {
          try {
            setupHaxm();
            myIsSuccessfullyCompleted.set(myHaxm.isConfiguredSuccessfully());
          }
          catch (Exception e) {
            LOG.warn("Exception caught while trying to configure HAXM", e);
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

    private void setupHaxm() throws IOException {
      final InstallContext installContext = new InstallContext(FileUtil.createTempDirectory("AndroidStudio", "Haxm", true), this);
      final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      myHaxm.updateState(sdkHandler);
      final ComponentInstaller componentInstaller = new ComponentInstaller(sdkHandler);
      final Collection<? extends InstallableComponent> selectedComponents = Lists.newArrayList(myHaxm);

      double configureHaxmProgressRatio = 1.0;
      if (myHaxm.getInstallationIntention() == Haxm.HaxmInstallationIntention.INSTALL_WITH_UPDATES) {
        configureHaxmProgressRatio = 0.5; // leave the first half of the progress to the updates check & install operation
      }

      InstallOperation<File, File> configureHaxmOperation = InstallOperation.wrap(installContext, new Function<File, File>() {
        @Override
        public File apply(@Nullable File input) {
          myHaxm.configure(installContext, sdkHandler);
          return input;
        }
      }, configureHaxmProgressRatio);

      InstallOperation<File, File> opChain;
      if (myHaxm.getInstallationIntention() == Haxm.HaxmInstallationIntention.INSTALL_WITH_UPDATES) {
        InstallComponentsOperation install =
          new InstallComponentsOperation(installContext, selectedComponents, componentInstaller, 0.5);
        opChain = install.then(configureHaxmOperation);
      }
      else {
        opChain = configureHaxmOperation;
      }

      try {
        opChain.execute(sdkHandler.getLocation());
      }
      catch (InstallationCancelledException e) {
        installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
      }
      catch (WizardException e) {
        throw new RuntimeException(e);
      }
      finally {
        if (!myHaxm.isConfiguredSuccessfully() && myHaxm.getInstallationIntention() != Haxm.HaxmInstallationIntention.UNINSTALL) {
          try {
            // The intention was to install HAXM, but the installation failed. Ensure we don't leave the SDK package behind
            sdkHandler.getSdkManager(myProgressIndicator).reloadLocalIfNeeded(myProgressIndicator);
            componentInstaller
              .ensureSdkPackagesUninstalled(myHaxm.getRequiredSdkPackages(), myProgressIndicator);
          }
          catch (WizardException e) {
            LOG.warn("HAXM SDK package cleanup failed due to an exception", e);
            installContext.print(SDK_PACKAGE_CLEANUP_FAILED, ConsoleViewContentType.ERROR_OUTPUT);
          }
        }
      }
      installContext.print("Done", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  private class HaxmPath extends DynamicWizardPath {
    SetupProgressStep mySetupProgressStep;
    Haxm myHaxm;

    @Override
    protected void init() {
      ScopedStateStore.Key<Boolean> canShow = ScopedStateStore.createKey("ShowHaxmSteps", ScopedStateStore.Scope.PATH, Boolean.class);
      myState.put(canShow, true);
      Haxm.HaxmInstallationIntention haxmInstallationIntention =
        HaxmWizard.this.myInvokedToUninstall ? Haxm.HaxmInstallationIntention.UNINSTALL
                                             : Haxm.HaxmInstallationIntention.INSTALL_WITH_UPDATES;
      myHaxm = new Haxm(haxmInstallationIntention, getState(), canShow);

      for (DynamicWizardStep step : myHaxm.createSteps()) {
        addStep(step);
      }
      if (!HaxmWizard.this.myInvokedToUninstall) {
        addStep(new LicenseAgreementStep(getWizard().getDisposable()));
      }
      mySetupProgressStep = new SetupProgressStep(getWizard().getDisposable(), myHaxm, HaxmWizard.this.myHost);
      addStep(mySetupProgressStep);
      myHaxm.init(mySetupProgressStep);
    }

    @NotNull
    @Override
    public String getPathName() {
      return "Haxm Path";
    }

    @Override
    public boolean canPerformFinishingActions() { return mySetupProgressStep.isSuccessfullyCompleted(); }

    @Override
    public boolean performFinishingActions() {
      return true;
    }
  }
}

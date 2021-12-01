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

import static com.intellij.util.PlatformUtils.getPlatformPrefix;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.install.ComponentInstaller;
import com.android.tools.idea.welcome.install.Gvm;
import com.android.tools.idea.welcome.install.Haxm;
import com.android.tools.idea.welcome.install.InstallComponentsOperation;
import com.android.tools.idea.welcome.install.InstallContext;
import com.android.tools.idea.welcome.install.InstallOperation;
import com.android.tools.idea.welcome.install.InstallableComponent;
import com.android.tools.idea.welcome.install.InstallationCancelledException;
import com.android.tools.idea.welcome.install.InstallationIntention;
import com.android.tools.idea.welcome.install.Vm;
import com.android.tools.idea.sdk.install.VmType;
import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard that downloads (if necessary), configures, and installs VM.
 */
public class VmWizard extends DynamicWizard {
  @NotNull VmType myType;
  @NotNull VmPath myVmPath;
  boolean myInvokedToUninstall;

  public VmWizard(boolean invokedToUninstall, @NotNull VmType type) {
    super(null, null, type.toString());
    myType = type;
    myInvokedToUninstall = invokedToUninstall;
    myVmPath = new VmPath(type);
    addPath(myVmPath);
  }

  @Override
  public void performFinishingActions() {
    // Nothing. Handled by SetupProgressStep.
  }

  @Override
  public void doCancelAction() {
    if (myVmPath.canPerformFinishingActions()) {
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
        componentInstaller.ensureSdkPackagesUninstalled(myVmPath.myVm.getRequiredSdkPackages(), progress);
      }
      catch (Exception e) {
        Messages.showErrorDialog(sdkPackageCleanupFailedMessage(myType), "Cleanup Error");
        LOG.warn("Failed to make sure " + myType + " SDK package is uninstalled after " + myType + " wizard was cancelled", e);
      }
    }
    super.doCancelAction();
  }

  @Override
  public void doFinishAction() {
    if (!myVmPath.canPerformFinishingActions()) {
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
    return myType + " Installation";
  }

  private static class SetupProgressStep extends ProgressStep {
    @NotNull VmType myType;
    @NotNull private Vm myVm;
    @NotNull private final AtomicBoolean myIsSuccessfullyCompleted = new AtomicBoolean(false);
    @NotNull private DynamicWizardHost myHost;
    @NotNull private StudioLoggerProgressIndicator myProgressIndicator;

    SetupProgressStep(@NotNull Disposable parentDisposable,
                      @NotNull Vm vm,
                      @NotNull DynamicWizardHost host,
                      @NotNull VmType type) {
      super(parentDisposable, "Invoking installer");
      myType = type;
      myVm = vm;
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
      myHost.runSensitiveOperation(getProgressIndicator(), true, () -> {
        try {
          setupVm();
          myIsSuccessfullyCompleted.set(myVm.isInstallerSuccessfullyCompleted());
        }
        catch (Exception e) {
          LOG.warn("Exception caught while trying to configure " + myType, e);
          showConsole();
          print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
      });
    }

    @Override
    public boolean canGoPrevious() {
      return false;
    }

    private void setupVm() throws IOException {
      final File tmpDir = FileUtil.createTempDirectory(getPlatformPrefix(), myType.toString(), true);
      final InstallContext installContext = new InstallContext(tmpDir, this);
      final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      myVm.updateState(sdkHandler);
      final ComponentInstaller componentInstaller = new ComponentInstaller(sdkHandler);
      final Collection<? extends InstallableComponent> selectedComponents = Lists.newArrayList(myVm);

      double configureVmProgressRatio = 1.0;
      if (myVm.installationIntention == InstallationIntention.INSTALL_WITH_UPDATES) {
        configureVmProgressRatio = 0.5; // leave the first half of the progress to the updates check & install operation
      }

      InstallOperation<File, File> configureVmOperation = InstallOperation.wrap(installContext, input -> {
        myVm.configure(installContext, sdkHandler);
        return input;
      }, configureVmProgressRatio);

      InstallOperation<File, File> opChain;
      if (myVm.installationIntention == InstallationIntention.INSTALL_WITH_UPDATES) {
        InstallComponentsOperation install =
          new InstallComponentsOperation(installContext, selectedComponents, componentInstaller, 0.5);
        opChain = install.then(configureVmOperation);
      }
      else {
        opChain = configureVmOperation;
      }

      try {
        opChain.execute(sdkHandler.getLocation().toFile());
      }
      catch (InstallationCancelledException e) {
        installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
      }
      catch (WizardException e) {
        throw new RuntimeException(e);
      }
      finally {
        if (!myVm.isInstallerSuccessfullyCompleted() && myVm.installationIntention != InstallationIntention.UNINSTALL) {
          // The intention was to install VM, but the installation failed. Ensure we don't leave the SDK package behind
          sdkHandler.getSdkManager(myProgressIndicator).reloadLocalIfNeeded(myProgressIndicator);
          componentInstaller.ensureSdkPackagesUninstalled(myVm.getRequiredSdkPackages(), myProgressIndicator);
        }
      }
      installContext.print("Done", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  private static String sdkPackageCleanupFailedMessage(@NotNull VmType type) {
    return type + " installer cleanup failed. The status of the package in the SDK manager may " +
           "be reflected incorrectly. Reinstalling the package may solve the issue" +
           (SystemInfo.isWindows ? " (is the SDK folder opened in another program?)" : ".");
  }

  private class VmPath extends DynamicWizardPath {
    @NotNull SetupProgressStep mySetupProgressStep;
    @NotNull VmType myType;
    @NotNull Vm myVm;
    private LicenseAgreementStep myLicenseAgreementStep;

    private VmPath(@NotNull VmType type) {
      myType = type;
    }

    @Override
    protected void init() {
      final String key = "Show" + myType + "Steps";
      ScopedStateStore.Key<Boolean> canShow = ScopedStateStore.createKey(key, ScopedStateStore.Scope.PATH, Boolean.class);
      myState.put(canShow, true);
      InstallationIntention vmInstallationIntention =
        VmWizard.this.myInvokedToUninstall ? InstallationIntention.UNINSTALL : InstallationIntention.INSTALL_WITH_UPDATES;
      myVm = myType == VmType.HAXM ? new Haxm(vmInstallationIntention, canShow)
                                   : new Gvm(vmInstallationIntention, canShow);

      for (DynamicWizardStep step : myVm.createSteps()) {
        addStep(step);
      }
      if (!VmWizard.this.myInvokedToUninstall) {
        addStep(
          myLicenseAgreementStep = new LicenseAgreementStep(getWizard().getDisposable(), () -> myVm.getRequiredSdkPackages(),
                                                            AndroidSdks.getInstance()::tryToChooseSdkHandler)
        );
      }
      mySetupProgressStep = new SetupProgressStep(getWizard().getDisposable(), myVm, VmWizard.this.myHost, myType);
      addStep(mySetupProgressStep);
      myVm.init(mySetupProgressStep);
    }

    @NotNull
    @Override
    public String getPathName() {
      return myType + " Path";
    }

    @Override
    public boolean canPerformFinishingActions() { return mySetupProgressStep.isSuccessfullyCompleted(); }

    @Override
    public boolean performFinishingActions() {
      if (myLicenseAgreementStep != null) {
        myLicenseAgreementStep.performFinishingActions();
      }
      return true;
    }
  }
}
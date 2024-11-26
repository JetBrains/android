
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
import com.android.tools.idea.welcome.install.SdkComponentInstaller;
import com.android.tools.idea.welcome.install.AehdSdkComponent;
import com.android.tools.idea.welcome.install.InstallSdkComponentsOperation;
import com.android.tools.idea.welcome.install.InstallContext;
import com.android.tools.idea.welcome.install.InstallOperation;
import com.android.tools.idea.welcome.install.InstallableSdkComponentTreeNode;
import com.android.tools.idea.welcome.install.InstallationCancelledException;
import com.android.tools.idea.welcome.wizard.IProgressStep;
import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.welcome.wizard.deprecated.AehdInstallInfoStep;
import com.android.tools.idea.welcome.wizard.deprecated.AehdUninstallInfoStep;
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
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
 * Wizard that downloads (if necessary), configures, and installs AEHD.
 */
public class AehdWizard extends DynamicWizard {
  @NotNull private final AehdPath myAehdPath;
  @NotNull private final AehdSdkComponent.InstallationIntention myInstallationIntention;

  public AehdWizard(AehdSdkComponent.InstallationIntention installationIntention) {
    super(null, null, "AEHD");
    myInstallationIntention = installationIntention;
    myAehdPath = new AehdPath();
    addPath(myAehdPath);
  }

  @Override
  public void performFinishingActions() {
    // Nothing. Handled by SetupProgressStep.
  }

  @Override
  public void doCancelAction() {
    if (myAehdPath.canPerformFinishingActions()) {
      doFinishAction();
      return;
    }

    // The wizard was invoked to install, but installer invocation failed or was cancelled.
    // Have to ensure the SDK package is removed
    if (myInstallationIntention.isInstall()) {
      try {
        AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
        SdkComponentInstaller sdkComponentInstaller = new SdkComponentInstaller(sdkHandler);
        ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
        sdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        sdkComponentInstaller.ensureSdkPackagesUninstalled(myAehdPath.myAehdSdkComponent.getRequiredSdkPackages(), progress);
      }
      catch (Exception e) {
        Messages.showErrorDialog(sdkPackageCleanupFailedMessage(), "Cleanup Error");
        LOG.warn("Failed to make sure AEHD SDK package is uninstalled after AEHD wizard was cancelled", e);
      }
    }
    super.doCancelAction();
  }

  @Override
  public void doFinishAction() {
    if (!myAehdPath.canPerformFinishingActions()) {
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
    return "AEHD Installation";
  }

  private static class SetupProgressStep extends ProgressStep implements IProgressStep {
    @NotNull private AehdSdkComponent myAehdSdkComponent;
    @NotNull private final AtomicBoolean myIsSuccessfullyCompleted = new AtomicBoolean(false);
    @NotNull private DynamicWizardHost myHost;
    @NotNull private StudioLoggerProgressIndicator myProgressIndicator;

    SetupProgressStep(@NotNull Disposable parentDisposable,
                      @NotNull AehdSdkComponent aehdSdkComponent,
                      @NotNull DynamicWizardHost host) {
      super(parentDisposable, "Invoking installer");
      myAehdSdkComponent = aehdSdkComponent;
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
          setupAehd();
          myIsSuccessfullyCompleted.set(myAehdSdkComponent.isInstallerSuccessfullyCompleted());
        }
        catch (Exception e) {
          LOG.warn("Exception caught while trying to configure AEHD", e);
          showConsole();
          print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
      });
    }

    @Override
    public boolean canGoPrevious() {
      return false;
    }

    private void setupAehd() throws IOException {
      final File tmpDir = FileUtil.createTempDirectory(getPlatformPrefix(), "AEHD", true);
      final InstallContext installContext = new InstallContext(tmpDir, this);
      final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      myAehdSdkComponent.updateState(sdkHandler);
      final SdkComponentInstaller sdkComponentInstaller = new SdkComponentInstaller(sdkHandler);
      final Collection<? extends InstallableSdkComponentTreeNode> selectedComponents = Lists.newArrayList(myAehdSdkComponent);

      double configureAehdProgressRatio = 1.0;
      if (myAehdSdkComponent.installationIntention.isInstall()) {
        configureAehdProgressRatio = 0.5; // leave the first half of the progress to the updates check & install operation
      }

      InstallOperation<File, File> configureAehdOperation = InstallOperation.wrap(installContext, input -> {
        myAehdSdkComponent.configure(installContext, sdkHandler);
        return input;
      }, configureAehdProgressRatio);

      InstallOperation<File, File> opChain;
      if (myAehdSdkComponent.installationIntention.isInstall()) {
        InstallSdkComponentsOperation install =
          new InstallSdkComponentsOperation(installContext, selectedComponents, sdkComponentInstaller, 0.5);
        opChain = install.then(configureAehdOperation);
      }
      else {
        opChain = configureAehdOperation;
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
        if (!myAehdSdkComponent.isInstallerSuccessfullyCompleted() && myAehdSdkComponent.installationIntention != AehdSdkComponent.InstallationIntention.UNINSTALL) {
          // The intention was to install VM, but the installation failed. Ensure we don't leave the SDK package behind
          sdkHandler.getSdkManager(myProgressIndicator).reloadLocalIfNeeded(myProgressIndicator);
          sdkComponentInstaller.ensureSdkPackagesUninstalled(myAehdSdkComponent.getRequiredSdkPackages(), myProgressIndicator);
        }
      }
      installContext.print("Done", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  private static String sdkPackageCleanupFailedMessage() {
    return "AEHD installer cleanup failed. The status of the package in the SDK manager may " +
           "be reflected incorrectly. Reinstalling the package may solve the issue" +
           (SystemInfo.isWindows ? " (is the SDK folder opened in another program?)" : ".");
  }

  private class AehdPath extends DynamicWizardPath {
    @NotNull SetupProgressStep mySetupProgressStep;
    @NotNull AehdSdkComponent myAehdSdkComponent;
    private LicenseAgreementStep myLicenseAgreementStep;

    @Override
    protected void init() {
      myAehdSdkComponent = new AehdSdkComponent(myInstallationIntention);

      addStep(getInfoStep(myInstallationIntention));

      if (myInstallationIntention != AehdSdkComponent.InstallationIntention.UNINSTALL) {
        addStep(
          myLicenseAgreementStep = new LicenseAgreementStep(getWizard().getDisposable(), () -> myAehdSdkComponent.getRequiredSdkPackages(),
                                                            AndroidSdks.getInstance()::tryToChooseSdkHandler)
        );
      }
      mySetupProgressStep = new SetupProgressStep(getWizard().getDisposable(), myAehdSdkComponent, AehdWizard.this.myHost);
      addStep(mySetupProgressStep);
    }

    @NotNull
    @Override
    public String getPathName() {
      return "AEHD Path";
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

    private DynamicWizardStep getInfoStep(AehdSdkComponent.InstallationIntention installationIntention) {
      return switch (installationIntention) {
        case UNINSTALL -> new AehdUninstallInfoStep();
        case INSTALL_WITH_UPDATES, INSTALL_WITHOUT_UPDATES, CONFIGURE_ONLY -> new AehdInstallInfoStep();
      };
    }
  }
}

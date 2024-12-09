
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

import static com.android.tools.idea.sdk.wizard.AehdWizardUtils.handleCancel;
import static com.android.tools.idea.sdk.wizard.AehdWizardUtils.setupAehd;

import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.welcome.wizard.deprecated.AbstractProgressStep;
import com.android.tools.idea.welcome.wizard.deprecated.AehdInstallInfoStep;
import com.android.tools.idea.welcome.wizard.deprecated.AehdUninstallInfoStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard that downloads (if necessary), configures, and installs AEHD.
 */
public class AehdWizard extends DynamicWizard {
  @NotNull private final AehdPath myAehdPath;
  @NotNull private final AehdSdkComponentTreeNode.InstallationIntention myInstallationIntention;

  public AehdWizard(AehdSdkComponentTreeNode.InstallationIntention installationIntention) {
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

    handleCancel(myInstallationIntention, myAehdPath.myAehdSdkComponentTreeNode, getClass(), LOG);
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

  private static class SetupProgressStep extends AbstractProgressStep implements ProgressStep {
    @NotNull private AehdSdkComponentTreeNode myAehdSdkComponentTreeNode;
    @NotNull private final AtomicBoolean myIsSuccessfullyCompleted = new AtomicBoolean(false);
    @NotNull private DynamicWizardHost myHost;
    @NotNull private StudioLoggerProgressIndicator myProgressIndicator;

    SetupProgressStep(@NotNull Disposable parentDisposable,
                      @NotNull AehdSdkComponentTreeNode aehdSdkComponentTreeNode,
                      @NotNull DynamicWizardHost host) {
      super(parentDisposable, "Invoking installer");
      myAehdSdkComponentTreeNode = aehdSdkComponentTreeNode;
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
          setupAehd(myAehdSdkComponentTreeNode, this, myProgressIndicator);
          myIsSuccessfullyCompleted.set(myAehdSdkComponentTreeNode.isInstallerSuccessfullyCompleted());
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
  }

  private class AehdPath extends DynamicWizardPath {
    @NotNull SetupProgressStep mySetupProgressStep;
    @NotNull AehdSdkComponentTreeNode myAehdSdkComponentTreeNode;
    private LicenseAgreementStep myLicenseAgreementStep;

    @Override
    protected void init() {
      myAehdSdkComponentTreeNode = new AehdSdkComponentTreeNode(myInstallationIntention);

      addStep(getInfoStep(myInstallationIntention));

      if (myInstallationIntention != AehdSdkComponentTreeNode.InstallationIntention.UNINSTALL) {
        addStep(
          myLicenseAgreementStep = new LicenseAgreementStep(getWizard().getDisposable(), () -> myAehdSdkComponentTreeNode.getRequiredSdkPackages(),
                                                            AndroidSdks.getInstance()::tryToChooseSdkHandler)
        );
      }
      mySetupProgressStep = new SetupProgressStep(getWizard().getDisposable(), myAehdSdkComponentTreeNode, AehdWizard.this.myHost);
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

    private DynamicWizardStep getInfoStep(AehdSdkComponentTreeNode.InstallationIntention installationIntention) {
      return switch (installationIntention) {
        case UNINSTALL -> new AehdUninstallInfoStep();
        case INSTALL_WITH_UPDATES, INSTALL_WITHOUT_UPDATES, CONFIGURE_ONLY -> new AehdInstallInfoStep();
      };
    }
  }
}

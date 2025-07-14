
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

import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode;
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.welcome.wizard.deprecated.AbstractProgressStep;
import com.android.tools.idea.welcome.wizard.deprecated.AehdInstallInfoStep;
import com.android.tools.idea.welcome.wizard.deprecated.AehdUninstallInfoStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.google.wireless.android.sdk.stats.SetupWizardEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard that downloads (if necessary), configures, and installs AEHD.
 */
public class AehdWizard extends DynamicWizard {
  @NotNull private final AehdPath myAehdPath;
  @NotNull private final AehdSdkComponentTreeNode.InstallationIntention myInstallationIntention;
  @NotNull private final AehdWizardController myAehdWizardController;
  private final @NotNull FirstRunWizardTracker myTracker;

  public AehdWizard(@NotNull AehdSdkComponentTreeNode.InstallationIntention installationIntention,
                    @NotNull AehdWizardController aehdWizardController,
                    @NotNull FirstRunWizardTracker tracker) {
    super(null, null, "AEHD");
    myInstallationIntention = installationIntention;
    myTracker = tracker;
    myAehdPath = new AehdPath();
    myAehdWizardController = aehdWizardController;
    addPath(myAehdPath);
  }

  @Override
  public void init() {
    myTracker.trackWizardStarted();
    super.init();
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

    myAehdWizardController.handleCancel(myInstallationIntention, myAehdPath.myAehdSdkComponentTreeNode, LOG);
    super.doCancelAction();

    myTracker.trackWizardFinished(SetupWizardEvent.CompletionStatus.CANCELED);
  }

  @Override
  public void doFinishAction() {
    if (!myAehdPath.canPerformFinishingActions()) {
      doCancelAction();
      return;
    }
    super.doFinishAction();

    myTracker.trackWizardFinished(SetupWizardEvent.CompletionStatus.FINISHED);
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
    @NotNull private final AehdWizardController myAehdWizardController;

    SetupProgressStep(@NotNull Disposable parentDisposable,
                      @NotNull AehdSdkComponentTreeNode aehdSdkComponentTreeNode,
                      @NotNull DynamicWizardHost host,
                      @NotNull AehdWizardController aehdWizardController,
                      @NotNull FirstRunWizardTracker tracker) {
      super(parentDisposable, "Invoking installer", tracker);
      myAehdSdkComponentTreeNode = aehdSdkComponentTreeNode;
      myHost = host;
      myProgressIndicator = new StudioLoggerProgressIndicator(getClass());
      myAehdWizardController = aehdWizardController;
    }

    @Override
    public boolean canGoNext() {
      return false;
    }

    public boolean isSuccessfullyCompleted() { return myIsSuccessfullyCompleted.get(); }

    @Override
    protected void execute() {
      myHost.runSensitiveOperation(getProgressIndicator(), true, () -> {
        myTracker.trackInstallingComponentsStarted();
        try {
          myTracker.trackSdkComponentsToInstall(List.of(myAehdSdkComponentTreeNode.sdkComponentsMetricKind()));

          boolean success = myAehdWizardController.setupAehd(myAehdSdkComponentTreeNode, this, myProgressIndicator);
          myIsSuccessfullyCompleted.set(success);
        }
        catch (Exception e) {
          LOG.warn("Exception caught while trying to configure AEHD", e);
          showConsole();
          print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
        finally {
          if (this.isCanceled()) {
            myTracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.CANCELED);
          }
          else if (myIsSuccessfullyCompleted.get()) {
            myTracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.SUCCESS);
          }
          else {
            myTracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.ERROR);
          }
        }
      });
    }

    @Override
    public boolean canGoPrevious() {
      return false;
    }

    @Override
    protected SetupWizardEvent.WizardStep.WizardStepKind getWizardStepKind() {
      return SetupWizardEvent.WizardStep.WizardStepKind.INSTALL_SDK;
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
                                                            AndroidSdks.getInstance()::tryToChooseSdkHandler, myTracker)
        );
      }
      mySetupProgressStep = new SetupProgressStep(
        getWizard().getDisposable(),
        myAehdSdkComponentTreeNode,
        AehdWizard.this.myHost,
        myAehdWizardController,
        myTracker
      );
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
        case UNINSTALL -> new AehdUninstallInfoStep(myTracker);
        case INSTALL_WITH_UPDATES, INSTALL_WITHOUT_UPDATES, CONFIGURE_ONLY -> new AehdInstallInfoStep(myTracker);
      };
    }
  }
}
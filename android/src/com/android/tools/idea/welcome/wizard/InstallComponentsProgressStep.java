/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.google.wireless.android.sdk.stats.SetupWizardEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * Step to show installation progress when installing components
 */
public class InstallComponentsProgressStep extends AbstractProgressStep<FirstRunWizardModel> {
  private final BoolProperty myIsBusyProperty = new BoolProperty() {
    private final AtomicBoolean myIsBusy = new AtomicBoolean(false);

    @Override
    protected void setDirectly(@NotNull Boolean value) {
      myIsBusy.set(value);
    }

    @Override
    public @NotNull Boolean get() {
      return myIsBusy.get();
    }
  };
  private final LicenseAgreementModel myLicenseAgreementModel;
  private final FirstRunWizardTracker myTracker;

  public InstallComponentsProgressStep(
    @NotNull FirstRunWizardModel model,
    @NotNull LicenseAgreementModel licenseAgreementModel,
    @NotNull FirstRunWizardTracker tracker
  ) {
    super(model, "Downloading Components");
    this.myLicenseAgreementModel = licenseAgreementModel;
    this.myTracker = tracker;
  }

  @Override
  protected void onWizardStarting(ModelWizard.@NotNull Facade wizard) {
    super.onWizardStarting(wizard);
    wizard.updateNavigationProperties();
  }

  @Override
  protected boolean shouldShow() {
    Path sdkLocation = getModel().getSdkInstallLocation();
    return sdkLocation != null && SdkLocationUtils.isWritable(sdkLocation);
  }

  @Override
  protected boolean canGoBack() {
    return false;
  }

  @Override
  protected @NotNull ObservableBool canGoForward() {
    return myIsBusyProperty.not().and(super.canGoForward());
  }

  @Override
  protected void execute() {
    myIsBusyProperty.set(true);

    final Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();

    Task.Backgroundable task = new Task.Backgroundable(null, "Android Studio Setup Wizard", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        boolean wasSuccess = false;
        myTracker.trackInstallingComponentsStarted();

        try {
          myLicenseAgreementModel.acceptLicenses();
          getModel().installComponents(InstallComponentsProgressStep.this);
          wasSuccess = true;
        }
        catch (WizardException e) {
          Logger.getInstance(getClass()).error(e);
          showConsole();
          print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
        finally {
          if (InstallComponentsProgressStep.this.isCanceled()) {
            myTracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.CANCELED);
          } else if (wasSuccess) {
            myTracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.SUCCESS);
          } else {
            myTracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.ERROR);
          }

          myIsBusyProperty.set(false);
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, getProgressIndicator());
  }
}

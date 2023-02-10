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

import static com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.wizard.ConfirmFirstRunWizardCloseDialog;
import com.android.tools.idea.welcome.wizard.StudioFirstRunWelcomeScreen;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard to setup Android Studio before the first run
 * @deprecated use {@link StudioFirstRunWelcomeScreen}
 */
@Deprecated
public class FirstRunWizard extends DynamicWizard {
  private static final String WIZARD_TITLE = "Android Studio Setup Wizard";
  public static final ScopedStateStore.Key<Boolean> KEY_CUSTOM_INSTALL =
    ScopedStateStore.createKey("custom.install", ScopedStateStore.Scope.WIZARD, Boolean.class);

  @NotNull private final FirstRunWizardMode myMode;
  /**
   * On the first user click on finish button, we show progress step & perform setup.
   * Second attempt will close the wizard.
   */
  private final AtomicInteger myFinishClicks = new AtomicInteger(0);
  private InstallComponentsPath myComponentsPath;

  public FirstRunWizard(@NotNull DynamicWizardHost host,
                        @NotNull FirstRunWizardMode mode) {
    super(null, null, WIZARD_TITLE, host);
    myMode = mode;
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    File initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(myMode);
    ConsolidatedProgressStep progressStep = new FirstRunProgressStep();
    myComponentsPath = new InstallComponentsPath(myMode, initialSdkLocation, progressStep, true);
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      boolean sdkExists = false;
      if (initialSdkLocation.isDirectory()) {
        AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, initialSdkLocation.toPath());
        ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
        sdkExists = !sdkHandler.getSdkManager(progress).getPackages().getLocalPackages().isEmpty();
      }
      addPath(new SingleStepPath(new FirstRunWelcomeStep(sdkExists)));
      if (initialSdkLocation.getPath().isEmpty()) {
        // We don't have a default path specified, have to do custom install.
        myState.put(KEY_CUSTOM_INSTALL, true);
      }
      else {
        addPath(new SingleStepPath(new InstallationTypeWizardStep(KEY_CUSTOM_INSTALL)));
      }
      addPath(new SingleStepPath(new SelectThemeStep()));
    }
    if (myMode == FirstRunWizardMode.MISSING_SDK) {
      addPath(new SingleStepPath(new MissingSdkAlertStep()));
    }

    addPath(myComponentsPath);
    conditionallyAddEmulatorSettingsStep();

    addPath(new SingleStepPath(progressStep));
    super.init();
  }

  private void conditionallyAddEmulatorSettingsStep() {
    if (!SystemInfo.isLinux) {
      return;
    }

    if (isChromeOSAndIsNotHWAccelerated()) {
      return;
    }

    if (!myMode.equals(FirstRunWizardMode.NEW_INSTALL)) {
      return;
    }

    addPath(new SingleStepPath(new LinuxHaxmInfoStep()));
  }

  @Override
  public void doCancelAction() {
    ConfirmFirstRunWizardCloseDialog.Result result = ConfirmFirstRunWizardCloseDialog.show();
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

  @Override
  public void performFinishingActions() {
    // Nothing
  }

  @Override
  protected String getWizardActionDescription() {
    return "Android Studio Setup Wizard";
  }

  private class FirstRunProgressStep extends ConsolidatedProgressStep {
    public FirstRunProgressStep() {
      super(getDisposable(), myHost);
      setPaths(myPaths);
    }

    /**
     * The goal is not to show this step until the user completes the wizard. So this page is
     * only shown once, after the user clicks finish for the first time.
     */
    @Override
    public boolean isStepVisible() {
      return myFinishClicks.get() == 1 && myComponentsPath.shouldDownloadingComponentsStepBeShown();
    }
  }
}

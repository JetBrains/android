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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.SdkLoadedCallback;
import com.android.tools.idea.sdk.SdkLoggerIntegration;
import com.android.tools.idea.sdk.SdkPackages;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.internal.updater.SdkUpdaterNoWindow;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ModelWizardStep} responsible for installing all selected packages before allowing the user the proceed.
 * This class extends {@link ModelWizardStep.WithoutModel} since this step only acts as a middleman between the user
 * accepting the packages and an InstallTask installing the packages in the background. No model is needed since no data
 * is recorded.
 */
public final class InstallSelectedPackagesStep extends ModelWizardStep.WithoutModel {
  private final BoolProperty installFailed = new BoolValueProperty();
  private final BoolProperty installationFinished = new BoolValueProperty();

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;

  private JPanel myContentPanel;
  private JBLabel myLabelSdkPath;
  private JBLabel myProgressOverallLabel;
  private JTextArea mySdkManagerOutput;
  private JProgressBar myProgressBar;
  private JBLabel myProgressDetailLabel;

  /**
   * Will be {@code null} until set by a background thread.
   */
  private Boolean myBackgroundSuccess;
  private List<IPkgDesc> myInstallRequests;
  private AndroidSdkData mySdkData;

  public InstallSelectedPackagesStep(@NotNull List<IPkgDesc> installRequests, @NotNull AndroidSdkData data) {
    super("Component Installer");
    myInstallRequests = installRequests;
    mySdkData = data;
    myValidatorPanel = new ValidatorPanel(this, myContentPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, "Installing Requested Components");
  }

  private static Logger getLog() {
    return Logger.getInstance(InstallSelectedPackagesStep.class);
  }

  /**
   * Look through the list of completed changes, and set a key if any new platforms
   * were installed.
   */
  private static void checkForUpgrades(@Nullable List<IPkgDesc> completedChanges) {
    if (completedChanges == null) {
      return;
    }
    Integer highestNewApiLevel = 0;
    for (IPkgDesc pkgDesc : completedChanges) {
      if (pkgDesc.getType().equals(PkgType.PKG_PLATFORM)) {
        AndroidVersion version = pkgDesc.getAndroidVersion();
        if (version != null && version.getApiLevel() > highestNewApiLevel) {
          highestNewApiLevel = version.getApiLevel();
        }
      }
    }
    if (highestNewApiLevel > 0) {
      // TODO: Fix this code after we delete WizardConstants
      PropertiesComponent.getInstance().setValue(WizardConstants.NEWLY_INSTALLED_API_KEY.name, highestNewApiLevel, -1);
    }
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    // This will show a warning to the user once installation starts and will disable the next/finish button until installation finishes
    myValidatorPanel.registerValidator(installationFinished, new Validator<Boolean>() {
      @NotNull
      @Override
      public Result validate(@NotNull Boolean value) {
        return (value) ? Result.OK : new Result(Severity.INFO, "Please wait until the installation finishes to continue");
      }
    });


    myValidatorPanel.registerValidator(installFailed, new Validator<Boolean>() {
      @NotNull
      @Override
      public Result validate(@NotNull Boolean value) {
        String error = "Install Failed. Please check your network connection and try again. " +
                       "You may continue with creating your project, but it will not compile correctly " +
                       "without the missing components.";

        return (value) ? new Result(Severity.ERROR, error) : Result.OK;
      }
    });
  }

  @Override
  protected void onEntering() {
    mySdkManagerOutput.setText("");
    myLabelSdkPath.setText(mySdkData.getLocation().getPath());

    startSdkInstall();
  }

  @Override
  public void dispose() {
    installationFinished.set(true);
  }

  @Override
  protected boolean shouldShow() {
    return myInstallRequests.size() > 0;
  }

  @Override
  public boolean canGoBack() {
    return false;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return installationFinished;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  private void startSdkInstall() {
    final CustomLogger logger = new CustomLogger();

    SdkLoadedCallback onSdkAvailable = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        // Since this is only run on complete (not on local complete) it's ok that we're not using the passed-in packages
        final ArrayList<String> requestedPackages = Lists.newArrayList();
        for (IPkgDesc packageDesc : myInstallRequests) {
          if (packageDesc != null) {
            // We look for the package in the local SDK to avoid installing duplicates
            LocalSdk localSdk = mySdkData.getLocalSdk();
            if (localSdk.getPkgInfo(packageDesc) == null) {
              requestedPackages.add(packageDesc.getInstallId());
            }
          }
        }

        InstallTask task = new InstallTask(mySdkData, requestedPackages, logger);
        BackgroundableProcessIndicator indicator = new BackgroundableProcessIndicator(task);
        logger.setIndicator(indicator);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
      }
    };

    // loadAsync checks if the timeout expired and/or loads the SDK if it's not loaded yet.
    // If needed, it does a backgroundable Task to load the SDK and then calls onSdkAvailable.
    // Otherwise it returns false, in which case we call onSdkAvailable ourselves.
    logger.info("Loading SDK information...\n");
    SdkState sdkState = SdkState.getInstance(mySdkData);
    sdkState.loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, onSdkAvailable, null,
                       false);  // TODO(jbakermalone): display something on error?
  }

  private class InstallTask extends Task.Backgroundable {
    @NotNull private final AndroidSdkData mySdkData;
    @NotNull private final ArrayList<String> myRequestedPackages;
    @NotNull private final ILogger myLogger;

    private InstallTask(@NotNull AndroidSdkData sdkData, @NotNull ArrayList<String> requestedPackages, @NotNull ILogger logger) {
      super(null, "Installing Android SDK", false, PerformInBackgroundOption.ALWAYS_BACKGROUND);
      mySdkData = sdkData;
      myRequestedPackages = requestedPackages;
      myLogger = logger;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      // This runs in a background task and isn't interrupted.

      // Perform the install by using the command-line interface and dumping the output into the logger.
      // The command-line API is a bit archaic and has some drastic limitations, one of them being that
      // it blindly re-install stuff even if already present IIRC.

      SdkManager sdkManager = SdkManager.createManager(mySdkData.getLocalSdk());
      final SdkUpdaterNoWindow upd = new SdkUpdaterNoWindow(mySdkData.getLocation().getPath(), sdkManager, myLogger, false, null, null);

      installationFinished.addListener(new InvalidationListener() {
        @Override
        public void onInvalidated(@NotNull ObservableValue<?> sender) {
          upd.cancel();
        }
      });

      // will block until the update is done
      upd.updateAll(myRequestedPackages, true, false, null, true);

      // myBackgroundSuccess is updated on a different thread and may not be ready when we reach this point.
      int retryCount = 0;
      while (myBackgroundSuccess == null) {
        TimeoutUtil.sleep(100);

        retryCount++;
        if (retryCount == 100) {
          assert false;

          // Don't wait forever, assume failure
          myBackgroundSuccess = Boolean.FALSE;
          break;
        }
      }

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myProgressBar.setValue(100);
          myProgressOverallLabel.setText("");

          if (!myBackgroundSuccess) {
            installFailed.set(true);
            myProgressBar.setEnabled(false);
          }
          else {
            myProgressDetailLabel.setText("Done");
            checkForUpgrades(myInstallRequests);
          }
          installationFinished.set(true);

          VirtualFile sdkDir = LocalFileSystem.getInstance().findFileByIoFile(mySdkData.getLocation());
          if (sdkDir != null) {
            sdkDir.refresh(true, true);
          }
          mySdkData.getLocalSdk().clearLocalPkg(PkgType.PKG_ALL);
        }
      });
    }
  }

  private final class CustomLogger extends SdkLoggerIntegration {
    @Override
    protected void setProgress(int progress) {
      myProgressBar.setValue(progress);
    }

    @Override
    protected void setDescription(String description) {
      myProgressDetailLabel.setText(description);
    }

    @Override
    protected void setTitle(String title) {
      myProgressOverallLabel.setText(title);
    }

    @Override
    protected void lineAdded(String string) {
      String current = mySdkManagerOutput.getText();
      if (current == null) {
        current = "";
      }
      mySdkManagerOutput.setText(current + string);
      if (string.contains("Nothing was installed") ||
          string.contains("Failed") ||
          string.contains("The package filter removed all packages")) {
        myBackgroundSuccess = Boolean.FALSE;
      }
      else if (string.contains("Done") && !string.contains("othing")) {
        myBackgroundSuccess = Boolean.TRUE;
      }
    }
  }
}

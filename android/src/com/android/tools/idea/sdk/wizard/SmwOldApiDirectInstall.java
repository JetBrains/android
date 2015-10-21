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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.*;
import com.android.tools.idea.sdk.remote.internal.updater.SdkUpdaterNoWindow;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.WizardConstants.NEWLY_INSTALLED_API_KEY;


public class SmwOldApiDirectInstall extends DynamicWizardStepWithDescription {
  private Logger LOG = Logger.getInstance(SmwOldApiDirectInstall.class);
  private JBLabel myLabelSdkPath;
  private JTextArea mySdkManagerOutput;
  private JBLabel myLabelProgress1;
  private JProgressBar myProgressBar;
  private JBLabel myLabelProgress2;
  private JLabel myErrorLabel;
  private JPanel myContentPanel;
  private boolean myInstallFinished;
  private Boolean myBackgroundSuccess = null;

  public SmwOldApiDirectInstall(@NotNull Disposable disposable) {
    super(disposable);
    setBodyComponent(myContentPanel);
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    mySdkManagerOutput.setText("");
    startSdkInstallUsingNonSwtOldApi();
  }

  @Override
  public boolean isStepVisible() {
    return myState.listSize(INSTALL_REQUESTS_KEY) > 0;
  }

  @Override
  public boolean validate() {
    return myInstallFinished;
  }

  @Override
  public boolean canGoPrevious() {
    return myInstallFinished;
  }

  //-----------

  private void startSdkInstallUsingNonSwtOldApi() {
    // Get the SDK instance.
    final AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    SdkState sdkState = sdkData == null ? null : SdkState.getInstance(sdkData);

    if (sdkState == null) {
      myErrorLabel.setText("Error: can't get SDK instance.");
      myErrorLabel.setForeground(JBColor.RED);
      return;
    }

    File androidSdkPath = IdeSdks.getAndroidSdkPath();
    if (androidSdkPath != null && androidSdkPath.exists() && !androidSdkPath.canWrite()) {
      myErrorLabel.setText(String.format("SDK folder is read-only: '%1$s'", androidSdkPath.getPath()));
      myErrorLabel.setForeground(JBColor.RED);
      return;
    }

    myLabelSdkPath.setText(sdkData.getLocation().getPath());

    final CustomLogger logger = new CustomLogger();

    SdkLoadedCallback onSdkAvailable = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        // Since this is only run on complete (not on local complete) it's ok that we're not using the passed-in packages

        // TODO: since the local SDK has been parsed, this is now a good time
        // to filter requestedPackages to remove current installed packages.
        // That's because on Windows trying to update some of the packages in-place
        // *will* fail (e.g. typically the android platform or the tools) as the
        // folder is most likely locked.
        // As mentioned in InstallTask below, the shortcut we're taking here will
        // install all the requested packages, even if already installed, which is
        // useless so that's another incentive to remove already installed packages
        // from the requested list.

        final ArrayList<String> requestedPackages = Lists.newArrayList();
        List requestedChanges = myState.get(INSTALL_REQUESTS_KEY);
        if (requestedChanges == null) {
          // This should never occur
          myInstallFinished = true;
          invokeUpdate(null);
          return;
        }

        for (Object object : requestedChanges) {
          try {
            IPkgDesc packageDesc = (IPkgDesc)object;
            if (packageDesc != null) {
              // TODO use localSdk to filter list and remove already installed items
              requestedPackages.add(packageDesc.getInstallId());
            }
          } catch (ClassCastException e) {
            LOG.error(e);
          }
        }

        InstallTask task = new InstallTask(sdkData, requestedPackages, logger);
        BackgroundableProcessIndicator indicator = new BackgroundableProcessIndicator(task);
        logger.setIndicator(indicator);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
      }
    };

    // loadAsync checks if the timeout expired and/or loads the SDK if it's not loaded yet.
    // If needed, it does a backgroundable Task to load the SDK and then calls onSdkAvailable.
    // Otherwise it returns false, in which case we call onSdkAvailable ourselves.
    logger.info("Loading SDK information...\n");
    sdkState.loadAsync(1000 * 3600 * 24, false, null, onSdkAvailable, null, false);  // TODO(jbakermalone): display something on error?
  }

  @NotNull
  @Override
  public String getStepName() {
    return "InstallingSDKComponentsStep";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Installing Requested Components";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  private class InstallTask extends Task.Backgroundable {
    @NotNull private final AndroidSdkData mySdkData;
    @NotNull private final ArrayList<String> myRequestedPackages;
    @NotNull private final ILogger myLogger;

    private InstallTask(@NotNull AndroidSdkData sdkData,
                        @NotNull ArrayList<String> requestedPackages,
                        @NotNull ILogger logger) {
      super(null /*project*/,
            "Installing Android SDK",
            false /*canBeCancelled*/,
            PerformInBackgroundOption.ALWAYS_BACKGROUND);
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
      SdkUpdaterNoWindow upd = new SdkUpdaterNoWindow(
        mySdkData.getLocation().getPath(),
        sdkManager,
        myLogger,
        false,  // force -- The reply to any question asked by the update process.
               //          Currently this will be yes/no for ability to replace modified samples, restart ADB, restart on locked win folder.
        null,  // proxyPort -- An optional HTTP/HTTPS proxy port. Can be null. -- Can we get it from Studio?
        null); // proxyHost -- An optional HTTP/HTTPS proxy host. Can be null. -- Can we get it from Studio?

      upd.updateAll(myRequestedPackages,
                    true,   // all
                    false,  // dryMode
                    null,   // acceptLicense
                    true);  // includeDependencies

      while (myBackgroundSuccess == null) {
        TimeoutUtil.sleep(100);
      }

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myProgressBar.setValue(100);
          myLabelProgress1.setText("");
          if (!myBackgroundSuccess) {
            myLabelProgress2.setText("<html>Install Failed. Please check your network connection and try again. " +
                                     "You may continue with creating your project, but it will not compile correctly " +
                                     "without the missing components.</html>");
            myLabelProgress2.setForeground(JBColor.RED);
            myProgressBar.setEnabled(false);
          } else {
            myLabelProgress2.setText("Done");
            List requestedChanges = myState.get(INSTALL_REQUESTS_KEY);
            checkForUpgrades(requestedChanges);
            myState.remove(INSTALL_REQUESTS_KEY);
          }
          myInstallFinished = true;
          invokeUpdate(null);

          VirtualFile sdkDir = LocalFileSystem.getInstance().findFileByIoFile(mySdkData.getLocation());
          if (sdkDir != null) {
            sdkDir.refresh(true, true);
          }
          mySdkData.getLocalSdk().clearLocalPkg(PkgType.PKG_ALL);
        }
      });
    }
  }

  /**
   * Look through the list of completed changes, and set a key if any new platforms
   * were installed.
   */
  private void checkForUpgrades(@Nullable List completedChanges) {
    if (completedChanges == null) {
      return;
    }
    int highestNewApiLevel = 0;
    for (Object o : completedChanges) {
      if (! (o instanceof IPkgDesc)) {
        continue;
      }
      IPkgDesc pkgDesc = (IPkgDesc)o;
      if (pkgDesc.getType().equals(PkgType.PKG_PLATFORM)) {
        AndroidVersion version = pkgDesc.getAndroidVersion();
        if (version != null && version.getApiLevel() > highestNewApiLevel) {
          highestNewApiLevel = version.getApiLevel();
        }
      }
    }
    if (highestNewApiLevel > 0) {
      myState.put(NEWLY_INSTALLED_API_KEY, highestNewApiLevel);
    }
  }

  private final class CustomLogger extends SdkLoggerIntegration {
    @Override
    protected void setProgress(int progress) {
      myProgressBar.setValue(progress);
    }

    @Override
    protected void setDescription(String description) {
      myLabelProgress2.setText(description);
    }

    @Override
    protected void setTitle(String title) {
      myLabelProgress1.setText(title);
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
        myBackgroundSuccess = false;
      } else if (string.contains("Done") && !string.contains("othing")) {
        myBackgroundSuccess = true;
      }
    }
  }
}

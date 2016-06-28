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
package com.android.tools.idea.run;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApkInstaller {
  private static final Logger LOG = Logger.getInstance(ApkInstaller.class);

  @NotNull private final Project myProject;
  @NotNull private final LaunchOptions myLaunchOptions;
  @NotNull private final InstalledApkCache myInstalledApkCache;
  @NotNull private final ConsolePrinter myPrinter;

  public ApkInstaller(@NotNull Project project,
                      @NotNull LaunchOptions options,
                      @NotNull InstalledApkCache installedApkCache,
                      @NotNull ConsolePrinter printer) {
    myProject = project;
    myLaunchOptions = options;
    myInstalledApkCache = installedApkCache;
    myPrinter = printer;
  }

  /**
   * Installs the given apk on the device.
   * @return whether the installation was successful
   */
  public boolean uploadAndInstallApk(@NotNull IDevice device,
                                     @NotNull String packageName,
                                     @NotNull File localFile,
                                     @NotNull LaunchStatus launchStatus) {
    if (!needsInstall(device, localFile, packageName)) {
      return true;
    }

    String remotePath = "/data/local/tmp/" + packageName;
    myPrinter.stdout("$ adb push " + localFile + " " + remotePath);

    try {
      device.pushFile(localFile.getPath(), remotePath);
      boolean installed = installApp(device, remotePath, packageName, launchStatus);
      if (installed) {
        myInstalledApkCache.setInstalled(device, localFile, packageName);
      }
      return installed;
    } catch (Exception e) {
      myPrinter.stderr(e.toString());
      return false;
    }
  }

  @VisibleForTesting
  boolean needsInstall(@NotNull IDevice device, @NotNull File localFile, @NotNull String packageName) {
    if (!myLaunchOptions.isSkipNoopApkInstallations()) {
      return true;
    }

    try {
      Integer userId = LaunchUtils.getUserIdFromFlags(myLaunchOptions.getPmInstallOptions());
      if (!myInstalledApkCache.isInstalled(device, localFile, packageName, userId)) {
        return true;
      }
    } catch (IOException e) {
      return true;
    }

    myPrinter.stdout("No apk changes detected since last installation, skipping installation of " + localFile.getPath());
    if (myLaunchOptions.isForceStopRunningApp()) {
      forceStopPackageSilently(device, packageName, true);
    }

    return false;
  }

  private void forceStopPackageSilently(@NotNull IDevice device, @NotNull String packageName, boolean ignoreErrors) {
    String command = "am force-stop " + packageName;
    myPrinter.stdout("$ adb shell " + command);
    try {
      device.executeShellCommand(command, new NullOutputReceiver(), 1, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      if (!ignoreErrors) {
        throw new RuntimeException(e);
      }
    }
  }

  private boolean installApp(@NotNull IDevice device,
                             @NotNull String remotePath,
                             @NotNull String packageName,
                             @NotNull LaunchStatus launchStatus) throws IOException, AdbCommandRejectedException, TimeoutException {
    InstallResult result = null;
    boolean retry = true;

    while (!launchStatus.isLaunchTerminated() && retry) {
      result = installApp(device, remotePath, launchStatus);
      if (result.installOutput != null) {
        if (result.failureCode == InstallResult.FailureCode.NO_ERROR) {
          myPrinter.stdout(result.installOutput);
        }
        else {
          myPrinter.stderr(result.installOutput);
        }
      }

      switch (result.failureCode) {
        case DEVICE_NOT_RESPONDING:
          int waitTime = 2;
          myPrinter.stdout("Device is not ready. Waiting for " + waitTime + " seconds.");
          try {
            TimeUnit.SECONDS.sleep(waitTime);
          }
          catch (InterruptedException e) {
            LOG.info(e);
          }
          retry = true;
          break;
        case INSTALL_FAILED_VERSION_DOWNGRADE:
          String reason = AndroidBundle
            .message("deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.version.downgrade"));
          retry = showPrompt(reason) && uninstallPackage(device, packageName);
          break;
        case INCONSISTENT_CERTIFICATES:
          reason = AndroidBundle
            .message("deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.different.signature"));
          retry = showPrompt(reason) && uninstallPackage(device, packageName);
          break;
        case INSTALL_FAILED_DEXOPT:
          reason =
            AndroidBundle.message("deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.dexopt"));
          retry = showPrompt(reason) && uninstallPackage(device, packageName);
          break;
        case NO_CERTIFICATE:
          myPrinter.stderr(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
          showMessageDialog(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
          retry = false;
          break;
        case INSTALL_FAILED_OLDER_SDK: // TODO: this should not happen and should have been caught by the device picker
          String message = AndroidBundle.message("deployment.failed.reason.oldersdk", device.getVersion().toString());
          myPrinter.stderr(message);
          retry = false;
          break;
        case UNTYPED_ERROR:
          reason = AndroidBundle.message("deployment.failed.uninstall.prompt.generic.text", result.failureMessage);
          retry = showPrompt(reason) && uninstallPackage(device, packageName);
          break;
        default:
          retry = false;
          break;
      }
    }

    return result != null && result.failureCode == InstallResult.FailureCode.NO_ERROR;
  }

  private void showMessageDialog(@NotNull final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myProject, message, AndroidBundle.message("deployment.failed.title"));
      }
    });
  }

  private boolean showPrompt(final String reason) {
    final AtomicBoolean ok = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        int result =
          Messages.showOkCancelDialog(myProject, reason, AndroidBundle.message("deployment.failed.title"), Messages.getQuestionIcon());
        ok.set(result == Messages.OK);
      }
    }, ModalityState.defaultModalityState());

    return ok.get();
  }

  private boolean uninstallPackage(@NotNull IDevice device, @NotNull String packageName) {
    myPrinter.stdout("$ adb shell pm uninstall " + packageName);
    String output;
    try {
      output = device.uninstallPackage(packageName);
    }
    catch (InstallException e) {
      return false;
    }

    if (output != null) {
      myPrinter.stderr(output);
      return false;
    }
    return true;
  }

  private InstallResult installApp(@NotNull IDevice device, @NotNull String remotePath, LaunchStatus launchStatus)
    throws AdbCommandRejectedException, TimeoutException, IOException {

    ErrorMatchingReceiver receiver = new ErrorMatchingReceiver(launchStatus);

    String command = getPmInstallCommand(remotePath, myLaunchOptions.getPmInstallOptions());
    myPrinter.stdout("$ adb shell " + command);
    try {
      device.executeShellCommand(command, receiver);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.info(e);
      return new InstallResult(InstallResult.FailureCode.DEVICE_NOT_RESPONDING, null, null);
    }

    return InstallResult.forLaunchOutput(receiver);
  }

  @NotNull
  private static String getPmInstallCommand(@NotNull String remotePath, @Nullable String pmInstallOptions) {
    StringBuilder sb = new StringBuilder(30);
    sb.append("pm install ");

    if (!StringUtil.isEmpty(pmInstallOptions)) {
      sb.append(pmInstallOptions);
      sb.append(' ');
    }

    sb.append("-r \"");
    sb.append(remotePath);
    sb.append("\"");
    return sb.toString();
  }
}

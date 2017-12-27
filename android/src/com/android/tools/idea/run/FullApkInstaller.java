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
package com.android.tools.idea.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class FullApkInstaller {
  @NotNull private final Project myProject;
  @NotNull private final LaunchOptions myLaunchOptions;
  @NotNull private final InstalledApkCache myInstalledApkCache;
  @NotNull private final ConsolePrinter myPrinter;

  public FullApkInstaller(@NotNull Project project,
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
    }
    catch (IOException | AdbCommandRejectedException | SyncException | TimeoutException e) {
      myPrinter.stderr(e.toString());
      return false;
    }

    String pmInstallOptions = getPmInstallOptions(device);
    RetryingInstaller.Installer installer = new ApkInstaller(myPrinter, remotePath, pmInstallOptions);
    RetryingInstaller retryingInstaller = new RetryingInstaller(myProject, device, installer, packageName, myPrinter, launchStatus);

    boolean installed = retryingInstaller.install();
    if (installed) {
      try {
        myInstalledApkCache.setInstalled(device, localFile, packageName);
      }
      catch (IOException e) {
        // a failure here doesn't affect any functionality other than the install state cache being broken
        Logger.getInstance(FullApkInstaller.class).info("Exception while caching installation state: ", e);
      }
    }
    return installed;
  }

  @VisibleForTesting
  String getPmInstallOptions(@NotNull IDevice device) {
    String pmInstallOptions = myLaunchOptions.getPmInstallOptions();
    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user interaction/display.
    // However, regular installation will not grant some permissions until the next device reboot. Installing with "-g" guarantees that
    // the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      pmInstallOptions = StringUtil.trimLeading(StringUtil.notNullize(pmInstallOptions) + " -g");
    }
    return pmInstallOptions;
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

  static final class ApkInstaller implements RetryingInstaller.Installer {
    private final String myRemotePath;
    private final ConsolePrinter myPrinter;
    private final String myPmInstallOptions;

    public ApkInstaller(@NotNull ConsolePrinter printer, @NotNull String remotePath, @Nullable String pmInstallOptions) {
      myPrinter = printer;
      myRemotePath = remotePath;
      myPmInstallOptions = pmInstallOptions;
    }

    @NotNull
    @Override
    public InstallResult installApp(@NotNull IDevice device, @NotNull LaunchStatus launchStatus) {
      ErrorMatchingReceiver receiver = new ErrorMatchingReceiver(launchStatus);

      String command = getPmInstallCommand(myRemotePath, myPmInstallOptions);
      myPrinter.stdout("$ adb shell " + command);
      try {
        device.executeShellCommand(command, receiver);
      }
      catch (ShellCommandUnresponsiveException | AdbCommandRejectedException | TimeoutException | IOException e) {
        Logger.getInstance(ApkInstaller.class).info(e);
        return new InstallResult(InstallResult.FailureCode.DEVICE_NOT_RESPONDING, "Exception while installing: " + e, null);
      }

      return InstallResult.forLaunchOutput(receiver);
    }

    @VisibleForTesting
    @NotNull
    static String getPmInstallCommand(@NotNull String remotePath, @Nullable String pmInstallOptions) {
      StringBuilder sb = new StringBuilder(30);
      sb.append("pm install ");

      if (!StringUtil.isEmpty(pmInstallOptions)) {
        sb.append(pmInstallOptions);
        sb.append(' ');
      }

      sb.append("-t -r \"");
      sb.append(remotePath);
      sb.append("\"");
      return sb.toString();
    }
  }
}

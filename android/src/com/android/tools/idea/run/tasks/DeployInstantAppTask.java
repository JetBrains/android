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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.*;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.InstallResult;
import com.android.tools.idea.run.RetryingInstaller;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.instantapp.InstantApps.isLoggedInGoogleAccount;
import static com.android.tools.idea.instantapp.InstantApps.isPostO;
import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;
import static com.google.common.io.Files.createTempDir;

/**
 * Uploads an Instant App for debugging / running
 */
public class DeployInstantAppTask implements LaunchTask {
  @NotNull private final Collection<ApkInfo> myPackages;
  @NotNull private final Project myProject;


  public DeployInstantAppTask(@NotNull Collection<ApkInfo> packages, @NotNull Project project) {
    myPackages = packages;
    myProject = project;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uploading and registering Instant App";
  }

  @Override
  public int getDuration() {
    return DEPLOY_INSTANT_APP;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    if (launchStatus.isLaunchTerminated()) {
      return false;
    }

    // We expect exactly one zip file per Instant App that will contain the apk-splits for the Instant App
    if (myPackages.size() != 1) {
      printer.stderr("Zip file not found or not unique.");
      return false;
    }

    ApkInfo apkInfo = myPackages.iterator().next();
    File zipFile = apkInfo.getFile();
    String appId = apkInfo.getApplicationId();

    if (!zipFile.exists()) {
      printer.stderr("The file " + zipFile.getPath() + " does not exist on disk.");
      return false;
    }

    if (!zipFile.getName().endsWith(".zip")) {
      printer.stderr("The file " + zipFile.getPath() + " is not a zip file.");
      return false;
    }

    RetryingInstaller.Installer installer;
    if (isPostO(device)) {
      printer.stdout("Uploading Instant App to post O device.");
      installer = new InstantAppPostOInstaller(printer, zipFile, Lists.newArrayList("-t", "--ephemeral"));
    }
    else {
      printer.stdout("Uploading Instant App to pre O device.");
      installer = new InstantAppPreOInstaller(printer, zipFile);
    }

    RetryingInstaller retryingInstaller = new RetryingInstaller(myProject, device, installer, appId, printer, launchStatus);
    boolean status = retryingInstaller.install();
    if (status) {
      printer.stdout("Instant App uploaded successfully.");
    }

    return status;
  }

  private static final class InstantAppPostOInstaller implements RetryingInstaller.Installer {
    @NotNull private final ConsolePrinter myPrinter;
    @NotNull private final List<String> myInstallOptions;
    @NotNull private final List<File> myApks;

    public InstantAppPostOInstaller(@NotNull ConsolePrinter printer, @NotNull File zip, @NotNull List<String> installOptions) {
      myPrinter = printer;
      myInstallOptions = installOptions;
      myApks = extractApks(zip);
    }

    @NotNull
    @Override
    public InstallResult installApp(@NotNull IDevice device, @NotNull LaunchStatus launchStatus) {
      String cmd = getAdbInstallCommand(myApks, myInstallOptions);

      try {
        myPrinter.stdout(cmd);

        device.installPackages(myApks, true, myInstallOptions, 5, TimeUnit.MINUTES);
        return new InstallResult(InstallResult.FailureCode.NO_ERROR, null, null);
      }
      catch (InstallException e) {
        return new InstallResult(InstallResult.FailureCode.UNTYPED_ERROR, e.getMessage(), null);
      }
    }

    @NotNull
    private static String getAdbInstallCommand(@NotNull List<File> apks, @NotNull List<String> installOptions) {
      StringBuilder sb = new StringBuilder();
      sb.append("$ adb install-multiple -r ");
      if (!installOptions.isEmpty()) {
        sb.append(Joiner.on(' ').join(installOptions));
        sb.append(' ');
      }

      for (File f : apks) {
        sb.append(f.getPath());
        sb.append(' ');
      }

      return sb.toString();
    }

    @NotNull
    private static List<File> extractApks(@NotNull File zip) {
      File tempDir = createTempDir();
      try {
        ZipUtil.extract(zip, tempDir, null);
      }
      catch (IOException e) {
        return Collections.emptyList();
      }

      File[] apks = tempDir.listFiles();
      return apks == null ? Collections.emptyList() : Arrays.asList(apks);
    }
  }

  private static final class InstantAppPreOInstaller implements RetryingInstaller.Installer {
    @NotNull private static final String OS_BUILD_TYPE_PROPERTY = "ro.build.tags";
    @NotNull private static final String TMP_REMOTE_DIR = "/data/local/tmp/aia/";

    @NotNull private final ConsolePrinter myPrinter;
    @NotNull private final File myZipFile;

    public InstantAppPreOInstaller(@NotNull ConsolePrinter printer, @NotNull File zip) {
      myPrinter = printer;
      myZipFile = zip;
    }

    @NotNull
    @Override
    public InstallResult installApp(@NotNull IDevice device, @NotNull LaunchStatus launchStatus) {
      try {
        if (!isLoggedInGoogleAccount(device, true)) {
          return new InstallResult(InstallResult.FailureCode.UNTYPED_ERROR, "Device not logged in Google account", null);
        }

        NullOutputReceiver receiver = new NullOutputReceiver();

        String osBuildType = device.getProperty(OS_BUILD_TYPE_PROPERTY);

        // TODO(b/34235489): When OnePlatform issue is resolved we could remove this.
        if (osBuildType != null && osBuildType.compareTo("test-keys") == 0) {
          // Force sync the domain filter. Clear the error state. We need to do it here because the disableDomainFilterFallback only works
          // when Devman is present. So in case domain filter was synced to a bad state at provisioning, we need to get it to the right state here.
          device.executeShellCommand("am startservice -a com.google.android.gms.instantapps .ACTION_UPDATE_DOMAIN_FILTER", receiver);
        }

        // If our ADB is running as root somehow when we run this, and later becomes unrooted, the next run won't be able to write to this temp
        // directory and will fail. Thus, we ensure that the directory we create is  owned by the "shell" (unrooted) user. Note that this doesn't matter for
        // replacing the APK itself, because unlink permissions are scoped to the  parent directory in Linux.
        if (osBuildType != null && osBuildType.compareTo("release-keys") != 0) {
          device.executeShellCommand("su shell mkdir -p " + TMP_REMOTE_DIR, receiver);
        }

        // The following actions are derived from the run command in the Instant App SDK and are liable to change.
        UUID installToken = UUID.randomUUID();

        // Upload the Instant App
        String remotePath = TMP_REMOTE_DIR + myZipFile.getName();
        device.pushFile(myZipFile.getCanonicalPath(), remotePath);

        myPrinter.stdout("Starting / refreshing Instant App services");
        device.executeShellCommand("am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" " +
                                   "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" \"" + remotePath + "\" " +
                                   "--es \"com.google.android.instantapps.devman.iapk.INSTALL_TOKEN\" \"" + installToken.toString() + "\" " +
                                   "--ez \"com.google.android.instantapps.devman.iapk.FORCE\" \"false\" " +
                                   "-n com.google.android.instantapps.devman/.iapk.IapkLoadService", receiver);
        device.executeShellCommand("rm -f " + remotePath, receiver);
        device.executeShellCommand("am force-stop com.google.android.instantapps.supervisor", receiver);
      }
      catch (AdbCommandRejectedException | ShellCommandUnresponsiveException e) {
        return new InstallResult(InstallResult.FailureCode.DEVICE_NOT_RESPONDING, e.getMessage(), null);
      }
      catch (Exception e) {
        return new InstallResult(InstallResult.FailureCode.UNTYPED_ERROR, e.getMessage(), null);
      }

      return new InstallResult(InstallResult.FailureCode.NO_ERROR, null, null);
    }
  }
}

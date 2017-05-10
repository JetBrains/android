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
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.InstallResult;
import com.android.tools.idea.run.RetryingInstaller;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.instantapp.InstantApps.isLoggedInGoogleAccount;
import static com.android.tools.idea.instantapp.InstantApps.isPostO;
import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;
import static com.google.common.io.Files.createTempDir;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

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

    String uninstallError = uninstallAppIfInstalled(device, appId);
    if (uninstallError != null) {
      printer.stderr("Couldn't uninstall installed apk. " + uninstallError);
      return false;
    }

    RetryingInstaller.Installer installer;
    if (isPostO(device)) {
      printer.stdout("Uploading Instant App to post O device.");
      installer = new InstantAppPostOInstaller(printer, zipFile, Lists.newArrayList("-t", "--ephemeral"));
    }
    else {
      printer.stdout("Uploading Instant App to pre O device.");
      installer = new InstantAppPreOInstaller(printer, zipFile, 5);
    }

    RetryingInstaller retryingInstaller = new RetryingInstaller(myProject, device, installer, appId, printer, launchStatus);
    boolean status = retryingInstaller.install();
    if (status) {
      printer.stdout("Instant App uploaded successfully.");
    }

    return status;
  }

  @Nullable
  private String uninstallAppIfInstalled(@NotNull IDevice device, @NotNull String pkgName) {
    try {
      if (!isEmpty(executeShellCommand(device, "pm path " + pkgName))) {
        return device.uninstallPackage(pkgName);
      }
      return null;
    }
    catch (Exception e) {
      return e.getMessage();
    }
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

    @NotNull private final AtomicLong myTimeout;

    public InstantAppPreOInstaller(@NotNull ConsolePrinter printer, @NotNull File zip, long timeout) {
      myPrinter = printer;
      myZipFile = zip;
      myTimeout = new AtomicLong(timeout);
    }

    @NotNull
    @Override
    public InstallResult installApp(@NotNull IDevice device, @NotNull LaunchStatus launchStatus) {
      AtomicBoolean retry = new AtomicBoolean(true);
      while (retry.get()) {
        try {
          return installAppWithTimeoutRetry(device, launchStatus);
        }
        catch (TimeoutException e) {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            int choice = Messages
              .showYesNoDialog("Operation timed out." + e.getMessage() + " Do you want to retry increasing the timeout?", "Instant Apps", null);
            if (choice == Messages.OK) {
              myTimeout.set(myTimeout.get() * 2);
            }
            else {
              retry.set(false);
            }
          });
        }
      }
      // Timeout error already treated.
      return new InstallResult(InstallResult.FailureCode.NO_ERROR, "Operation timed out.", null);
    }

    private InstallResult installAppWithTimeoutRetry(@NotNull IDevice device, @NotNull LaunchStatus launchStatus) throws TimeoutException {
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
          device.executeShellCommand("am startservice -a com.google.android.gms.instantapps.ACTION_UPDATE_DOMAIN_FILTER", receiver);
        }

        // If our ADB is running as root somehow when we run this, and later becomes unrooted, the next run won't be able to write to this temp
        // directory and will fail. Thus, we ensure that the directory we create is  owned by the "shell" (unrooted) user. Note that this doesn't matter for
        // replacing the APK itself, because unlink permissions are scoped to the  parent directory in Linux.
        if (osBuildType != null && osBuildType.compareTo("release-keys") != 0) {
          device.executeShellCommand("su shell mkdir -p " + TMP_REMOTE_DIR, receiver);
        }

        // Upload the Instant App
        String remotePath = TMP_REMOTE_DIR + myZipFile.getName();
        device.pushFile(myZipFile.getCanonicalPath(), remotePath);

        myPrinter.stdout("Starting / refreshing Instant App services");

        try {
          String error = readIapk(device, remotePath);
          if (error != null) {
            return new InstallResult(InstallResult.FailureCode.UNTYPED_ERROR, error, null);
          }
        }
        finally {
          device.executeShellCommand("rm -f " + remotePath, receiver);
        }

        device.executeShellCommand("am force-stop com.google.android.instantapps.supervisor", receiver);
      }
      catch (AdbCommandRejectedException | ShellCommandUnresponsiveException e) {
        return new InstallResult(InstallResult.FailureCode.DEVICE_NOT_RESPONDING, e.getMessage(), null);
      }
      catch (TimeoutException e) {
        throw e;
      }
      catch (Exception e) {
        return new InstallResult(InstallResult.FailureCode.UNTYPED_ERROR, e.getMessage(), null);
      }

      return new InstallResult(InstallResult.FailureCode.NO_ERROR, null, null);
    }

    @Nullable
    private String readIapk(@NotNull IDevice device, @NotNull String remotePath)
      throws InterruptedException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
      UUID installToken = UUID.randomUUID();
      String installTokenIdentifier = "token=" + installToken;

      CountDownLatch latch = new CountDownLatch(1);
      AtomicBoolean succeeded = new AtomicBoolean(false);
      AtomicReference<String> error = new AtomicReference<>(null);

      AndroidLogcatService.LogcatListener listener = new AndroidLogcatService.LogcatListener() {
        @Override
        public void onLogLineReceived(@NotNull LogCatMessage line) {
          String message = line.getMessage();
          if (message.contains(installTokenIdentifier)) {
            if (message.contains("LOAD_SUCCESS")) {
              succeeded.set(true);
            }
            else {
              error.set(message.replace(installTokenIdentifier, ""));
            }
            latch.countDown();
          }
        }
      };

      AndroidLogcatService logcat = null;

      boolean isUnitTest = ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode();
      if (!isUnitTest) {
        logcat = AndroidLogcatService.getInstance();
        logcat.addListener(device, listener);
      }

      device.executeShellCommand("am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" " +
                                 "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" \"" + remotePath + "\" " +
                                 "--es \"com.google.android.instantapps.devman.iapk.INSTALL_TOKEN\" \"" + installToken.toString() + "\" " +
                                 "--ez \"com.google.android.instantapps.devman.iapk.FORCE\" \"false\" " +
                                 "-n com.google.android.instantapps.devman/.iapk.IapkLoadService", new NullOutputReceiver());

      if (isUnitTest) {
        // No UI or multi thread in unit tests.
        return null;
      }

      if (latch.await(myTimeout.get(), TimeUnit.SECONDS)) {
        logcat.removeListener(device, listener);
        if (!succeeded.get()) {
          myPrinter.stderr("DevMan error: " + error.get());
          return "DevMan error: " + error.get();
        }
        // No error.
        return null;
      }
      throw new TimeoutException("Timeout: " + myTimeout + "s.");
    }
  }

  @NotNull
  @VisibleForTesting
  String executeShellCommand(@NotNull IDevice device, @NotNull String command) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);

    try {
      device.executeShellCommand(command, receiver);
      latch.await(4, TimeUnit.SECONDS);
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException | InterruptedException e) {
      throw new Exception(e);
    }

    return receiver.getOutput();
  }
}

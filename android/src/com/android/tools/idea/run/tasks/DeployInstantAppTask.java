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
import com.android.instantapp.run.InstantAppRunException;
import com.android.instantapp.run.InstantAppSideLoader;
import com.android.instantapp.run.RunListener;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.instantapp.InstantApps.isPostO;
import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;
import static com.google.common.io.Files.createTempDir;

/**
 * Uploads an Instant App for debugging / running
 */
public class DeployInstantAppTask implements LaunchTask {
  @NotNull private final Collection<ApkInfo> myPackages;

  public DeployInstantAppTask(@NotNull Collection<ApkInfo> packages) {
    myPackages = packages;
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

    return install(device, launchStatus, printer, appId, zipFile);
  }

  @VisibleForTesting
  boolean install(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer, @NotNull String appId, @NotNull File zipFile) {
    RunListener listener = new RunListener() {
      @Override
      public void printMessage(@NotNull String message) {
        printer.stdout(message);
      }

      @Override
      public void logMessage(@NotNull String message, @Nullable InstantAppRunException e) {
        if (e == null) {
          getLogger().info(message);
        }
        else {
          getLogger().warn(message, e);
        }
      }

      @Override
      public boolean isCancelled() {
        return launchStatus.isLaunchTerminated();
      }
    };

    InstantAppSideLoader installer;
    if (isPostO(device)) {
      installer = new InstantAppSideLoader(appId, extractApks(zipFile), listener);
    }
    else {
      installer = new InstantAppSideLoader(appId, zipFile, listener);
    }

    AtomicBoolean tryAgain = new AtomicBoolean(true);
    AtomicBoolean result = new AtomicBoolean(false);
    while (tryAgain.get()) {
      try {
        installer.install(device);
        tryAgain.set(false);
        result.set(true);
      }
      catch (InstantAppRunException e) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          int choice = Messages
            .showYesNoDialog("Side loading failed with message: " + e.getMessage() + " Do you want to retry?", "Instant Apps", null);
          if (choice != Messages.OK) {
            tryAgain.set(false);
            // If there was an error while provisioning, we stop running the RunConfiguration
            result.set(false);
          }
        });
      }
    }
    return result.get();
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

  @NotNull
  private Logger getLogger() {
    return Logger.getInstance(getClass());
  }
}

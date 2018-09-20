/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.android.instantapps.sdk.api.HandlerResult;
import com.google.android.instantapps.sdk.api.ProgressIndicator;
import com.google.android.instantapps.sdk.api.ResultStream;
import com.google.android.instantapps.sdk.api.StatusCode;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.google.android.instantapps.sdk.api.Sdk;
import org.apache.commons.compress.utils.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.TestOnly;

import static com.android.SdkConstants.DOT_ZIP;
import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;
import static com.google.android.instantapps.sdk.api.RunHandler.SetupBehavior;

public class RunInstantAppTask implements LaunchTask {
  private static final String ID = "RUN_INSTANT_APP";

  @NotNull private final Collection<ApkInfo> myPackages;
  @Nullable private final String myDeepLink;
  @NotNull private final InstantAppSdks mySdk;
  @NotNull private final List<String> myDisabledFeatures;
  @Nullable private Path myTempDir;

  public RunInstantAppTask(@NotNull Collection<ApkInfo> packages, @Nullable String link, @NotNull List<String> disabledFeatures) {
    myPackages = packages;
    myDeepLink = link;
    mySdk = InstantAppSdks.getInstance();
    myDisabledFeatures = disabledFeatures;
    myTempDir = null;
  }

  public RunInstantAppTask(@NotNull Collection<ApkInfo> packages, @Nullable String link) {
    this(packages, link, ImmutableList.of());
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uploading and launching Instant App";
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

    // We expect exactly one zip file per Instant App that will contain the apk-splits for the
    // Instant App
    if (myPackages.size() != 1) {
      printer.stderr("Zip file not found or not unique.");
      return false;
    }

    ApkInfo apkInfo = myPackages.iterator().next();
    File zipFile;
    try {
      zipFile = createDeploymentFile(apkInfo);
    } catch (IOException e) {
      printer.stderr("Could not create temporary archive for package.");
      return false;
    }


    URL url = null;
    if (myDeepLink != null && !myDeepLink.isEmpty()) {
      try {
        url = new URL(myDeepLink);
      } catch (MalformedURLException e) {
        printer.stderr("Invalid launch URL: " + myDeepLink);
        return false;
      }
    }

    try {
      Sdk aiaSdk = mySdk.loadLibrary();
      // If null, this entire task will not be called
      assert aiaSdk != null;
      StatusCode status = aiaSdk.getRunHandler().runInstantApp(
        url,
        zipFile,
        device.getSerialNumber(),
        SetupBehavior.SET_UP_IF_NEEDED,
        new ResultStream() {
          @Override
          public void write(HandlerResult result) {
            if (result.isError()) {
              printer.stderr(result.toString());
              getLogger().error(new RunInstantAppException(result.getMessage()));
            }
            else {
              printer.stdout(result.toString());
            }
          }
        },
        new ProgressIndicator() {
          @Override
          public void setProgress(double v) {
          }
        }
      );
      return status == StatusCode.SUCCESS;
    } catch (Exception e) {
      printer.stderr(e.toString());
      getLogger().error(new RunInstantAppException(e));
      return false;
    } finally {
      if (myTempDir != null) {
        zipFile.delete();
        try {
          Files.deleteIfExists(myTempDir);
        } catch (IOException e) {
          printer.stderr("Could not delete temporary directory for instant app deploy.");
        }
      }
    }
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  public static class RunInstantAppException extends Exception {

    private RunInstantAppException(@NotNull String message) {
      super(message);
    }

    private RunInstantAppException(@NotNull Throwable t) {
      super(t);
    }
  }

  @NotNull
  private File createDeploymentFile(ApkInfo apkInfo) throws IOException {
    List<ApkFileUnit> apkFiles = apkInfo.getFiles();
    if (apkFiles.size() == 1) {
      return apkFiles.get(0).getApkFile();
    } else {
      // TODO: Zip up apks for now, change API to support List of Files in the future.
      myTempDir = Files.createTempDirectory(apkInfo.getApplicationId());
      File zipFile = new File(myTempDir.toFile(), apkInfo.getApplicationId() + DOT_ZIP);
      try (ZipOutputStream zipOutputStream =
          new ZipOutputStream(new FileOutputStream(zipFile))) {
        for (ApkFileUnit apkFileUnit : apkFiles) {
          if (DynamicAppUtils.isFeatureEnabled(myDisabledFeatures, apkFileUnit)) {
            File apkFile = apkFileUnit.getApkFile();
            try (FileInputStream fileInputStream = new FileInputStream(apkFile)) {
              byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
              zipOutputStream.putNextEntry(new ZipEntry(apkFile.getName()));
              zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
              zipOutputStream.closeEntry();
            }
          }
        }
      }
      return zipFile;
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(RunInstantAppTask.class);
  }

  @TestOnly
  public Collection<ApkInfo> getPackages() {
    return myPackages;
  }
}

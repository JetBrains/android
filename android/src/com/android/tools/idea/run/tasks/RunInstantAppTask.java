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
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.android.instantapps.sdk.api.HandlerResult;
import com.google.android.instantapps.sdk.api.ProgressIndicator;
import com.google.android.instantapps.sdk.api.ResultStream;
import com.google.android.instantapps.sdk.api.StatusCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;
import static com.google.android.instantapps.sdk.api.RunHandler.SetupBehavior;

public class RunInstantAppTask implements LaunchTask {
  @NotNull private final Collection<ApkInfo> myPackages;
  @Nullable private final String myDeepLink;
  @NotNull private final InstantAppSdks mySdk;

  public RunInstantAppTask(@NotNull Collection<ApkInfo> packages, @Nullable String link) {
    myPackages = packages;
    myDeepLink = link;
    mySdk = InstantAppSdks.getInstance();
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

    if (!mySdk.shouldUseSdkLibraryToRun()) {
      // This shouldn't happen as it would have already been checked by AndroidLaunchTasksProvider
      printer.stderr("The Instant Apps SDK is not available or this task has been disabled");
      return false;
    }

    // We expect exactly one zip file per Instant App that will contain the apk-splits for the
    // Instant App
    if (myPackages.size() != 1) {
      printer.stderr("Zip file not found or not unique.");
      return false;
    }

    ApkInfo apkInfo = myPackages.iterator().next();
    File zipFile = apkInfo.getFile();

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
      StatusCode status = mySdk.loadLibrary().getRunHandler().runInstantApp(
        url,
        zipFile,
        device.getSerialNumber(),
        SetupBehavior.SET_UP_IF_NEEDED,
        new ResultStream() {
          @Override
          public void write(HandlerResult result) {
            if (result.isError()) {
              printer.stderr(result.toString());
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
      return false;
    }
  }
}

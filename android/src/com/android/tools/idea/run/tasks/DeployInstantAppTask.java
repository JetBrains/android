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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;

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
    // We expect exactly one zip file per Instant App that will contain the apk-splits for the Instant App
    if (!myPackages.iterator().hasNext()) {
      printer.stderr("No file provided to upload.");
      return false;
    }

    ApkInfo toUpload = myPackages.iterator().next();

    File localFile = toUpload.getFile();
    if (!localFile.exists()) {
      printer.stderr("The file " + localFile.getPath() + " does not exist on disk.");
      return false;
    }

    try {
      // The following actions are derived from the run command in the Instant App SDK and are liable to change.
      UUID installToken = UUID.randomUUID();
      NullOutputReceiver receiver = new NullOutputReceiver();

      // Upload the Instant App
      String remotePath = "/data/local/tmp/aia/" + localFile.getName();
      device.pushFile(localFile.getPath(), remotePath);

      printer.stdout("Starting / refreshing Instant App services");
      device.executeShellCommand("am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" " +
                                 "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" \"" + remotePath + "\" " +
                                 "--es \"com.google.android.instantapps.devman.iapk.INSTALL_TOKEN\" \"" + installToken.toString() + "\" " +
                                 "--ez \"com.google.android.instantapps.devman.iapk.FORCE\" \"false\" " +
                                 "-n com.google.android.instantapps.devman/.iapk.IapkLoadService", receiver);
      device.executeShellCommand("rm " + remotePath, receiver);
      device.executeShellCommand("am force-stop com.google.android.instantapps.supervisor", receiver);
    }
    catch (Exception e) {
      printer.stderr(e.toString());
      return false;
    }
    return true;
  }
}

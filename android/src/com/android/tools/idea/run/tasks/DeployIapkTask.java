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

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_IAPK;

/**
 * Uploads an IAPK for debugging / running development builds of instant apps
 */
public final class DeployIapkTask implements LaunchTask {
  @NotNull private final Collection<ApkInfo> myApks;

  public DeployIapkTask(@NotNull Collection<ApkInfo> apks) {
    myApks = apks;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uploading and registering IAPK";
  }

  @Override
  public int getDuration() {
    return DEPLOY_IAPK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    if (!myApks.iterator().hasNext()) {
      printer.stderr("No Iapk provided.");
      return false;
    }

    ApkInfo iapk = myApks.iterator().next();

    File localFile = iapk.getFile();
    if (!localFile.exists()) {
      String message = "The APK file " + localFile.getPath() + " does not exist on disk.";
      printer.stderr(message);
      return false;
    }

    // This is currently the required location for uploading IAPKs and is liable to change
    String remotePath = "/sdcard/instantapps/" + localFile.getName();
    printer.stdout("$ adb push " + localFile + " " + remotePath);

    try {
      // In theory this is the only required step for running / debugging an IAPK
      device.pushFile(localFile.getPath(), remotePath);

      // The following commands are liable to change as the Instant App runtime matures.
      CountDownLatch latch = new CountDownLatch(3);
      CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);

      printer.stdout("Starting / refreshing Instant App services");
      device.executeShellCommand("am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" " +
                                 "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" " +
                                 "\"" + remotePath + "\" -n \"com.google.android.instantapps.devman\"/.iapk.IapkLoadService", receiver);

      device.executeShellCommand("pm clear com.google.android.instantapps.supervisor", receiver);
      device.executeShellCommand("am force-stop com.google.android.instantapps.supervisor", receiver);
      latch.await(3000, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      printer.stderr(e.toString());
      return false;
    }

    printer.stdout("IAPK upload complete");
    return true;
  }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp.provision;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.instantapp.InstantApps.getInstantAppSdk;

class ProvisionRunner {
  private static final int O_API_LEVEL = 26;

  @NotNull private final ProgressIndicator myIndicator;
  @NotNull private final List<ProvisionPackage> myPackages;

  ProvisionRunner(@NotNull ProgressIndicator indicator) throws ProvisionException {
    myIndicator = indicator;

    File instantAppSdk;
    try {
      instantAppSdk = getInstantAppSdk();
    }
    catch (Exception e) {
      throw new ProvisionException(e);
    }

    myPackages = Lists.newArrayList(
      new SupervisorPackage(instantAppSdk),
      new GmsCorePackage(instantAppSdk),
      new PolicySetsPackage(instantAppSdk),
      new DevManPackage(instantAppSdk)
    );
  }

  @VisibleForTesting
  ProvisionRunner(@NotNull ProgressIndicator indicator, @NotNull List<ProvisionPackage> packages) {
    myIndicator = indicator;
    myPackages = packages;
  }

  void runProvision(@NotNull IDevice device) throws ProvisionException {
    getLogger().info("Provisioning device " + device.getName());
    myIndicator.setText("Provisioning device " + device.getName());
    myIndicator.setIndeterminate(false);

    if (isPostO(device)) {
      // No need to provision the device
      return;
    }

    double index = 1.0;
    for (ProvisionPackage pack : myPackages) {
      getLogger().info("Checking package " + pack.getDescription());
      myIndicator.setText2("Checking package " + pack.getDescription());

      myIndicator.setFraction(index / myPackages.size());
      index++;

      if (myIndicator.isCanceled()) {
        getLogger().info("Provision cancelled by the user");
        throw new ProvisionException("Provision cancelled by the user");
      }

      if (pack.shouldInstall(device)) {
        getLogger().info("Installing package " + pack.getDescription());
        myIndicator.setText2("Installing package " + pack.getDescription());

        pack.install(device);
      }
    }

    checkSignedIn(device);

    getLogger().info("Device " + device.getName() + " provisioned successfully");

    myIndicator.setIndeterminate(true);
    myIndicator.setText("");
    myIndicator.setText2("");
  }

  private boolean isPostO(@NotNull IDevice device) {
    myIndicator.setText2("Checking API level");

    AndroidVersion androidVersion = device.getVersion();
    getLogger().info("API level detected: " + androidVersion.getApiLevel());

    return androidVersion.isGreaterOrEqualThan(O_API_LEVEL);
  }

  private void checkSignedIn(@NotNull IDevice device) throws ProvisionException {
    // TODO: delete this when Google accounts are not needed anymore

    myIndicator.setText2("Checking Google account");
    getLogger().info("Checking Google account");

    CountDownLatch latch = new CountDownLatch(1);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
    try {
      device.executeShellCommand("dumpsys account", receiver);
      latch.await(500, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      throw new ProvisionException("Couldn't get account in device", e);
    }

    String output = receiver.getOutput();

    Iterable<String> lines = Splitter.on("\n").split(output);
    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("Account {")) {
        if (line.contains("type=com.google")) {
          return;
        }
      }
    }

    getLogger().warn("Device not logged in a Google account");
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(ProvisionRunner.class);
  }
}

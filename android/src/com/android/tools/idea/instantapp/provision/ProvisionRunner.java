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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.tools.idea.instantapp.InstantApps;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.instantapp.InstantApps.getInstantAppSdk;
import static com.android.tools.idea.instantapp.InstantApps.isLoggedInGoogleAccount;

class ProvisionRunner {
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
      new DevManPackage(instantAppSdk),
      new SupervisorPackage(instantAppSdk),
      new GmsCorePackage(instantAppSdk),
      new PolicySetsPackage(instantAppSdk)
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

    checkSignedIn(device);

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
      pack.setFlags(device);
    }

    try {
      // Trigger a domain filter reload. Domain filters need to be populated in order to have an eligible account on the device, and this happens on a
      // loose 24-hour schedule. Developers need AIA on their device right away, and this will cause our GCore module to pull new domain filters.
      device.executeShellCommand("am broadcast -a com.google.android.finsky.action.CONTENT_FILTERS_CHANGED", new NullOutputReceiver());
    }
    catch (Exception e) {
      throw new ProvisionException("Couldn't execute shell command", e);
    }

    getLogger().info("Device " + device.getName() + " provisioned successfully");

    myIndicator.setIndeterminate(true);
    myIndicator.setText("");
    myIndicator.setText2("");
  }

  private boolean isPostO(@NotNull IDevice device) {
    myIndicator.setText2("Checking API level");
    getLogger().info("API level detected: " + device.getVersion().getApiLevel());

    return InstantApps.isPostO(device);
  }

  private void checkSignedIn(@NotNull IDevice device) throws ProvisionException {
    myIndicator.setText2("Checking Google account");
    getLogger().info("Checking Google account");

    try {
      if (isLoggedInGoogleAccount(device, false)) {
        return;
      }
    }
    catch (Exception e) {
      throw new ProvisionException(e);
    }

    getLogger().warn("Device not logged in a Google account");
    throw new ProvisionException("Device not logged in a Google account");
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(ProvisionRunner.class);
  }
}

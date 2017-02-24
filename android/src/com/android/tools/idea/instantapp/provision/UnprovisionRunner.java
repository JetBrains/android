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
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.instantapp.InstantApps.getInstantAppSdk;

/**
 * Not used yet, but it was easier to implement it now.
 * TODO: delete if we don't plan to use it
 */
class UnprovisionRunner {
  static void runUnprovision(@NotNull Collection<IDevice> devices) throws ProvisionException {
    File instantAppSdk;
    try {
      instantAppSdk = getInstantAppSdk();
    }
    catch (Exception e) {
      throw new ProvisionException(e);
    }

    List<ProvisionPackage> packages = Lists.newArrayList(
      new SupervisorPackage(instantAppSdk),
      new DevManPackage(instantAppSdk)
    );

    for (IDevice device : devices) {
      runUnprovision(device, packages);
    }
  }

  private static void runUnprovision(@NotNull IDevice device, @NotNull List<ProvisionPackage> packages) throws ProvisionException {
    for (ProvisionPackage pack : packages) {
      pack.uninstall(device);
    }
  }
}

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
package com.android.tools.idea.run.deployment.selector;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.util.concurrent.ListenableFuture;
import java.time.Instant;
import java.util.Collection;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface Device {
  /**
   * A physical device will always return a serial number. A virtual device will usually return a virtual device path. But if Studio doesn't
   * know about the virtual device (it's outside the scope of the AVD Manager because it uses a locally built system image, for example) it
   * can return a virtual device path (probably not but I'm not going to assume), virtual device name, or serial number depending on what
   * the IDevice returned.
   */
  @NotNull
  Key getKey();

  @NotNull
  Icon getIcon();

  @NotNull
  LaunchCompatibility getLaunchCompatibility();

  boolean isConnected();

  @Nullable
  Instant getConnectionTime();

  @NotNull
  String getName();

  @NotNull
  Collection<Snapshot> getSnapshots();

  @NotNull
  Target getDefaultTarget();

  @NotNull
  Collection<Target> getTargets();

  @NotNull
  AndroidDevice getAndroidDevice();

  @NotNull
  default ListenableFuture<IDevice> getDdmlibDeviceAsync() {
    AndroidDevice device = getAndroidDevice();

    if (!device.isRunning()) {
      throw new RuntimeException(device + " is not running");
    }

    return device.getLaunchedDevice();
  }
}

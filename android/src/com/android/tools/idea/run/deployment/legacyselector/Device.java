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
package com.android.tools.idea.run.deployment.legacyselector;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.util.concurrent.ListenableFuture;
import icons.StudioIcons;
import java.time.Instant;
import java.util.Collection;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Device {
  abstract class Builder {
    @Nullable
    Key myKey;

    @NotNull
    Type myType = Type.PHONE;

    @NotNull
    LaunchCompatibility myLaunchCompatibility = LaunchCompatibility.YES;

    @Nullable
    Instant myConnectionTime;

    @Nullable
    String myName;

    @Nullable
    AndroidDevice myAndroidDevice;

    @NotNull
    abstract Device build();
  }

  @NotNull
  Key key();

  /**
   * A physical device will always return a serial number. A virtual device will usually return a virtual device path. But if Studio doesn't
   * know about the virtual device (it's outside the scope of the AVD Manager because it uses a locally built system image, for example) it
   * can return a virtual device path (probably not but I'm not going to assume), virtual device name, or serial number depending on what
   * the IDevice returned.
   */
  @Deprecated
  @NotNull
  @SuppressWarnings("GrazieInspection")
  default Key getKey() {
    return key();
  }

  @NotNull
  Icon icon();

  @Deprecated
  @NotNull
  default Icon getIcon() {
    return icon();
  }

  @NotNull
  Type type();

  enum Type {
    PHONE(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE),
    WEAR(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR),
    TV(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV);

    @NotNull
    private final Icon myVirtualIcon;

    @NotNull
    private final Icon myPhysicalIcon;

    Type(@NotNull Icon virtualIcon, @NotNull Icon physicalIcon) {
      myVirtualIcon = virtualIcon;
      myPhysicalIcon = physicalIcon;
    }

    @NotNull
    final Icon getVirtualIcon() {
      return myVirtualIcon;
    }

    @NotNull
    final Icon getPhysicalIcon() {
      return myPhysicalIcon;
    }
  }

  @NotNull
  LaunchCompatibility launchCompatibility();

  @Deprecated
  @NotNull
  default LaunchCompatibility getLaunchCompatibility() {
    return launchCompatibility();
  }

  boolean connected();

  @Deprecated
  default boolean isConnected() {
    return connected();
  }

  @Nullable
  Instant connectionTime();

  @Deprecated
  @Nullable
  default Instant getConnectionTime() {
    return connectionTime();
  }

  @NotNull
  String name();

  @Deprecated
  @NotNull
  default String getName() {
    return name();
  }

  @NotNull
  Collection<Snapshot> snapshots();

  @Deprecated
  @NotNull
  default Collection<Snapshot> getSnapshots() {
    return snapshots();
  }

  @NotNull
  Target defaultTarget();

  @Deprecated
  @NotNull
  default Target getDefaultTarget() {
    return defaultTarget();
  }

  @NotNull
  Collection<Target> targets();

  @Deprecated
  @NotNull
  default Collection<Target> getTargets() {
    return targets();
  }

  @NotNull
  AndroidDevice androidDevice();

  @Deprecated
  @NotNull
  default AndroidDevice getAndroidDevice() {
    return androidDevice();
  }

  @NotNull
  default ListenableFuture<IDevice> ddmlibDeviceAsync() {
    AndroidDevice device = getAndroidDevice();

    if (!device.isRunning()) {
      throw new RuntimeException(device + " is not running");
    }

    return device.getLaunchedDevice();
  }

  @Deprecated
  @NotNull
  default ListenableFuture<IDevice> getDdmlibDeviceAsync() {
    return ddmlibDeviceAsync();
  }
}

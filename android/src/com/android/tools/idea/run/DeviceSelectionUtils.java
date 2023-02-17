/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility methods for selecting a running device.
 */
public class DeviceSelectionUtils {
  @NotNull private static final Logger LOG = Logger.getInstance(DeviceSelectionUtils.class);
  // We remember what serials the user uses so we can pre-select them in the manual choosing dialog.
  @NonNls private static final String ANDROID_TARGET_DEVICES_PROPERTY = "AndroidTargetDevices";

  /**
   * Returns a compatible, already-running device for launch.
   * If no compatible devices are running, returns an empty list.
   * If exactly one compatible device is running, returns it.
   * If multiple compatible devices are running, prompt the user to select devices.
   * If they select any, return them, but if they do not, return null.
   */
  @Nullable
  public static Collection<IDevice> chooseRunningDevice(@NotNull final AndroidFacet facet,
                                                        @NotNull final Predicate<IDevice> deviceFilter,
                                                        @NotNull final DeviceCount deviceCount) {
    final Collection<IDevice> compatibleDevices = getAllCompatibleDevices(deviceFilter);

    if (compatibleDevices.isEmpty()) {
      return ImmutableList.of();
    }
    else if (compatibleDevices.size() == 1) {
      return compatibleDevices;
    }
    else {
      // Ask the user to pick one (or more) of the matching devices.
      final AtomicReference<IDevice[]> devicesRef = new AtomicReference<IDevice[]>();
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          devicesRef.set(chooseDevicesManually(facet, deviceFilter, deviceCount));
        }
      });
      // Return the selected devices, or null if the user cancelled.
      return devicesRef.get().length > 0 ? ImmutableList.copyOf(devicesRef.get()) : null;
    }
  }

  @NotNull
  public static List<IDevice> getAllCompatibleDevices(Predicate<IDevice> deviceFilter) {
    final List<IDevice> compatibleDevices = new ArrayList<IDevice>();
    final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();

    if (bridge != null) {
      IDevice[] devices = bridge.getDevices();

      for (IDevice device : devices) {
        if (deviceFilter.apply(device)) {
          compatibleDevices.add(device);
        }
      }
    }
    return compatibleDevices;
  }

  @NotNull
  public static Collection<IDevice> getOnlineDevices(@Nullable Collection<IDevice> devices) {
    if (devices == null) {
      return Collections.emptyList();
    }

    final List<IDevice> online = Lists.newArrayListWithExpectedSize(devices.size());
    for (IDevice device : devices) {
      if (device.isOnline()) {
        online.add(device);
      }
    }
    return online;
  }

  @NotNull
  private static IDevice[] chooseDevicesManually(@NotNull AndroidFacet facet,
                                                 @NotNull Predicate<IDevice> filter,
                                                 @NotNull DeviceCount deviceCount) {
    final Project project = facet.getModule().getProject();
    String value = PropertiesComponent.getInstance(project).getValue(ANDROID_TARGET_DEVICES_PROPERTY);
    String[] selectedSerials = value != null ? deserialize(value) : null;
    AndroidPlatform platform = AndroidPlatforms.getInstance(facet.getModule());
    if (platform == null) {
      LOG.error("Android platform not set for module: " + facet.getModule().getName());
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    DeviceChooserDialog chooser =
      new DeviceChooserDialog(facet, platform.getTarget(), deviceCount.isMultiple(), selectedSerials, filter);
    chooser.show();
    IDevice[] devices = chooser.getSelectedDevices();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || devices.length == 0) {
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    PropertiesComponent.getInstance(project).setValue(ANDROID_TARGET_DEVICES_PROPERTY, serialize(devices));
    return devices;
  }

  @NotNull
  public static String serialize(@NotNull IDevice[] devices) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = devices.length; i < n; i++) {
      builder.append(devices[i].getSerialNumber());
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  @NotNull
  private static String[] deserialize(@NotNull String s) {
    return s.split(" ");
  }
}

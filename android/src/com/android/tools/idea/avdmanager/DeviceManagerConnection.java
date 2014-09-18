/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.SdkConstants;
import com.android.prefs.AndroidLocation;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionStatus;
import org.jetbrains.android.util.StringBuildingOutputProcessor;
import org.jetbrains.android.util.WaitingStrategies;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * A wrapper class which manages a {@link DeviceManager} instance and provides convenience functions
 * for working with {@link Device}s.
 */
public class DeviceManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG);
  private static DeviceManager ourDeviceManager;
  private static Map<File, SkinLayoutDefinition> ourSkinLayoutDefinitions = Maps.newHashMap();

  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private static boolean initIfNecessary() {
    if (ourDeviceManager == null) {
      AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      if (androidSdkData == null) {
        IJ_LOG.error("No installed SDK found!");
        return false;
      }
      LocalSdk localSdk = androidSdkData.getLocalSdk();
      ourDeviceManager = DeviceManager.createInstance(localSdk.getLocation(), SDK_LOG);
    }
    return true;
  }

  /**
   * @return a list of Devices currently present on the system.
   */
  @NotNull
  public static List<Device> getDevices() {
    if (!initIfNecessary()) {
      return ImmutableList.of();
    }
    return Lists.newArrayList(ourDeviceManager.getDevices(DeviceManager.ALL_DEVICES));
  }


  /**
   * Calculate an ID for a device (optionally from a given ID) which does not clash
   * with any existing IDs.
   */
  @NotNull
  public static String getUniqueId(@Nullable String id) {
    String baseId = id == null? "New Device" : id;
    if (!initIfNecessary()) {
      return baseId;
    }
    Collection<Device> devices = ourDeviceManager.getDevices(DeviceManager.DeviceFilter.USER);
    String candidate = baseId;
    int i = 0;
    while (anyIdMatches(candidate, devices)) {
      candidate = String.format(Locale.getDefault(), "%s %d", baseId, ++i);
    }
    return candidate;
  }

  private static boolean anyIdMatches(@NotNull String id, @NotNull Collection<Device> devices) {
    for (Device d : devices) {
      if (id.equalsIgnoreCase(d.getId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Delete the given device if it exists.
   */
  public static void deleteDevice(@Nullable Device info) {
    if (info != null) {
      if (!initIfNecessary()) {
        return;
      }
      ourDeviceManager.removeUserDevice(info);
    }
  }

  /**
   * Edit the given device, overwriting existing data, or creating it if it does not exist.
   */
  public static void createOrEditDevice(@NotNull Device device) {
    if (!initIfNecessary()) {
      return;
    }
    ourDeviceManager.replaceUserDevice(device);
    ourDeviceManager.saveUserDevices();
  }

  /**
   * Return true iff the given device matches one of the user declared devices.
   */
  public static boolean isUserDevice(@NotNull final Device device) {
    if (!initIfNecessary()) {
      return false;
    }
    return Iterables.any(ourDeviceManager.getDevices(DeviceManager.DeviceFilter.USER), new Predicate<Device>() {
      @Override
      public boolean apply(Device input) {
        return device.getId().equalsIgnoreCase(input.getId());
      }
    });
  }
}

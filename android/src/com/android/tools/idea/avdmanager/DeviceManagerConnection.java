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
import com.android.sdklib.devices.DeviceParser;
import com.android.sdklib.devices.DeviceWriter;
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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
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
   * Create the given devices
   */
  public static void createDevices(@NotNull List<Device> devices) {
    if (!initIfNecessary()) {
      return;
    }
    for (Device device : devices) {
      // Find a unique ID for this new device
      String deviceIdBase = device.getId();
      String deviceNameBase = device.getDisplayName();
      int i = 2;
      while (isUserDevice(device)) {
        String id = String.format(Locale.getDefault(), "%1$s_%2$d", deviceIdBase, i);
        String name = String.format(Locale.getDefault(), "%1$s_%2$d", deviceNameBase, i);
        device = cloneDeviceWithNewIdAndName(device, id, name);
      }
      ourDeviceManager.addUserDevice(device);
    }
    ourDeviceManager.saveUserDevices();
  }

  private static Device cloneDeviceWithNewIdAndName(@NotNull Device device, @NotNull String id, @NotNull String name) {
    Device.Builder builder = new Device.Builder(device);
    builder.setId(id);
    builder.setName(name);
    return builder.build();
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

  public static List<Device> getDevicesFromFile(@NotNull File xmlFile) {
    InputStream stream = null;
    List<Device> list = Lists.newArrayList();
    try {
      stream = new FileInputStream(xmlFile);
      list.addAll(DeviceParser.parse(stream));
    } catch (IllegalStateException e) {
      // The device builders can throw IllegalStateExceptions if
      // build gets called before everything is properly setup
      IJ_LOG.error(e);
    } catch (Exception e) {
      IJ_LOG.error("Error reading devices", e);
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignore) {}
      }
    }
    return list;
  }

  public static void writeDevicesToFile(@NotNull List<Device> devices, @NotNull File file) {
    if (devices.size() > 0) {
      try {
        DeviceWriter.writeToXml(new FileOutputStream(file), devices);
      } catch (FileNotFoundException e) {
        IJ_LOG.warn(String.format("Couldn't open file: %1$s", e.getMessage()));
      } catch (ParserConfigurationException e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      } catch (TransformerFactoryConfigurationError e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      } catch (TransformerException e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      }
    }
  }
}

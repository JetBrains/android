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

import com.android.prefs.AndroidLocationsSingleton;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.DeviceParser;
import com.android.sdklib.devices.DeviceWriter;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.sdk.DeviceManagers;
import com.android.utils.ILogger;
import com.android.tools.idea.log.LogWrapper;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A wrapper class which manages a {@link DeviceManager} instance and provides convenience functions
 * for working with {@link Device}s.
 */
public class DeviceManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG).alwaysLogAsDebug(true).allowVerbose(false);
  private static final DeviceManagerConnection NULL_CONNECTION = new DeviceManagerConnection(null);
  private static Map<Path, DeviceManagerConnection> ourCache = new WeakHashMap<>();
  private DeviceManager ourDeviceManager;
  @Nullable private Path mySdkPath;

  public DeviceManagerConnection(@Nullable Path sdkPath) {
    mySdkPath = sdkPath;
  }

  @NotNull
  public static DeviceManagerConnection getDeviceManagerConnection(@NotNull Path sdkPath) {
    if (!ourCache.containsKey(sdkPath)) {
      ourCache.put(sdkPath, new DeviceManagerConnection(sdkPath));
    }
    return ourCache.get(sdkPath);
  }

  @NotNull
  public static DeviceManagerConnection getDefaultDeviceManagerConnection() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    Path sdkPath = handler.getLocation();
    if (sdkPath != null) {
      return getDeviceManagerConnection(sdkPath);
    }
    else {
      return NULL_CONNECTION;
    }
  }


  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private boolean initIfNecessary() {
    if (ourDeviceManager == null) {
      if (mySdkPath == null) {
        IJ_LOG.error("No installed SDK found!");
        return false;
      }
      ourDeviceManager = DeviceManagers.getDeviceManager(AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, mySdkPath));
    }
    return true;
  }

  /**
   * @return a list of Devices currently present on the system.
   */
  @NotNull
  public List<Device> getDevices() {
    if (!initIfNecessary()) {
      return ImmutableList.of();
    }
    return Lists.newArrayList(ourDeviceManager.getDevices(DeviceManager.ALL_DEVICES));
  }

  /**
   * @return the device identified by device name and manufacturer or null if not found.
   */
  @Nullable
  public Device getDevice(@NotNull String id, @NotNull String manufacturer) {
    if (!initIfNecessary()) {
      return null;
    }
    return ourDeviceManager.getDevice(id, manufacturer);
  }

  /**
   * Calculate an ID for a device (optionally from a given ID) which does not clash
   * with any existing IDs.
   */
  @NotNull
  public String getUniqueId(@Nullable String id) {
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
  public void deleteDevice(@Nullable Device info) {
    if (info != null) {
      if (!initIfNecessary()) {
        return;
      }
      ourDeviceManager.removeUserDevice(info);
      ourDeviceManager.saveUserDevices();
    }
  }

  /**
   * Edit the given device, overwriting existing data, or creating it if it does not exist.
   */
  public void createOrEditDevice(@NotNull Device device) {
    if (!initIfNecessary()) {
      return;
    }
    ourDeviceManager.replaceUserDevice(device);
    ourDeviceManager.saveUserDevices();
  }

  /**
   * Create the given devices
   */
  public void createDevices(@NotNull List<Device> devices) {
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
  public boolean isUserDevice(@NotNull final Device device) {
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
    List<Device> list = new ArrayList<>();
    try {
      stream = new FileInputStream(xmlFile);
      list.addAll(DeviceParser.parse(stream).values());
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
    if (!devices.isEmpty()) {
      FileOutputStream stream = null;
      try {
        stream = new FileOutputStream(file);
        DeviceWriter.writeToXml(stream, devices);
      } catch (FileNotFoundException e) {
        IJ_LOG.warn(String.format("Couldn't open file: %1$s", e.getMessage()));
      } catch (ParserConfigurationException e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      } catch (TransformerFactoryConfigurationError e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      } catch (TransformerException e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      } finally {
        if (stream != null) {
          try {
            stream.close();
          } catch (IOException e) {
            IJ_LOG.warn(String.format("Error closing file: %1$s", e.getMessage()));
          }
        }
      }
    }
  }
}

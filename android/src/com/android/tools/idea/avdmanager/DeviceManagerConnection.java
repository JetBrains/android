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
import com.android.sdklib.devices.DeviceManager.DeviceCategory;
import com.android.sdklib.devices.DeviceParser;
import com.android.sdklib.devices.DeviceWriter;
import com.android.sdklib.devices.UserDeviceTable;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.sdk.AndroidSdkData;
import com.android.tools.sdk.DeviceManagers;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A wrapper class which manages a {@link DeviceManager} instance and provides convenience functions
 * for working with {@link Device}s.
 */
public class DeviceManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private final DeviceManager deviceManager;
  @Nullable private final UserDeviceTable userDevices;

  @VisibleForTesting
  DeviceManagerConnection(DeviceManager deviceManager) {
    this.deviceManager = deviceManager;
    this.userDevices = deviceManager.getUserDevices();
  }

  @NotNull
  public static DeviceManagerConnection getDeviceManagerConnection(@NotNull Path sdkPath) {
    return new DeviceManagerConnection(DeviceManagers.INSTANCE.getDeviceManager(AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, sdkPath)));
  }

  @NotNull
  public static DeviceManagerConnection getDefaultDeviceManagerConnection() {
    AndroidSdkData data = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (data != null) {
      return new DeviceManagerConnection(data.getDeviceManager());
    } else {
      return new DeviceManagerConnection(DeviceManagers.INSTANCE.getDeviceManagerWithoutSystemImageDevices());
    }
  }

  @NotNull
  public Collection<Device> getDevices() {
    return deviceManager.getDevices();
  }

  @NotNull
  public Collection<Device> getDevices(@NotNull DeviceCategory... filters) {
    return deviceManager.getDevices(filters);
  }

  /**
   * @return the device identified by device name and manufacturer or null if not found.
   */
  @Nullable
  public Device getDevice(@NotNull String id, @NotNull String manufacturer) {
    return deviceManager.getDevice(id, manufacturer);
  }

  /**
   * Calculate an ID for a device (optionally from a given ID) which does not clash
   * with any existing IDs.
   */
  @NotNull
  public String getUniqueId(@Nullable String id) {
    String baseId = id == null ? "New Device" : id;
    var devices = deviceManager.getDevices();
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
    if (info != null && userDevices != null) {
      userDevices.removeUserDevice(info);
      userDevices.saveUserDevices();
    }
  }

  /**
   * Edit the given device, overwriting existing data, or creating it if it does not exist.
   */
  public void createOrEditDevice(@NotNull Device device) {
    if (userDevices != null) {
      userDevices.replaceUserDevice(device);
      userDevices.saveUserDevices();
    }
  }

  /**
   * Create the given devices
   */
  public void createDevices(@NotNull List<Device> devices) {
    if (userDevices == null) {
      return;
    }
    for (Device device : devices) {
      // Find a unique ID for this new device
      String deviceIdBase = device.getId();
      String deviceNameBase = device.getDisplayName();

      for (int i = 2; isUserDevice(device); i++) {
        String id = String.format(Locale.getDefault(), "%1$s_%2$d", deviceIdBase, i);
        String name = String.format(Locale.getDefault(), "%1$s_%2$d", deviceNameBase, i);
        device = cloneDeviceWithNewIdAndName(device, id, name);
      }
      userDevices.addUserDevice(device);
    }
    userDevices.saveUserDevices();
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
    if (userDevices == null) {
      return false;
    }
    return userDevices.getDevices().values().stream()
      .map(Device::getId)
      .anyMatch(device.getId()::equalsIgnoreCase);
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
      try (FileOutputStream stream = new FileOutputStream(file)) {
        DeviceWriter.writeToXml(stream, devices);
      } catch (FileNotFoundException e) {
        IJ_LOG.warn(String.format("Couldn't open file: %1$s", e.getMessage()));
      } catch (ParserConfigurationException | IOException | TransformerFactoryConfigurationError | TransformerException e) {
        IJ_LOG.warn(String.format("Error writing file: %1$s", e.getMessage()));
      }
    }
  }
}

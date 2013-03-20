/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Per file state for layouts */
@SuppressWarnings("UnusedDeclaration") // Getters called by XML serialization reflection
@Tag("config")
public class ConfigurationFileState {
  @Nullable private String myDevice;
  @Nullable private String myDeviceState;
  @Nullable private String myDockMode;
  @Nullable private String myNightMode;
  @Nullable private String myTheme;

  @Tag("device")
  @Nullable
  public String getDevice() {
    return myDevice;
  }

  public void setDevice(@Nullable String device) {
    myDevice = device;
  }

  @Tag("state")
  @Nullable
  public String getDeviceState() {
    return myDeviceState;
  }

  public void setDeviceState(@Nullable String deviceState) {
    myDeviceState = deviceState;
  }

  @Tag("dock")
  @Nullable
  public String getDockMode() {
    return myDockMode;
  }

  public void setDockMode(@Nullable String dockMode) {
    myDockMode = dockMode;
  }

  @Tag("night")
  @Nullable
  public String getNightMode() {
    return myNightMode;
  }

  public void setNightMode(@Nullable String nightMode) {
    myNightMode = nightMode;
  }

  @Tag("theme")
  @Nullable
  public String getTheme() {
    return myTheme;
  }

  public void setTheme(@Nullable String theme) {
    myTheme = theme;
  }

  public void saveState(@NotNull Configuration configuration) {
    Device device = configuration.getDevice();
    myDevice = null;
    myDeviceState = null;
    if (device != null) {
      myDevice = device.getName();
      State deviceState = configuration.getDeviceState();
      if (deviceState != null && deviceState != device.getDefaultState()) {
        myDeviceState = deviceState.getName();
      }
    }

    // Null out if same as the default

    UiMode dockMode = configuration.getUiMode();
    if (dockMode != UiMode.NORMAL) {
      myDockMode = dockMode.getResourceValue();
    } else {
      myDockMode = null;
    }

    myDockMode = StringUtil.nullize(dockMode.getResourceValue());
    NightMode nightMode = configuration.getNightMode();
    if (nightMode != NightMode.NOTNIGHT) {
      myNightMode = nightMode.getResourceValue();
    } else {
      myNightMode = null;
    }

    myTheme = StringUtil.nullize(configuration.getTheme());
  }

  public void loadState(@NotNull Configuration configuration) {
    configuration.startBulkEditing();
    ConfigurationManager manager = configuration.getConfigurationManager();
    if (myDevice != null) {
      Device matchedDevice = null;
      for (Device device : manager.getDevices()) {
        if (myDevice.equals(device.getName())) {
          matchedDevice = device;
          break;
        }
      }

      if (matchedDevice == null) {
        AndroidFacet facet = AndroidFacet.getInstance(manager.getModule());
        assert facet != null;
        final AvdManager avdManager = facet.getAvdManagerSilently();
        if (avdManager != null) {
          for (AvdInfo avd : avdManager.getValidAvds()) {
            if (myDevice.equals(avd.getName())) {
              matchedDevice = manager.createDeviceForAvd(avd);
              if (matchedDevice != null) {
                break;
              }
            }
          }
        }
      }

      if (matchedDevice != null) {
        configuration.setDevice(matchedDevice, false);
        if (myDeviceState != null) {
          State state = getState(matchedDevice, myDeviceState);
          if (state != null) {
              configuration.setDeviceState(state);
          }
        }
      }
    }

    if (myDockMode != null) {
      UiMode dockMode = UiMode.getEnum(myDockMode);
      if (dockMode != null) {
        configuration.setUiMode(dockMode);
      }
    }

    if (myNightMode != null) {
      NightMode nightMode = NightMode.getEnum(myNightMode);
      if (nightMode != null) {
        configuration.setNightMode(nightMode);
      }
    }

    if (myTheme != null) {
      configuration.setTheme(myTheme);
    }
    configuration.finishBulkEditing();
  }

  /**
   * Returns the {@link com.android.sdklib.devices.State} by the given name for the given {@link com.android.sdklib.devices.Device}
   *
   * @param device the device
   * @param name   the name of the state
   */
  @Nullable
  static State getState(@Nullable Device device, @Nullable String name) {
    if (device == null) {
      return null;
    }
    else if (name != null) {
      State state = device.getState(name);
      if (state != null) {
        return state;
      }
    }

    return device.getDefaultState();
  }
}

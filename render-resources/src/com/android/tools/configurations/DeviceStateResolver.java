/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations;

import static com.android.tools.configurations.ConfigurationListener.CFG_DEVICE;
import static com.android.tools.configurations.ConfigurationListener.CFG_DEVICE_STATE;

import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.annotations.concurrency.Slow;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the specific hardware constraints and orientation matching logic for a Configuration.
 */
public class DeviceStateResolver {

  public interface Context {
    @NotNull ConfigurationSettings getSettings();

    @NotNull FolderConfiguration getEditedConfig();

    void updateDeviceOverlay();

    @Nullable Device computeBestDevice();
  }

  private final Context myContext;

  @Nullable private Device mySpecificDevice;
  @Nullable private Device myDevice;
  @Nullable private State myState;
  @Nullable private String myStateName;

  public DeviceStateResolver(@NotNull Context context) {
    this.myContext = context;
  }

  public void initFromEditedConfig() {
    ScreenOrientationQualifier qualifier = myContext.getEditedConfig().getScreenOrientationQualifier();
    if (qualifier != null) {
      ScreenOrientation orientation = qualifier.getValue();
      if (orientation != null) {
        myStateName = orientation.getShortDisplayValue();
      }
    }
  }

  @Nullable
  public Device getCachedDevice() { return myDevice; }

  @Nullable
  public State getCachedState() { return myState; }

  @Nullable
  public String getStateName() { return myStateName; }

  @Slow
  @Nullable
  public Device getDevice() {
    if (myDevice != null) return myDevice;

    // This preserves the protected computeBestDevice() extension point for subclasses!
    myDevice = mySpecificDevice != null ? mySpecificDevice : myContext.computeBestDevice();

    myContext.updateDeviceOverlay();
    return myDevice;
  }

  @Nullable
  public State getDeviceState() {
    if (myState == null) {
      Device device = getDevice();
      myState = DeviceState.getDeviceState(device, myStateName);
    }
    return myState;
  }

  public int setDevice(Device device, boolean preserveState) {
    if (mySpecificDevice == device) {
      // The specific device is already set to the correct device so simply clear myDevice
      // which will be re-calculated to be the same as myDevice on the next query.
      myDevice = null;
      return 0;
    }

    Device prevDevice = mySpecificDevice;
    State prevState = myState;

    myDevice = mySpecificDevice = device;
    myContext.updateDeviceOverlay();

    int updateFlags = CFG_DEVICE;

    if (device != null) {
      State state = null;
      // Attempt to preserve the device state?
      if (preserveState && prevDevice != null) {
        if (prevState != null) {
          FolderConfiguration oldConfig = DeviceConfigHelper.getFolderConfig(prevState);
          if (oldConfig != null) {
            String stateName = getClosestMatch(oldConfig, device.getAllStates());
            state = device.getState(stateName);
          }
          else {
            state = device.getState(prevState.getName());
          }
        }
      }
      else if (preserveState && myStateName != null) {
        state = device.getState(myStateName);
      }
      if (state == null) {
        state = device.getDefaultState();
      }
      if (myState != state) {
        updateFlags |= setDeviceStateName(state.getName());
        myState = state;
        updateFlags |= CFG_DEVICE_STATE;
      }
    }
    return updateFlags;
  }

  public int setDeviceState(State state) {
    if (myState != state) {
      int flags = CFG_DEVICE_STATE;
      if (state != null) {
        flags |= setDeviceStateName(state.getName());
      }
      else {
        myStateName = null;
      }
      myState = state;
      return flags;
    }
    return 0;
  }

  public int setDeviceStateName(@Nullable String stateName) {
    ScreenOrientationQualifier qualifier = myContext.getEditedConfig().getScreenOrientationQualifier();
    if (qualifier != null) {
      ScreenOrientation orientation = qualifier.getValue();
      if (orientation != null) {
        stateName = orientation.getShortDisplayValue(); // Also used as state names
      }
    }

    if (!Objects.equals(stateName, myStateName)) {
      myStateName = stateName;
      myState = null;
      return CFG_DEVICE_STATE;
    }
    return 0;
  }

  public int setEffectiveDevice(@Nullable Device device, @Nullable State state) {
    int updateFlags = 0;
    if (myDevice != device) {
      updateFlags = CFG_DEVICE;
      myDevice = device;
      myContext.updateDeviceOverlay();
    }
    if (myState != state) {
      myState = state;
      myStateName = state != null ? state.getName() : null;
      updateFlags |= CFG_DEVICE_STATE;
    }
    return updateFlags;
  }

  @Nullable
  public ScreenSize getScreenSize() {
    State deviceState = getDeviceState();
    if (deviceState != null) {
      FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(deviceState);
      if (folderConfig != null) {
        ScreenSizeQualifier qualifier = folderConfig.getScreenSizeQualifier();
        assert qualifier != null;
        return qualifier.getValue();
      }
    }

    Device device = getDevice();
    if (device != null) {
      for (State state : device.getAllStates()) {
        FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(state);
        if (folderConfig != null) {
          ScreenSizeQualifier qualifier = folderConfig.getScreenSizeQualifier();
          assert qualifier != null;
          return qualifier.getValue();
        }
      }
    }
    return null;
  }

  @Nullable
  public State getNextDeviceState(@Nullable State from) {
    Device device = getDevice();
    if (device == null) return null;

    List<State> states = device.getAllStates();
    for (int i = 0; i < states.size(); i++) {
      if (states.get(i) == from) {
        return states.get((i + 1) % states.size());
      }
    }

    if (from != null) {
      String name = from.getName();
      for (int i = 0; i < states.size(); i++) {
        if (states.get(i).getName().equals(name)) {
          return states.get((i + 1) % states.size());
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getClosestMatch(@NotNull FolderConfiguration oldConfig, @NotNull List<State> states) {
    List<State> list1 = new ArrayList<>(states.size());
    List<State> list2 = new ArrayList<>(states.size());

    list1.addAll(states);

    final int count = FolderConfiguration.getQualifierCount();
    for (int i = 0; i < count; i++) {
      for (State s : list1) {
        ResourceQualifier oldQualifier = oldConfig.getQualifier(i);
        FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(s);
        ResourceQualifier newQualifier = folderConfig != null ? folderConfig.getQualifier(i) : null;

        if (oldQualifier == null) {
          if (newQualifier == null) {
            list2.add(s);
          }
        }
        else if (oldQualifier.equals(newQualifier)) {
          list2.add(s);
        }
      }
      if (list2.size() == 1) {
        return list2.get(0).getName();
      }
      if (!list2.isEmpty()) {
        list1.clear();
        list1.addAll(list2);
        list2.clear();
      }
    }
    if (!list1.isEmpty()) {
      return list1.get(0).getName();
    }
    return null;
  }

  public void copyFrom(DeviceStateResolver other) {
    this.mySpecificDevice = other.mySpecificDevice;
    this.myDevice = other.myDevice;
    this.myState = other.myState;
    this.myStateName = other.myStateName;
  }
}
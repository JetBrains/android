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
package com.android.tools.idea.avdmanager;

import com.android.resources.*;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.State;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * {@link WizardModel} that holds all properties in {@link Device} to be used in
 * {@link ConfigureDeviceOptionsStep} for the user to edit.
 */
public final class ConfigureDeviceModel extends WizardModel {

  private BindingsManager myBindings = new BindingsManager();
  private Device.Builder myBuilder = new Device.Builder();
  private final DeviceUiAction.DeviceProvider myProvider;
  private StringProperty myWizardPanelDescription = new StringValueProperty();
  private AvdDeviceData myDeviceData;

  public StringProperty wizardPanelDescription() {
    return myWizardPanelDescription;
  }

  public AvdDeviceData getDeviceData() {
    return myDeviceData;
  }

  public ConfigureDeviceModel(@NotNull DeviceUiAction.DeviceProvider provider) {
    this(provider, null, true);
  }

  public ConfigureDeviceModel(@NotNull DeviceUiAction.DeviceProvider provider, @Nullable Device device, boolean forcedCreation) {
    myProvider = provider;
    myDeviceData = new AvdDeviceData(device, forcedCreation);
    Disposer.register(this, myDeviceData);

    String description =
      (device == null) ? "Create a new hardware profile by selecting hardware features below." : device.getDisplayName() + " (Edited)";
    myWizardPanelDescription.set(description);

    initBootProperties(device);
  }

  /**
   * Initialize boot properties from the given device
   */
  private void initBootProperties(@Nullable Device device) {
    if (device != null) {
      for (Map.Entry<String, String> entry : device.getBootProps().entrySet()) {
        myBuilder.addBootProp(entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  protected void handleFinished() {
    Device device = buildDevice();
    DeviceManagerConnection.getDefaultDeviceManagerConnection().createOrEditDevice(device);
    myProvider.refreshDevices();
    myProvider.setDevice(device);
  }

  /**
   * Once we finish editing the device, we set it to its final configuration
   */
  @NotNull
  private Device buildDevice() {
    String deviceName = myDeviceData.name().get();
    myBuilder.setName(deviceName);
    myBuilder.setId(deviceName);
    myBuilder.addSoftware(myDeviceData.software().getValue());
    myBuilder.setManufacturer(myDeviceData.manufacturer().get());
    IdDisplay tag = myDeviceData.deviceType().getValueOrNull();
    myBuilder.setTagId((SystemImage.DEFAULT_TAG.equals(tag) || tag == null) ? null : tag.getId());
    List<State> states = generateStates(new AvdHardwareData(myDeviceData).buildHardware());
    myBuilder.addAllState(states);
    return myBuilder.build();
  }

  @Nullable
  private static State createState(ScreenOrientation orientation, Hardware hardware, boolean hasHardwareKeyboard) {
    State state = null;
    String name = "";
    String description = "";

    if (orientation == ScreenOrientation.LANDSCAPE) {
      name = "Landscape";
      description = "The device in landscape orientation";
      state = new State();
    }
    else if (orientation == ScreenOrientation.PORTRAIT) {
      name = "Portrait";
      description = "The device in portrait orientation";
      state = new State();
    }

    if (state != null) {
      if (hasHardwareKeyboard) {
        name += " with keyboard";
        description += " with a keyboard open";
        state.setKeyState(KeyboardState.EXPOSED);
      }
      else {
        if (hardware.getKeyboard() != null && hardware.getKeyboard().equals(Keyboard.NOKEY)) {
          state.setKeyState(KeyboardState.SOFT);
        }
        else {
          state.setKeyState(KeyboardState.HIDDEN);
        }
      }
      state.setName(name);
      state.setHardware(hardware);
      state.setOrientation(orientation);
      state.setDescription(description);
      state.setNavState(hardware.getNav().equals(Navigation.NONAV) ? NavigationState.HIDDEN : NavigationState.EXPOSED);
    }

    return state;
  }

  private List<State> generateStates(Hardware hardware) {
    List<State> states = Lists.newArrayListWithExpectedSize(4);

    if (myDeviceData.supportsPortrait().get()) {
      states.add(createState(ScreenOrientation.PORTRAIT, hardware, false));
    }

    if (myDeviceData.supportsLandscape().get()) {
      states.add(createState(ScreenOrientation.LANDSCAPE, hardware, false));
    }

    if (myDeviceData.hasHardwareKeyboard().get()) {
      if (myDeviceData.supportsPortrait().get()) {
        states.add(createState(ScreenOrientation.PORTRAIT, hardware, true));
      }

      if (myDeviceData.supportsLandscape().get()) {
        states.add(createState(ScreenOrientation.LANDSCAPE, hardware, true));
      }
    }

    // We've added states in the order of most common to least common, so let's mark the first one as default
    states.get(0).setDefaultState(true);
    return states;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}

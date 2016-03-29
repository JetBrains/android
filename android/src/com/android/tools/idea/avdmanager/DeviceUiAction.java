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

import com.android.sdklib.devices.Device;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;

/**
 * A base class for actions that operate on {@link Device}s and can be bound to buttons
 */
public abstract class DeviceUiAction implements Action {
  @NotNull protected final DeviceProvider myProvider;
  @NotNull private final String myText;

  public interface DeviceProvider {
    @Nullable
    Device getDevice();
    void refreshDevices();
    void setDevice(@Nullable Device device);
    void selectDefaultDevice();
  }

  public DeviceUiAction(@NotNull DeviceProvider provider, @NotNull String text) {
    myProvider = provider;
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  public Object getValue(String key) {
    return Action.NAME.equals(key) ? myText : null;
  }

  @Override
  public void putValue(String key, Object value) {

  }

  @Override
  public void setEnabled(boolean b) {

  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {

  }

  static void showHardwareProfileWizard(ConfigureDeviceModel model) {
    ModelWizard wizard = new ModelWizard.Builder().addStep(new ConfigureDeviceOptionsStep(model)).build();
    new StudioWizardDialogBuilder(wizard, "Hardware Profile Configuration").build().show();
  }
}

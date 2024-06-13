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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.intellij.openapi.project.Project;
import java.beans.PropertyChangeListener;
import javax.swing.Action;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    Project getProject();
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
    new StudioWizardDialogBuilder(wizard, "Hardware Profile Configuration").setProject(model.getProject()).build().show();
  }
}

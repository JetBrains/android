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
import com.android.tools.idea.wizard.DynamicWizardStepWithHeaderAndDescription;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.DEVICE_DEFINITION_KEY;

/**
 * Wizard step for selecting a device definition from the devices declared in the SDK and
 * defined by the user.
 */
public class ChooseDeviceDefinitionStep extends DynamicWizardStepWithHeaderAndDescription {
  private JPanel myPanel;
  private DeviceDefinitionList myDeviceDefinitionList;
  private DeviceDefinitionPreview myDeviceDefinitionPreview;

  public ChooseDeviceDefinitionStep(@Nullable Disposable parentDisposable) {
    super("Select Hardware", "Choose a device definition", null, parentDisposable);
    setBodyComponent(myPanel);
    myDeviceDefinitionList.addSelectionListener(new DeviceDefinitionList.DeviceDefinitionSelectionListener() {
      @Override
      public void onDeviceSelectionChanged(@Nullable Device selectedDevice) {
        myDeviceDefinitionPreview.setDevice(selectedDevice);
        myState.put(DEVICE_DEFINITION_KEY, selectedDevice);
      }
    });
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    myDeviceDefinitionList.setSelectedDevice(myState.get(DEVICE_DEFINITION_KEY));
  }

  @Override
  public boolean validate() {
    return myState.get(DEVICE_DEFINITION_KEY) != null;
  }

  @Override
  public boolean isStepVisible() {
    return myState.get(DEVICE_DEFINITION_KEY) == null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return AvdWizardConstants.CHOOSE_DEVICE_DEFINITION_STEP;
  }
}

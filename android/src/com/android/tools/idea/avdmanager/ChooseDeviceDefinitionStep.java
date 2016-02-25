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
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.swing.util.FormScalingUtil;
import com.intellij.openapi.Disposable;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.DEVICE_DEFINITION_KEY;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.IS_IN_EDIT_MODE_KEY;

/**
 * Wizard step for selecting a device definition from the devices declared in the SDK and
 * defined by the user.
 */
public class ChooseDeviceDefinitionStep extends DynamicWizardStepWithDescription implements DeviceUiAction.DeviceProvider {
  private JPanel myPanel;
  private DeviceDefinitionList myDeviceDefinitionList;
  private com.android.tools.idea.avdmanager.legacy.DeviceDefinitionPreview myDeviceDefinitionPreview;
  private JButton myEditDeviceButton;
  private JPanel myEditButtonContainer;

  private final EditDeviceAction myEditDeviceAction = new EditDeviceAction(this, "Edit Device...");
  private final CreateDeviceAction myCreateDeviceAction = new CreateDeviceAction(this, "Create Device...");
  private final CloneDeviceAction myCloneDeviceAction = new CloneDeviceAction(this, "Clone Device...");

  public ChooseDeviceDefinitionStep(@Nullable Disposable parentDisposable) {
    super(parentDisposable);
    setBodyComponent(myPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), createStepBody());
    myDeviceDefinitionList.setParentProvider(this);
    myDeviceDefinitionList.addSelectionListener(new DeviceDefinitionList.DeviceDefinitionSelectionListener() {
      @Override
      public void onDeviceSelectionChanged(@Nullable Device selectedDevice) {
        myDeviceDefinitionPreview.setDevice(selectedDevice);
        myState.put(DEVICE_DEFINITION_KEY, selectedDevice);
        updateEditButton(selectedDevice);
      }
    });
    myDeviceDefinitionList.addCategoryListener(myDeviceDefinitionPreview);
    myEditButtonContainer.setBackground(JBColor.background());
    myEditDeviceButton.setBackground(JBColor.background());
    myDeviceDefinitionList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
    updateEditButton(null);
  }

  private void updateEditButton(@Nullable Device selectedDevice) {
    myEditDeviceButton.setAction(null);
    Action action;
    if (selectedDevice == null) {
      action = myCreateDeviceAction;
    } else if (DeviceManagerConnection.getDefaultDeviceManagerConnection().isUserDevice(selectedDevice)) {
      action = myEditDeviceAction;
    } else {
      action = myCloneDeviceAction;
    }
    myEditDeviceButton.setAction(action);
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    Device selectedDevice = myState.get(DEVICE_DEFINITION_KEY);
    if (selectedDevice == null) {
      myDeviceDefinitionList.selectDefaultDevice();
    } else {
      myDeviceDefinitionList.setSelectedDevice(selectedDevice);
    }
  }

  @Override
  public boolean validate() {
    return myState.get(DEVICE_DEFINITION_KEY) != null;
  }

  @Override
  public boolean isStepVisible() {
    return !myState.getNotNull(IS_IN_EDIT_MODE_KEY, false);
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

  @Nullable
  @Override
  public Device getDevice() {
    return myDeviceDefinitionList.getDevice();
  }

  @Override
  public void setDevice(@Nullable Device device) {
    myDeviceDefinitionList.setSelectedDevice(device);
  }

  @Override
  public void selectDefaultDevice() {
    myDeviceDefinitionList.selectDefaultDevice();
  }

  @Override
  public void refreshDevices() {
    myDeviceDefinitionList.refreshDevices();
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Select Hardware";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return "Choose a device definition";
  }

}

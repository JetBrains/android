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

import com.android.sdklib.devices.Device;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Optional;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * {@link ModelWizardStep} for selecting a device definition from the devices declared in the SDK
 * and defined by the user.
 *
 * The whole purpose of this step is to select a single device from a list of possible devices, and
 * instead of relying on a model, it takes an a constructor argument to store the selected result.
 */
public final class ChooseDeviceDefinitionStep extends ModelWizardStep.WithoutModel implements DeviceUiAction.DeviceProvider {
  private final EditDeviceAction myEditDeviceAction = new EditDeviceAction(this, "Edit Device...");
  private final CreateDeviceAction myCreateDeviceAction = new CreateDeviceAction(this, "Create Device...");
  private final CloneDeviceAction myCloneDeviceAction = new CloneDeviceAction(this, "Clone Device...");

  private DeviceDefinitionPreview myDeviceDefinitionPreview;
  private JPanel myEditButtonContainer;
  private JButton myEditDeviceButton;
  private JPanel myPanel;
  private StudioWizardStepPanel myStudioWizardStepPanel;
  private ValidatorPanel myValidatorPanel;
  private DeviceDefinitionList myDeviceDefinitionList;
  private OptionalProperty<Device> myDevice;

  public ChooseDeviceDefinitionStep(OptionalProperty<Device> device) {
    super("Select Hardware");
    myStudioWizardStepPanel = new StudioWizardStepPanel(myPanel, "Choose a device definition");
    myValidatorPanel = new ValidatorPanel(this, myStudioWizardStepPanel);
    myDevice = device;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    FormScalingUtil.scaleComponentTree(this.getClass(), myPanel);

    myValidatorPanel.registerValidator(myDevice, new Validator<Optional<Device>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<Device> value) {
        return (value.isPresent())
               ? Result.OK
               : new Validator.Result(Severity.ERROR, "A hardware profile must be selected to continue.");
      }
    });

    myDeviceDefinitionList.addSelectionListener(new DeviceDefinitionList.DeviceDefinitionSelectionListener() {
      @Override
      public void onDeviceSelectionChanged(@Nullable Device selectedDevice) {
        if (selectedDevice != null && myDeviceDefinitionPreview.getDeviceData() != null) {
          myDeviceDefinitionPreview.getDeviceData().getValuesFromDevice(selectedDevice, false);
        }
        else {
          myDeviceDefinitionPreview.getDeviceData().name().set(DeviceDefinitionPreview.DO_NOT_DISPLAY);
        }
        updateEditButton(selectedDevice);
        myDevice.setNullableValue(selectedDevice);
      }
    });

    myDeviceDefinitionList.addCategoryListener(myDeviceDefinitionPreview);
    myDeviceDefinitionList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));

    myEditButtonContainer.setBackground(JBColor.background());
    myEditDeviceButton.setBackground(JBColor.background());
    updateEditButton(null);
  }

  @Override
  protected void onEntering() {
    if (myDevice.get().isPresent()) {
      myDeviceDefinitionList.setSelectedDevice(myDevice.getValue());
    }
    else {
      myDeviceDefinitionList.selectDefaultDevice();
    }
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void updateEditButton(@Nullable Device selectedDevice) {
    myEditDeviceButton.setAction(null);
    Action action;
    if (selectedDevice == null) {
      action = myCreateDeviceAction;
    }
    else if (DeviceManagerConnection.getDefaultDeviceManagerConnection().isUserDevice(selectedDevice)) {
      action = myEditDeviceAction;
    }
    else {
      action = myCloneDeviceAction;
    }
    myEditDeviceButton.setAction(action);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  public Device getDevice() {
    if (myDevice.get().isPresent()) {
      return myDevice.getValue();
    }
    return null;
  }

  @Override
  public void setDevice(@Nullable Device device) {
    myDeviceDefinitionList.setSelectedDevice(device);
    myDevice.setNullableValue(device);
  }

  @Override
  public void selectDefaultDevice() {
    myDeviceDefinitionList.selectDefaultDevice();
  }

  @Override
  public void refreshDevices() {
    myDeviceDefinitionList.refreshDevices();
  }

  private void createUIComponents() {
    myDeviceDefinitionPreview = new DeviceDefinitionPreview(new AvdDeviceData());
  }
}

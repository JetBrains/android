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
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * UI panel that presents the user with a list of {@link Device}s to choose from.
 *
 * You should register a listener via {@link #addDeviceListener(Consumer)} to be notified of
 * when the user updates their choice.
 */
public final class ChooseDeviceDefinitionPanel extends JPanel implements DeviceUiAction.DeviceProvider, Disposable {
  private final EditDeviceAction myEditDeviceAction = new EditDeviceAction(this, "Edit Device...");
  private final CreateDeviceAction myCreateDeviceAction = new CreateDeviceAction(this, "Create Device...");
  private final CloneDeviceAction myCloneDeviceAction = new CloneDeviceAction(this, "Clone Device...");

  private DeviceDefinitionPreview myDeviceDefinitionPreview;
  private JPanel myEditButtonContainer;
  private JButton myEditDeviceButton;
  private JPanel myPanel;
  private DeviceDefinitionList myDeviceDefinitionList;

  private List<Consumer<Device>> myDeviceListeners = Lists.newArrayListWithExpectedSize(1);

  public ChooseDeviceDefinitionPanel(@Nullable Device initialDevice) {
    super(new BorderLayout());
    FormScalingUtil.scaleComponentTree(this.getClass(), myPanel);
    myDeviceDefinitionList.addSelectionListener(new DeviceDefinitionList.DeviceDefinitionSelectionListener() {
      @Override
      public void onDeviceSelectionChanged(@Nullable Device selectedDevice) {
        if (selectedDevice != null) {
          myDeviceDefinitionPreview.getDeviceData().updateValuesFromDevice(selectedDevice);
        }
        else {
          myDeviceDefinitionPreview.getDeviceData().name().set(DeviceDefinitionPreview.DO_NOT_DISPLAY);
        }
        updateEditButton(selectedDevice);
        for (Consumer<Device> listener : myDeviceListeners) {
          listener.consume(selectedDevice);
        }
      }
    });

    myDeviceDefinitionList.addCategoryListener(myDeviceDefinitionPreview);
    myDeviceDefinitionList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));

    myEditButtonContainer.setBackground(JBColor.background());
    myEditDeviceButton.setBackground(JBColor.background());

    if (initialDevice != null) {
      myDeviceDefinitionList.setSelectedDevice(initialDevice);
    }
    else {
      myDeviceDefinitionList.selectDefaultDevice();
    }

    updateEditButton(initialDevice);

    add(myPanel);
  }

  public void addDeviceListener(@NotNull Consumer<Device> onDeviceSelected) {
    myDeviceListeners.add(onDeviceSelected);
    onDeviceSelected.consume(getDevice());
  }

  @Nullable
  @Override
  public Device getDevice() {
    return myDeviceDefinitionList.getDevice();
  }

  @Override
  public void setDevice(@Nullable Device device) {
    myDeviceDefinitionList.setSelectedDevice(device);
    updateEditButton(device);
  }

  @Override
  public void selectDefaultDevice() {
    myDeviceDefinitionList.selectDefaultDevice();
  }

  @Override
  public void refreshDevices() {
    myDeviceDefinitionList.refreshDevices();
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

  private void createUIComponents() {
    myDeviceDefinitionPreview = new DeviceDefinitionPreview(new AvdDeviceData());
  }

  @Override
  public void dispose() {
    myDeviceListeners.clear();
  }
}

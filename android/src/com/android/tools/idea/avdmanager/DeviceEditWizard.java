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
import com.android.tools.idea.wizard.dynamic.SingleStepDialogWrapperHost;
import com.android.tools.idea.wizard.dynamic.SingleStepWizard;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard for creating or editing a {@link Device}
 */
public class DeviceEditWizard extends SingleStepWizard {
  /**
   * @param deviceTemplate If not null, the given device will be cloned.
   * @param forceCreation if set to true, the given device will be edited rather than cloned
   */
  public DeviceEditWizard(@Nullable Device deviceTemplate, boolean forceCreation) {
    super(null, null, new ConfigureDeviceOptionsStep(deviceTemplate, forceCreation, null),
          new SingleStepDialogWrapperHost(null, DialogWrapper.IdeModalityType.PROJECT));
    setTitle("Hardware Profile Configuration");
  }

  @Override
  public void performFinishingActions() {
    Device device = getState().get(AvdWizardConstants.DEVICE_DEFINITION_KEY);
    if (device != null) {
      DeviceManagerConnection.getDefaultDeviceManagerConnection().createOrEditDevice(device);
    }
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Creating/Updating device...";
  }

  @Nullable
  public Device getEditedDevice() {
    return getState().get(AvdWizardConstants.DEVICE_DEFINITION_KEY);
  }

  @Override
  protected String getWizardActionDescription() {
    return "Create or edit a virtual device hardware profile";
  }
}

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
import com.android.sdklib.devices.DeviceManager;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.SingleStepPath;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard for creating or editing a {@link Device}
 */
public class DeviceEditWizard extends DynamicWizard {
  @Nullable private final Device myDeviceTemplate;
  private final boolean myForceCreation;

  /**
   * @param deviceTemplate If not null, the given device will be cloned.
   * @param forceCreation if set to true, the given device will be edited rather than cloned
   */
  public DeviceEditWizard(@Nullable Device deviceTemplate, boolean forceCreation) {
    super(null, null, "Create hardware profile");
    myDeviceTemplate = deviceTemplate;
    myForceCreation = forceCreation;
    setTitle("Hardware Profile Configuration");
  }

  @Override
  public void init() {
    addPath(new SingleStepPath(new ConfigureDeviceOptionsStep(myDeviceTemplate, myForceCreation, getDisposable())));
    super.init();
  }

  @Override
  public void performFinishingActions() {
    Device device = getState().get(AvdWizardConstants.DEVICE_DEFINITION_KEY);
    if (device != null) {
      DeviceManagerConnection.getDefaultDeviceManagerConnection().createOrEditDevice(device);
    }
  }

  @Override
  protected String getWizardActionDescription() {
    return "Create or edit a virtual device hardware profile";
  }
}

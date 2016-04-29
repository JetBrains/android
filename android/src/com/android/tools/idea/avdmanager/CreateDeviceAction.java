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

import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;

/**
 * Create a new {@link com.android.sdklib.devices.Device}
 */
public class CreateDeviceAction extends DeviceUiAction {
  public CreateDeviceAction(@NotNull DeviceProvider provider) {
    super(provider, "Create");
  }

  public CreateDeviceAction(@NotNull DeviceProvider provider, @NotNull String text) {
    super(provider, text);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new ConfigureDeviceOptionsStep(new ConfigureDeviceModel(myProvider), myProvider.getProject()));
    ModelWizard wizard = wizardBuilder.build();
    ModelWizardDialog dialog =
      new StudioWizardDialogBuilder(wizard, "Hardware Profile Configuration").setProject(myProvider.getProject()).build();
    dialog.show();
  }
}


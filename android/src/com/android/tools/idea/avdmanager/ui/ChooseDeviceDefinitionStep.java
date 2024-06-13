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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Device;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ModelWizardStep} for selecting a device definition from the devices declared in the SDK
 * and defined by the user.
 */
public final class ChooseDeviceDefinitionStep extends ModelWizardStep<AvdOptionsModel> {
  private JPanel myRootPanel;
  private ChooseDeviceDefinitionPanel myDeviceDefinitionPanel;
  private StudioWizardStepPanel myStudioWizardStepPanel;
  private ValidatorPanel myValidatorPanel;

  public ChooseDeviceDefinitionStep(@NotNull AvdOptionsModel model) {
    super(model, "Select Hardware");
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    myStudioWizardStepPanel = new StudioWizardStepPanel(myValidatorPanel, "Choose a device definition");
    FormScalingUtil.scaleComponentTree(this.getClass(), myStudioWizardStepPanel);

    Disposer.register(this, myDeviceDefinitionPanel);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myValidatorPanel.registerValidator(getModel().device(), new Validator<Optional<Device>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<Device> value) {
        return (value.isPresent())
               ? Result.OK
               : new Validator.Result(Severity.ERROR, "A hardware profile must be selected to continue.");
      }
    });

    myDeviceDefinitionPanel.addDeviceListener(new Consumer<Device>() {
      @Override
      public void consume(Device device) {
        getModel().device().setNullableValue(device);
      }
    });
  }

  @Override
  protected void onEntering() {
    myDeviceDefinitionPanel.setDevice(getModel().device().getValueOrNull());
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioWizardStepPanel;
  }

  private void createUIComponents() {
    myDeviceDefinitionPanel = new ChooseDeviceDefinitionPanel(getModel().device().getValueOrNull());
  }
}

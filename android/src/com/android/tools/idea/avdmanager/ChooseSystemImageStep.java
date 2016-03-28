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

import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Optional;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Wizard step for selecting a {@link SystemImage} from the installed images in the SDK.
 */
public class ChooseSystemImageStep extends ModelWizardStep<AvdOptionsModel> {
  @Nullable private final Project myProject;

  private JPanel myPanel;
  private ChooseSystemImagePanel myChooseImagePanel;
  private StudioWizardStepPanel myStudioWizardStepPanel;
  private ValidatorPanel myValidatorPanel;

  public ChooseSystemImageStep(@NotNull AvdOptionsModel model, @Nullable Project project) {
    super(model, "System Image");
    myProject = project;
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myStudioWizardStepPanel = new StudioWizardStepPanel(myValidatorPanel, "Select a system image");
    FormScalingUtil.scaleComponentTree(this.getClass(), myStudioWizardStepPanel);

    myChooseImagePanel.addSystemImageListener(new Consumer<SystemImageDescription>() {
      @Override
      public void consume(SystemImageDescription systemImage) {
        getModel().systemImage().setNullableValue(systemImage);
      }
    });
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myValidatorPanel.registerValidator(getModel().systemImage(), new Validator<Optional<SystemImageDescription>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<SystemImageDescription> value) {
        return (value.isPresent())
               ? Result.OK
               : new Validator.Result(Severity.ERROR, "A system image must be selected to continue.");
      }
    });
  }

  @Override
  protected void onEntering() {
    myChooseImagePanel.setDevice(getModel().device().getValueOrNull());
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
    myChooseImagePanel =
      new ChooseSystemImagePanel(myProject, getModel().device().getValueOrNull(), getModel().systemImage().getValueOrNull());
  }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard step that allows the user to point to an existing ml model file to import as ml model. Also shows
 * necessary deps to use this ml model.
 */
public class ChooseMlModelStep extends ModelWizardStep<MlWizardModel> {

  private final BindingsManager myBindings = new BindingsManager();

  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private final ValidatorPanel myValidatorPanel;

  private JPanel myPanel;
  private TextFieldWithBrowseButton myModelLocation;
  private JLabel myRequiredInfoLabel;

  public ChooseMlModelStep(@NotNull MlWizardModel model, @NotNull Project project, @NotNull String title) {
    super(model, title);

    myModelLocation.addBrowseFolderListener("Select TFLite Model Location",
                                            "Select existing TFLite model to import to ml folder",
                                            project,
                                            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor());

    myBindings.bindTwoWay(new TextProperty(myModelLocation.getTextField()), model.sourceLocation);

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    Expression<File> locationFile = model.sourceLocation.transform(File::new);
    myValidatorPanel.registerValidator(locationFile, value -> checkPath(value));

    //TODO(jackqdyulei): Get it from mlkit-common module.
    List<String> requiredDependencies = Arrays.asList(
      "org.apache.commons:commons-compress:1.19",
      "org.tensorflow:tensorflow-lite:1.13.1",
      "org.tensorflow:tensorflow-lite-support:0.0.0-nightly");

    myRequiredInfoLabel.setText(getRequiredInformationHtml(requiredDependencies));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  private static String getRequiredInformationHtml(@NotNull List<String> requiredDeps) {
    // TODO(jackqdyulei): Add dependencies automatically after user click Finish.
    StringBuilder stringBuilder = new StringBuilder("You need to add following deps\n\n");
    for (String dep : requiredDeps) {
      stringBuilder.append(dep + "\n");
    }

    return WizardUtils.toHtmlString(stringBuilder.toString());
  }

  @NotNull
  Validator.Result checkPath(@NotNull File file) {
    //TODO(jackqdyulei): check whether destination already contains this file.
    if (!file.isFile() || !file.getName().endsWith(".tflite")) {
      return new Validator.Result(Validator.Severity.ERROR, "This file is not a tflite model file");
    }
    return Validator.Result.OK;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myModelLocation;
  }
}

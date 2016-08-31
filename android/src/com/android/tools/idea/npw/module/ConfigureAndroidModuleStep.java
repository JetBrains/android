/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.FormFactorApiComboBox;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.ui.LabelWithEditLink;
import com.android.tools.idea.ui.properties.core.ObjectProperty;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.android.util.AndroidBundle.message;


public final class ConfigureAndroidModuleStep extends SkippableWizardStep<NewModuleModel> {
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final FormFactor myFormFactor;
  private ObjectProperty<AndroidSourceSet> mySourceSet;
  private FormFactorApiComboBox mySdkControls;
  private JTextField myModuleName;
  private JPanel myPanel;
  private JTextField myAppName;
  private LabelWithEditLink myPackageName;

  // TODO: At the moment only creates the next step. Needs all code to create AndroidSourceSet
  protected ConfigureAndroidModuleStep(@NotNull NewModuleModel model, @NotNull FormFactor formFactor, @NotNull String title) {
    super(model, title);

    myFormFactor = formFactor;
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, message("android.wizard.module.configure.title"));
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    List<TemplateHandle> templateList = TemplateManager.getInstance().getTemplateList(myFormFactor);

    AndroidSourceSet sourceSet = new AndroidSourceSet("Dummy", new AndroidProjectPaths(null, null, null, null, null, null));

    RenderTemplateModel renderModel =
      new RenderTemplateModel(getModel().getProject().getValue(), templateList.get(0), sourceSet,
                              message("android.wizard.activity.add"));

    mySourceSet = renderModel.getSourceSet();

    ChooseActivityTypeStep chooseActivityTypeStep =
      new ChooseActivityTypeStep(renderModel, "todo.pakage.name", templateList, Lists.newArrayList());

    return Lists.newArrayList(chooseActivityTypeStep);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  private void createUIComponents() {
    mySdkControls = new FormFactorApiComboBox();
  }

  @Override
  protected void onProceeding() {
    // TODO: Need to create a new AndroidSourceSet and assign it to mySourceSet
  }
}

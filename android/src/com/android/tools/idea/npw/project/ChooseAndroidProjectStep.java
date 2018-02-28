/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * First page in the New Project wizard that allows user to select the Form Factor (Mobile, Wear, TV, etc) and its
 * Template ("Empty Activity", "Basic", "Nav Drawer", etc)
 */
public class ChooseAndroidProjectStep extends ModelWizardStep<NewProjectModel> {

  private JPanel myRootPanel;
  private JComboBox<FormFactor> myFormFactorComboBox;


  public ChooseAndroidProjectStep(@NotNull NewProjectModel model) {
    super(model, message("android.wizard.project.new.choose"));

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Lists.newArrayList();
  }

  @Override
  protected void onEntering() {
    for (FormFactor formFactor : FormFactor.values()) {
      myFormFactorComboBox.addItem(formFactor);
    }
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFormFactorComboBox;
  }
}

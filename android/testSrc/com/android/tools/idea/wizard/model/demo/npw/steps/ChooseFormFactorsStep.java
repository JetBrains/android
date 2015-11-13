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
package com.android.tools.idea.wizard.model.demo.npw.steps;

import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.demo.npw.android.FormFactor;
import com.android.tools.idea.wizard.model.demo.npw.models.ActivityModel;
import com.android.tools.idea.wizard.model.demo.npw.models.ProjectModel;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions.any;

public final class ChooseFormFactorsStep extends ModelWizardStep<ProjectModel> {
  private final ListenerManager myListeners = new ListenerManager();
  private final Map<FormFactor, ChooseActivityStep> myFormFactorSteps = Maps.newHashMap();

  private JPanel myRootPanel;

  private JCheckBox myPreferredFocus;

  private ObservableBool myAnySelected;

  public ChooseFormFactorsStep(@NotNull ProjectModel model) {
    super(model, "Target Android Devices");
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();
    for (final FormFactor formFactor : FormFactor.FORM_FACTORS) {
      ActivityModel model = new ActivityModel(formFactor);
      ChooseActivityStep step = new ChooseActivityStep(model);
      step.setVisible(false); // Default to false, show only if selected
      myFormFactorSteps.put(formFactor, step);
      allSteps.add(step);
    }

    return allSteps;
  }

  @Override
  protected void onWizardStarting(@NotNull final ModelWizard.Facade wizard) {

    List<SelectedProperty> checkboxSelectedProperties = Lists.newArrayListWithCapacity(FormFactor.FORM_FACTORS.size());
    for (final FormFactor formFactor : FormFactor.FORM_FACTORS) {
      JCheckBox checkbox = new JCheckBox(formFactor.getName());

      SelectedProperty selected = new SelectedProperty(checkbox);
      if (formFactor == FormFactor.MOBILE) {
        checkbox.setSelected(true);
        myPreferredFocus = checkbox;
      }

      myListeners.listenAndFire(selected, new Consumer<Boolean>() {
        @Override
        public void consume(Boolean selected) {
          ChooseActivityStep step = myFormFactorSteps.get(formFactor);
          step.setVisible(selected);
        }
      });

      myRootPanel.add(checkbox);
      checkboxSelectedProperties.add(selected);
    }

    myAnySelected = any(checkboxSelectedProperties);
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myAnySelected;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myPreferredFocus;
  }

  private void createUIComponents() {
    myRootPanel = new JPanel(new VerticalFlowLayout());
  }
}

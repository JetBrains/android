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

import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.swing.SelectedIndexProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.demo.npw.android.ActivityTemplate;
import com.android.tools.idea.wizard.model.demo.npw.models.ActivityModel;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings("unchecked")
public final class ChooseActivityStep extends ModelWizardStep<ActivityModel> {

  private JPanel myRootPanel;
  private JComboBox myTargetActivityComboBox;
  private ListenerManager myListeners = new ListenerManager();

  private boolean myIsVisible = true;

  public ChooseActivityStep(@NotNull ActivityModel model) {
    super(model, String.format("Add an Activity to %s", model.getFormFactor().getName()));

    DefaultComboBoxModel comboboxModel = new DefaultComboBoxModel();
    comboboxModel.addElement(null);
    for (ActivityTemplate activityTemplate : model.getFormFactor().getTemplates()) {
      comboboxModel.addElement(activityTemplate);
    }
    myTargetActivityComboBox.setModel(comboboxModel);
    myTargetActivityComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("Add No Activity");
        }
        else {
          setText(((ActivityTemplate)value).getName());
        }
      }
    });
  }

  /**
   * Hook to hide/show this step externally, as some steps may enable / disable the form factor
   * backing this step.
   */
  public void setVisible(boolean visible) {
    myIsVisible = visible;
  }

  @Override
  protected boolean shouldShow() {
    return myIsVisible;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfigureActivityStep(getModel()));
  }

  @Override
  protected void onWizardStarting(@NotNull final ModelWizard.Facade wizard) {
    myListeners.listen(new SelectedIndexProperty(myTargetActivityComboBox), new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        ActivityTemplate targetTemplate = (ActivityTemplate)myTargetActivityComboBox.getSelectedItem();
        // Set the target template in the model immediately (instead of waiting until
        // onProceeding), since whether this is set or not affects if later steps show up.
        getModel().setTargetTemplate(targetTemplate);
        wizard.updateNavigationProperties();
      }
    });

    myTargetActivityComboBox.setSelectedItem(getModel().getTargetTemplate());
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myTargetActivityComboBox;
  }
}

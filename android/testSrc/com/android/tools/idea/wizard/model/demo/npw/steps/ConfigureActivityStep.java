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

import com.android.tools.idea.ui.ProportionalLayout;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.demo.npw.models.ActivityModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class ConfigureActivityStep extends ModelWizardStep<ActivityModel> {
  private JPanel myRootPanel;
  private JBTextField myPreferredFocus;

  public ConfigureActivityStep(@NotNull ActivityModel model) {
    super(model, String.format("Customize the %s Activity", model.getFormFactor().getName()));
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

  @Override
  protected boolean shouldShow() {
    return getModel().getTargetTemplate() != null;
  }

  @Override
  protected void onEntering() {
    assert getModel().getTargetTemplate() != null; // Verified by shouldSkip

    myRootPanel.removeAll();
    int row = 0;
    for (String param : getModel().getTargetTemplate().getParameters()) {
      myRootPanel.add(new JBLabel(param + ": "), new ProportionalLayout.Constraint(row, 0));
      JBTextField valueTextField = new JBTextField("Value" + (row + 1));
      valueTextField.putClientProperty("param", param);
      myRootPanel.add(valueTextField, new ProportionalLayout.Constraint(row, 1));

      if (row == 0) {
        myPreferredFocus = valueTextField;
      }

      row++;
    }
  }

  private void createUIComponents() {
    myRootPanel = new JPanel(ProportionalLayout.fromString("Fit,*"));
  }

  @Override
  protected void onProceeding() {
    getModel().getKeyValues().clear();
    for (Component component : myRootPanel.getComponents()) {
      if (component instanceof JBTextField) {
        JBTextField valueTextField = (JBTextField)component;
        getModel().getKeyValues().put((String)valueTextField.getClientProperty("param"), valueTextField.getText());
      }
    }
  }
}

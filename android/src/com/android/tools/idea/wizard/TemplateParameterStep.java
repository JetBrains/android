/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.android.tools.idea.templates.Parameter;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import javax.swing.*;

/**
 * TemplateParameterStep is a step in new project or add module wizards that pulls eligible parameters from the template being run
 * and puts up a UI to let the user edit those parameters.
 */
public class TemplateParameterStep extends TemplateWizardStep {
  private JPanel myParamContainer;
  private JPanel myContainer;
  private JLabel myDescription;
  private JLabel myError;
  private JComponent myPreferredFocusComponent;

  public TemplateParameterStep(TemplateWizardState state, @Nullable Project project, @Nullable Icon sidePanelIcon,
                               UpdateListener updateListener) {
    super(state, project, sidePanelIcon, updateListener);
  }

  @Override
  public void _init() {
    myParamContainer.removeAll();
    myPreferredFocusComponent = null;
    int row = 0;
    Collection<Parameter> parameters = myTemplateState.getTemplateMetadata().getParameters();
    myParamContainer.setLayout(new GridLayoutManager(parameters.size() + 1, 3));
    GridConstraints c = new GridConstraints();
    c.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    c.setAnchor(GridConstraints.ANCHOR_WEST);
    c.setFill(GridConstraints.FILL_HORIZONTAL);
    for (Parameter parameter : parameters) {
      if (myTemplateState.myHidden.contains(parameter.id)) {
        continue;
      }
      JLabel label = new JLabel(parameter.name);
      c.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW);
      c.setColumn(0);
      c.setColSpan(1);
      c.setRow(row++);
      Object value = myTemplateState.get(parameter.id) != null ? myTemplateState.get(parameter.id) : parameter.initial;
      myTemplateState.put(parameter.id, value);
      switch(parameter.type) {
        case BOOLEAN:
          c.setColumn(1);
          JCheckBox checkBox = new JCheckBox(parameter.name);
          register(parameter.id, checkBox);
          myParamContainer.add(checkBox, c);
          if (myPreferredFocusComponent == null) {
            myPreferredFocusComponent = checkBox;
          }
          break;
        case ENUM:
          myParamContainer.add(label, c);
          JComboBox comboBox = new JComboBox();
          c.setColumn(1);
          populateComboBox(comboBox, parameter);
          register(parameter.id, comboBox);
          myParamContainer.add(comboBox, c);
          if (myPreferredFocusComponent == null) {
            myPreferredFocusComponent = comboBox;
          }
          break;
        case SEPARATOR:
          myParamContainer.add(new JSeparator());
          break;
        case STRING:
          myParamContainer.add(label, c);
          c.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
          JTextField textField = new JTextField();
          c.setColumn(1);
          c.setColSpan(2);
          register(parameter.id, textField);
          myParamContainer.add(textField, c);
          if (myPreferredFocusComponent == null) {
            myPreferredFocusComponent = textField;
          }
          break;
      }
    }
    c.setHSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
    c.setVSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
    c.setRow(row);
    c.setColSpan(1);
    c.setColumn(2);
    myParamContainer.add(new Spacer(), c);
  }

  @Override
  public JComponent getComponent() {
    return myContainer;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocusComponent;
  }

  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @Override
  protected JLabel getError() {
    return myError;
  }

  @Override
  public boolean isStepVisible() {
    boolean hasVisibleParameter = false;
    if (myTemplateState.hasTemplate()) {
      Collection<Parameter> parameters = myTemplateState.getTemplateMetadata().getParameters();
      for (Parameter parameter : parameters) {
        if (!myTemplateState.myHidden.contains(parameter.id)) {
          hasVisibleParameter = true;
          break;
        }
      }
    }
    return hasVisibleParameter && myVisible;
  }
}

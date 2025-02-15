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
package com.android.tools.idea.gradle.structure.configurables.ui.properties.manipulation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.Dimension;
import java.awt.Insets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ExtractVariableForm {
  public JPanel myPanel;
  public JTextField myNameField;
  public JComponent myValueEditor;
  public JComboBox myScopeField;

  public ExtractVariableForm() {
    setupUI();
  }

  public void setValueEditor(@NotNull JComponent editor) {
    if (myValueEditor != null) {
      myPanel.remove(myValueEditor);
      if (myValueEditor instanceof Disposable) {
        Disposer.dispose((Disposable)myValueEditor);
      }
    }
    myValueEditor = editor;
    myValueEditor.setName("value");
    myPanel.add(myValueEditor,
                new GridConstraints(1, 1, 1, 1,
                                    GridConstraints.ALIGN_FILL, GridConstraints.FILL_BOTH,
                                    GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                    GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                    null, null, null));
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Name:");
    myPanel.add(jBLabel1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Value:");
    myPanel.add(jBLabel2,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameField = new JTextField();
    myNameField.setName("name");
    myPanel.add(myNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                 new Dimension(150, -1), null, 0, false));
    myScopeField = new JComboBox();
    myScopeField.setName("scope");
    myPanel.add(myScopeField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                  0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("Scope:");
    myPanel.add(jBLabel3,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    jBLabel1.setLabelFor(myNameField);
    jBLabel3.setLabelFor(myScopeField);
  }
}

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
import com.intellij.uiDesigner.core.GridConstraints;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

public class ExtractVariableForm {
  public JPanel myPanel;
  public JTextField myNameField;
  public JComponent myValueEditor;
  public JComboBox myScopeField;

  public ExtractVariableForm() {
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
}

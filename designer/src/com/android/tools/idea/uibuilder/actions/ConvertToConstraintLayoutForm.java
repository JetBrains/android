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
package com.android.tools.idea.uibuilder.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction.TITLE;

/**
 * Dialog for editing convert to constraint layout parameters
 */
public class ConvertToConstraintLayoutForm extends DialogWrapper {

  private JPanel myPanel;
  private JBCheckBox myFlattenCheckBox;
  private JBCheckBox myFlattenReferencedCheckBox;

  protected ConvertToConstraintLayoutForm(@Nullable Project project) {
    super(project);
    setTitle(TITLE);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public boolean getFlattenHierarchy() {
    return myFlattenCheckBox.isSelected();
  }

  public boolean getFlattenReferenced() {
    return myFlattenReferencedCheckBox.isSelected();
  }
}

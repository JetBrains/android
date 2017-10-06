/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AddActionDialog extends DialogWrapper {
  private JComboBox myFromComboBox;
  private JComboBox myDestinationComboBox;
  private JComboBox myEnterComboBox;
  private JComboBox myExitComboBox;
  private JComboBox myPopToComboBox;
  private JCheckBox myInclusiveCheckBox;
  private JCheckBox mySingleTopCheckBox;
  private JCheckBox myDocumentCheckBox;
  private JCheckBox myClearTaskCheckBox;
  private JPanel myContentPanel;

  protected AddActionDialog() {
    super(false);
    init();
    myOKAction.putValue(Action.NAME, "Add");
    setTitle("Add Action");
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}

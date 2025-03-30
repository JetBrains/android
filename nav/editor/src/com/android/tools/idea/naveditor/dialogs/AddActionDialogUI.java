/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs;

import com.android.tools.idea.common.model.NlComponent;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBTextField;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * This is just a container for the fields in the add action dialog form. The logic is all in {@link AddActionDialog}
 */
@VisibleForTesting
public class AddActionDialogUI {
  public JComboBox<NlComponent> myFromComboBox;
  public JComboBox<AddActionDialog.DestinationListEntry> myDestinationComboBox;
  public JComboBox<ValueWithDisplayString> myEnterComboBox;
  public JComboBox<ValueWithDisplayString> myExitComboBox;
  public JComboBox<AddActionDialog.DestinationListEntry> myPopToComboBox;
  public JCheckBox myInclusiveCheckBox;
  public JComboBox<ValueWithDisplayString> myPopEnterComboBox;
  public JComboBox<ValueWithDisplayString> myPopExitComboBox;
  public JCheckBox mySingleTopCheckBox;
  JPanel myContentPanel;
  public JBTextField myIdTextField;
}

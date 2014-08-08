/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class EditFocusListener extends FocusAdapter {
  private final JTable myTable;
  private final JTextComponent myKey;
  private final JTextComponent myDefaultValue;
  private final JTextComponent myTranslation;

  public EditFocusListener(@NotNull JTable table,
                           @NotNull JTextComponent key,
                           @NotNull JTextComponent defaultValue,
                           @NotNull JTextComponent translation) {
    myTable = table;
    myKey = key;
    myDefaultValue = defaultValue;
    myTranslation = translation;
  }

  @Override
  public void focusLost(FocusEvent e) {
    JTextComponent component = (JTextComponent) e.getComponent();
    StringResourceDataController controller = ((StringResourceTableModel) myTable.getModel()).getController();
    if (component.equals(myKey)) {
      controller.setKey(component.getText());
    } else if (component.equals(myDefaultValue)) {
      controller.setDefaultValue(component.getText());
    } else if (component.equals(myTranslation)) {
      controller.setTranslation(component.getText());
    } else {
      assert false : "EditFocusListener cannot be attached to component: " + component.toString();
    }
  }
}

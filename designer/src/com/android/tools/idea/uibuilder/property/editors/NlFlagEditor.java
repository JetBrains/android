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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NlFlagEditor extends PTableCellEditor implements ActionListener {
  private final JPanel myPanel;
  private final JCheckBox myCheckbox;

  private NlProperty myProperty;
  private String myValue;

  public NlFlagEditor() {
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
    myCheckbox = new JCheckBox();
    myPanel.add(myCheckbox, BorderLayout.LINE_START);
    myCheckbox.addActionListener(this);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;

    myProperty = (NlProperty)value;

    Color fg = UIUtil.getTableSelectionForeground();
    Color bg = UIUtil.getTableSelectionBackground();

    myPanel.setForeground(fg);
    myPanel.setBackground(bg);

    for (int i = 0; i < myPanel.getComponentCount(); i++) {
      Component comp = myPanel.getComponent(i);
      comp.setForeground(fg);
      comp.setBackground(bg);
    }

    myValue = myProperty.getValue();
    myCheckbox.setSelected(SdkConstants.VALUE_TRUE.equalsIgnoreCase(myValue));

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myValue;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    myValue = myCheckbox.isSelected() ? SdkConstants.VALUE_TRUE : SdkConstants.VALUE_FALSE;
    stopCellEditing();
  }

  @Override
  public void activate() {
    myValue = SdkConstants.VALUE_TRUE.equalsIgnoreCase(myValue) ? SdkConstants.VALUE_FALSE : SdkConstants.VALUE_TRUE;
    stopCellEditing();
  }
}

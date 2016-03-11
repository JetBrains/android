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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NlEnumEditor extends PTableCellEditor implements ActionListener {
  private static final String UNSET = "<unset>";

  private final JPanel myPanel;
  private final JComboBox myCombo;
  private final FixedSizeButton myBrowseButton;

  private NlProperty myProperty;
  private Object myValue;

  public NlEnumEditor() {
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myCombo = new ComboBox();
    myCombo.setEditable(true);
    myPanel.add(myCombo, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myCombo.addActionListener(this);
    myBrowseButton.addActionListener(this);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;
    myProperty = (NlProperty)value;

    String propValue = StringUtil.notNullize(myProperty.getValue());
    myValue = propValue;

    myBrowseButton.setVisible(NlReferenceEditor.hasResourceChooser(myProperty));

    AttributeDefinition definition = myProperty.getDefinition();
    String[] values = definition == null ? ArrayUtil.EMPTY_STRING_ARRAY : definition.getValues();

    DefaultComboBoxModel model = new DefaultComboBoxModel(values);
    model.insertElementAt(UNSET, 0);
    if (model.getIndexOf(propValue) == -1) {
      model.insertElementAt(propValue, 1);
    }
    model.setSelectedItem(propValue);
    myCombo.setModel(model);

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return UNSET.equals(myValue) ? null : myValue;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myBrowseButton) {
      ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
      if (dialog.showAndGet()) {
        myValue = dialog.getResourceName();
        stopCellEditing();
      } else {
        cancelCellEditing();
      }
    }
    else if (e.getSource() == myCombo) {
      myValue = myCombo.getModel().getSelectedItem();
      // stop cell editing only if a value has been picked from the combo box, not for every event from the combo
      if ("comboBoxEdited".equals(e.getActionCommand())) {
        stopCellEditing();
      }
    }
  }

  @Override
  public void activate() {
    myCombo.showPopup();
  }
}

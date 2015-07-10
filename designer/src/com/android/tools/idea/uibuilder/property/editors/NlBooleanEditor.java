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
import com.android.tools.idea.uibuilder.property.renderer.NlBooleanRenderer;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.uipreview.ChooseResourceDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NlBooleanEditor extends PTableCellEditor implements ActionListener {
  private final JPanel myPanel;
  private final FixedSizeButton myBrowseButton;
  private final ThreeStateCheckBox myCheckbox;

  private NlProperty myProperty;
  private Object myValue;

  public NlBooleanEditor() {
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myCheckbox = new ThreeStateCheckBox();
    myPanel.add(myCheckbox, BorderLayout.LINE_START);

    myBrowseButton = new FixedSizeButton(myCheckbox);
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myCheckbox.addActionListener(this);
    myBrowseButton.addActionListener(this);
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

    String propValue = myProperty.getValue();
    myValue = propValue;
    ThreeStateCheckBox.State state = NlBooleanRenderer.getState(propValue);
    myCheckbox.setState(state == null ? ThreeStateCheckBox.State.NOT_SELECTED : state);

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myValue;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myCheckbox) {
      myValue = NlBooleanRenderer.getBoolean(myCheckbox.getState());
      stopCellEditing();
    } else if (e.getSource() == myBrowseButton) {
      ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
      if (dialog.showAndGet()) {
        myValue = dialog.getResourceName();
        stopCellEditing();
      } else {
        cancelCellEditing();
      }
    }
  }

  @Override
  public void activate() {
    myValue = NlBooleanRenderer.getNextState(myCheckbox.getState());
    stopCellEditing();
  }

  @Override
  public boolean isBooleanEditor() {
    return true;
  }
}

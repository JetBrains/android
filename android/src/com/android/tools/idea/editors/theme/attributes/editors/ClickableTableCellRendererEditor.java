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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Cell editor that contains a button that can be used to edit "details".
 * <p/>We currently use it to allow editing sub-styles.
 */
public class ClickableTableCellRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  protected JPanel myPanel = new JPanel();
  protected JLabel myLabel = new JLabel();
  protected JButton myEditButton = new JButton();
  protected JTextField myEditLabel = new JTextField();
  protected EditedStyleItem myEditValue;
  protected String myStringValue;

  public ClickableTableCellRendererEditor(@NotNull final ClickListener listener) {
    myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.LINE_AXIS));
    myPanel.add(myEditLabel);
    myPanel.add(Box.createHorizontalGlue());
    myPanel.add(myEditButton);
    myEditLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    myEditButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    myEditButton.setText("...");
    int buttonWidth = myEditButton.getFontMetrics(myEditButton.getFont()).stringWidth("...") + 10;
    myEditButton.setPreferredSize(new Dimension(buttonWidth, myEditButton.getHeight()));
    myLabel.setOpaque(true); // Allows for colored background

    if (listener != null) {
      myEditButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          if (myEditValue == null) {
            return;
          }

          ClickableTableCellRendererEditor.this.cancelCellEditing();
          listener.clicked(myEditValue);
        }
      });
    }
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    myLabel.setFont(table.getFont());
    myLabel.setText(((EditedStyleItem)value).getValue());

    return myLabel;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    EditedStyleItem item = (EditedStyleItem)value;
    myEditValue = item;
    myStringValue = item.getRawXmlValue();

    myEditLabel.setText(myStringValue);
    myEditLabel.setFont(table.getFont());
    myPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myEditLabel.getText();
  }

  public void setDetailsActive(boolean detailsActive) {
    myEditButton.setVisible(detailsActive);
  }

  public interface ClickListener {
    void clicked(@NotNull EditedStyleItem value);
  }
}

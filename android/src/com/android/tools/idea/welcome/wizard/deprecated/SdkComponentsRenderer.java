/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.welcome.install.ComponentTreeNode;
import com.android.tools.idea.welcome.wizard.ComponentsTableModel;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * A [TableCellRenderer] and [TableCellEditor] for the SDK components in [SdkComponentsTableModel].
 * Each row of the table has a checkbox to enable/disable the installation of the corresponding SDK
 * component. The checkbox is disabled for non-optional components, where `node.isEnabled=false`.
 *
 * <p>Note: You need to create separate instances when configuring the cell renderer and cell editor,
 * since components are cached and re-used when configuring the cells.
 */
public abstract class SdkComponentsRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
  private final RendererPanel myPanel;
  private final RendererCheckBox myCheckBox;
  private Border myEmptyBorder;

  private final ComponentsTableModel myTableModel;
  private final JBTable myComponentsTable;

  public SdkComponentsRenderer(ComponentsTableModel tableModel, JBTable componentsTable) {
    myTableModel = tableModel;
    myComponentsTable = componentsTable;

    myPanel = new RendererPanel();
    myCheckBox = new RendererCheckBox();
    myCheckBox.setOpaque(false);
    myCheckBox.addActionListener(e -> {
      if (myComponentsTable.isEditing()) {
        // Stop cell editing as soon as the SPACE key is pressed. This allows the SPACE key
        // to toggle the checkbox while allowing the other navigation keys to function as
        // soon as the toggle action is finished.
        // Note: This calls "setValueAt" on "myTableModel" automatically.
        stopCellEditing();
      }
      else {
        // This happens when the "pressed" action is invoked programmatically through
        // accessibility, so we need to call "setValueAt" manually.
        myTableModel.setValueAt(myCheckBox.isSelected(), myCheckBox.getRow(), 0);
      }
      onCheckboxUpdated();
    });
  }

  public abstract void onCheckboxUpdated();

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setupControl(table, value, row, isSelected, hasFocus);
    return myPanel;
  }

  private void setupControl(JTable table, Object value, int row, boolean isSelected, boolean hasFocus) {
    myCheckBox.setRow(row);
    myPanel.setBorder(getCellBorder(table, isSelected && hasFocus));
    Color foreground;
    Color background;
    if (isSelected) {
      background = table.getSelectionBackground();
      foreground = table.getSelectionForeground();
    }
    else {
      background = table.getBackground();
      foreground = table.getForeground();
    }
    myPanel.setBackground(background);
    myCheckBox.setForeground(foreground);
    myPanel.remove(myCheckBox);
    //noinspection unchecked
    Pair<ComponentTreeNode, Integer> pair = (Pair<ComponentTreeNode, Integer>)value;
    int indent = 0;
    if (pair != null) {
      ComponentTreeNode node = pair.getFirst();
      myCheckBox.setEnabled(node.isEnabled());
      myCheckBox.setText(node.getLabel());
      myCheckBox.setSelected(node.isChecked());
      indent = pair.getSecond();
    }
    myPanel.add(myCheckBox,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, indent * 2));
  }

  private Border getCellBorder(JTable table, boolean isSelectedFocus) {
    Border focusedBorder = UIUtil.getTableFocusCellHighlightBorder();
    Border border;
    if (isSelectedFocus) {
      border = focusedBorder;
    }
    else {
      if (myEmptyBorder == null) {
        myEmptyBorder = new EmptyBorder(focusedBorder.getBorderInsets(table));
      }
      border = myEmptyBorder;
    }
    return border;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    setupControl(table, value, row, true, true);
    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myCheckBox.isSelected();
  }

  /**
   * A specialization of {@link JPanel} that provides complete accessibility support by
   * delegating most of its behavior to {@link #myCheckBox}.
   */
  protected class RendererPanel extends JPanel {
    public RendererPanel() {
      super(new GridLayoutManager(1, 1));
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
      if (myComponentsTable.isEditing()) {
        myCheckBox._processKeyEvent(e);
      }
      else {
        super.processKeyEvent(e);
      }
    }

    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
      if (myComponentsTable.isEditing()) {
        return myCheckBox._processKeyBinding(ks, e, condition, pressed);
      }
      else {
        return super.processKeyBinding(ks, e, condition, pressed);
      }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new RendererPanel.AccessibleRendererPanel();
      }
      return accessibleContext;
    }

    /**
     * Delegate accessible implementation to the embedded {@link #myCheckBox}.
     */
    protected class AccessibleRendererPanel extends AccessibleContextDelegate {
      public AccessibleRendererPanel() {
        super(myCheckBox.getAccessibleContext());
      }

      @Override
      protected Container getDelegateParent() {
        return RendererPanel.this.getParent();
      }

      @Override
      public String getAccessibleDescription() {
        return myTableModel.getComponentDescription(myCheckBox.getRow());
      }

      @Override
      public Accessible getAccessibleParent() {
        return (Accessible)RendererPanel.this.getParent();
      }
    }
  }

  /**
   * A specialization of {@link JCheckBox} that provides keyboard friendly behavior
   * when contained inside {@link RendererPanel} inside a table cell editor.
   */
  protected class RendererCheckBox extends JCheckBox {
    private int myRow;

    public int getRow() {
      return myRow;
    }

    public void setRow(int row) {
      myRow = row;
    }

    public boolean _processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
      return super.processKeyBinding(ks, e, condition, pressed);
    }

    public void _processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }

    @Override
    public void requestFocus() {
      // Ignore focus requests when editing cells. If we were to accept the focus request
      // the focus manager would move the focus to some other component when the checkbox
      // exits editing mode.
      if (myComponentsTable.isEditing()) {
        return;
      }

      super.requestFocus();
    }
  }
}

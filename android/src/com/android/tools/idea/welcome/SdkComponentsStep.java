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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Set;

/**
 * Wizard page for selecting SDK components to download.
 */
public class SdkComponentsStep extends FirstRunWizardStep {
  private final Node[] myNodes;
  private JPanel myContents;
  private JBTable myComponentsTable;
  private JTextPane myComponentDescription;
  private JLabel myNeededSpace;
  private JLabel myAvailableSpace;
  private JLabel myErrorMessage;
  private JSplitPane mySplitPane;
  private Set<Node> myUncheckedComponents = Sets.newHashSet();

  public SdkComponentsStep() {
    super("SDK Settings");
    myComponentDescription.setEditable(false);
    myComponentDescription.setContentType("text/html");
    myComponentDescription.setText("<html><h1>SDK Component</h1>" + "<body>A <em>really</em> important component.</body></html>");
    myComponentDescription.setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
    mySplitPane.setBorder(null);
    Font labelFont = UIUtil.getLabelFont();
    Font smallLabelFont = labelFont.deriveFont(labelFont.getSize() - 1.0f);
    myNeededSpace.setFont(smallLabelFont);
    myAvailableSpace.setFont(smallLabelFont);
    myErrorMessage.setText("");
    myErrorMessage.setForeground(JBColor.red);

    myNodes = createModel();
    DefaultTableModel model = new DefaultTableModel(0, 1) {
      @Override
      public void setValueAt(Object aValue, int row, int column) {
        boolean isSelected = ((Boolean)aValue);
        Node node = myNodes[row];
        if (isSelected) {
          select(node);
        }
        else {
          unselect(node);
        }
      }
    };
    for (Node node : myNodes) {
      model.addRow(new Object[]{node});
    }
    myComponentsTable.setModel(model);
    myComponentsTable.setTableHeader(null);
    TableColumn column = myComponentsTable.getColumnModel().getColumn(0);
    column.setCellRenderer(new SdkComponentRenderer());
    column.setCellEditor(new SdkComponentRenderer());
    setComponent(myContents);
  }

  private static Node[] createModel() {
    Node androidSdk = new Node("Android Studio + SDK – (684Mb)", null, false);
    Node sdkPlatform = new Node("Android SDK Platform", null, false);
    Node lmp = new Node("LMP - Android 5.0 (API 21) – (292Mb)", null, false);
    Node root = new Node("Android Emulator", null, false);
    Node nexus = new Node("Nexus", root, false);
    Node nexus5 = new Node("Nexus 5 – (2.44Gb)", nexus, true);
    Node performance = new Node("Performance", root, false);
    Node haxm = new Node("Intel® HAXM – (2.2Mb)", performance, true);
    return new Node[]{androidSdk, sdkPlatform, lmp, root, nexus, nexus5, performance, haxm};
  }

  private static boolean isChild(@Nullable Node child, @NotNull Node node) {
    return child != null && (child == node || isChild(child.myParent, node));
  }

  private void unselect(Node node) {
    for (Node child : myNodes) {
      if (child.myIsLeaf && isChild(child, node)) {
        myUncheckedComponents.add(child);
      }
    }
  }

  private Iterable<Node> getChildren(final Node node) {
    return Iterables.filter(Arrays.asList(myNodes), new Predicate<Node>() {
      @Override
      public boolean apply(@Nullable Node input) {
        assert input != null;
        Node n = input;
        do {
          if (n == node) {
            return true;
          }
          n = n.myParent;
        }
        while (n != null);
        return false;
      }
    });
  }

  private void select(Node node) {
    for (Node child : getChildren(node)) {
      myUncheckedComponents.remove(child);
    }
  }

  @Override
  public void init() {

  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return myErrorMessage;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myComponentsTable;
  }

  private boolean isSelected(Node node) {
    for (Node child : getChildren(node)) {
      if (myUncheckedComponents.contains(child)) {
        return false;
      }
    }
    return true;
  }

  private static final class Node {
    @NotNull private final String myName;
    @Nullable private final Node myParent;
    private final boolean myIsLeaf;


    public Node(@NotNull String name, @Nullable Node parent, boolean isLeaf) {
      myName = name;
      myParent = parent;
      myIsLeaf = isLeaf;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private final class SdkComponentRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final JPanel myPanel;
    private final JCheckBox myCheckBox;
    private Border myEmptyBorder;

    public SdkComponentRenderer() {
      myPanel = new JPanel(new GridLayoutManager(1, 1));
      myCheckBox = new JCheckBox();
      myCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
        }
      });
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setupControl(table, value, isSelected, hasFocus);
      return myPanel;
    }

    private void setupControl(JTable table, Object value, boolean isSelected, boolean hasFocus) {
      myPanel.setBorder(getCellBorder(table, isSelected && hasFocus));
      Color foreground;
      if (isSelected) {
        myPanel.setBackground(table.getSelectionBackground());
        foreground = table.getSelectionForeground();
      }
      else {
        myPanel.setBackground(table.getBackground());
        foreground = table.getForeground();
      }
      myCheckBox.setForeground(foreground);
      myPanel.remove(myCheckBox);
      Node node = (Node)value;
      int ident = 0;
      if (node != null) {
        myCheckBox.setText(node.myName);
        myCheckBox.setSelected(isSelected((Node)value));
        while (node.myParent != null) {
          ident++;
          node = node.myParent;
          assert node != null;
        }
      }
      myPanel.add(myCheckBox,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, ident * 2));
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
      setupControl(table, value, true, true);
      return myPanel;
    }

    @Override
    public Object getCellEditorValue() {
      return myCheckBox.isSelected();
    }
  }
}

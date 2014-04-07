/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import com.intellij.util.ui.LocalPathCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * A Project Structure Android-Gradle module editor panel that handles editing of a group of generic {@linkplain NamedObject} instances
 * in a build.gradle file.
 */
public class NamedObjectPanel extends BuildFilePanel {
  protected final BuildFileKey myRoot;
  protected final List<BuildFileKey> myProperties;
  private final JBTable myTable;
  @Nullable private final NamedObjectTableModel myModel;
  private AnActionButton myRemoveButton;
  private final String myNewItemName;

  /**
   * A Boolean-like enum that also permits a "not present" state.
   */
  public enum ThreeStateBoolean {
    EMPTY("-", null),
    TRUE("true", Boolean.TRUE),
    FALSE("false", Boolean.FALSE);

    private final String myName;
    private final Boolean myValue;

    ThreeStateBoolean(String name, Boolean value) {
      myName = name;
      myValue = value;
    }

    @Override
    public String toString() {
      return myName;
    }

    @Nullable
    public Boolean getValue() {
      return myValue;
    }

    @NotNull
    public static ThreeStateBoolean forValue(@Nullable Boolean b) {
      if (b == null) {
        return EMPTY;
      } else if (b) {
        return TRUE;
      } else {
        return FALSE;
      }
    }
  }

  public NamedObjectPanel(@NotNull Project project, @NotNull String moduleName, @NotNull BuildFileKey root, String newItemName) {
    super(project, moduleName);
    myNewItemName = newItemName;
    NamedObject.Factory objectFactory = (NamedObject.Factory)root.getValueFactory();
    if (objectFactory == null) {
      throw new IllegalArgumentException("Can't instantiate a NamedObjectPanel for BuildFileKey " + root.toString());
    }
    myRoot = root;
    myProperties = objectFactory.getProperties();
    myModel = myGradleBuildFile != null ? new NamedObjectTableModel(myGradleBuildFile, root, myProperties) : null;
    myTable = new JBTable(myGradleBuildFile != null ? myModel : new DefaultTableModel());
    myTable.setShowGrid(false);
    myTable.setDragEnabled(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setCellSelectionEnabled(false);
    LocalPathCellEditor editor = new FileCellEditor();
    myTable.setDefaultEditor(File.class, editor);

    JComboBox booleanEditor = new JComboBox(new EnumComboBoxModel<ThreeStateBoolean>(ThreeStateBoolean.class));
    myTable.setDefaultEditor(ThreeStateBoolean.class, new DefaultCellEditor(booleanEditor));
    myTable.setDefaultRenderer(ThreeStateBoolean.class, new ComboBoxTableRenderer<ThreeStateBoolean>(ThreeStateBoolean.values()));
  }

  private void updateButtons() {
    if (myRemoveButton != null) {
      myRemoveButton.setEnabled(myTable.getSelectedRows().length > 0);
    }
  }

  private void removeSelectedItems(@NotNull final List removedRows) {
    if (myModel == null) {
      return;
    }
    if (removedRows.isEmpty()) {
      return;
    }
    final int[] selectedRows = myTable.getSelectedRows();
    myModel.fireTableDataChanged();
    TableUtil.selectRows(myTable, selectedRows);
  }

  @Override
  protected void addItems(@NotNull JPanel parent) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateButtons();
      }
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        if (myModel == null) {
          return;
        }
        myModel.addRow();
        myTable.clearSelection();
        int row = myTable.getRowCount() - 1;
        myTable.setRowSelectionInterval(row, row);
        myModel.setValueAt(getUniqueObjectName(), row, 0);
        myTable.editCellAt(row, 0);
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        removeSelectedItems(TableUtil.removeSelectedItems(myTable));
      }
    });

    final JPanel panel = decorator.createPanel();
    myRemoveButton = ToolbarDecorator.findRemoveButton(panel);
    add(panel, BorderLayout.CENTER);

    if (myTable.getRowCount() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(myRemoveButton);
    PopupHandler.installPopupHandler(myTable, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  @Override
  public void apply() {
    if (myModel != null) {
      myModel.apply();
    }
  }

  @Override
  public boolean isModified() {
    return myModel != null && myModel.isModified();
  }

  @NotNull
  protected String getUniqueObjectName() {
    int num = 1;
    String name;
    do {
      name = myNewItemName + num++;
    } while (myModel != null && myModel.hasObjectNamed(name));
    return name;
  }

  private class FileCellEditor extends LocalPathCellEditor {
    public FileCellEditor() {
      super("", myProject);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
      ((CellEditorComponentWithBrowseButton<?>)component).getChildComponent().setBorder(new LineBorder(Color.black));
      return component;
    }

    @Override
    public FileChooserDescriptor getFileChooserDescriptor() {
      FileChooserDescriptor d = new FileChooserDescriptor(true, false, false, true, false, false);
      d.setShowFileSystemRoots(true);
      return d;
    }
  }
}

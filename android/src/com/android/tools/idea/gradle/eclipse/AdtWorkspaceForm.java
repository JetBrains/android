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
package com.android.tools.idea.gradle.eclipse;

import com.google.common.collect.Maps;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Map;

class AdtWorkspaceForm extends ProjectImportWizardStep {
  private JBTable myPathMapTable;
  private TextFieldWithBrowseButton myWorkspaceField;
  private JPanel myPanel;
  private Map<String, File> myPathMap = Maps.newHashMap();
  private boolean myIgnore;

  AdtWorkspaceForm(WizardContext context) {
    super(context);

    myPathMapTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myPathMapTable.setStriped(true);
    myPathMapTable.setDefaultRenderer(File.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable var1, Object var2, boolean var3, boolean var4, int var5, int var6) {
        Component component = super.getTableCellRendererComponent(var1, var2, var3, var4, var5, var6);
        File file = (File)var2;
        if (file == null || file.getPath().trim().isEmpty()) {
          super.setValue("<click to edit>");
        }
        return component;
      }
    });

    JTextField textField = new JTextField();
    DefaultCellEditor cellEditor = new DefaultCellEditor(textField);
    cellEditor.setClickCountToStart(1);
    myPathMapTable.setDefaultEditor(String.class, cellEditor);

    // TODO: Listen for invoking the button
    myWorkspaceField.addBrowseFolderListener("Select Eclipse Workspace", null, null,
                               FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myWorkspaceField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        File workspace = new File(myWorkspaceField.getText());
        boolean workspaceValid = GradleImport.isEclipseWorkspaceDir(workspace);
        //setBackground(myAndroidHomeLocation, workspaceValid);
        //myAndroidHomeError.setText(workspaceValid ? " " : "Please choose a valid Eclipse workspace directory.");
        if (workspaceValid) {
          AdtImportBuilder builder = AdtImportBuilder.getBuilder(getWizardContext());
          if (builder != null) {
            GradleImport importer = builder.getImporter();
            if (importer != null) {
              importer.setEclipseWorkspace(workspace);
              builder.readProjects();
              updateStep();
            }
          }
        }
      }
    });

  }


  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateStep() {
    if (myIgnore) {
      return;
    }
    super.updateStep();
    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    if (importer != null) {
      File eclipseWorkspace = importer.getEclipseWorkspace();
      if (eclipseWorkspace != null && eclipseWorkspace.exists()) {
        try {
          myIgnore = true;
          myWorkspaceField.setText(eclipseWorkspace.getPath());
        } finally {
          myIgnore = false;
        }
      }

      Map<String,File> map = importer.getPathMap();
      for (Map.Entry<String,File> entry : map.entrySet()) {
        if (myPathMap.get(entry.getKey()) == null) {
          myPathMap.put(entry.getKey(), entry.getValue());
        }
      }
      PathMapModel model = new PathMapModel(myPathMap);
      myPathMapTable.setModel(model);
    }
  }

  @Override
  public void updateDataModel() {
    AdtImportBuilder builder = AdtImportBuilder.getBuilder(getWizardContext());
    if (builder != null) {
      GradleImport importer = builder.getImporter();
      if (importer != null) {
        File workspace = new File(myWorkspaceField.getText().trim());
        if (workspace.exists()) {
          importer.setEclipseWorkspace(workspace);
        }

        importer.getPathMap().putAll(myPathMap);
      }

      // Refresh read state in case reading the workspace paths updates the set of available warnings
      builder.readProjects();
    }
  }

  @Override
  public boolean isStepVisible() {
    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    return importer != null && !importer.getPathMap().isEmpty();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    for (Map.Entry<String,File> entry : myPathMap.entrySet()) {
      String path = entry.getKey();
      File file = entry.getValue();
      if (file == null || file.getPath().trim().isEmpty()) {
        throw new ConfigurationException("Enter a value for workspace path " + path);
      } else if (!file.exists()) {
        throw new ConfigurationException(file.getPath() + " does not exist");
      }
    }

    return super.validate();
  }

  @Override
  public String getName() {
    return "Eclipse Workspace Location";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myWorkspaceField.getTextField();
  }

  private static final int WORKSPACE_PATH_COLUMN = 0;
  private static final int FILE_COLUMN = 1;

  private class PathMapModel extends AbstractTableModel implements EditableModel {
    private Map<String, File> myPathMap;
    private String[] myKeys;

    public PathMapModel(Map<String, File> map) {
      myKeys = map.keySet().toArray(new String[map.size()]);
      Arrays.sort(myKeys);
      myPathMap = map;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public int getRowCount() {
      return myKeys.length;
    }

    @Override
    public Object getValueAt(int row, int col) {
      String key = myKeys[row];
      switch (col) {
        case WORKSPACE_PATH_COLUMN:
          return key;

        case FILE_COLUMN:
        default:
          return myPathMap.get(key);
      }
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case WORKSPACE_PATH_COLUMN:
          return "Workspace Path";
        case FILE_COLUMN:
          return "Actual Path";
        default:
          assert false : column;
          return "";
      }
    }

    @Override
    public Class<?> getColumnClass(int col) {
      switch (col) {
        case FILE_COLUMN:
          return File.class;
        case WORKSPACE_PATH_COLUMN:
        default:
          return String.class;
      }
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return col == FILE_COLUMN;
    }

    @Override
    public void setValueAt(Object aValue   , int row, int col) {
      String string = aValue == null ? "" : aValue.toString();
      myPathMap.put(myKeys[row], !string.isEmpty() ? new File(string) : null);

      AdtImportBuilder builder = AdtImportBuilder.getBuilder(getWizardContext());
      if (builder != null) {
        builder.readProjects();
      }
    }

    @Override
    public void addRow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeRow(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      String temp = myKeys[oldIndex];
      myKeys[oldIndex] = myKeys[newIndex];
      myKeys[newIndex] = temp;
    }
  }
}

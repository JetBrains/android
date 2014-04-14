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
package com.android.tools.idea.wizard;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Table for showing a list of modules that will be imported.
 */
public final class ModulesTable extends JBTable {
  public static final String PROPERTY_SELECTED_MODULES = "selectedModules";
  @Nullable private VirtualFile myCurrentPath;

  public ModulesTable() {
    super(new ModulesTableModel());
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));
    getColumnModel().getColumn(0).setCellEditor(new ModuleNameCellEditor());
    getColumnModel().getColumn(0).setCellRenderer(new ModuleNameCellEditor());
    getColumnModel().getColumn(1).setCellRenderer(new ModulePathCellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  private static void configureComponent(JComponent component, @Nullable Module module, JTable table, boolean isSelected) {
    final Color background, foreground;
    if (isSelected) {
      background = table.getSelectionBackground();
      foreground = table.getSelectionForeground();
    }
    else {
      background = table.getBackground();
      foreground = table.getForeground();
    }
    component.setBackground(background);
    component.setForeground(foreground);
    component.setEnabled(module != null && module.canToggle());
  }

  /**
   * Returns a relative path string to be shown in the UI. Wizard logic
   * operates with VirtualFile's so these paths are only for user. The paths
   * shown are relative to the file system location user specified, showing
   * relative paths will be easier for the user to read.
   */
  private String getRelativePath(@Nullable VirtualFile file) {
    if (file == null) {
      return "";
    }
    String path = file.getPath();
    if (myCurrentPath == null) {
      return path;
    }
    else if (file.equals(myCurrentPath)) {
      return ".";
    }
    else if (!myCurrentPath.isDirectory()) {
      return getRelativePath(myCurrentPath.getParent());
    }
    else {
      String basePath = myCurrentPath.getPath();
      if (path.startsWith(basePath + "/")) {
        return path.substring(basePath.length() + 1);
      }
      else if (file.getFileSystem().equals(myCurrentPath.getFileSystem())) {
        StringBuilder builder = new StringBuilder(basePath.length());
        String prefix = Strings.commonPrefix(path, basePath);
        if (!prefix.endsWith("/")) {
          prefix = prefix.substring(0, prefix.indexOf("/"));
        }
        if (!path.startsWith(basePath)) {
          Iterable<String> segments = Splitter.on("/").split(basePath.substring(prefix.length()));
          Joiner.on("/").appendTo(builder, Iterables.transform(segments, Functions.constant("..")));
          builder.append("/");
        }
        builder.append(path.substring(prefix.length()));
        return builder.toString();
      }
      else {
        return path;
      }
    }
  }

  public Map<String, VirtualFile> getSelectedModules() {
    return getModel().getSelectedModules();
  }

  public void setModules(@Nullable VirtualFile vfile, @Nullable Map<String, VirtualFile> modules) {
    myCurrentPath = vfile;
    getModel().setModules(modules);
  }

  @Override
  public ModulesTableModel getModel() {
    return (ModulesTableModel)super.getModel();
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    assert model instanceof ModulesTableModel;
    super.setModel(model);
  }

  @Override
  protected TableModel createDefaultDataModel() {
    return new ModulesTableModel();
  }

  private void toggleSelection(@Nullable Module module) {
    if (module != null) {
      Map<String, VirtualFile> old = getModel().getSelectedModules();
      module.setEnabled(!module.isEnabled());
      Map<String, VirtualFile> current = getModel().getSelectedModules();
      firePropertyChange(PROPERTY_SELECTED_MODULES, old, current);
    }
  }

  private static class Module implements Comparable<Module> {
    @NotNull public final String name;
    @Nullable public final VirtualFile location;
    private boolean myEnabled = true;

    private Module(@NotNull String name, @Nullable VirtualFile location) {
      this.name = name;
      this.location = location;
    }

    public boolean isEnabled() {
      return myEnabled && location != null;
    }

    public void setEnabled(boolean enabled) {
      this.myEnabled = enabled;
    }

    @Override
    public int compareTo(@NotNull Module o) {
      if (location == null && o.location != null) {
        return 1;
      }
      else if (location != null && o.location == null) {
        return -1;
      }
      else {
        return name.compareTo(o.name);
      }
    }

    @Override
    public int hashCode() {
      return name.hashCode() * 31;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      else if (!(obj instanceof Module)) {
        return false;
      }
      else {
        return ((Module)obj).name.equals(name);
      }
    }

    public boolean canToggle() {
      return location != null;
    }
  }

  private static class ModulesTableModel extends AbstractTableModel {
    private Map<String, Module> knownModules = new HashMap<String, Module>();
    private List<Module> myModules = Collections.emptyList();

    @Override
    public int getRowCount() {
      return myModules.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0 ? Module.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 && myModules.get(rowIndex).canToggle();
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        myModules.get(rowIndex).setEnabled(Boolean.TRUE.equals(aValue));
      }
      fireTableRowsUpdated(0, myModules.size() - 1);
    }

    @Override
    @NotNull
    public Object getValueAt(int rowIndex, int columnIndex) {
      Module module = myModules.get(rowIndex);
      assert module != null;
      return module;
    }

    public void setModules(@Nullable Map<String, VirtualFile> modules) {
      if (modules == null) {
        myModules = Collections.emptyList();
      }
      else {
        myModules = new ArrayList<Module>(modules.size());
        for (Map.Entry<String, VirtualFile> entry : modules.entrySet()) {
          myModules.add(getModule(entry));
        }
      }
      Collections.sort(myModules);
      fireTableDataChanged();
    }

    private Module getModule(Map.Entry<String, VirtualFile> entry) {
      VirtualFile file = entry.getValue();
      String name = entry.getKey();
      String key = String.format("%1$s#%2$s", name, file == null ? "<null>" : file.getPath());
      Module module = knownModules.get(key);
      if (module == null) {
        module = new Module(name, file);
        knownModules.put(key, module);
      }
      return module;
    }

    public Map<String, VirtualFile> getSelectedModules() {
      Map<String, VirtualFile> files = new TreeMap<String, VirtualFile>();
      for (Module module : myModules) {
        if (module.isEnabled()) {
          files.put(module.name, module.location);
        }
      }
      return Collections.unmodifiableMap(files);
    }
  }

  private final class ModuleNameCellEditor extends AbstractTableCellEditor implements TableCellRenderer {
    private final JBCheckBox myCheckBox = new JBCheckBox();

    @Nullable private Module myModule;

    public ModuleNameCellEditor() {
      myCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          toggleSelection(myModule);
        }
      });
      myCheckBox.setOpaque(true);
    }

    private void setupCheckbox(@NotNull JTable table, @Nullable Module module, @NotNull JBCheckBox checkBox, boolean isSelected) {
      // JTable may send a null value to the cell editor during initialization
      final boolean checked = module != null && module.isEnabled();
      final String text = module == null ? "<No Module>" : module.name;

      checkBox.setText(text);
      checkBox.setSelected(checked);

      configureComponent(checkBox, module, table, isSelected);
      myModule = module;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      setupCheckbox(table, (Module)value, myCheckBox, true);
      return myCheckBox;
    }

    @Override
    public Object getCellEditorValue() {
      return myCheckBox.isSelected();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,boolean isSelected, boolean hasFocus, int row, int column) {
      setupCheckbox(table, (Module)value, myCheckBox, isSelected);
      return myCheckBox;
    }
  }

  private class ModulePathCellRenderer implements TableCellRenderer {
    private final JLabel myLabel = new JLabel();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myLabel.setOpaque(true);
      configureComponent(myLabel, (Module)value, table, isSelected);
      if (value != null) {
        myLabel.setText(getRelativePath(((Module)value).location));
      }
      return myLabel;
    }
  }
}

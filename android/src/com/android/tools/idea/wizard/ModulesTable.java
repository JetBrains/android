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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.*;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
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
import java.io.File;
import java.text.Collator;
import java.util.*;
import java.util.List;

/**
 * Table for showing a list of modules that will be imported.
 */
public final class ModulesTable extends JBTable {
  public static final String PROPERTY_SELECTED_MODULES = "selectedModules";

  private Project myProject;
  @Nullable private VirtualFile myCurrentPath;
  private Map<Module, ModuleState> myModules;
  private Set<Module> myDisabledModules = new HashSet<Module>();

  public ModulesTable() {
    setModel(new ModulesTableModel());
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));
    getColumnModel().getColumn(0).setCellEditor(new ModuleNameCellEditor());
    getColumnModel().getColumn(0).setCellRenderer(new ModuleNameCellEditor());
    getColumnModel().getColumn(1).setCellRenderer(new ModulePathCellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  private static void configureComponent(JComponent component, @NotNull ModuleState state, @NotNull JTable table, boolean isSelected) {
    final Color background, foreground;
    if (isSelected) {
      background = table.getSelectionBackground();
      foreground = table.getSelectionForeground();
    }
    else {
      background = table.getBackground();
      foreground = table.getForeground();
    }
    Font font = component.getFont();
    final int style = BitUtil.set(font.getStyle(), Font.BOLD, state.isBoldFont());

    //noinspection MagicConstant
    component.setFont(font.deriveFont(style));
    component.setBackground(background);
    component.setForeground(foreground);
    component.setEnabled(state.canToggle());
    component.setToolTipText(state.getDescription());
  }

  @NotNull
  private static ModuleState getModuleState(@Nullable Module module) {
    return module == null ? ModuleState.NULL : module.getState();
  }

  public Map<String, VirtualFile> getSelectedModules() {
    HashMap<String, VirtualFile> result = new HashMap<String, VirtualFile>(myModules.size());
    for (Module module : myModules.keySet()) {
      if (getModuleState(module).isChecked(module)) {
        result.put(module.name, module.location);
      }
    }
    return result;
  }

  public void setModules(@NotNull Project project, @Nullable VirtualFile currentPath, @Nullable Map<String, VirtualFile> modules) {
    myCurrentPath = currentPath;
    myProject = project;
    if (modules == null) {
      myModules = Collections.emptyMap();
    }
    else {
      myModules = new TreeMap<Module, ModuleState>(new ModuleComparator(currentPath));
      for (Map.Entry<String, VirtualFile> entry : modules.entrySet()) {
        Module module = new Module(entry.getKey(), entry.getValue());
        myModules.put(module, null);
      }
    }
    //noinspection UseOfObsoleteCollectionType
    ((ModulesTableModel)getModel()).setModules(myModules.keySet());
  }

  @Override
  protected TableModel createDefaultDataModel() {
    return new ModulesTableModel();
  }

  private void setModuleSelection(@Nullable Module module, boolean selected) {
    if (module != null && getModuleState(module).canToggle()) {
      Map<String, VirtualFile> old = getSelectedModules();
      module.setEnabled(selected);
      Map<String, VirtualFile> current = getSelectedModules();
      firePropertyChange(PROPERTY_SELECTED_MODULES, old, current);
    }
  }

  private enum ModuleState {
    OK(true), NULL(false), NOT_FOUND(false), ALREADY_EXISTS(false), PRIMARY(true), REQUIRED(true);

    private final boolean myModuleChecked;

    ModuleState(boolean checked) {
      myModuleChecked = checked;
    }

    public boolean isChecked(@Nullable Module module) {
      if (this == OK) {
        return module != null && module.isEnabled();
      }
      else {
        return myModuleChecked;
      }
    }

    @Nullable
    public String getDescription() {
      switch (this) {
        case OK:
        case NULL:
          return null;
        case NOT_FOUND:
          return "Module sources not found";
        case ALREADY_EXISTS:
          return "Project already contains module with this name";
        case PRIMARY:
          return null; // Nothing to explain
        case REQUIRED:
          return "Module is required by another selected module";
        default:
          throw new IllegalStateException(name());
      }
    }

    public boolean isBoldFont() {
      return this == PRIMARY;
    }

    public boolean canToggle() {
      return this == OK;
    }
  }

  /**
   * Sorts module in the tree.
   * <ol>
   * <li>First element is the module located in a path selected by user, if any.</li>
   * <li>Modules are ordered based on their location</li>
   * <li>Modules with unknown location come last.</li>
   * </ol>
   */
  private static class ModuleComparator implements Comparator<Module> {
    @Nullable private final VirtualFile myImportPath;

    public ModuleComparator(@Nullable VirtualFile importPath) {
      myImportPath = importPath;
    }

    @Override
    public int compare(Module o1, Module o2) {
      if (o1 == null) {
        return o2 == null ? 0 : 1;
      }
      else if (o2 == null) {
        return -1;
      }
      else {
        VirtualFile l1 = o1.location;
        VirtualFile l2 = o2.location;

        Collator collator = Collator.getInstance();
        int namesComparison = collator.compare(o1.name, o2.name);

        if (l1 == null) {
          return l2 == null ? namesComparison : 1;
        }
        else if (l2 == null) {
          return -1;
        }
        else {
          if (Objects.equal(l1, myImportPath)) {
            return Objects.equal(l2, myImportPath) ? namesComparison : -1;
          }
          else if (Objects.equal(l2, myImportPath)) {
            return 1;
          }
          else {
            int pathComparison = collator.compare(l1.getPath(), l2.getPath());
            return pathComparison == 0 ? namesComparison : pathComparison;
          }
        }
      }
    }
  }

  private static class ModulePathCellRenderer implements TableCellRenderer {
    private final JLabel myLabel = new JLabel();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myLabel.setOpaque(true);
      configureComponent(myLabel, value == null ? ModuleState.NULL : getModuleState((Module)value), table, isSelected);
      if (value != null) {
        myLabel.setText(((Module)value).getLocationString());
      }
      return myLabel;
    }
  }

  private static final class ModuleNameCellEditor extends AbstractTableCellEditor implements TableCellRenderer {
    private final JBCheckBox myCheckBox = new JBCheckBox();

    public ModuleNameCellEditor() {
      myCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
        }
      });
      myCheckBox.setOpaque(true);
    }

    private void setupCheckbox(@NotNull JTable table, @Nullable Module module, @NotNull JBCheckBox checkBox, boolean isSelected) {
      // JTable may send a null value to the cell editor during initialization
      final String text;
      if (module != null) {
        text = module.name;
      }
      else {
        text = "<No Module>";
      }

      ModuleState state = getModuleState(module);

      checkBox.setText(text);
      checkBox.setSelected(state.isChecked(module));

      configureComponent(checkBox, state, table, isSelected);
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
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setupCheckbox(table, (Module)value, myCheckBox, isSelected);
      return myCheckBox;
    }
  }

  private class ModulesTableModel extends AbstractTableModel {
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
      Module module = myModules.get(rowIndex);
      return columnIndex == 0 && getModuleState(module).canToggle();
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      Module module = myModules.get(rowIndex);
      if (columnIndex == 0) {
        setModuleSelection(module, Boolean.TRUE.equals(aValue));
      }
      fireTableRowsUpdated(0, myModules.size() - 1);
    }

    @Override
    public Object getValueAt(int row, int column) {
      return myModules.get(row);
    }

    public void setModules(@NotNull Collection<Module> modules) {
      myModules = new ArrayList<Module>(modules);
      fireTableDataChanged();
    }

    public Collection<Module> getModules() {
      return myModules;
    }
  }

  @VisibleForTesting
  protected class Module {
    @NotNull public final String name;
    @Nullable public final VirtualFile location;

    private Module(@NotNull String name, @Nullable VirtualFile location) {
      this.name = name;
      this.location = location;
    }

    public boolean isEnabled() {
      return !myDisabledModules.contains(this);
    }

    public void setEnabled(boolean enabled) {
      if (enabled) {
        myDisabledModules.remove(this);
      }
      else {
        myDisabledModules.add(this);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name, location);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Module) {
        Module other = (Module)obj;
        return Objects.equal(location, other.location) && Objects.equal(name, other.name);
      }
      else {
        return false;
      }
    }

    private boolean projectHasSubprojectWithPath() {
      if (GradleUtil.findModuleByGradlePath(myProject, name) != null) {
        return true;
      }
      File targetPath = GradleUtil.getDefaultSubprojectLocation(myProject.getBaseDir(), name);
      if (targetPath.exists()) {
        String[] children = targetPath.list();
        return (children == null || children.length > 0);
      }
      return false;
    }

    private ModuleState validate() {
      if (location == null) {
        return ModuleState.NOT_FOUND;
      }
      if (projectHasSubprojectWithPath()) {
        return ModuleState.ALREADY_EXISTS;
      }
      if (Objects.equal(location, myCurrentPath)) {
        return ModuleState.PRIMARY;
      }
      return ModuleState.OK;
    }

    private ModuleState getState() {
      ModuleState state = myModules.get(this);
      if (state == null) {
        state = validate();
        myModules.put(this, state);
      }
      return state;
    }

    public String getLocationString() {
      return getRelativePath(location);
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
  }
}

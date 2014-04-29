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

import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.*;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
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
import java.util.Queue;

import static com.google.common.base.Predicates.*;

/**
 * Table for showing a list of modules that will be imported.
 */
public final class ModulesTable extends JBTable {
  public static final String PROPERTY_SELECTED_MODULES = "selectedModules";

  @Nullable private Project myProject;
  @Nullable private VirtualFile myCurrentPath;
  private Map<ModuleToImport, ModuleValidationState> myModules;
  private Set<ModuleToImport> myUncheckedModules = new HashSet<ModuleToImport>();
  private Multimap<ModuleToImport, ModuleToImport> myRequiredModules;
  private boolean myHasPrimaryModule; // Checkboxes are not shown in this case
                                      // and required modules are not grayed out

  public ModulesTable() {
    setModel(new ModulesTableModel());
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));
    getColumnModel().getColumn(0).setCellEditor(new ModuleNameCellEditor());
    getColumnModel().getColumn(0).setCellRenderer(new ModuleNameCellEditor());
    getColumnModel().getColumn(1).setCellRenderer(new ModulePathCellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  @Nullable
  public String getDescription(@Nullable ModuleToImport module, @NotNull ModuleValidationState state) {
    switch (state) {
      case OK:
      case NULL:
      case PRIMARY:
        return null;
      case NOT_FOUND:
        return "Module sources not found";
      case ALREADY_EXISTS:
        return "Project already contains module with this name";
      case REQUIRED:
        Iterable<String> requiredBy = Iterables.transform(myRequiredModules.get(module),
                                                          new Function<ModuleToImport, String>() {
                                                            @Override
                                                            public String apply(ModuleToImport input) {
                                                              return input.name;
                                                            }
                                                          }
        );
        return ImportUIUtil.formatElementListString(requiredBy,
                                                    "Required by %s",
                                                    "Required by %s and %s",
                                                    "Required by %s and %d more subprojects");
      default:
        throw new IllegalStateException(state.name());
    }
  }


  private void configureComponent(JComponent component, @Nullable ModuleToImport module,
                                  @NotNull JTable table, boolean isSelected) {
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

    ModuleValidationState state = getModuleValidationState(module);

    final int style = BitUtil.set(font.getStyle(), Font.BOLD, state.isBoldFont());

    //noinspection MagicConstant
    component.setFont(font.deriveFont(style));
    component.setBackground(background);
    component.setForeground(foreground);
    component.setEnabled(state == ModuleValidationState.PRIMARY || state.canToggle());
    component.setToolTipText(getDescription(module, state));
  }

  /**
   * Returns a relative path string to be shown in the UI. Wizard logic
   * operates with VirtualFile's so these paths are only for user. The paths
   * shown are relative to the file system location user specified, showing
   * relative paths will be easier for the user to read.
   */
  private static String getRelativePath(@Nullable VirtualFile baseFile, @Nullable VirtualFile file) {
    if (file == null) {
      return "";
    }
    String path = file.getPath();
    if (baseFile == null) {
      return path;
    }
    else if (file.equals(baseFile)) {
      return ".";
    }
    else if (!baseFile.isDirectory()) {
      return getRelativePath(baseFile.getParent(), file);
    }
    else {
      String basePath = baseFile.getPath();
      if (path.startsWith(basePath + "/")) {
        return path.substring(basePath.length() + 1);
      }
      else if (file.getFileSystem().equals(baseFile.getFileSystem())) {
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

  public List<ModuleToImport> getSelectedModules() {
    List<ModuleToImport> result = new ArrayList<ModuleToImport>(myModules.keySet().size());
    for (ModuleToImport module : myModules.keySet()) {
      if (isModuleSelected(module)) {
        result.add(module);
      }
    }
    return result;
  }

  private boolean isModuleSelected(@Nullable ModuleToImport module) {
    Boolean checked = getModuleValidationState(module).isChecked();
    return checked != null ? checked : !myUncheckedModules.contains(module);
  }

  public void setModules(@Nullable Project project, @Nullable VirtualFile currentPath, @Nullable Iterable<ModuleToImport> modules) {
    myCurrentPath = currentPath;
    myProject = project;
    if (modules == null) {
      myModules = Collections.emptyMap();
    }
    else {
      myModules = Maps.newTreeMap(new ModuleComparator(myCurrentPath));
      for (ModuleToImport module : modules) {
        myModules.put(module, null);
      }
    }
    revalidate(myModules.keySet());
    ((ModulesTableModel)getModel()).setModules(myModules.keySet());
  }

  private void revalidate(Set<ModuleToImport> modules) {
    // 1. Validate.
    for (ModuleToImport module : modules) {
      myModules.put(module, validateModule(module));
    }
    myHasPrimaryModule = !Maps.filterValues(myModules, equalTo(ModuleValidationState.PRIMARY)).isEmpty();
    // If we have a "primary module" then all modules in the list are required
    if (!myHasPrimaryModule) {
      computeRequiredModules();
    }
  }

  private void computeRequiredModules() {
    Set<ModuleToImport> validModules =
      Maps.filterValues(myModules, in(Arrays.asList(ModuleValidationState.OK, ModuleValidationState.PRIMARY))).keySet();
    Map<String, ModuleToImport> namesToModules = Maps.newHashMapWithExpectedSize(myModules.size());
    // We only care about modules we are actually going to import.
    for (ModuleToImport module : validModules) {
      namesToModules.put(module.name, module);
    }
    myRequiredModules = LinkedListMultimap.create();
    Queue<ModuleToImport> queue = Lists.newLinkedList(Iterables.filter(validModules, not(in(myUncheckedModules))));

    while (!queue.isEmpty()) {
      ModuleToImport moduleToImport = queue.remove();
      for (ModuleToImport dep : Iterables.transform(moduleToImport.getDependencies(), Functions.forMap(namesToModules, null))) {
        if (dep != null) {
          if (!myRequiredModules.containsKey(dep)) {
            queue.add(dep);
          }
          myRequiredModules.put(dep, moduleToImport);
        }
      }
    }
  }

  @Override
  protected TableModel createDefaultDataModel() {
    return new ModulesTableModel();
  }

  private void setModuleSelection(@Nullable ModuleToImport module, boolean selected) {
    if (module != null && getModuleValidationState(module).canToggle()) {
      List<ModuleToImport> old = getSelectedModules();
      if (selected) {
        myUncheckedModules.remove(module);
      }
      else {
        myUncheckedModules.add(module);
      }
      computeRequiredModules();
      List<ModuleToImport> current = getSelectedModules();
      firePropertyChange(PROPERTY_SELECTED_MODULES, old, current);
    }
  }

  private boolean projectHasSubprojectWithPath(ModuleToImport module) {
    if (myProject == null) {
      return false;
    }
    else {
      String name = module.name;
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
  }

  private ModuleValidationState validateModule(ModuleToImport module) {
    VirtualFile location = module.location;
    if (location == null) {
      return ModuleValidationState.NOT_FOUND;
    }
    if (projectHasSubprojectWithPath(module)) {
      return ModuleValidationState.ALREADY_EXISTS;
    }
    if (Objects.equal(location, myCurrentPath)) {
      return ModuleValidationState.PRIMARY;
    }
    return ModuleValidationState.OK;
  }

  @NotNull
  private ModuleValidationState getModuleValidationState(@Nullable ModuleToImport module) {
    if (module == null) {
      return ModuleValidationState.NULL;
    }
    ModuleValidationState state = myModules.get(module);
    if (!myHasPrimaryModule && state == ModuleValidationState.OK && myRequiredModules.containsKey(module)) {
      return ModuleValidationState.REQUIRED;
    }
    else {
      return state;
    }
  }

  private enum ModuleValidationState {
    OK(null), NULL(false), NOT_FOUND(false), ALREADY_EXISTS(false), PRIMARY(true), REQUIRED(true);

    private final Boolean myAlwaysChecked;

    ModuleValidationState(Boolean alwaysChecked) {
      myAlwaysChecked = alwaysChecked;
    }

    @Nullable
    public Boolean isChecked() {
      return myAlwaysChecked;
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
  private static class ModuleComparator implements Comparator<ModuleToImport> {
    @Nullable private final VirtualFile myImportPath;

    public ModuleComparator(@Nullable VirtualFile importPath) {
      myImportPath = importPath;
    }

    @Override
    public int compare(ModuleToImport o1, ModuleToImport o2) {
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

  private class ModulePathCellRenderer implements TableCellRenderer {
    private final JLabel myLabel = new JLabel();

    public ModulePathCellRenderer() {
      myLabel.setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      ModuleToImport module = (ModuleToImport)value;
      myLabel.setText(getModulePath(module));
      configureComponent(myLabel, module, table, isSelected);
      return myLabel;
    }

    private String getModulePath(ModuleToImport module) {
      if (module == null || module.location == null) {
        return "";
      }
      else {
        if (getModuleValidationState(module) == ModuleValidationState.PRIMARY) {
          return module.location.getPath();
        } else {
          return getRelativePath(myCurrentPath, module.location);
        }
      }
    }
  }

  private final class ModuleNameCellEditor extends AbstractTableCellEditor implements TableCellRenderer {
    private final JBCheckBox myCheckBox = new JBCheckBox();
    private final JLabel myLabel = new JLabel();

    public ModuleNameCellEditor() {
      myCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
        }
      });
      myCheckBox.setOpaque(true);
      myLabel.setOpaque(true);
    }

    private JComponent setupComponent(@NotNull JTable table,
                                      @Nullable ModuleToImport module,
                                      @NotNull JBCheckBox checkBox,
                                      boolean isSelected) {
      // JTable may send a null value to the cell editor during initialization
      final String text;
      if (module != null) {
        text = module.name;
      }
      else {
        text = "<No Module>";
      }
      final JComponent component;
      if (myHasPrimaryModule) {
        myLabel.setText(text);
        component = myLabel;
      } else {
        checkBox.setText(text);
        checkBox.setSelected(isModuleSelected(module));
        component = checkBox;
      }

      configureComponent(component, module, table, isSelected);
      return component;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      return setupComponent(table, (ModuleToImport)value, myCheckBox, true);
    }

    @Override
    public Object getCellEditorValue() {
      return myCheckBox.isSelected();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return setupComponent(table, (ModuleToImport)value, myCheckBox, isSelected);
    }
  }

  private class ModulesTableModel extends AbstractTableModel {
    private List<ModuleToImport> myModules = Collections.emptyList();

    @Override
    public int getRowCount() {
      return myModules.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      ModuleToImport module = myModules.get(rowIndex);
      return !myHasPrimaryModule && columnIndex == 0 && getModuleValidationState(module).canToggle();
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      ModuleToImport module = myModules.get(rowIndex);
      if (columnIndex == 0) {
        setModuleSelection(module, Boolean.TRUE.equals(aValue));
      }
      fireTableRowsUpdated(0, myModules.size() - 1);
    }

    @Override
    public Object getValueAt(int row, int column) {
      return myModules.get(row);
    }

    public void setModules(@NotNull Collection<ModuleToImport> modules) {
      myModules = new ArrayList<ModuleToImport>(modules);
      fireTableDataChanged();
    }
  }
}


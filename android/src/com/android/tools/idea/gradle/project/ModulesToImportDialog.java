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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SystemProperties;
import org.jdesktop.swingx.JXLabel;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_ANDROID_PROJECT;
import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_GRADLE_PROJECT;
import static com.intellij.icons.AllIcons.Nodes.PpJdk;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static icons.AndroidIcons.AppModule;
import static icons.AndroidIcons.LibraryModule;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;
import static java.awt.event.KeyEvent.*;
import static java.util.Collections.emptyList;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

public class ModulesToImportDialog extends DialogWrapper {
  @NotNull private final List<DataNode<ModuleData>> alwaysIncludedModules = Lists.newArrayList();

  @Nullable private final Project myProject;

  private JPanel myPanel;
  private JBTable myModulesTable;
  private JXLabel myDescriptionLabel;
  private JPanel myContentsPanel;
  private JBLabel mySelectionStatusLabel;

  private volatile boolean skipValidation;

  public ModulesToImportDialog(@NotNull Collection<DataNode<ModuleData>> modules, @Nullable Project project) {
    super(project, true, IdeModalityType.IDE);
    setTitle("Select Modules to Include");
    myProject = project;

    List<DataNode<ModuleData>> sortedModules = Lists.newArrayList(modules);
    Collections.sort(sortedModules, new Comparator<DataNode<ModuleData>>() {
      @Override
      public int compare(@NotNull DataNode<ModuleData> m1, @NotNull DataNode<ModuleData> m2) {
        return getNameOf(m1).compareTo(getNameOf(m2));
      }
    });

    init();
    for (DataNode<ModuleData> module : sortedModules) {
      Collection<DataNode<IdeaGradleProject>> gradleProjects = getChildren(module, IDE_GRADLE_PROJECT);
      if (gradleProjects.isEmpty()) {
        alwaysIncludedModules.add(module);
      }
      else {
        // We only show modules that are recognized in Gradle.
        // For example, in a multi-module project the top-level module is just a folder that contains the rest of
        // modules, which is not defined in settings.gradle.
        getModulesTable().add(module);
      }
    }

    myDescriptionLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(new SelectAllAction(true), new SelectAllAction(false));
    group.addSeparator();
    group.addAll(new LoadFromFileAction(), new SaveToFileAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("android.gradle.module.selection.dialog.toolbar", group, true);
    myContentsPanel.add(toolbar.getComponent(), NORTH);

    mySelectionStatusLabel = new JBLabel();
    myContentsPanel.add(mySelectionStatusLabel, SOUTH);
    updateSelectionStatus();
  }

  @NotNull
  private ModuleTable getModulesTable() {
    return (ModuleTable)myModulesTable;
  }

  private void updateSelectionStatus() {
    ModuleTable table = getModulesTable();
    int rowCount = table.getRowCount();
    int selectedRowCount = table.getModel().selectedRowCount;
    String msg = String.format("%1$d Modules. %2$d selected", rowCount, selectedRowCount);
    mySelectionStatusLabel.setText(msg);
  }

  @NotNull
  private static String getNameOf(@NotNull DataNode<ModuleData> module) {
    return module.getData().getExternalName();
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    if (!hasSelectedModules()) {
      return new ValidationInfo("Please select at least on Module", myModulesTable);
    }
    return null;
  }

  private boolean hasSelectedModules() {
    return getModulesTable().getModel().selectedRowCount > 0;
  }

  private void setAllSelected(boolean selected) {
    ModuleTable table = getModulesTable();
    int count = table.getRowCount();
    skipValidation = true;
    for (int i = 0; i < count; i++) {
      table.setItemSelected(i, selected);
    }
    skipValidation = false;
    if (!selected) {
      initValidation();
    }
    updateSelectionStatus();
  }

  @NotNull
  public Collection<DataNode<ModuleData>> getSelectedModules() {
    List<DataNode<ModuleData>> modules = Lists.newArrayList(alwaysIncludedModules);
    modules.addAll(getUserSelectedModules());
    return modules;
  }

  @VisibleForTesting
  @NotNull
  public Collection<String> getDisplayedModules() {
    return getModulesTable().getModel().getModuleNames();
  }

  @NotNull
  private Collection<DataNode<ModuleData>> getUserSelectedModules() {
    List<DataNode<ModuleData>> modules = Lists.newArrayList();
    ModuleTable table = getModulesTable();
    int count = table.getRowCount();
    for (int i = 0; i < count; i++) {
      if (table.isItemSelected(i)) {
        modules.add(table.getItemAt(i));
      }
    }
    return modules;
  }

  private void select(@NotNull List<String> moduleNames) {
    ModuleTable table = getModulesTable();
    int count = table.getRowCount();
    for (int i = 0; i < count; i++) {
      DataNode<ModuleData> module = table.getItemAt(i);
      String name = getNameOf(module);
      table.setItemSelected(i, moduleNames.contains(name));
    }
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myModulesTable;
  }

  private void createUIComponents() {
    myModulesTable = new ModuleTable();
    new TableSpeedSearch(myModulesTable);
  }

  private class SelectAllAction extends DumbAwareAction {
    private final boolean mySelect;

    SelectAllAction(boolean select) {
      super(select ? "Select All" : "Unselect All", null, select ? PlatformIcons.SELECT_ALL_ICON : PlatformIcons.UNSELECT_ALL_ICON);
      mySelect = select;
      int keyCode = select ? VK_A : VK_N;
      registerCustomShortcutSet(keyCode, SystemInfo.isMac? META_MASK : CTRL_MASK, myModulesTable);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myModulesTable.getRowCount() > 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setAllSelected(mySelect);
    }
  }

  private class LoadFromFileAction extends DumbAwareAction {
    LoadFromFileAction() {
      super("Load Selection from File", null, AllIcons.Actions.Menu_open);
      registerCustomShortcutSet(VK_O, SystemInfo.isMac ? META_MASK : CTRL_MASK, myModulesTable);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          boolean selectable = super.isFileSelectable(file);
          if (selectable) {
            selectable = SdkConstants.EXT_XML.equals(file.getExtension());
          }
          return selectable;
        }
      };
      String title = "Load Module Selection";
      descriptor.setTitle(title);
      FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, myProject, getWindow());
      VirtualFile[] allSelected = dialog.choose(myProject);
      if (allSelected.length > 0) {
        File file = VfsUtilCore.virtualToIoFile(allSelected[0]);
        try {
          List<String> loadedModuleNames = Selection.load(file);
          select(loadedModuleNames);
        }
        catch (Throwable error) {
          String msg = String.format("Failed to load Module selection from file '%1$s'", file.getPath());
          Messages.showErrorDialog(getWindow(), msg, title);
          String cause = error.getMessage();
          if (StringUtil.isNotEmpty(cause)) {
            msg = msg + ":\n" + cause;
          }
          Logger.getInstance(ModulesToImportDialog.class).info(msg, error);
        }
      }
    }
  }

  private class SaveToFileAction extends DumbAwareAction {
    SaveToFileAction() {
      super("Save Selection As", null, AllIcons.Actions.Menu_saveall);
      registerCustomShortcutSet(VK_S, SystemInfo.isMac? META_MASK : CTRL_MASK, myModulesTable);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(hasSelectedModules());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final String title = "Save Module Selection";
      FileSaverDescriptor descriptor = new FileSaverDescriptor(title, "Save the list of selected Modules to a file",
                                                               SdkConstants.EXT_XML);
      FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, getWindow());
      VirtualFile baseDir = myProject != null ? myProject.getBaseDir() : null;
      VirtualFileWrapper result = dialog.save(baseDir, null);
      if (result != null) {
        File file = result.getFile();
        try {
          Selection.save(getUserSelectedModules(), file);
        }
        catch (IOException error) {
          String msg = String.format("Failed to save Module selection to file '%1$s'", file.getPath());
          Messages.showErrorDialog(getWindow(), msg, title);
          String cause = error.getMessage();
          if (StringUtil.isNotEmpty(cause)) {
            msg = msg + ":\n" + cause;
          }
          Logger.getInstance(ModulesToImportDialog.class).info(msg, error);
        }
      }
    }
  }

  // Module selection can be stored in XML files. Sample:
  // <?xml version="1.0" encoding="UTF-8"?>
  // <selectedModules>
  //   <module name="app" />
  //   <module name="mylibrary" />
  //</selectedModules>

  @VisibleForTesting
  static class Selection {
    @NonNls private static final String ROOT_ELEMENT_NAME = "selectedModules";
    @NonNls private static final String MODULE_ELEMENT_NAME = "module";
    @NonNls private static final String MODULE_NAME_ATTRIBUTE_NAME = "name";

    @NotNull
    static List<String> load(@NotNull File file) throws JDOMException, IOException {
      Document document = JDOMUtil.loadDocument(file);
      List<String> modules = Lists.newArrayList();
      Element rootElement = document.getRootElement();
      if (rootElement != null && ROOT_ELEMENT_NAME.equals(rootElement.getName())) {
        for (Element child : rootElement.getChildren(MODULE_ELEMENT_NAME)) {
          String moduleName = child.getAttributeValue(MODULE_NAME_ATTRIBUTE_NAME);
          if (StringUtil.isNotEmpty(moduleName)) {
            modules.add(moduleName);
          }
        }
      }
      return modules;
    }

    static void save(@NotNull Collection<DataNode<ModuleData>> modules, @NotNull File file) throws IOException {
      Document document = new Document(new Element(ROOT_ELEMENT_NAME));
      for (DataNode<ModuleData> module : modules) {
        Element child = new Element(MODULE_ELEMENT_NAME);
        child.setAttribute(MODULE_NAME_ATTRIBUTE_NAME, getNameOf(module));
        document.getRootElement().addContent(child);
      }
      JDOMUtil.writeDocument(document, file, SystemProperties.getLineSeparator());
    }
  }

  private class ModuleTable extends JBTable {
    ModuleTable() {
      super(new ModuleTableModel());
      setCheckBoxColumnWidth();
      setModuleNameCellRenderer();

      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      setIntercellSpacing(new Dimension(0, 0));
      setRowSelectionAllowed(true);
      setSelectionMode(SINGLE_SELECTION);
      setShowGrid(false);
      setTableHeader(null);
    }

    private void setCheckBoxColumnWidth() {
      TableColumn column = getColumnModel().getColumn(ModuleTableModel.SELECTED_MODULE_COLUMN);
      int width = 30;
      column.setMaxWidth(width);
      column.setPreferredWidth(width);
      column.setWidth(width);
    }

    private void setModuleNameCellRenderer() {
      TableColumn column = getColumnModel().getColumn(ModuleTableModel.MODULE_NAME_COLUMN);
      column.setCellRenderer(new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int rowIndex,
                                                       int columnIndex) {
          Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, columnIndex);
          if (c instanceof JLabel && value instanceof ModuleRow) {
            JLabel label = (JLabel)c;
            ModuleRow row = (ModuleRow)value;
            label.setIcon(row.icon);
            label.setText(getNameOf(row.module));
          }
          return c;
        }
      });
    }

    @Override
    @NotNull
    public ModuleTableModel getModel() {
      return (ModuleTableModel)super.getModel();
    }

    void add(@NotNull DataNode<ModuleData> module) {
      getModel().add(module);
    }

    boolean isItemSelected(int rowIndex) {
      return getModel().isItemSelected(rowIndex);
    }

    @NotNull
    public DataNode<ModuleData> getItemAt(int rowIndex) {
      return getModel().getItemAt(rowIndex);
    }

    void setItemSelected(int rowIndex, boolean selected) {
      getModel().setItemSelected(rowIndex, selected);
    }
  }

  private class ModuleTableModel extends AbstractTableModel {
    private static final int SELECTED_MODULE_COLUMN = 0;
    private static final int MODULE_NAME_COLUMN = 1;

    public int selectedRowCount;

    @NotNull private final List<ModuleRow> rows = Lists.newArrayList();

    @NotNull
    Collection<String> getModuleNames() {
      if (rows.isEmpty()) {
        return emptyList();
      }
      List<String> names = Lists.newArrayListWithExpectedSize(rows.size());
      for (ModuleRow row : rows) {
        names.add(getNameOf(row.module));
      }
      return names;
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < rows.size()) {
        ModuleRow row = rows.get(rowIndex);
        switch (columnIndex) {
          case SELECTED_MODULE_COLUMN:
            return row.selected;
          default:
            return row;
        }
      }
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      switch (columnIndex) {
        case SELECTED_MODULE_COLUMN:
          return Boolean.class;
        default:
          return Object.class;
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == SELECTED_MODULE_COLUMN;
    }

    @Override
    public void setValueAt(@Nullable Object aValue, int rowIndex, int columnIndex) {
      if (rowIndex < rows.size() && columnIndex == SELECTED_MODULE_COLUMN && aValue instanceof Boolean) {
        boolean selected = (Boolean)aValue;
        if (setItemSelected(rowIndex, selected) && !skipValidation) {
          initValidation();
          updateSelectionStatus();
        }
      }
    }

    void add(@NotNull DataNode<ModuleData> module) {
      rows.add(new ModuleRow(module));
      selectedRowCount++;
    }

    boolean isItemSelected(int rowIndex) {
      ModuleRow row = rows.get(rowIndex);
      return row.selected;
    }

    @NotNull
    DataNode<ModuleData> getItemAt(int rowIndex) {
      ModuleRow row = rows.get(rowIndex);
      return row.module;
    }

    boolean setItemSelected(int rowIndex, boolean selected) {
      ModuleRow row = rows.get(rowIndex);
      if (row.selected != selected) {
        row.selected = selected;
        if (row.selected) {
          selectedRowCount++;
        }
        else {
          selectedRowCount--;
        }
        return true;
      }
      return false;
    }
  }

  private static class ModuleRow {
    @NotNull public final DataNode<ModuleData> module;
    @NotNull public final Icon icon;
    public boolean selected = true;

    ModuleRow(@NotNull DataNode<ModuleData> module) {
      this.module = module;
      icon = getModuleIcon(module);
    }

    @NotNull
    private static Icon getModuleIcon(@NotNull DataNode<ModuleData> module) {
      Collection<DataNode<IdeaAndroidProject>> children = getChildren(module, IDE_ANDROID_PROJECT);
      if (!children.isEmpty()) {
        DataNode<IdeaAndroidProject> child = getFirstItem(children);
        if (child != null) {
          IdeaAndroidProject androidProject = child.getData();
          return androidProject.getDelegate().isLibrary() ? LibraryModule : AppModule;
        }
      }
      return PpJdk;
    }
  }
}

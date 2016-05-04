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
package com.android.tools.idea.gradle.project.subset;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.Cell;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PairFunction;
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
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.gradle.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.AndroidProjectKeys.GRADLE_MODEL;
import static com.intellij.icons.AllIcons.Nodes.PpJdk;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.JDOMUtil.writeDocument;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
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
  private static final int SELECTED_MODULE_COLUMN = 0;
  private static final int MODULE_NAME_COLUMN = 1;

  @NotNull private final List<DataNode<ModuleData>> alwaysIncludedModules = Lists.newArrayList();

  @Nullable private final Project myProject;

  private JPanel myPanel;
  private JBTable myModulesTable;
  private JXLabel myDescriptionLabel;
  private JPanel myContentsPanel;
  private JBLabel mySelectionStatusLabel;

  private volatile boolean mySkipValidation;
  private int myMaxSelectionCount = -1;

  public ModulesToImportDialog(@NotNull Collection<DataNode<ModuleData>> modules, @Nullable Project project) {
    super(project, true, IdeModalityType.IDE);
    setTitle("Select Modules to Include in Project Subset");
    myProject = project;

    init();
    ModuleTableModel model = getModulesTable().getModel();
    for (DataNode<ModuleData> module : modules) {
      Collection<DataNode<GradleModel>> gradleProjects = getChildren(module, GRADLE_MODEL);
      if (gradleProjects.isEmpty()) {
        alwaysIncludedModules.add(module);
      }
      else {
        // We only show modules that are recognized in Gradle.
        // For example, in a multi-module project the top-level module is just a folder that contains the rest of
        // modules, which is not defined in settings.gradle.
        model.add(module);
      }
    }

    getModulesTable().sort();

    myDescriptionLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(new SelectAllAction(true), new SelectAllAction(false));
    group.addSeparator();
    group.add(new ShowSelectedModulesAction(getModulesTable()));
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
    ModuleTableModel model = table.getModel();
    int rowCount = model.getRowCount();
    int selectedRowCount = model.selectedRowCount;
    String msg = String.format("%1$d Modules. %2$d selected", rowCount, selectedRowCount);
    mySelectionStatusLabel.setText(msg);
    table.updateFilter();
  }

  @NotNull
  private static String getNameOf(@NotNull DataNode<ModuleData> module) {
    return module.getData().getExternalName();
  }

  public void setDescription(@NotNull String description) {
    myDescriptionLabel.setText(description);
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    int selectionCount = getModulesTable().getModel().selectedRowCount;
    if (selectionCount <= 0) {
      return new ValidationInfo("Please select at least one Module", myModulesTable);
    }
    if (myMaxSelectionCount > 0 && selectionCount > myMaxSelectionCount) {
      String message = "Please select only " + myMaxSelectionCount + " module";
      if (myMaxSelectionCount > 1) {
        message += "s";
      }
      message += ".";
      return new ValidationInfo(message, myModulesTable);
    }
    return null;
  }

  private boolean hasSelectedModules() {
    return getModulesTable().getModel().selectedRowCount > 0;
  }

  private void setAllSelected(boolean selected) {
    ModuleTableModel model = getModulesTable().getModel();
    int count = model.getRowCount();
    mySkipValidation = true;
    for (int i = 0; i < count; i++) {
      model.setItemSelected(i, selected);
    }
    mySkipValidation = false;
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
    ModuleTableModel model = table.getModel();
    int count = model.getRowCount();
    for (int i = 0; i < count; i++) {
      if (model.isItemSelected(i)) {
        modules.add(model.getItemAt(i));
      }
    }
    return modules;
  }

  private void select(@NotNull List<String> moduleNames) {
    ModuleTableModel model = getModulesTable().getModel();
    int count = model.getRowCount();
    for (int i = 0; i < count; i++) {
      DataNode<ModuleData> module = model.getItemAt(i);
      String name = getNameOf(module);
      model.setItemSelected(i, moduleNames.contains(name));
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
    new TableSpeedSearch(myModulesTable, new PairFunction<Object, Cell, String>() {
      @Override
      public String fun(Object o, Cell v) {
        if (o instanceof ModuleRow) {
          ModuleRow row = (ModuleRow)o;
          return getNameOf(row.module);
        }
        return o == null || o instanceof Boolean ? "" : o.toString();
      }
    });
  }

  public void updateSelection(@NotNull Collection<String> selection) {
    ModuleTableModel model = getModulesTable().getModel();
    int count = model.getRowCount();
    mySkipValidation = true;
    for (int i = 0; i < count; i++) {
      DataNode<ModuleData> module = model.getItemAt(i);
      String name = getNameOf(module);
      boolean selected = selection.contains(name);
      model.setItemSelected(i, selected);
    }
    mySkipValidation = false;
    initValidation();
    updateSelectionStatus();
  }

  public void setMaxSelectionCount(int maxSelectionCount) {
    if (maxSelectionCount == 0) {
      throw new IllegalArgumentException("Value must be different than zero");
    }
    myMaxSelectionCount = maxSelectionCount;
  }

  public void clearSelection() {
    List<String> selection = Collections.emptyList();
    updateSelection(selection);
  }

  private static class ShowSelectedModulesAction extends ToggleAction {
    private ModuleTable myTable;

    ShowSelectedModulesAction(@NotNull ModuleTable table) {
      super("Show Selected Modules Only", null, AllIcons.Actions.ShowHiddens);
      myTable = table;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myTable.myShowSelectedRowsOnly;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myTable.setShowSelectedRowsOnly(state);
    }
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
      int rowCount = getModulesTable().getModel().getRowCount();
      e.getPresentation().setEnabled(rowCount > 0);
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
        File file = virtualToIoFile(allSelected[0]);
        try {
          List<String> loadedModuleNames = Selection.load(file);
          select(loadedModuleNames);
        }
        catch (Throwable error) {
          String msg = String.format("Failed to load Module selection from file '%1$s'", file.getPath());
          Messages.showErrorDialog(getWindow(), msg, title);
          String cause = error.getMessage();
          if (isNotEmpty(cause)) {
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
      String title = "Save Module Selection";
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
          if (isNotEmpty(cause)) {
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
      List<String> modules = Lists.newArrayList();
      Element rootElement = JDOMUtil.load(file);
      if (ROOT_ELEMENT_NAME.equals(rootElement.getName())) {
        for (Element child : rootElement.getChildren(MODULE_ELEMENT_NAME)) {
          String moduleName = child.getAttributeValue(MODULE_NAME_ATTRIBUTE_NAME);
          if (isNotEmpty(moduleName)) {
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
      writeDocument(document, file, SystemProperties.getLineSeparator());
    }
  }

  private class ModuleTable extends JBTable {
    private ModuleTableRowSorter myRowSorter;
    private boolean myShowSelectedRowsOnly;

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
      TableColumn column = getColumnModel().getColumn(SELECTED_MODULE_COLUMN);
      int width = 30;
      column.setMaxWidth(width);
      column.setPreferredWidth(width);
      column.setWidth(width);
    }

    private void setModuleNameCellRenderer() {
      TableColumn column = getColumnModel().getColumn(MODULE_NAME_COLUMN);
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

    void updateFilter() {
      setShowSelectedRowsOnly(myShowSelectedRowsOnly);
    }

    void setShowSelectedRowsOnly(boolean showSelectedRowsOnly) {
      myShowSelectedRowsOnly = showSelectedRowsOnly;
      if (myRowSorter == null) {
        sort();
      }
      if (showSelectedRowsOnly) {
        myRowSorter.setRowFilter(new RowFilter<ModuleTableModel, Integer>() {
          @Override
          public boolean include(Entry<? extends ModuleTableModel, ? extends Integer> entry) {
            Object value = entry.getValue(MODULE_NAME_COLUMN);
            if (value instanceof ModuleRow) {
              ModuleRow row = (ModuleRow)value;
              return row.selected;
            }
            return false;
          }
        });
      }
      else {
        myRowSorter.setRowFilter(null);
      }
    }

    void sort() {
      myRowSorter = new ModuleTableRowSorter(getModel());
      setRowSorter(myRowSorter);
    }

    @Override
    @NotNull
    public ModuleTableModel getModel() {
      return (ModuleTableModel)super.getModel();
    }
  }

  private static class ModuleTableRowSorter extends TableRowSorter<ModuleTableModel> {
    ModuleTableRowSorter(@NotNull ModuleTableModel model) {
      super(model);
      setComparator(MODULE_NAME_COLUMN, new Comparator<ModuleRow>() {
        @Override
        public int compare(ModuleRow row1, ModuleRow row2) {
          return Collator.getInstance().compare(row1.toString(), row2.toString());
        }
      });
      List<RowSorter.SortKey> sortKeys = Lists.newArrayList();
      sortKeys.add(new RowSorter.SortKey(MODULE_NAME_COLUMN, SortOrder.ASCENDING));
      setSortKeys(sortKeys);
    }
  }

  private class ModuleTableModel extends AbstractTableModel {
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
        case MODULE_NAME_COLUMN:
          return ModuleRow.class;
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
        if (setItemSelected(rowIndex, selected) && !mySkipValidation) {
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
      Collection<DataNode<AndroidGradleModel>> children = getChildren(module, ANDROID_MODEL);
      if (!children.isEmpty()) {
        DataNode<AndroidGradleModel> child = getFirstItem(children);
        if (child != null) {
          AndroidGradleModel androidModel = child.getData();
          return androidModel.getAndroidProject().isLibrary() ? LibraryModule : AppModule;
        }
      }
      return PpJdk;
    }

    @Override
    public String toString() {
      return getNameOf(module);
    }
  }
}

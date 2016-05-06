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
package com.android.tools.idea.gradle.structure.editors;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.tools.idea.gradle.parser.*;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.structure.EditorPanel;
import com.android.tools.idea.templates.SupportLibrary;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ActionRunner;
import com.intellij.util.PlatformIcons;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.EnumSet;
import java.util.List;

import static com.android.tools.idea.templates.RepositoryUrlManager.REVISION_ANY;

/**
 * A GUI object that displays and modifies dependencies for an Android-Gradle module.
 */
public class ModuleDependenciesPanel extends EditorPanel {
  private static final Logger LOG = Logger.getInstance(ModuleDependenciesPanel.class);
  private static final int SCOPE_COLUMN_WIDTH = 120;
  private final JBTable myEntryTable;
  private final ModuleDependenciesTableModel myModel;
  private final String myModulePath;
  private final Project myProject;
  private final GradleBuildFile myGradleBuildFile;
  private final GradleSettingsFile myGradleSettingsFile;
  private AnActionButton myRemoveButton;

  public ModuleDependenciesPanel(@NotNull Project project, @NotNull String modulePath) {
    super(new BorderLayout());

    myModulePath = modulePath;
    myProject = project;
    myModel = new ModuleDependenciesTableModel();
    myGradleSettingsFile = GradleSettingsFile.get(myProject);

    Module module = GradleUtil.findModuleByGradlePath(myProject, modulePath);
    myGradleBuildFile = module != null ? GradleBuildFile.get(module) : null;
    if (myGradleBuildFile != null) {
      List<BuildFileStatement> dependencies = myGradleBuildFile.getDependencies();
      for (BuildFileStatement dependency : dependencies) {
        myModel.addItem(new ModuleDependenciesTableItem(dependency));
      }
    } else {
      LOG.warn("Unable to find Gradle build file for module " + myModulePath);
    }
    myModel.resetModified();

    myEntryTable = new JBTable(myModel);
    TableRowSorter<ModuleDependenciesTableModel> sorter = new TableRowSorter<ModuleDependenciesTableModel>(myModel);
    sorter.setRowFilter(myModel.getFilter());
    myEntryTable.setRowSorter(sorter);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(ModuleDependenciesTableItem.class, new TableItemRenderer());

    if (myGradleBuildFile == null) {
      return;
    }
    final boolean isAndroid = myGradleBuildFile.hasAndroidPlugin();
    List<Dependency.Scope> scopes = Lists.newArrayList(
      Sets.filter(EnumSet.allOf(Dependency.Scope.class), new Predicate<Dependency.Scope>() {
        @Override
        public boolean apply(Dependency.Scope input) {
          return isAndroid ? input.isAndroidScope() : input.isJavaScope();
        }
      }));
    ComboBoxModel boxModel = new CollectionComboBoxModel(scopes, null);
    JComboBox scopeEditor = new ComboBox(boxModel);
    myEntryTable.setDefaultEditor(Dependency.Scope.class, new DefaultCellEditor(scopeEditor));
    myEntryTable.setDefaultRenderer(Dependency.Scope.class, new ComboBoxTableRenderer<Dependency.Scope>(Dependency.Scope.values()) {
        @Override
        protected String getTextFor(@NotNull final Dependency.Scope value) {
          return value.getDisplayName();
        }
      });

    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    new SpeedSearchBase<JBTable>(myEntryTable) {
      @Override
      public int getSelectedIndex() {
        return myEntryTable.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return myEntryTable.convertRowIndexToModel(viewIndex);
      }

      @Override
      @NotNull
      public Object[] getAllElements() {
        return myModel.getItems().toArray();
      }

      @Override
      @NotNull
      public String getElementText(Object element) {
        return getCellAppearance((ModuleDependenciesTableItem)element).getText();
      }

      @Override
      public void selectElement(@NotNull Object element, @NotNull String selectedText) {
        final int count = myModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myModel.getItemAt(row))) {
            final int viewRow = myEntryTable.convertRowIndexToView(row);
            myEntryTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            TableUtil.scrollSelectionToVisible(myEntryTable);
            break;
          }
        }
      }
    };

    TableColumn column = myEntryTable.getTableHeader().getColumnModel().getColumn(ModuleDependenciesTableModel.SCOPE_COLUMN);
    column.setResizable(false);
    column.setMaxWidth(SCOPE_COLUMN_WIDTH);
    column.setMinWidth(SCOPE_COLUMN_WIDTH);

    add(createTableWithButtons(), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(myRemoveButton);
    PopupHandler.installPopupHandler(myEntryTable, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  @NotNull
  private JComponent createTableWithButtons() {
    myEntryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateButtons();
      }
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myEntryTable);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ImmutableList<PopupAction> popupActions = ImmutableList.of(
          new PopupAction(AndroidIcons.MavenLogo, 1, "Library dependency") {
            @Override
            public void run() {
              addExternalDependency();
            }
          }, new PopupAction(PlatformIcons.LIBRARY_ICON, 2, "File dependency") {
            @Override
            public void run() {
              addFileDependency();
            }
          }, new PopupAction(AllIcons.Nodes.Module, 3, "Module dependency") {
            @Override
            public void run() {
              addModuleDependency();
            }
          }
        );
        final JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PopupAction>(null, popupActions) {
          @Override
          public Icon getIconFor(PopupAction value) {
            return value.myIcon;
          }

          @Override
          public boolean hasSubstep(PopupAction value) {
            return false;
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(final PopupAction value, final boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                value.run();
              }
            });
          }

          @Override
          @NotNull
          public String getTextFor(PopupAction value) {
            return "&" + value.myIndex + "  " + value.myTitle;
          }
        });
        popup.show(button.getPreferredPopupPoint());
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeSelectedItems();
        }
      });
    decorator.setMoveUpAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        moveSelectedRows(-1);
      }
    });
    decorator.setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveSelectedRows(+1);
        }
      });

    final JPanel panel = decorator.createPanel();
    myRemoveButton = ToolbarDecorator.findRemoveButton(panel);
    return panel;
  }

  private void addExternalDependency() {
    Module module = GradleUtil.findModuleByGradlePath(myProject, myModulePath);
    MavenDependencyLookupDialog dialog = new MavenDependencyLookupDialog(myProject, module);
    dialog.setTitle("Choose Library Dependency");
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      String coordinateText = dialog.getSearchText();
      coordinateText = installRepositoryIfNeeded(coordinateText);
      if (coordinateText != null) {
        myModel.addItem(new ModuleDependenciesTableItem(
            new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, coordinateText)));
      }
    }
    myModel.fireTableDataChanged();
  }

  private String installRepositoryIfNeeded(String coordinateText) {
    GradleCoordinate gradleCoordinate = GradleCoordinate.parseCoordinateString(coordinateText);
    assert gradleCoordinate != null;  // Only allowed to click ok when the string is valid.
    SupportLibrary supportLibrary = SupportLibrary.forGradleCoordinate(gradleCoordinate);

    if (!REVISION_ANY.equals(gradleCoordinate.getRevision()) || supportLibrary == null) {
      // No installation needed, or it's not a local repository.
      return coordinateText;
    }
    String message = "Library " + gradleCoordinate.getArtifactId() + " is not installed. Install repository?";
    if (Messages.showYesNoDialog(myProject, message, "Install Repository", Messages.getQuestionIcon()) != Messages.YES) {
      // User cancelled installation.
      return null;
    }
    List<String> requested = Lists.newArrayList();
    SdkMavenRepository repository;
    if (coordinateText.startsWith("com.android.support")) {
      repository = SdkMavenRepository.ANDROID;
    }
    else if (coordinateText.startsWith("com.google.android")) {
      repository = SdkMavenRepository.GOOGLE;
    }
    else {
      // Not a local repository.
      assert false;  // EXTRAS_REPOSITORY.containsKey() should have returned false.
      return coordinateText + ':' + REVISION_ANY;
    }
    requested.add(repository.getPackageId());
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(myProject, requested);
    if (dialog != null) {
      dialog.setTitle("Install Missing Components");
      if (dialog.showAndGet()) {
        return RepositoryUrlManager.get().getLibraryStringCoordinate(supportLibrary, true);
      }
    }

    // Installation wizard didn't complete - skip adding the dependency.
    return null;
  }

  private void addFileDependency() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false);
    VirtualFile buildFile = myGradleBuildFile.getFile();
    VirtualFile parent = buildFile.getParent();
    descriptor.setRoots(parent);
    VirtualFile virtualFile = FileChooser.chooseFile(descriptor, myProject, null);
    if (virtualFile != null) {
      String path = VfsUtilCore.getRelativePath(virtualFile, parent, '/');
      if (path == null) {
        path = virtualFile.getPath();
      }
      myModel.addItem(new ModuleDependenciesTableItem(new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, path)));
    }
    myModel.fireTableDataChanged();
  }

  private void addModuleDependency() {
    List<String> modules = Lists.newArrayList();
    for (String s : myGradleSettingsFile.getModules()) {
      modules.add(s);
    }
    List<BuildFileStatement> dependencies = myGradleBuildFile.getDependencies();
    for (BuildFileStatement dependency : dependencies) {
      if (dependency instanceof Dependency) {
        Object data = ((Dependency)dependency).data;
        if (data instanceof String) {
          modules.remove(data);
        }
      }
    }
    modules.remove(myModulePath);
    final Component parent = this;
    final String title = ProjectBundle.message("classpath.chooser.title.add.module.dependency");
    final String description = ProjectBundle.message("classpath.chooser.description.add.module.dependency");
    ChooseElementsDialog<String> dialog = new ChooseElementsDialog<String>(parent, modules, title, description, true) {
      @Override
      protected Icon getItemIcon(final String item) {
        return AllIcons.Nodes.Module;
      }

      @Override
      protected String getItemText(final String item) {
        return item;
      }
    };
    dialog.show();
    for (String module : dialog.getChosenElements()) {
      myModel.addItem(new ModuleDependenciesTableItem(
          new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, module)));
    }
    myModel.fireTableDataChanged();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateButtons();
  }

  private void updateButtons() {
    final int[] selectedRows = myEntryTable.getSelectedRows();
    boolean removeButtonEnabled = true;
    int minRow = myEntryTable.getRowCount() + 1;
    int maxRow = -1;
    for (final int selectedRow : selectedRows) {
      minRow = Math.min(minRow, selectedRow);
      maxRow = Math.max(maxRow, selectedRow);
      final ModuleDependenciesTableItem item = myModel.getItemAt(selectedRow);
      if (!item.isRemovable()) {
        removeButtonEnabled = false;
      }
    }
    if (myRemoveButton != null) {
      myRemoveButton.setEnabled(removeButtonEnabled && selectedRows.length > 0);
    }
  }

  private void removeSelectedItems() {
    if (myEntryTable.isEditing()){
      myEntryTable.getCellEditor().stopCellEditing();
    }
    for (int modelRow = myModel.getRowCount() - 1; modelRow >= 0; modelRow--) {
      if (myEntryTable.isCellSelected(myEntryTable.convertRowIndexToView(modelRow), 0)) {
        myModel.removeDataRow(modelRow);
      }
    }
    myModel.fireTableDataChanged();
    myModel.setModified();
  }

  private void moveSelectedRows(int increment) {
    if (increment == 0) {
      return;
    }
    if (myEntryTable.isEditing()){
      myEntryTable.getCellEditor().stopCellEditing();
    }
    final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
    for (int modelRow = increment < 0 ? 0 : myModel.getRowCount() - 1;
         increment < 0 ? modelRow < myModel.getRowCount() : modelRow >= 0;
         modelRow += increment < 0 ? +1 : -1) {
      int visibleRow = myEntryTable.convertRowIndexToView(modelRow);
      if (selectionModel.isSelectedIndex(visibleRow)) {
        int newVisibleRow = myEntryTable.convertRowIndexToView(moveRow(modelRow, increment));
        selectionModel.removeSelectionInterval(visibleRow, visibleRow);
        myModel.fireTableDataChanged();
        selectionModel.addSelectionInterval(newVisibleRow, newVisibleRow);
      }
    }
    Rectangle cellRect = myEntryTable.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    myEntryTable.scrollRectToVisible(cellRect);
    myEntryTable.repaint();
  }

  private int moveRow(final int row, final int increment) {
    int newIndex = Math.abs(row + increment) % myModel.getRowCount();
    final ModuleDependenciesTableItem item = myModel.removeDataRow(row);
    myModel.addItemAt(item, newIndex);
    return newIndex;
  }

  @NotNull
  private static CellAppearanceEx getCellAppearance(@NotNull final ModuleDependenciesTableItem item) {
    BuildFileStatement entry = item.getEntry();
    String data = "";
    Icon icon = null;
    if (entry instanceof Dependency) {
      Dependency dependency = (Dependency)entry;
      data = dependency.getValueAsString();
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (dependency.type) {
        case EXTERNAL:
          icon = AndroidIcons.MavenLogo;
          break;
        case FILES:
          icon = PlatformIcons.LIBRARY_ICON;
          break;
        case MODULE:
          icon = AllIcons.Nodes.Module;
          break;
      }
    } else if (entry != null) {
      data = entry.toString();
    }
    return SimpleTextCellAppearance.regular(data, icon);
  }

  @Override
  public void apply() {
    List<ModuleDependenciesTableItem> items = myModel.getItems();
    final List<BuildFileStatement> dependencies = Lists.newArrayListWithExpectedSize(items.size());
    for (ModuleDependenciesTableItem item : items) {
      dependencies.add(item.getEntry());
    }
    DumbService.getInstance(myProject).setAlternativeResolveEnabled(true);
    try {
      ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
        @Override
        public void run() throws Exception {
          myGradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
        }
      });
    }
    catch (Exception e) {
      LOG.error("Unable to commit dependency changes", e);
    }
    finally {
      DumbService.getInstance(myProject).setAlternativeResolveEnabled(false);
    }
    myModel.resetModified();
  }

  @Override
  public boolean isModified() {
    return myModel.isModified();
  }

  public void select(@NotNull GradleCoordinate dependency) {
    int row = myModel.getRow(dependency);
    if (row >= 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(row, row);
    }
  }

  private static class TableItemRenderer extends ColoredTableCellRenderer {
    private final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus,
                                         int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);
      if (value != null && value instanceof ModuleDependenciesTableItem) {
        final ModuleDependenciesTableItem tableItem = (ModuleDependenciesTableItem)value;
        getCellAppearance(tableItem).customize(this);
        setToolTipText(tableItem.getTooltipText());
      }
    }
  }

  private abstract static class PopupAction implements Runnable {
    private Icon myIcon;
    private Object myIndex;
    private Object myTitle;

    protected PopupAction(Icon icon, Object index, Object title) {
      myIcon = icon;
      myIndex = index;
      myTitle = title;
    }
  }
}

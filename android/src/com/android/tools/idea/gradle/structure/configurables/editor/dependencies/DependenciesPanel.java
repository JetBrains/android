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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.configurables.model.ArtifactDependencyMergedModel;
import com.android.tools.idea.gradle.structure.configurables.model.DependencyMergedModel;
import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.android.tools.idea.structure.dialog.HeaderPanel;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.intellij.icons.AllIcons.FileTypes.Any_type;
import static com.intellij.icons.AllIcons.Nodes.Module;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.getInactiveTextColor;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

class DependenciesPanel extends JPanel {
  @NotNull private final DependenciesEditor myDependenciesEditor;
  @NotNull private final ModuleMergedModel myModel;
  @NotNull private final TableView<DependencyMergedModel> myDependencyTable;

  @NotNull private final JBSplitter myMainHorizontalSplitter;
  @NotNull private final JBSplitter myMainVerticalSplitter;
  @NotNull private final List<ArtifactRepositorySearch> myRepositorySearches;
  @NotNull private final List<AddDependencyPanel> myDependenciesPanels;

  @NotNull private final JPanel myEditorPanel;
  @NotNull private JPanel myEmptyEditorPanel;
  @NotNull private final JBScrollPane myEditorScrollPane;
  @NotNull private final ArtifactDependencyEditor myArtifactDependencyEditor;
  @NotNull private final DependenciesTreePanel myDependenciesTreePanel;

  private boolean myShowGroupId;
  private int mySelectedAddDependencyActionIndex;

  DependenciesPanel(@NotNull  DependenciesEditor dependenciesEditor, @NotNull ModuleMergedModel model) {
    super(new BorderLayout());
    myDependenciesEditor = dependenciesEditor;
    myModel = model;

    // First thing, populate the "Dependencies" table.
    DependenciesTableModel tableModel = new DependenciesTableModel();
    List<DependencyMergedModel> dependencies = model.getDependencies();
    tableModel.setItems(dependencies);
    myDependencyTable = new TableView<DependencyMergedModel>(tableModel);
    if (!dependencies.isEmpty()) {
      myDependencyTable.changeSelection(0, 0, false, false);
    }

    myRepositorySearches = Lists.newArrayList(new MavenCentralRepositorySearch(), new AndroidSdkRepositorySearch(myModel));
    myDependenciesPanels = Lists.newArrayList(new LibrariesPanel(this, myRepositorySearches), new ModulesPanel(), new FilesPanel());

    myDependenciesTreePanel = new DependenciesTreePanel(this);

    // This splitter is the main horizontal splitter. Top component is the "Dependencies" table and the bottom component is the
    // "Library Search"/"Popular Libraries" panel.
    myMainHorizontalSplitter = new JBSplitter(true, "psd.dependencies.main.horizonal.splitter.proportion", .55f);

    // This splitter separates the "Module" tree view from the rest of dependency-related views.
    myMainVerticalSplitter = new JBSplitter(false, "psi.dependencies.main.vertical.splitter.proportion", .75f);

    // This is the panel where users see/edit details of a dependency selected in the "Dependencies" table.
    myEditorPanel = new JPanel(new BorderLayout());
    myEditorPanel.add(new HeaderPanel("Details"), BorderLayout.NORTH);
    myEditorScrollPane = new JBScrollPane();
    myEditorPanel.add(myEditorScrollPane, BorderLayout.CENTER);
    myEditorPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));

    // This is the editor that appears if the "Dependencies" table does not have a single dependency selected.
    myEmptyEditorPanel = new JPanel(new BorderLayout());
    JBLabel emptyText = new JBLabel("Please select a dependency");
    emptyText.setForeground(getInactiveTextColor());
    emptyText.setHorizontalAlignment(SwingConstants.CENTER);
    myEmptyEditorPanel.add(emptyText, BorderLayout.CENTER);

    myArtifactDependencyEditor = new ArtifactDependencyEditor();

    setUpUI();
  }

  private void setUpUI() {
    myDependencyTable.setDragEnabled(false);
    myDependencyTable.setIntercellSpacing(new Dimension(0, 0));
    myDependencyTable.setShowGrid(false);
    myDependencyTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateSelection();
      }
    });

    myDependencyTable.getSelectionModel().setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(createDependenciesEditorPanel(), BorderLayout.CENTER);
    mainPanel.add(createActionsPanel(), BorderLayout.SOUTH);
    mainPanel.setBorder(createEmptyBorder());

    myMainHorizontalSplitter.setFirstComponent(mainPanel);
    myMainHorizontalSplitter.setSecondComponent(getSelectedPanel());

    myMainVerticalSplitter.setFirstComponent(myMainHorizontalSplitter);
    myMainVerticalSplitter.setSecondComponent(myDependenciesTreePanel);

    add(myMainVerticalSplitter, BorderLayout.CENTER);

    updateSelection();
    myDependencyTable.updateColumnSizes();
  }

  private void updateSelection() {
    updateCurrentEditor();
    selectInTreeView();
  }

  void selectInTreeView() {
    Collection<DependencyMergedModel> selection = myDependencyTable.getSelection();
    if (selection.size() == 1) {
      DependencyMergedModel dependency = getFirstItem(selection);
      assert dependency != null;
      myDependenciesTreePanel.select(dependency);
      return;
    }
    myDependenciesTreePanel.clearSelection();
  }

  private void updateCurrentEditor() {
    Collection<DependencyMergedModel> selection = myDependencyTable.getSelection();
    if (selection.size() == 1) {
      DependencyMergedModel dependency = getFirstItem(selection);
      if (dependency instanceof ArtifactDependencyMergedModel) {
        myArtifactDependencyEditor.update((ArtifactDependencyMergedModel)dependency);
        setCurrentEditor(myArtifactDependencyEditor.getPanel());
        return;
      }
    }
    setCurrentEditor(myEmptyEditorPanel);
  }

  @NotNull
  private JPanel createDependenciesEditorPanel() {
    JBSplitter splitter = new OnePixelSplitter(false, "psd.dependencies.editor.splitter.proportion", 0.65f);
    splitter.setFirstComponent(new JBScrollPane(myDependencyTable));
    splitter.setSecondComponent(myEditorPanel);
    setCurrentEditor(myEmptyEditorPanel);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(splitter, BorderLayout.CENTER);
    return panel;
  }

  private void setCurrentEditor(@NotNull JPanel editorPanel) {
    myEditorScrollPane.setViewportView(editorPanel);
  }

  @NotNull
  private JPanel createActionsPanel() {

    JBLabel addLabel = new JBLabel("Add:");
    addLabel.setBorder(createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP |SideBorder.BOTTOM),
                                            createEmptyBorder(0, 3, 0, 3)));

    JPanel actionsPanel = new JPanel(new BorderLayout());
    actionsPanel.add(addLabel, BorderLayout.WEST);

    DefaultActionGroup group = new DefaultActionGroup();
    for (int i = 0; i < myDependenciesPanels.size(); i++) {
      AddDependencyPanel panel = myDependenciesPanels.get(i);
      group.add(new AddDependencyAction(panel.getDescription(), panel.getIcon(), i));
    }
    group.addSeparator();
    group.add(new CheckboxAction("Show Group ID") {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myShowGroupId;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setShowGroupId(state);
      }
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("BOTTOM", group, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT | SideBorder.BOTTOM));
    actionsPanel.add(toolbarComponent, BorderLayout.CENTER);
    return actionsPanel;
  }

  private void setShowGroupId(boolean showGroupId) {
    myShowGroupId = showGroupId;
    Collection<DependencyMergedModel> selection = myDependencyTable.getSelection();
    myDependencyTable.getListTableModel().fireTableDataChanged();
    myDependencyTable.setSelection(selection);
  }

  @NotNull
  private AddDependencyPanel getSelectedPanel() {
    return myDependenciesPanels.get(mySelectedAddDependencyActionIndex);
  }

  void runAction(@NotNull DependencyAction action) {
    try {
      action.run();
    }
    finally {
      myDependencyTable.requestFocusInWindow();
    }
  }

  @NotNull
  ModuleMergedModel getModel() {
    return myModel;
  }

  void addLibraryDependency(@NotNull String coordinate) {

  }

  @Nullable
  ArtifactDependencyMergedModel find(@NotNull Library library) {
    for (DependencyMergedModel dependency : myModel.getDependencies()) {
      if (dependency instanceof ArtifactDependencyMergedModel) {
        ArtifactDependencyMergedModel artifactDependency = (ArtifactDependencyMergedModel)dependency;
        if (artifactDependency.matches(library)) {
          return artifactDependency;
        }
      }
    }
    return null;
  }

  void select(@NotNull DependencyMergedModel dependency) {
    myDependencyTable.setSelection(Collections.singleton(dependency));
  }

  private class DependenciesTableModel extends ListTableModel<DependencyMergedModel> {
    DependenciesTableModel() {
      createAndSetColumnInfos();
    }

    private void createAndSetColumnInfos() {
      ColumnInfo<DependencyMergedModel, String> dependency = new ColumnInfo<DependencyMergedModel, String>("Dependency") {
        @Override
        @Nullable
        public String valueOf(DependencyMergedModel model) {
          if (model instanceof ArtifactDependencyMergedModel && !myShowGroupId) {
            String compactNotation = model.toString();
            GradleCoordinate coordinate = parseCoordinateString(compactNotation);
            if (coordinate != null) {
              return coordinate.getArtifactId() + GRADLE_PATH_SEPARATOR + coordinate.getRevision();
            }
          }
          return model.toString();
        }

        @Override
        @NotNull
        public TableCellRenderer getRenderer(DependencyMergedModel model) {
          return new DependencyCellRenderer(model);
        }

        @Override
        @NotNull
        public String getPreferredStringValue() {
          return "com.android.support:appcompat-v7:23.1.0";
        }
      };

      ColumnInfo<DependencyMergedModel, String> scope = new ColumnInfo<DependencyMergedModel, String>("Scope") {
        @Override
        @Nullable
        public String valueOf(DependencyMergedModel model) {
          return model.getConfigurationName();
        }

        @Override
        @NotNull
        public String getPreferredStringValue() {
          return "flavor1AndroidTestCompile";
        }
      };
      setColumnInfos(new ColumnInfo[]{dependency, scope});
    }
  }

  void registerDisposable(@NotNull Disposable disposable) {
    myDependenciesEditor.registerDisposable(disposable);
  }

  private class DependencyCellRenderer extends DefaultTableCellRenderer {
    @NotNull private final DependencyMergedModel myModel;

    DependencyCellRenderer(@NotNull DependencyMergedModel model) {
      myModel = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      label.setIcon(myModel.getIcon());
      if (!myShowGroupId && myModel instanceof ArtifactDependencyMergedModel) {
        // Show the complete compact notation (including group ID) if the table hides group ID.
        label.setToolTipText(myModel.toString());
      }
      return label;
    }
  }

  private class AddDependencyAction extends ToggleActionButton {
    @NotNull private final JPanel myDependencyPanel;
    private final int myIndex;

    AddDependencyAction(@NotNull String text, @NotNull Icon icon, int index) {
      super(text + " Dependency", icon);
      myDependencyPanel = myDependenciesPanels.get(index);
      myIndex = index;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myIndex == mySelectedAddDependencyActionIndex;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySelectedAddDependencyActionIndex = myIndex;
      myMainHorizontalSplitter.setSecondComponent(myDependencyPanel);
    }
  }

  private static class ModulesPanel extends AddDependencyPanel {
    ModulesPanel() {
      super("Module", Module);
      add(new HeaderPanel("Modules"), BorderLayout.NORTH);
    }
  }

  private static class FilesPanel extends AddDependencyPanel {
    FilesPanel() {
      super("File", Any_type);
      add(new HeaderPanel("Files"), BorderLayout.NORTH);
    }
  }
}


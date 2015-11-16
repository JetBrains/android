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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.configurables.model.ArtifactDependencyMergedModel;
import com.android.tools.idea.gradle.structure.configurables.model.DependencyMergedModel;
import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.android.tools.idea.structure.dialog.HeaderPanel;
import com.google.common.collect.Lists;
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
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.intellij.icons.AllIcons.FileTypes.Any_type;
import static com.intellij.icons.AllIcons.Nodes.Module;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.getHTMLEditorKit;
import static com.intellij.util.ui.UIUtil.getInactiveTextColor;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

class DependenciesPanel extends JPanel {
  @NotNull private final ModuleMergedModel myModel;
  @NotNull private final TableView<DependencyMergedModel> myDependencyTable;

  @NotNull private final JBSplitter myMainSplitter;
  @NotNull private final List<AddDependencyPanel> myDependenciesPanels;

  @NotNull private final JPanel myEditorPanel;
  @NotNull private JPanel myEmptyEditorPanel;
  @NotNull private final JBScrollPane myEditorScrollPane;
  @NotNull private final ArtifactDependencyEditor myArtifactDependencyEditor;

  private boolean myShowGroupId = true;
  private int mySelectedAddDependencyActionIndex;

  private static final List<PopularLibrary> POPULAR_LIBRARIES = Lists.newArrayList();
  static {
    POPULAR_LIBRARIES.add(new PopularLibrary("appcompat-v7", "com.android.support:appcompat-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("cardview-v7", "com.android.support:cardview-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("dagger", "com.google.dagger:dagger:2.0.2"));
    POPULAR_LIBRARIES.add(new PopularLibrary("design", "com.android.support:design:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("guava", "com.google.guava:guava:18.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("gridlayout-v7", "com.android.support:gridlayout-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("gson", "org.immutables:gson:2.1.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("play-services", "com.google.android.gms:play-services:7.8.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("recyclerview-v7", "com.android.support:recyclerview-v7:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("support-annotations", "com.android.support:support-annotations:23.0.0"));
    POPULAR_LIBRARIES.add(new PopularLibrary("support-v13", "com.android.support:support-v13:23.0.0"));
  }

  DependenciesPanel(@NotNull ModuleMergedModel model) {
    super(new BorderLayout());
    myModel = model;
    myDependenciesPanels = Lists.newArrayList(new LibrariesPanel(), new ModulesPanel(), new FilesPanel());
    DependenciesTableModel tableModel = new DependenciesTableModel();
    List<DependencyMergedModel> dependencies = model.getDependencies();
    tableModel.setItems(dependencies);
    myDependencyTable = new TableView<DependencyMergedModel>(tableModel);
    if (!dependencies.isEmpty()) {
      myDependencyTable.changeSelection(0, 0, false, false);
    }

    myMainSplitter = new JBSplitter(true, "psd.dependencies.main.splitter.proportion", .55f);

    myEditorPanel = new JPanel(new BorderLayout());
    myEditorScrollPane = new JBScrollPane(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myEditorPanel.add(new HeaderPanel("Details"), BorderLayout.NORTH);
    myEditorPanel.add(myEditorScrollPane, BorderLayout.CENTER);

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
        updateCurrentEditor();
      }
    });

    myDependencyTable.getSelectionModel().setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(createDependenciesEditorPanel(), BorderLayout.CENTER);
    mainPanel.add(createActionsPanel(), BorderLayout.SOUTH);
    mainPanel.setBorder(createEmptyBorder());

    myMainSplitter.setFirstComponent(mainPanel);
    myMainSplitter.setSecondComponent(getSelectedPanel());
    myMainSplitter.setShowDividerControls(true);
    add(myMainSplitter, BorderLayout.CENTER);

    updateCurrentEditor();
    myDependencyTable.updateColumnSizes();
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
    int borders = SideBorder.TOP | SideBorder.BOTTOM;

    JBLabel addLabel = new JBLabel("Add:");
    addLabel.setBorder(IdeBorderFactory.createBorder(borders));
    addLabel.setBorder(createCompoundBorder(IdeBorderFactory.createBorder(borders), createEmptyBorder(0, 3, 0, 3)));

    JPanel actionsPanel = new JPanel(new BorderLayout());
    actionsPanel.add(addLabel, BorderLayout.WEST);

    DefaultActionGroup group = new DefaultActionGroup();
    for (int i = 0; i < myDependenciesPanels.size(); i++) {
      AddDependencyPanel panel = myDependenciesPanels.get(i);
      group.add(new AddDependencyAction(panel.text, panel.icon, i));
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
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(borders));
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
      myMainSplitter.setSecondComponent(myDependencyPanel);
    }
  }

  private static abstract class AddDependencyPanel extends JPanel {
    @NotNull final String text;
    @NotNull final Icon icon;

    AddDependencyPanel(@NotNull String text, @NotNull Icon icon) {
      super(new BorderLayout());
      this.text = text;
      this.icon = icon;
    }
  }

  private class LibrariesPanel extends AddDependencyPanel {
    LibrariesPanel() {
      super("Library", LIBRARY_ICON);
      JBSplitter splitter = new OnePixelSplitter(false, "psd.dependencies.libraries.splitter.proportion", 0.80f);

      ArtifactRepositorySearch[] searches = {new MavenCentralRepositorySearch(), new AndroidSdkRepositorySearch(myModel)};
      JPanel librarySearchPanel = new JPanel(new BorderLayout());
      librarySearchPanel.add(new HeaderPanel("Library Search"), BorderLayout.NORTH);
      librarySearchPanel.add(new LibrarySearch(DependenciesPanel.this, searches).getPanel(), BorderLayout.CENTER);

      splitter.setFirstComponent(librarySearchPanel);
      splitter.setSecondComponent(new PopularLibrariesPanel());

      add(splitter, BorderLayout.CENTER);
    }
  }

  private class PopularLibrariesPanel extends JPanel {
    PopularLibrariesPanel() {
      super(new BorderLayout());
      JTextPane textPane = new JTextPane();
      textPane.setEditable(false);

      textPane.setEditorKit(getHTMLEditorKit());

      StringBuilder buffer = new StringBuilder(860);
      for (PopularLibrary library : POPULAR_LIBRARIES) {
        buffer.append(String.format("<a href='%1$s'>", library.coordinate)).append(library.name).append("</a><br/>");
      }
      textPane.setText(buffer.toString());
      textPane.setBorder(createEmptyBorder(5, 5, 5, 5));

      textPane.addHyperlinkListener(new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          String coordinate = e.getDescription();
          assert isNotEmpty(coordinate);
          addLibraryDependency(coordinate);
        }
      });

      add(new HeaderPanel("Popular Libraries"), BorderLayout.NORTH);
      add(new JBScrollPane(textPane), BorderLayout.CENTER);
      setBorder(createEmptyBorder());
    }
  }

  private static class PopularLibrary {
    @NotNull final String name;
    @NotNull final String coordinate;

    PopularLibrary(@NotNull String name, @NotNull String coordinate) {
      this.name = name;
      this.coordinate = coordinate;
    }
  }

  private class ModulesPanel extends AddDependencyPanel {
    ModulesPanel() {
      super("Module", Module);
      add(new HeaderPanel("Modules"), BorderLayout.NORTH);
    }
  }

  private class FilesPanel extends AddDependencyPanel {
    FilesPanel() {
      super("File", Any_type);
      add(new HeaderPanel("Files"), BorderLayout.NORTH);
    }
  }
}


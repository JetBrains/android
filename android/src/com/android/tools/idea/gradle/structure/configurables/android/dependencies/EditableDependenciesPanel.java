/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.editor.DependencyEditor;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.editor.LibraryDependencyEditor;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.structure.dialog.Header;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.getInactiveTextColor;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class EditableDependenciesPanel extends JPanel implements DependencySelection, Disposable {
  @NotNull private final PsAndroidModule myModule;
  @NotNull private final EditableDependenciesTableModel myDependenciesTableModel;
  @NotNull private final TableView<PsAndroidDependency> myDependenciesTable;
  @NotNull private final ListSelectionListener myTableSelectionListener;
  @NotNull private final JScrollPane myEditorScrollPane;
  @NotNull private final EmptyEditorPanel myEmptyEditorPanel;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();
  @NotNull private final Map<Class<?>, DependencyEditor> myEditors = Maps.newHashMap();

  private List<AbstractPopupAction> myPopupActions;

  EditableDependenciesPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super(new BorderLayout());
    myModule = module;

    myDependenciesTableModel = new EditableDependenciesTableModel(myModule);
    myDependenciesTable = new TableView<PsAndroidDependency>(myDependenciesTableModel);

    initializeEditors(context);
    myEmptyEditorPanel = new EmptyEditorPanel();
    myEditorScrollPane = createScrollPane(myEmptyEditorPanel);
    myEditorScrollPane.setBorder(createEmptyBorder());

    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    tableSelectionModel.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    if (!myDependenciesTable.getItems().isEmpty()) {
      myDependenciesTable.changeSelection(0, 0, false, false);
      updateEditor();
    }
    myTableSelectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        PsAndroidDependency selected = getSelection();
        if (selected != null) {
          for (SelectionListener listener : mySelectionListeners) {
            listener.dependencyModelSelected(selected);
          }
        }
        updateEditor();
      }
    };
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);

    myDependenciesTable.setDragEnabled(false);
    myDependenciesTable.setIntercellSpacing(new Dimension(0, 0));
    myDependenciesTable.setShowGrid(false);

    Header header = new Header("Declared Dependencies");
    add(header, BorderLayout.NORTH);

    OnePixelSplitter splitter = new OnePixelSplitter(true, "psd.editable.dependencies.main.horizontal.splitter.proportion", 0.75f);

    JPanel contents = new JPanel(new BorderLayout());
    contents.add(createActionsPanel(), BorderLayout.NORTH);
    JScrollPane scrollPane = createScrollPane(myDependenciesTable);
    scrollPane.setBorder(createEmptyBorder());
    contents.add(scrollPane, BorderLayout.CENTER);

    splitter.setFirstComponent(contents);
    splitter.setSecondComponent(myEditorScrollPane);

    add(splitter, BorderLayout.CENTER);

    updateTableColumnSizes();
  }

  private void updateEditor() {
    Collection<PsAndroidDependency> selection = myDependenciesTable.getSelection();
    if (selection.size() == 1) {
      PsAndroidDependency selected = getFirstItem(selection);
      assert selected != null;
      DependencyEditor editor = myEditors.get(selected.getClass());
      if (editor != null) {
        myEditorScrollPane.setViewportView(editor.getPanel());
        //noinspection unchecked
        editor.display(selected);
        return;
      }
    }
    myEditorScrollPane.setViewportView(myEmptyEditorPanel);
  }

  @NotNull
  private JPanel createActionsPanel() {
    final JPanel actionsPanel = new JPanel(new BorderLayout());

    DefaultActionGroup actions = new DefaultActionGroup();

    AnAction addDependencyAction = new DumbAwareAction("Add Dependency", "", IconUtil.getAddIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        initPopupActions();
        JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<AbstractPopupAction>(null, myPopupActions) {
          @Override
          public Icon getIconFor(AbstractPopupAction action) {
            return action.icon;
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(final AbstractPopupAction action, boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                action.execute();
              }
            });
          }

          @Override
          @NotNull
          public String getTextFor(AbstractPopupAction action) {
            return "&" + action.index + "  " + action.text;
          }
        });
        popup.show(new RelativePoint(actionsPanel, new Point(0, actionsPanel.getHeight() - 1)));
      }
    };

    actions.add(addDependencyAction);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", actions, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    actionsPanel.add(toolbarComponent, BorderLayout.CENTER);

    return actionsPanel;
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      List<AbstractPopupAction> actions = Lists.newArrayList();
      actions.add(new AddDependencyAction());
      myPopupActions = actions;
    }
  }

  private void initializeEditors(@NotNull PsContext context) {
    addEditor(new LibraryDependencyEditor(context));
  }

  private void addEditor(@NotNull DependencyEditor<?> editor) {
    myEditors.put(editor.getSupportedModelType(), editor);
  }

  void updateTableColumnSizes() {
    myDependenciesTable.updateColumnSizes();
  }

  @Override
  public void dispose() {
    mySelectionListeners.clear();
  }

  void add(@NotNull SelectionListener listener) {
    PsAndroidDependency selected = getSelection();
    if (selected != null) {
      listener.dependencyModelSelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Override
  @Nullable
  public PsAndroidDependency getSelection() {
    Collection<PsAndroidDependency> selection = myDependenciesTable.getSelection();
    if (selection.size() == 1) {
      PsAndroidDependency selected = getFirstItem(selection);
      assert selected != null;
      return selected;
    }
    return null;
  }

  @Override
  public void setSelection(@Nullable PsAndroidDependency selection) {
    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    // Remove ListSelectionListener. We only want the selection event when the user selects a table cell directly. If we got here is
    // because the user selected a dependency in the "Variants" tree view, and we are simply syncing the table.
    tableSelectionModel.removeListSelectionListener(myTableSelectionListener);

    if (selection == null) {
      myDependenciesTable.clearSelection();
    }
    else {
      myDependenciesTable.setSelection(Collections.singleton(selection));
    }
    updateEditor();

    // Add ListSelectionListener again, to react when user selects a table cell directly.
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);
  }

  public interface SelectionListener {
    void dependencyModelSelected(@NotNull PsAndroidDependency dependency);
  }

  private class AddDependencyAction extends AbstractPopupAction {
    AddDependencyAction() {
      super("Artifact Dependency", LIBRARY_ICON, 1);
    }

    @Override
    void execute() {
      AddArtifactDependencyDialog dialog = new AddArtifactDependencyDialog(myModule);
      dialog.showAndGet();
    }
  }

  private static abstract class AbstractPopupAction implements ActionListener {
    @NotNull final String text;
    @NotNull final Icon icon;

    final int index;

    AbstractPopupAction(@NotNull String text, @NotNull Icon icon, int index) {
      this.text = text;
      this.icon = icon;
      this.index = index;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      execute();
    }

    abstract void execute();
  }

  private static class EmptyEditorPanel extends JPanel {
    EmptyEditorPanel() {
      super(new BorderLayout());
      JBLabel emptyText = new JBLabel("Please select a declared dependency");
      emptyText.setForeground(getInactiveTextColor());
      emptyText.setHorizontalAlignment(SwingConstants.CENTER);
      add(emptyText, BorderLayout.CENTER);
    }
  }
}

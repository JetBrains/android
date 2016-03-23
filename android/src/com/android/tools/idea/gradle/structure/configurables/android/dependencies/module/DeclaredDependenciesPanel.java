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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDeclaredDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Lists;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class DeclaredDependenciesPanel extends AbstractDeclaredDependenciesPanel implements DependencySelection {
  @NotNull private final DeclaredDependenciesTableModel myDependenciesTableModel;
  @NotNull private final TableView<PsAndroidDependency> myDependenciesTable;
  @NotNull private final ListSelectionListener myTableSelectionListener;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  DeclaredDependenciesPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super("Declared Dependencies", context, module.getParent(), module);

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);

    myDependenciesTableModel = new DeclaredDependenciesTableModel(module);
    myDependenciesTable = new TableView<PsAndroidDependency>(myDependenciesTableModel);

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
            listener.dependencySelected(selected);
          }
        }
        updateEditor();
      }
    };
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);

    myDependenciesTable.setDragEnabled(false);
    myDependenciesTable.setIntercellSpacing(new Dimension(0, 0));
    myDependenciesTable.setShowGrid(false);

    JScrollPane scrollPane = createScrollPane(myDependenciesTable);
    scrollPane.setBorder(createEmptyBorder());
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    updateTableColumnSizes();
  }

  private void updateEditor() {
    Collection<PsAndroidDependency> selection = myDependenciesTable.getSelection();
    PsAndroidDependency selected = null;
    if (selection.size() == 1) {
      selected = getFirstItem(selection);
    }
    updateEditor(selected);
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
      listener.dependencySelected(selected);
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
    void dependencySelected(@NotNull PsAndroidDependency dependency);
  }
}

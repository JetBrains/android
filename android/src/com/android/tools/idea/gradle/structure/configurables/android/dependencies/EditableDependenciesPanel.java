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

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.IdeBorderFactory;
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

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class EditableDependenciesPanel extends JPanel implements Disposable {
  @NotNull private final PsdAndroidModuleModel myModuleModel;
  @NotNull private final TableView<PsdAndroidDependencyModel> myDependencyTable;
  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  EditableDependenciesPanel(@NotNull PsdAndroidModuleModel moduleModel) {
    super(new BorderLayout());
    myModuleModel = moduleModel;

    List<PsdAndroidDependencyModel> dependencies = myModuleModel.getDeclaredDependencies();
    EditableDependenciesTableModel tableModel = new EditableDependenciesTableModel(dependencies);
    myDependencyTable = new TableView<PsdAndroidDependencyModel>(tableModel);

    setUpUI();
  }

  private void setUpUI() {
    myDependencyTable.setDragEnabled(false);
    myDependencyTable.setIntercellSpacing(new Dimension(0, 0));
    myDependencyTable.setShowGrid(false);

    ListSelectionModel tableSelectionModel = myDependencyTable.getSelectionModel();
    tableSelectionModel.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          // This is a selection triggered by an user action.
          PsdAndroidDependencyModel selected = getSingleSelection();
          if (selected != null) {
            for (SelectionListener listener : mySelectionListeners) {
              listener.dependencyModelSelected(selected);
            }
          }
        }
      }
    });
    tableSelectionModel.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);
    if (!myDependencyTable.getItems().isEmpty()) {
      myDependencyTable.changeSelection(0, 0, false, false);
    }

    JScrollPane scrollPane = createScrollPane(myDependencyTable);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    updateTableColumnSizes();
  }

  void updateTableColumnSizes() {
    myDependencyTable.updateColumnSizes();
  }

  @Override
  public void dispose() {
    mySelectionListeners.clear();
  }

  void add(@NotNull SelectionListener listener) {
    PsdAndroidDependencyModel selected = getSingleSelection();
    if (selected != null) {
      listener.dependencyModelSelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Nullable
  private PsdAndroidDependencyModel getSingleSelection() {
    Collection<PsdAndroidDependencyModel> selection = myDependencyTable.getSelection();
    if (selection.size() == 1) {
      PsdAndroidDependencyModel selected = getFirstItem(selection);
      assert selected != null;
      return selected;
    }
    return null;
  }

  void select(@NotNull PsdAndroidDependencyModel model) {
    myDependencyTable.setSelection(Collections.singleton(model));
  }

  public interface SelectionListener {
    void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model);
  }
}

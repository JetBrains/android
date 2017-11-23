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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.DependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.SingleLibraryDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.DeclaredDependenciesTableView;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class DeclaredDependenciesPanel extends AbstractDependenciesPanel implements DependencySelection {
  @NotNull private final PsContext myContext;

  @NotNull private final DeclaredDependenciesTableModel myDependenciesTableModel;
  @NotNull private final DeclaredDependenciesTableView<PsAndroidDependency> myDependenciesTable;
  @NotNull private final String myPlaceName;

  @NotNull private final SelectionChangeEventDispatcher<PsAndroidDependency> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  private boolean mySkipSelectionChangeNotification;

  DeclaredDependenciesPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super("Declared Dependencies", context, module);

    myContext = context;
    myContext.getAnalyzerDaemon().add(model -> {
      if (model == module) {
        invokeLaterIfNeeded(this::updateDetailsAndIssues);
      }
    }, this);

    myPlaceName = createPlaceName(module.getName());

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);
    initializeDependencyDetails();

    setIssuesViewer(new IssuesViewer(myContext, new SingleModuleIssuesRenderer()));

    myDependenciesTableModel = new DeclaredDependenciesTableModel(module, myContext);
    myDependenciesTable = new DeclaredDependenciesTableView<>(myDependenciesTableModel, myContext);

    module.add(event -> {
      myDependenciesTableModel.reset();
      PsAndroidDependency toSelect = null;
      if (event instanceof PsModule.LibraryDependencyAddedEvent) {
        myDependenciesTable.clearSelection();
        PsArtifactDependencySpec spec = ((PsModule.LibraryDependencyAddedEvent)event).getSpec();
        toSelect = myDependenciesTableModel.findDependency(spec);
      }
      else if (event instanceof PsModule.DependencyModifiedEvent) {
        PsDependency dependency = ((PsModule.DependencyModifiedEvent)event).getDependency();
        if (dependency instanceof PsAndroidDependency) {
          toSelect = (PsAndroidDependency)dependency;
        }
      }
      if (toSelect != null) {
        myDependenciesTable.setSelection(Collections.singletonList(toSelect));
      }
    }, this);

    ListSelectionListener tableSelectionListener = e -> updateDetailsAndIssues();
    myDependenciesTable.getSelectionModel().addListSelectionListener(tableSelectionListener);
    myDependenciesTable.selectFirstRow();

    JScrollPane scrollPane = createScrollPane(myDependenciesTable);
    scrollPane.setBorder(JBUI.Borders.empty());
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    updateTableColumnSizes();
  }

  @NotNull
  private static String createPlaceName(@NotNull String moduleName) {
    return "dependencies." + moduleName + ".place";
  }

  private void initializeDependencyDetails() {
    addDetails(new SingleLibraryDependencyDetails());
    addDetails(new ModuleDependencyDetails(getContext(), true));
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myDependenciesTable;
  }

  @Override
  @NotNull
  public String getPlaceName() {
    return myPlaceName;
  }

  @Override
  @NotNull
  protected List<AnAction> getExtraToolbarActions() {
    List<AnAction> actions = Lists.newArrayList();
    actions.add(new EditDependencyAction());
    return actions;
  }

  void updateTableColumnSizes() {
    myDependenciesTable.updateColumnSizes();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDependenciesTable);
  }

  void add(@NotNull SelectionChangeListener<PsAndroidDependency> listener) {
    myEventDispatcher.addListener(listener, this);
    notifySelectionChanged();
  }

  @Override
  @Nullable
  public PsAndroidDependency getSelection() {
    return myDependenciesTable.getSelectionIfSingle();
  }

  @Override
  public void setSelection(@Nullable PsAndroidDependency selection) {
    mySkipSelectionChangeNotification = true;
    if (selection == null) {
      myDependenciesTable.clearSelection();
    }
    else {
      myDependenciesTable.setSelection(Collections.singleton(selection));
    }
    updateDetailsAndIssues();
    mySkipSelectionChangeNotification = false;
  }

  private void updateDetailsAndIssues() {
    if (!mySkipSelectionChangeNotification) {
      notifySelectionChanged();
    }

    PsAndroidDependency selected = getSelection();
    super.updateDetails(selected);
    updateIssues(selected);

    History history = getHistory();
    if (history != null) {
      history.pushQueryPlace();
    }
  }

  private void notifySelectionChanged() {
    PsAndroidDependency selected = getSelection();
    if (selected != null) {
      myEventDispatcher.selectionChanged(selected);
    }
  }

  private void updateIssues(@Nullable PsAndroidDependency selected) {
    List<PsIssue> issues = Collections.emptyList();
    if (selected != null) {
      issues = myContext.getAnalyzerDaemon().getIssues().findIssues(selected, null);
    }
    displayIssues(issues);
  }

  @Override
  public void selectDependency(@Nullable String dependency) {
    if (isEmpty(dependency)) {
      myDependenciesTable.requestFocusInWindow();
      myDependenciesTable.clearSelection();
      return;
    }
    doSelectDependency(dependency);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place != null) {
      Object path = place.getPath(myPlaceName);
      if (path instanceof String) {
        String pathText = (String)path;
        if (!pathText.isEmpty()) {
          doSelectDependency(pathText);
        }
      }
    }
    return ActionCallback.DONE;
  }

  private void doSelectDependency(@NotNull String toSelect) {
    myDependenciesTable.selectDependency(toSelect);
  }

  private class EditDependencyAction extends DumbAwareAction {
    EditDependencyAction() {
      super("Edit Dependency...", "", AllIcons.Actions.Edit);
      registerCustomShortcutSet(CommonShortcuts.ENTER, myDependenciesTable);
    }

    @Override
    public void update(AnActionEvent e) {
      DependencyDetails details = getCurrentDependencyDetails();
      e.getPresentation().setEnabled(details != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
  }
}

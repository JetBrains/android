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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.DependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleLibraryDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.isMetaOrCtrlKeyPressed;
import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.FOR_NAVIGATION;
import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.awt.Cursor.*;
import static java.awt.event.KeyEvent.KEY_PRESSED;
import static java.awt.event.KeyEvent.KEY_RELEASED;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
import static javax.swing.SwingUtilities.convertPointFromScreen;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class DeclaredDependenciesPanel extends AbstractDependenciesPanel {
  @NotNull private final PsContext myContext;

  @NotNull private final DeclaredDependenciesTableModel myDependenciesTableModel;
  @NotNull private final TableView<PsAndroidDependency> myDependenciesTable;
  @NotNull private final String myPlaceName;

  @NotNull private final SelectionChangeEventDispatcher<PsAndroidDependency> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  private KeyEventDispatcher myKeyEventDispatcher;

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
    myDependenciesTable = new TableView<PsAndroidDependency>(myDependenciesTableModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          PsModuleDependency dependency = getIfHyperlink(e);
          if (dependency != null) {
            String name = dependency.getName();
            myContext.setSelectedModule(name, DeclaredDependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    module.add(event -> {
      myDependenciesTableModel.reset();
      PsAndroidDependency toSelect = null;
      if (event instanceof PsModule.LibraryDependencyAddedEvent) {
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

    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    tableSelectionModel.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    ListSelectionListener tableSelectionListener = e -> updateDetailsAndIssues();
    tableSelectionModel.addListSelectionListener(tableSelectionListener);

    if (!myDependenciesTable.getItems().isEmpty()) {
      myDependenciesTable.changeSelection(0, 0, false, false);
    }

    addHyperlinkFunctionality();

    myDependenciesTable.setDragEnabled(false);
    myDependenciesTable.setIntercellSpacing(new Dimension(0, 0));
    myDependenciesTable.setShowGrid(false);

    JScrollPane scrollPane = createScrollPane(myDependenciesTable);
    scrollPane.setBorder(createEmptyBorder());
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    updateTableColumnSizes();
  }

  @NotNull
  private static String createPlaceName(@NotNull String moduleName) {
    return "dependencies." + moduleName + ".place";
  }

  private void initializeDependencyDetails() {
    addDetails(new ModuleLibraryDependencyDetails());
    addDetails(new ModuleDependencyDetails(getContext(), true));
  }

  private void addHyperlinkFunctionality() {
    myDependenciesTable.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        PsModuleDependency hovered = getIfHyperlink(e);
        setHoveredDependency(hovered);
      }
    });

    myKeyEventDispatcher = e -> {
      PsModuleDependency dependency = null;
      if (e.getID() == KEY_PRESSED) {
        if (isMetaOrCtrlKeyPressed(e)) {
          dependency = getDependencyUnderMousePointer();
        }
        setHoveredDependency(dependency);
      }
      else if (e.getID() == KEY_RELEASED) {
        if (isMetaOrCtrlKeyPressed(e)) {
          setHoveredDependency(null);
        }
      }
      return false;
    };

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
  }

  @Nullable
  private PsModuleDependency getIfHyperlink(@NotNull MouseEvent e) {
    if (isMetaOrCtrlKeyPressed(e)) {
      Point point = new Point(e.getX(), e.getY());
      return getDependencyForLocation(point);
    }
    return null;
  }

  private PsModuleDependency getDependencyUnderMousePointer() {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo != null) {
      Point location = pointerInfo.getLocation();
      convertPointFromScreen(location, myDependenciesTable);
      return getDependencyForLocation(location);
    }
    return null;
  }

  @Nullable
  private PsModuleDependency getDependencyForLocation(Point location) {
    int column = myDependenciesTable.columnAtPoint(location);
    if (column == 0) {
      // "Dependency" column
      int row = myDependenciesTable.rowAtPoint(location);
      if (row > -1) {
        PsAndroidDependency dependency = myDependenciesTableModel.getItem(row);
        if (dependency instanceof PsModuleDependency) {
          return (PsModuleDependency)dependency;
        }
      }
    }
    return null;
  }

  private void setHoveredDependency(@Nullable PsModuleDependency dependency) {
    myDependenciesTableModel.setHoveredDependency(dependency);
    Cursor cursor = getDefaultCursor();
    if (dependency != null) {
      cursor = getPredefinedCursor(HAND_CURSOR);
    }
    myDependenciesTable.setCursor(cursor);
    myDependenciesTable.repaint();
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myDependenciesTable;
  }

  @Override
  protected void beforeAddingDependency() {
    myDependenciesTable.clearSelection();
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
    if (myKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
    }
  }

  void add(@NotNull SelectionChangeListener<PsAndroidDependency> listener) {
    myEventDispatcher.addListener(listener, this);
    notifySelectionChanged();
  }

  @Nullable
  private PsAndroidDependency getSelection() {
    Collection<PsAndroidDependency> selection = myDependenciesTable.getSelection();
    if (selection.size() == 1) {
      PsAndroidDependency selected = getFirstItem(selection);
      assert selected != null;
      return selected;
    }
    return null;
  }

  private void updateDetailsAndIssues() {
    notifySelectionChanged();

    PsAndroidDependency selected = getSelection();
    super.updateDetails(selected);
    updateIssues(selected);

    History history = getHistory();
    if (history != null) {
      history.pushQueryPlace();
    }
  }

  @Override
  public void notifySelectionChanged() {
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
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place != null) {
      Object path = place.getPath(myPlaceName);
      if (path instanceof String) {
        String pathText = (String)path;
        myDependenciesTable.requestFocusInWindow();
        if (!pathText.isEmpty()) {
          for (PsAndroidDependency dependency : myDependenciesTable.getItems()) {
            String dependencyAsText = dependency.toText(FOR_NAVIGATION);
            if (pathText.equals(dependencyAsText)) {
              myDependenciesTable.setSelection(Collections.singletonList(dependency));
              break;
            }
          }
        }
      }
    }
    return ActionCallback.DONE;
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

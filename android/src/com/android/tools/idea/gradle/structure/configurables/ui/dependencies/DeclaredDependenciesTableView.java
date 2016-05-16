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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleAndroidDependency;
import com.intellij.openapi.Disposable;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.isMetaOrCtrlKeyPressed;
import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.FOR_NAVIGATION;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.awt.Cursor.*;
import static java.awt.event.KeyEvent.KEY_PRESSED;
import static java.awt.event.KeyEvent.KEY_RELEASED;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
import static javax.swing.SwingUtilities.convertPointFromScreen;

public class DeclaredDependenciesTableView<T extends PsDependency> extends TableView<T> implements Disposable {
  @NotNull private final PsContext myContext;

  private KeyEventDispatcher myKeyEventDispatcher;

  public DeclaredDependenciesTableView(@NotNull AbstractDeclaredDependenciesTableModel<T> model, @NotNull PsContext context) {
    super(model);
    myContext = context;

    getSelectionModel().setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    addHyperlinkFunctionality();

    setDragEnabled(false);
    setIntercellSpacing(new Dimension(0, 0));
    setShowGrid(false);
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    int id = e.getID();
    if (id == MOUSE_PRESSED) {
      PsModuleDependency dependency = getIfHyperlink(e);
      if (dependency != null) {
        String name = dependency.getName();
        myContext.setSelectedModule(name, this);
        // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
        return;
      }
    }
    super.processMouseEvent(e);
  }

  @Nullable
  private PsModuleDependency getIfHyperlink(@NotNull MouseEvent e) {
    if (isMetaOrCtrlKeyPressed(e)) {
      Point point = new Point(e.getX(), e.getY());
      return getDependencyForLocation(point);
    }
    return null;
  }

  private void addHyperlinkFunctionality() {
    addMouseMotionListener(new MouseAdapter() {
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
  private PsModuleDependency getDependencyUnderMousePointer() {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo != null) {
      Point location = pointerInfo.getLocation();
      convertPointFromScreen(location, this);
      return getDependencyForLocation(location);
    }
    return null;
  }

  @Nullable
  private PsModuleAndroidDependency getDependencyForLocation(@NotNull Point location) {
    int column = columnAtPoint(location);
    if (column == 0) {
      // "Dependency" column
      int row = rowAtPoint(location);
      if (row > -1) {
        PsDependency dependency = getListTableModel().getItem(row);
        if (dependency instanceof PsModuleAndroidDependency) {
          return (PsModuleAndroidDependency)dependency;
        }
      }
    }
    return null;
  }

  private void setHoveredDependency(@Nullable PsModuleDependency dependency) {
    getListTableModel().setHoveredDependency(dependency);
    Cursor cursor = getDefaultCursor();
    if (dependency != null) {
      cursor = getPredefinedCursor(HAND_CURSOR);
    }
    setCursor(cursor);
    repaint();
  }

  @Override
  public AbstractDeclaredDependenciesTableModel<T> getListTableModel() {
    //noinspection unchecked
    return (AbstractDeclaredDependenciesTableModel)super.getModel();
  }

  public void selectFirstRow() {
    if (!getItems().isEmpty()) {
      changeSelection(0, 0, false, false);
    }
  }

  @Nullable
  public T getSelectionIfSingle() {
    Collection<T> selection = getSelection();
    if (selection.size() == 1) {
      T selected = getFirstItem(selection);
      assert selected != null;
      return selected;
    }
    return null;
  }

  public void selectDependency(@NotNull String toSelect) {
    requestFocusInWindow();
    for (T dependency : getItems()) {
      String dependencyAsText = dependency.toText(FOR_NAVIGATION);
      if (toSelect.equals(dependencyAsText)) {
        setSelection(Collections.singletonList(dependency));
        break;
      }
    }
  }

  @Override
  public void dispose() {
    if (myKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
    }
  }
}

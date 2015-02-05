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
package com.android.tools.idea.gradle.editor.ui;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.metadata.StdGradleEditorEntityMetaData;
import com.intellij.application.options.codeStyle.arrangement.util.IntObjectMap;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GradleEditorEntityTable extends JBTable implements DataProvider {

  private final IntObjectMap<GradleEditorCellComponent> myRendererComponents = new IntObjectMap<GradleEditorCellComponent>();
  private final IntObjectMap<GradleEditorCellComponent> myEditorComponents = new IntObjectMap<GradleEditorCellComponent>();

  @NotNull private final Project myProject;

  private int myEditingRow = -1;
  private int myRowUnderMouse = -1;

  public GradleEditorEntityTable(@NotNull Project project) {
    super(new GradleEditorEntityTableModel());
    myProject = project;
    setShowColumns(false);
    setShowGrid(false);
    setRowHeight(32);
    setDefaultRenderer(Object.class, new MyRenderer());
    getColumnModel().getColumn(0).setCellEditor(new MyEditor());
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        onMouseMoved(e);
      }
    });
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        onMouseExited();
      }
    });
    getModel().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) { onTableChange(e); }
    });
  }

  @Override
  public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
  }

  @NotNull
  @Override
  public GradleEditorEntityTableModel getModel() {
    return (GradleEditorEntityTableModel)super.getModel();
  }

  /**
   * Allows to ask for a row where given component was used (if any).
   *
   * @param component  target component
   * @return           a row where given component was used (if any);
   *                   negative value as an indication that no row for the given component has been found
   */
  public int getRowByComponent(@NotNull GradleEditorCellComponent component) {
    for (int i = 0, max = getModel().getRowCount(); i < max; i++) {
      if (myRendererComponents.get(i) == component || myEditorComponents.get(i) == component) {
        return i;
      }
    }
    return -1;
  }

  private void onTableChange(@NotNull TableModelEvent e) {
    final int signum;
    switch (e.getType()) {
      case TableModelEvent.INSERT:
        signum = 1;
        break;
      case TableModelEvent.DELETE:
        signum = -1;
        for (int i = e.getLastRow(); i >= e.getFirstRow(); i--) {
          myRendererComponents.remove(i);
          myEditorComponents.remove(i);
        }
        break;
      default:
        return;
    }
    int shift = Math.abs(e.getFirstRow() - e.getLastRow() + 1) * signum;
    myRendererComponents.shiftKeys(e.getFirstRow(), shift);
    myEditorComponents.shiftKeys(e.getFirstRow(), shift);
    if (myRowUnderMouse >= e.getFirstRow()) {
      myRowUnderMouse = -1;
    }
    if (getModel().getRowCount() > 0) {
      repaintRows(0, getModel().getRowCount() - 1, false);
    }
  }

  private void onMouseMoved(@NotNull MouseEvent e) {
    int i = rowAtPoint(e.getPoint());
    if (i != myRowUnderMouse) {
      onMouseExited();
    }

    if (i < 0) {
      return;
    }

    if (i != myRowUnderMouse) {
      onMouseEntered(e);
    }

    GradleEditorCellComponent component = isEditing() ? myEditorComponents.get(i) : myRendererComponents.get(i);
    if (component == null) {
      return;
    }

    Rectangle rectangle = component.onMouseMove(e);
    if (rectangle != null) {
      repaintScreenBounds(rectangle);
    }
  }

  private void onMouseExited() {
    if (myRowUnderMouse < 0) {
      return;
    }

    GradleEditorCellComponent c = isEditing() ? myEditorComponents.get(myRowUnderMouse) : myRendererComponents.get(myRowUnderMouse);
    if (c != null) {
      c.onMouseExited();
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
    myRowUnderMouse = -1;
  }

  private void onMouseEntered(@NotNull MouseEvent e) {
    myRowUnderMouse = rowAtPoint(e.getPoint());
    GradleEditorCellComponent c = isEditing() ? myEditorComponents.get(myRowUnderMouse) : myRendererComponents.get(myRowUnderMouse);
    if (c != null) {
      c.onMouseEntered(e);
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
  }

  private void repaintScreenBounds(@NotNull Rectangle bounds) {
    Point location = bounds.getLocation();
    SwingUtilities.convertPointFromScreen(location, this);
    int x = location.x;
    int width = bounds.width;
    repaint(x, location.y, width, bounds.height);
  }

  public void repaintRows(int first, int last, boolean rowStructureChanged) {
    for (int i = first; i <= last; i++) {
      if (rowStructureChanged) {
        myRendererComponents.remove(i);
        myEditorComponents.remove(i);
      }
    }
    getModel().fireTableRowsUpdated(first, last);
  }

  @Override
  public void removeEditor() {
    super.removeEditor();
    GradleEditorCellComponent component = myRendererComponents.get(myEditingRow);
    if (component != null) {
      component.onMouseExited(); // Hide tool bar.
      component.getValue(myProject); // Flush changes to the underlying gradle configuration.
    }
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (GradleEditorUiConstants.ACTIVE_ENTITY_KEY.is(dataId)) {
      if (isEditing()) {
        int row = getEditingRow();
        if (row >= 0 && row < getRowCount()) {
          Object value = getValueAt(row, 0);
          return value instanceof GradleEditorEntity ? value : null;
        }
      }
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    return null;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    Object value = getValueAt(row, column);
    if (!(value instanceof GradleEditorEntity)) {
      return false;
    }
    GradleEditorEntity entity = (GradleEditorEntity)value;
    return !entity.getMetaData().contains(StdGradleEditorEntityMetaData.READ_ONLY)
           && ServiceManager.getService(GradleEditorEntityUiRegistry.class).hasEntityUi(entity);
  }

  private class MyRenderer implements TableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      GradleEditorCellComponent component = myRendererComponents.get(row);
      if (component == null) {
        myRendererComponents.set(row, component = new GradleEditorCellComponentImpl(GradleEditorEntityTable.this));
      }
      return component.bind(table, value, myProject, row, column, false, isSelected, hasFocus);
    }
  }

  private class MyEditor extends AbstractTableCellEditor {

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myEditingRow = row;
      GradleEditorCellComponent component = myEditorComponents.get(row);
      if (component == null) {
        myEditorComponents.set(row, component = new GradleEditorCellComponentImpl(GradleEditorEntityTable.this));
      }
      return component.bind(table, value, myProject, row, column, true, isSelected, true);
    }

    @Nullable
    @Override
    public Object getCellEditorValue() {
      GradleEditorCellComponent component = myEditorComponents.get(myEditingRow);
      return component == null ? null : component.getValue(myProject);
    }
  }
}

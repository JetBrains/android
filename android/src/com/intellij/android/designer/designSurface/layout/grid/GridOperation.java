/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface.layout.grid;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.InsertFeedback;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.GridInsertType;
import com.intellij.android.designer.model.grid.IGridProvider;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class GridOperation extends AbstractEditOperation {
  private GridFeedback myFeedback;
  private InsertFeedback myInsertFeedback;
  private TextFeedback myTextFeedback;
  private Rectangle myBounds;
  protected int myColumn;
  protected int myRow;
  protected GridInsertType myInsertType;
  protected boolean myHasCellComponents;

  public GridOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  protected final GridInfo getGridInfo() {
    return ((IGridProvider)myContainer).getVirtualGridInfo();
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myInsertFeedback = new InsertFeedback(DrawingStyle.DROP_ZONE_ACTIVE);
      layer.add(myInsertFeedback);

      myBounds = myContainer.getBounds(layer);

      myFeedback = new GridFeedback();
      myFeedback.setBounds(myBounds);
      layer.add(myFeedback);

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myTextFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();
    calculateGridInfo();
    configureTextFeedback();
    myFeedback.repaint();
  }

  private void configureTextFeedback() {
    myTextFeedback.clear();

    int row = myRow;
    int column = myColumn;

    myTextFeedback.append("[");

    if (myInsertType == GridInsertType.before_h_cell) {
      myTextFeedback.append("before ");
    }
    else if (myInsertType == GridInsertType.after_h_cell) {
      myTextFeedback.append("after ");
    }
    else if (myInsertType != GridInsertType.in_cell) {
      myTextFeedback.append("insert: ");

      if (myInsertType == GridInsertType.corner_top_right) {
        column++;
      }
      else if (myInsertType == GridInsertType.corner_bottom_left) {
        row++;
      }
      else if (myInsertType == GridInsertType.corner_bottom_right) {
        row++;
        column++;
      }
    }

    myTextFeedback.append("row ");
    myTextFeedback.bold(Integer.toString(row));
    myTextFeedback.append(", ");

    if (myInsertType == GridInsertType.before_v_cell) {
      myTextFeedback.append("before ");
    }
    else if (myInsertType == GridInsertType.after_v_cell) {
      myTextFeedback.append("after ");
    }

    myTextFeedback.append("column ");
    myTextFeedback.bold(Integer.toString(column));
    myTextFeedback.append("]");

    myTextFeedback.centerTop(myBounds);
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myInsertFeedback);
      layer.remove(myFeedback);
      layer.remove(myTextFeedback);
      layer.repaint();
      myFeedback = null;
      myInsertFeedback = null;
      myTextFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    return myComponents.size() == 1 && (myInsertType != GridInsertType.in_cell || !myHasCellComponents);
  }

  @Override
  public abstract void execute() throws Exception;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Grid
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static final int CROSS_SIZE = 10;

  /**
   * Updates the GridOperation target row and column from the given location.
   *
   * @param location The pointer location in the {@link GridInfo#grid} coordinate system.
   * @param gridInfo The {@link GridInfo} instance containing the information about the grid component.
   */
  private void updateRowAndColumn(@NotNull Point location, GridInfo gridInfo) {
    RadComponent grid = gridInfo.grid;

    // The vLines and hLines are in model coordinates, convert the location to model coordinates to get the cell position.
    Point modelLocation = grid.toModel(myContext.getArea().getNativeComponent(), location);
    modelLocation.x -= grid.getBounds().x;
    modelLocation.y -= grid.getBounds().y;
    myColumn = getLineIndex(gridInfo.vLines, modelLocation.x);
    myRow  = getLineIndex(gridInfo.hLines, modelLocation.y);
  }

  /**
   * Calculates the insert position based on the cell position and the relative location to other existing components.
   *
   * <p>All calculations in this method are done on the target component coordinate system.
   */
  private void calculateGridInfo() {
    GridInfo gridInfo = getGridInfo();
    Point location = myContext.getLocation();

    updateRowAndColumn(location, gridInfo);

    Rectangle bounds = gridInfo.grid.fromModel(myContext.getArea().getNativeComponent(), gridInfo.grid.getBounds());
    location.x -= bounds.x;
    location.y -= bounds.y;

    myInsertType = GridInsertType.in_cell; // Set a default insert type.
    if (gridInfo.components == null) {
      // There are no existing components in the grid, since there is no component to calculate the relative position to, this is always
      // and in_cell insert.
      myHasCellComponents = false;
      myInsertFeedback.setVisible(false);
      return;
    }

    myHasCellComponents = hasComponents(myRow, myColumn);
    Rectangle cellRect = getInsertRect(myHasCellComponents);
    Rectangle dropCellRect = getInsertInCellRect(cellRect);
    boolean cellExists = cellExists(gridInfo.components, myRow, myColumn);

    if (dropCellRect.contains(location)) {
      // Trying to drop control in the cell. No additional feedback.
      myInsertFeedback.setVisible(false);
      return;
    }

    // Calculate which border the user is trying to drop the control in to provide additional feedback.
    if (location.x <= dropCellRect.x) {
      // At least the X is outside the drop area.
      if (location.y <= dropCellRect.y) {
        // Completely outside the drop area.
        if (cellExists) {
          myInsertType = GridInsertType.corner_top_left;
          myInsertFeedback.cross(myBounds.x + cellRect.x, myBounds.y + cellRect.y, CROSS_SIZE);
        }
      }
      else if (dropCellRect.y < location.y && location.y < dropCellRect.getMaxY()) {
        // Above the drop area.
        if (myHasCellComponents && (myColumn == 0 || hasComponents(myRow, myColumn - 1))) {
          boolean insert = true;

          if (isMoveOperation()) {
            if (myColumn != 0 || getMovedIndex(false) == 0) {
              insert = !isSingleMovedAxis(false);
            }
          }

          if (insert) {
            myInsertType = GridInsertType.before_v_cell;
            cellRect = getInsertRect(false);
            myInsertFeedback.vertical(myBounds.x + cellRect.x, myBounds.y + cellRect.y, cellRect.height);
          }
        }
      }
      else if (cellExists) {
        myInsertType = GridInsertType.corner_bottom_left;
        myInsertFeedback.cross(myBounds.x + cellRect.x, myBounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
      }
    }
    else if (location.x >= dropCellRect.getMaxX()) {
      if (location.y <= dropCellRect.y) {
        if (cellExists) {
          myInsertType = GridInsertType.corner_top_right;
          myInsertFeedback.cross(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y, CROSS_SIZE);
        }
      }
      else if (dropCellRect.y < location.y && location.y < dropCellRect.getMaxY()) {
        if (myHasCellComponents && (myColumn == gridInfo.lastInsertColumn || hasComponents(myRow, myColumn + 1))) {
          if (!isMoveOperation() || !isSingleMovedAxis(false)) {
            myInsertType = GridInsertType.after_v_cell;
            cellRect = getInsertRect(false);
            myInsertFeedback.vertical(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y, cellRect.height);
          }
        }
      }
      else if (cellExists) {
        myInsertType = GridInsertType.corner_bottom_right;
        myInsertFeedback.cross(myBounds.x + cellRect.x + cellRect.width, myBounds.y + cellRect.y + cellRect.height, CROSS_SIZE);
      }
    }
    else if (location.y <= dropCellRect.y) {
      if (myHasCellComponents && (myRow == 0 || hasComponents(myRow - 1, myColumn))) {
        boolean insert = true;

        if (isMoveOperation()) {
          if (myRow != 0 || getMovedIndex(true) == 0) {
            insert = !isSingleMovedAxis(true);
          }
        }

        if (insert) {
          myInsertType = GridInsertType.before_h_cell;
          cellRect = getInsertRect(false);
          myInsertFeedback.horizontal(myBounds.x + cellRect.x, myBounds.y + cellRect.y, cellRect.width);
        }
      }
    }
    else if (location.y >= dropCellRect.getMaxY()) {
      // Below the drop area.
      if (myHasCellComponents && (myRow == gridInfo.lastInsertRow || hasComponents(myRow + 1, myColumn))) {
        if (!isMoveOperation() || !isSingleMovedAxis(true)) {
          myInsertType = GridInsertType.after_h_cell;
          cellRect = getInsertRect(false);
          myInsertFeedback.horizontal(myBounds.x + cellRect.x, myBounds.y + cellRect.y + cellRect.height, cellRect.width);
        }
      }
    }
  }

  protected boolean isMoveOperation() {
    return myContext.isMove();
  }

  private static int getLineIndex(int[] line, int location) {
    for (int i = 0; i < line.length - 1; i++) {
      if (line[i] <= location && location <= line[i + 1]) {
        return i;
      }
    }
    return Math.max(0, line.length - 1);
  }

  protected static boolean rowExists(@Nullable RadComponent[][] components, int row) {
    return components != null && row >= 0 &&  components.length > row;
  }

  protected static boolean cellExists(@Nullable RadComponent[][] components, int row, int column) {
    return components != null &&
           0 <= row && row < components.length &&
           0 <= column && column < components[0].length;
  }

  /**
   * Returns whether a given cell already exists and has components in it.
   */
  private boolean hasComponents(int row, int column) {
    RadComponent[][] components = getGridInfo().components;

    return cellExists(components, row, column) && components[row][column] != null;
  }

  /**
   * Returns the coordinates of the insert rects (in target component's coordinate system).
   */
  private Rectangle getInsertRect(boolean includeSpans) {
    GridInfo gridInfo = getGridInfo();
    int startColumn = myColumn;
    int endColumn = myColumn + 1;
    int startRow = myRow;
    int endRow = myRow + 1;

    if (includeSpans) {
      RadComponent[] columnComponents = gridInfo.components[myRow];
      RadComponent existComponent = columnComponents[startColumn];

      while (startColumn > 0) {
        if (columnComponents[startColumn - 1] == existComponent) {
          startColumn--;
        }
        else {
          break;
        }
      }
      while (endColumn < columnComponents.length) {
        if (columnComponents[endColumn] == existComponent) {
          endColumn++;
        }
        else {
          break;
        }
      }

      while (startRow > 0) {
        if (gridInfo.components[startRow - 1][startColumn] == existComponent) {
          startRow--;
        }
        else {
          break;
        }
      }
      while (endRow < gridInfo.components.length) {
        if (gridInfo.components[endRow][startColumn] == existComponent) {
          endRow++;
        }
        else {
          break;
        }
      }
    }

    EditableArea area = myContext.getArea();
    JComponent target = area.getNativeComponent();
    int x1 = startColumn < gridInfo.vLines.length ? gridInfo.getCellPosition(target, 0, startColumn).x : 0;
    int x2 = endColumn < gridInfo.vLines.length ? gridInfo.getCellPosition(target, 0, endColumn).x : gridInfo.getSize(target).width;

    int y1 = startRow < gridInfo.hLines.length ? gridInfo.getCellPosition(target, startRow, 0).y : 0;
    int y2 = endRow < gridInfo.hLines.length ? gridInfo.getCellPosition(target, endRow, 0).y : gridInfo.getSize(target).height;

    return new Rectangle(x1, y1, x2 - x1, y2 - y1);
  }

  /**
   * Returns the insert-in-cell rect for the given cell rect. The insert-in-cell rect is the same as the cell rect but leaving a third of the size
   * for padding (with a maximum of 10px). This border allows the user to, for example, add new cells dropping controls in the current
   * existing cell when the control is dropped outside the insert-in-cell rect.
   */
  private static Rectangle getInsertInCellRect(Rectangle cellRect) {
    int borderWidth = Math.min(cellRect.width / 3, 10);
    int borderHeight = Math.min(cellRect.height / 3, 10);

    return new Rectangle(cellRect.x + borderWidth, cellRect.y + borderHeight, cellRect.width - 2 * borderWidth,
                         cellRect.height - 2 * borderHeight);
  }

  protected abstract int getMovedIndex(boolean row);

  protected abstract boolean isSingleMovedAxis(boolean row);

  protected final int getSizeInRow(int rowIndex, RadComponent excludeComponent) {
    int size = 0;
    RadComponent[][] components = getGridInfo().components;

    if (rowIndex < components.length) {
      RadComponent[] rowComponents = components[rowIndex];

      for (int j = 0; j < rowComponents.length; j++) {
        RadComponent cellComponent = rowComponents[j];
        if (cellComponent != null) {
          if (cellComponent != excludeComponent) {
            size++;
          }

          while (j + 1 < rowComponents.length && cellComponent == rowComponents[j + 1]) {
            j++;
          }
        }
      }
    }

    return size;
  }

  protected final int getSizeInColumn(int columnIndex, int columnCount, RadComponent excludeComponent) {
    int size = 0;
    RadComponent[][] components = getGridInfo().components;

    if (columnIndex < columnCount) {
      for (int j = 0; j < components.length; j++) {
        RadComponent cellComponent = components[j][columnIndex];

        if (cellComponent != null) {
          if (cellComponent != excludeComponent) {
            size++;
          }

          while (j + 1 < components.length && cellComponent == components[j + 1][columnIndex]) {
            j++;
          }
        }
      }
    }

    return size;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Feedback
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private class GridFeedback extends JComponent {
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      DesignerGraphics.useStroke(DrawingStyle.DROP_ZONE, g);

      GridInfo gridInfo = getGridInfo();
      Dimension size = gridInfo.getSize(this);

      if (gridInfo.vLines.length > 0 && gridInfo.hLines.length > 0) {
        for (int column = 0; column < gridInfo.vLines.length; column++) {
          int x = gridInfo.getCellPosition(this, 0, column).x;
          g.drawLine(x, 0, x, size.height);
        }
        for (int row = 0; row < gridInfo.hLines.length; row++) {
          int y = gridInfo.getCellPosition(this, row, 0).y;
          g.drawLine(0, y, size.width, y);
        }
      }
      g.drawRect(0, 0, size.width - 1, size.height - 1);
      g.drawRect(1, 1, size.width - 3, size.height - 3);
      DesignerGraphics.drawRect(DrawingStyle.DROP_RECIPIENT, g, 0, 0, size.width, size.height);

      Rectangle cellRect = getInsertRect(myHasCellComponents);
      if (!canExecute()) {
        // Not allowed to drop the component here.
        DesignerGraphics.drawFilledRect(DrawingStyle.INVALID, g, cellRect.x, cellRect.y, cellRect.width + 1, cellRect.height + 1);
        return;
      }

      if (myInsertType == GridInsertType.in_cell) {
        DesignerGraphics.drawFilledRect(DrawingStyle.DROP_ZONE_ACTIVE, g, cellRect.x, cellRect.y, cellRect.width + 1, cellRect.height + 1);
      }
    }
  }
}
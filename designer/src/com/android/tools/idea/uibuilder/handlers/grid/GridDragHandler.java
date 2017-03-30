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
package com.android.tools.idea.uibuilder.handlers.grid;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

final class GridDragHandler extends DragHandler {
  private final GridInfo info;
  private int row;
  private int column;

  GridDragHandler(@NotNull ViewEditor editor, @NotNull ViewGroupHandler handler, @NotNull SceneComponent layout,
                  @NotNull List<NlComponent> components, @NotNull DragType type) {
    super(editor, handler, layout, components, type);
    info = new GridInfo(layout.getNlComponent());
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    if (info.cellHasChild(row, column)) {
      return;
    }

    NlComponent[][] children = info.getChildren();
    NlComponent child = children[getStartRow()][getStartColumn()];
    int row = info.getRowSkippingEqualLineLocations(y);
    int column = info.getColumnSkippingEqualLineLocations(x);

    child.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW, Integer.toString(row));
    child.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN, Integer.toString(column));

    insertComponents(-1, insertType);
  }

  /**
   * For testing.
   */
  GridInfo getInfo() {
    return info;
  }

  @VisibleForTesting
  int getStartRow() {
    return info.getRow(editor.dpToPx(startY));
  }

  @VisibleForTesting
  int getStartColumn() {
    return info.getColumn(editor.dpToPx(startX));
  }

  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    String successful = super.update(x, y, modifiers);

    row = info.getRow(editor.dpToPx(y));
    column = info.getColumn(editor.dpToPx(x));

    return successful;
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    Insets padding = layout.getNlComponent().getPadding();

    @AndroidCoordinate int layoutX1 = editor.dpToPx(layout.getDrawX()) + padding.left;
    @AndroidCoordinate int layoutY1 = editor.dpToPx(layout.getDrawY()) + padding.top;
    @AndroidCoordinate int layoutX2 =
      editor.dpToPx(layout.getDrawX()) + padding.left + editor.dpToPx(layout.getDrawWidth()) - padding.width() - 1;
    @AndroidCoordinate int layoutY2 =
      editor.dpToPx(layout.getDrawY()) + padding.top + editor.dpToPx(layout.getDrawHeight()) - padding.height() - 1;

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int x : info.getVerticalLineLocations()) {
      graphics.drawLine(x, layoutY1, x, layoutY2);
    }

    for (int y : info.getHorizontalLineLocations()) {
      graphics.drawLine(layoutX1, y, layoutX2, y);
    }

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    graphics.drawRect(layoutX1, layoutY1, editor.dpToPx(layout.getDrawWidth()) - padding.width(),
                      editor.dpToPx(layout.getDrawHeight()) - padding.height());

    graphics.useStyle(info.cellHasChild(row, column) ? NlDrawingStyle.INVALID : NlDrawingStyle.DROP_ZONE_ACTIVE);

    Rectangle rectangle = getActiveDropZoneRectangle();
    graphics.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
  }

  @AndroidCoordinate
  private Rectangle getActiveDropZoneRectangle() {
    int startRow = row;
    int startColumn = column;
    int endRow = row + 1;
    int endColumn = column + 1;

    if (info.cellHasChild(row, column)) {
      Object[][] children = info.getChildren();

      while (startRow > 0) {
        if (children[row][column].equals(children[startRow - 1][column])) {
          startRow--;
        }
        else {
          break;
        }
      }

      while (startColumn > 0) {
        if (children[row][column].equals(children[row][startColumn - 1])) {
          startColumn--;
        }
        else {
          break;
        }
      }

      int rowCount = info.getRowCount();

      while (endRow < rowCount) {
        if (children[row][column].equals(children[endRow][column])) {
          endRow++;
        }
        else {
          break;
        }
      }

      int columnCount = info.getColumnCount();

      while (endColumn < columnCount) {
        if (children[row][column].equals(children[row][endColumn])) {
          endColumn++;
        }
        else {
          break;
        }
      }
    }

    int[] verticalLineLocations = info.getVerticalLineLocations();
    int[] horizontalLineLocations = info.getHorizontalLineLocations();

    int x = verticalLineLocations[startColumn];
    int y = horizontalLineLocations[startRow];
    int width = verticalLineLocations[endColumn] - x;
    int height = horizontalLineLocations[endRow] - y;

    return new Rectangle(x, y, width, height);
  }
}

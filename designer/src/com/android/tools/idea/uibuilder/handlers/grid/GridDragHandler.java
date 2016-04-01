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
import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.annotations.VisibleForTesting;

import java.awt.*;
import java.util.List;

final class GridDragHandler extends DragHandler {
  private final GridInfo info;
  private int row;
  private int column;

  GridDragHandler(ViewEditor editor, ViewGroupHandler handler, NlComponent layout, List<NlComponent> components, DragType type) {
    super(editor, handler, layout, components, type);
    info = new GridInfo(layout);
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    if (info.cellHasChild(row, column)) {
      return;
    }

    NlComponent[][] children = info.getChildren();
    NlComponent child = children[getStartRow()][getStartColumn()];
    int row = info.getRowSkippingEqualLineLocations(y);
    int column = info.getColumnSkippingEqualLineLocations(x);

    child.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW, Integer.toString(row));
    child.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN, Integer.toString(column));
  }

  /**
   * For testing.
   */
  GridInfo getInfo() {
    return info;
  }

  @VisibleForTesting
  int getStartRow() {
    return info.getRow(startY);
  }

  @VisibleForTesting
  int getStartColumn() {
    return info.getColumn(startX);
  }

  @Override
  public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    String successful = super.update(x, y, modifiers);

    row = info.getRow(y);
    column = info.getColumn(x);

    return successful;
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    Insets padding = layout.getPadding();

    int layoutX1 = layout.x + padding.left;
    int layoutY1 = layout.y + padding.top;
    int layoutX2 = layout.x + padding.left + layout.w - padding.width() - 1;
    int layoutY2 = layout.y + padding.top + layout.h - padding.height() - 1;

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int x : info.getVerticalLineLocations()) {
      graphics.drawLine(x, layoutY1, x, layoutY2);
    }

    for (int y : info.getHorizontalLineLocations()) {
      graphics.drawLine(layoutX1, y, layoutX2, y);
    }

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    graphics.drawRect(layoutX1, layoutY1, layout.w - padding.width(), layout.h - padding.height());

    graphics.useStyle(info.cellHasChild(row, column) ? NlDrawingStyle.INVALID : NlDrawingStyle.DROP_ZONE_ACTIVE);

    Rectangle rectangle = getActiveDropZoneRectangle();
    graphics.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
  }

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

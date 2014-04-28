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
package com.intellij.android.designer.model.grid;

import com.intellij.designer.model.RadComponent;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class GridInfo {
  public int width;  // in model pixels
  public int height; // in model pixels

  public GridInfo(@NotNull RadComponent grid) {
    this.grid = grid;
  }

  // horizontal lines: defines y positions of rows
  public int[] hLines = ArrayUtil.EMPTY_INT_ARRAY; // in model pixels
  // vertical lines: defines x positions of columns
  public int[] vLines = ArrayUtil.EMPTY_INT_ARRAY; // in model pixels

  public boolean[] emptyColumns = ArrayUtil.EMPTY_BOOLEAN_ARRAY;
  public boolean[] emptyRows = ArrayUtil.EMPTY_BOOLEAN_ARRAY;

  public RadComponent grid;
  public RadComponent[][] components;

  public int rowCount;
  public int columnCount;

  public int lastInsertRow = -1;
  public int lastInsertColumn = -1;

  private static final int NEW_CELL_SIZE = 32;

  @NotNull
  public Dimension getSize(@NotNull Component target) {
    return grid.fromModel(target, new Dimension(width, height));
  }

  @NotNull
  public Point getCellPosition(@NotNull Component target, int row, int column) {
    //return grid.fromModel(target, new Point(vLines[column], hLines[row]));
    // The positions are relative to the beginning of the grid, so do dimension
    // match rather than point math
    Dimension size = grid.fromModel(target, new Dimension(vLines[column], hLines[row]));
    return new Point(size.width, size.height);
  }

  public static int[] addLineInfo(int[] oldLines, int delta) {
    if (delta > 0) {
      int newLength = oldLines.length + delta / NEW_CELL_SIZE;

      if (newLength > oldLines.length) {
        int[] newLines = new int[newLength];
        int startIndex = oldLines.length;

        if (oldLines.length > 0) {
          System.arraycopy(oldLines, 0, newLines, 0, oldLines.length);
        }
        else {
          startIndex = 1;
        }

        for (int i = startIndex; i < newLength; i++) {
          newLines[i] = newLines[i - 1] + NEW_CELL_SIZE;
        }

        return newLines;
      }
      return oldLines;
    }
    return oldLines;
  }

  public static void setNull(RadComponent[][] components1,
                             @Nullable RadComponent[][] components2,
                             int startRow,
                             int endRow,
                             int startColumn,
                             int endColumn) {
    endRow = Math.min(endRow, components1.length);
    endColumn = Math.min(endColumn, components1[0].length);

    for (int i = startRow; i < endRow; i++) {
      for (int j = startColumn; j < endColumn; j++) {
        components1[i][j] = null;
        if (components2 != null) {
          components2[i][j] = null;
        }
      }
    }
  }
}
/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering.multi;

import com.android.tools.idea.configurations.RenderContext;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.rendering.multi.RenderPreviewManager.*;

/**
 * Layout algorithm for render previews which layouts out all
 * the previews in a regular tile. Suitable for XML preview where
 * the layout view isn't dominant since it is not directly manipulated on.
 */
public class PreviewTileLayout {
  private static final boolean FILL_FROM_BOTTOM = false;

  private final @NotNull List<RenderPreview> myPreviews;
  private final @NotNull RenderContext myRenderContext;
  private final boolean myFixedOrder;

  public PreviewTileLayout(@NotNull List<RenderPreview> previews, @NotNull RenderContext renderContext, boolean fixedOrder) {
    myPreviews = previews;
    myRenderContext = renderContext;
    myFixedOrder = fixedOrder;
  }

  private int myLayoutHeight;
  private Dimension myFixedRenderSize;

  @Nullable
  public Dimension getFixedRenderSize() {
    return myFixedRenderSize;
  }

  public void performLayout() {
    Rectangle clientArea = myRenderContext.getClientArea();
    int availableWidth = clientArea.width;
    int availableHeight = clientArea.height;
    availableWidth -= HORIZONTAL_GAP;

    Dimension fullImageSize = myRenderContext.getFullImageSize();
    List<RenderPreview> aspectOrder = new ArrayList<RenderPreview>(myPreviews);
    if (!myFixedOrder) {
      if (FILL_FROM_BOTTOM) {
        Collections.sort(aspectOrder, fullImageSize.width <= fullImageSize.height
                                      ? RenderPreview.DECREASING_ASPECT_RATIO
                                      : RenderPreview.INCREASING_ASPECT_RATIO);
      } else {
        Collections.sort(aspectOrder, fullImageSize.width >= fullImageSize.height ?
                                   RenderPreview.DECREASING_ASPECT_RATIO : RenderPreview.INCREASING_ASPECT_RATIO);
      }
    }

    // Compute best row/column size
    int cellCount = aspectOrder.size() + 1;  // +1: for main preview

    Pair<Integer,Integer> shape = computeOptimalShape(availableWidth, availableHeight, cellCount);
    int rows = shape.getFirst();
    int columns = shape.getSecond();
    int tileWidth = (availableWidth - HORIZONTAL_GAP * (columns - 1)) / columns;
    int tileHeight = (availableHeight - VERTICAL_GAP * (rows - 1) - TITLE_HEIGHT * rows) / rows;

    RenderPreview[][] grid = computeGrid(aspectOrder, fullImageSize, rows, columns);

    // Assign positions.
    // Work row by row; compute the minimum required height by the elements in
    // the row, and use that height rather than the originally allocated cell height.
    // If we for example have a row where all devices are in landscape mode, the entire
    // row is likely not as tall as assigned, so we can recapture that space and use
    // it for later rows near the top.

    layoutRows(availableWidth, availableHeight, rows, columns, tileWidth, tileHeight, grid);

    myLayoutHeight = availableHeight;
  }

  private void layoutRows(int availableWidth,
                          int availableHeight,
                          int rows,
                          int columns,
                          int tileWidth,
                          int tileHeight,
                          RenderPreview[][] grid) {
    int y = availableHeight - 2;
    int left = availableWidth - columns * tileWidth;
    if (left > HORIZONTAL_GAP) {
      // Some spacing on the right
      left -= HORIZONTAL_GAP;
    }
    for (int row = rows - 1; row >= 0; row--) {
      int rowHeight = 0;
      //int rowWidth = 0;
      for (int column = 0; column < columns; column++) {
        RenderPreview preview = grid[row][column];
        if (preview != null) {
          preview.setMaxSize(tileWidth, tileHeight);
          rowHeight = Math.max(rowHeight, preview.getLayoutHeight());
          //rowWidth += preview.getLayoutWidth();
        }
      }
      // Handle the fact that the grid doesn't actually contain an entry for the
      // main rendering
      if (row == 0) {
        rowHeight = Math.max(rowHeight, tileHeight);
        //rowWidth += tileWidth;
      }

      // Assign positions -- and perform cell center alignment
      y -= rowHeight;
      if (row < rows - 1) {
        y -= VERTICAL_GAP;
      }
      y -= TITLE_HEIGHT;
      if (y < 0 || row == 0 && rows == 2) {
        y = 0;
      }
      layoutRow(availableHeight, rows, columns, tileWidth, tileHeight, grid[row], y, left, row, rowHeight);

      if (row == rows - 1) {
        // Look at the last row; we might be able to grow the last element
        int last = columns - 1;
        for (; last >= 0; last--) {
          if (grid[rows - 1][last] != null) {
            break;
          }
        }
        if (last != -1 && last < columns - 1) {
          int x = left + last * (tileWidth + HORIZONTAL_GAP) - HORIZONTAL_GAP;
          int max = availableWidth;
          RenderPreview preview = grid[row][last];
          int maxWidth = preview.getMaxWidth();
          int maxHeight = preview.getMaxHeight();
          int layoutWidth = preview.getLayoutWidth();
          int layoutHeight = preview.getLayoutHeight();
          preview.setMaxSize(Math.min(availableWidth - x, Math.max(maxWidth, max - x - HORIZONTAL_GAP / 2)),
                             Math.min(availableHeight - y, Math.max(maxHeight, rowHeight - TITLE_HEIGHT)));
          if (preview.getLayoutWidth() != layoutWidth || preview.getLayoutHeight() != layoutHeight) {
            preview.setPosition(x + HORIZONTAL_GAP / 2, y);
          }
        }
      }
    }

    if (rows == 1) {
      // Center in row
      int row = 0;
      for (int column = 0; column < columns; column++) {
        RenderPreview preview = grid[row][column];
        if (preview != null) {
          preview.setPosition(preview.getX(), (availableHeight - preview.getLayoutHeight()) / 2);
        }
      }
    } else if (columns == 1) {
      // Center in column
      int column = 0;
      for (int row = 0; row < rows; row++) {
        RenderPreview preview = grid[row][column];
        if (preview != null) {
          preview.setPosition((availableWidth - preview.getLayoutWidth()) / 2, preview.getY());
        }
      }
    }
  }

  private void layoutRow(int availableHeight,
                         int rows,
                         int columns,
                         int tileWidth,
                         int tileHeight,
                         RenderPreview[] renderPreviews,
                         int y,
                         int left,
                         int row,
                         int rowHeight) {
    for (int column = 0; column < columns; column++) {
      RenderPreview preview = renderPreviews[column];
      if (preview != null) {
        int x = left + column * (tileWidth + HORIZONTAL_GAP);
        preview.setPosition(x + (tileWidth - preview.getLayoutWidth()) / 2, y + (rowHeight - preview.getLayoutHeight()) / 2);
        // TODO: Consider distributing extra space to the last item
        preview.setVisible(true);
      } else if (row == 0 && column == 0) {
        // TODO: This might allow room on remaining row for other views to be larger too!!! Consider giving it to them,
        // This requires me knowing the aspect ratio of the main view in order to determine whether it's actually going to
        // use up the allocated width!

        // Allocate one cell plus
        int maxWidth = left + tileWidth/* + HORIZONTAL_GAP*/;

        int maxHeight = TITLE_HEIGHT + tileHeight + availableHeight - rows * tileHeight - VERTICAL_GAP * (rows - 1);
        myFixedRenderSize = new Dimension(maxWidth, maxHeight);
        myRenderContext.setMaxSize(myFixedRenderSize.width, myFixedRenderSize.height);
      }
    }
  }

  private RenderPreview[][] computeGrid(List<RenderPreview> previews, Dimension fullImageSize, int rows, int columns) {
    int row;
    int column;
    RenderPreview[][] grid = new RenderPreview[rows][columns];

    if (FILL_FROM_BOTTOM) {
      // Attempt to leave room in the top row and to the left.
      // In other words, if we have a partial row, we want the
      // shape on the left instead of the one on the right:
      //
      //           XX             XXXXX
      //        XXXXX             XXXXX
      //        XXXXX             XXXXX
      //        XXXXX             XX

      row = rows - 1;
      column = columns - 1;
      for (RenderPreview preview : previews) {
        grid[row][column] = preview;
        column--;
        if (column < 0) {
          column = columns - 1;
          row--;
          if (row < 0) {
            break;
          }
        }
      }
    } else {
      if (columns > 1) {
        row = 0;
        //// Reserve spot 0 for the default preview
        column = 1;
      } else {
        row = 1;
        column = 0;
      }
      for (RenderPreview preview : previews) {
        grid[row][column] = preview;
        column++;
        if (column == columns) {
          column = 0;
          row++;
          if (row == rows) {
            break;
          }
        }
      }
    }
    return grid;
  }

  private double pickAspectRatio() {
    double minAspect = myPreviews.get(0).getAspectRatio();
    double maxAspect = minAspect;

    for (RenderPreview preview : myPreviews) {
      double aspect = preview.getAspectRatio();
      if (aspect > maxAspect) {
        maxAspect = aspect;
      }
      if (aspect < minAspect) {
        minAspect = aspect;
      }
    }

    // Aspect: figure out the aspect ratio with the title embedded!
    double aspect;
    if (minAspect < 1 && maxAspect > 1) {
      aspect = 1;
    } else if (minAspect < 1) {
      aspect = minAspect;
    } else {
      aspect = maxAspect;
    }
    return aspect;
  }

  private Pair<Integer, Integer> computeOptimalShape(int availableWidth, int availableHeight, int count) {
    double aspect = pickAspectRatio();
    int bestColumns = 1;
    int bestRows = count;
    int bestSize = 0;
    for (int columns = 1; columns <= count; columns++) {
      int rows = count / columns;
      if (count % columns > 0) {
        rows++;
      }
      int width = (availableWidth - HORIZONTAL_GAP * (columns - 1)) / columns;
      int height = (int)(width / aspect);
      int requiredHeight = height * rows + VERTICAL_GAP * (rows - 1) + TITLE_HEIGHT * rows;
      if (requiredHeight > availableHeight) {
        height = (availableHeight - VERTICAL_GAP * (rows - 1) - TITLE_HEIGHT * rows) / rows;
        width = (int)(height * aspect);
      }

      if (width > bestSize) {
        bestSize = width;
        bestColumns = columns;
        bestRows = rows;
      }
    }
    return Pair.create(bestRows, bestColumns);
  }

  public int getLayoutHeight() {
    return myLayoutHeight;
  }
}

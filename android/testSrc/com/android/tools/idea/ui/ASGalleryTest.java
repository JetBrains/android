/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.android.tools.idea.ui.ASGallery;
import com.google.common.base.Functions;
import com.intellij.ui.components.JBList;
import junit.framework.TestCase;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public final class ASGalleryTest extends TestCase {
  public static final Dimension THUMBNAIL_SIZE = new Dimension(128, 128);
  public static final int COLUMNS = 5;
  // Makes coordinates computation by gallery more complex
  public static final Border BORDER = BorderFactory.createEmptyBorder(COLUMNS, 10, 20, 40);
  private ASGallery<ModelObject> gallery;

  private static Dimension adjustByInsetsValue(int width, int height, Insets cellMargin) {
    int expectedWidth = width + cellMargin.left + cellMargin.right;
    int expectedHeight = height + cellMargin.top + cellMargin.bottom;
    return new Dimension(expectedWidth, expectedHeight);
  }

  private static Dimension adjustByInsetsValue(Dimension dimension, Insets borderInsets) {
    return adjustByInsetsValue(dimension.width, dimension.height, borderInsets);
  }

  private static int computeCellWidth(ASGallery<ModelObject> gallery) {
    return getColumnOffset(gallery, 1) - getColumnOffset(gallery, 0);
  }

  private static int getColumnOffset(ASGallery<ModelObject> gallery, int column) {
    Insets borderInsets = BORDER.getBorderInsets(gallery);
    int clientWidth = gallery.getWidth() - borderInsets.left - borderInsets.right;
    return gallery.getColumnOffset(column, gallery.getColumnCount(), clientWidth);
  }

  private static void assertColumnWidthForGallerySize(ASGallery<ModelObject> gallery, int galleryWidth, int expectedCellSize) {
    Insets borderInsets = BORDER.getBorderInsets(gallery);
    Dimension cellSize = gallery.computeCellSize();
    gallery.setSize(adjustByInsetsValue(galleryWidth, cellSize.height, borderInsets));
    assertEquals(expectedCellSize, computeCellWidth(gallery));
  }

  private static void assertColumnCountForWidth(ASGallery<ModelObject> gallery, int columns, int width) {
    gallery.setSize(adjustByInsetsValue(width, gallery.computeCellSize().height, BORDER.getBorderInsets(gallery)));
    assertEquals(columns, gallery.getColumnCount());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModelObject[] objects = new ModelObject[COLUMNS];
    for (int i = 0; i < COLUMNS; i++) {
      objects[i] = new ModelObject(i + 1);
    }
    ASGallery<ModelObject> asGallery =
      new ASGallery<ModelObject>(JBList.createDefaultListModel(objects), Functions.<Image>constant(null), Functions.toStringFunction(),
                                 THUMBNAIL_SIZE);
    asGallery.setBorder(BORDER);
    gallery = asGallery;
  }

  public void testColumnSize() {
    Dimension cellSize = gallery.computeCellSize();
    // 1. 5 elements, 2 columns - span the whole width
    assertColumnWidthForGallerySize(gallery, cellSize.width * (COLUMNS + 1) / COLUMNS, cellSize.width * (COLUMNS + 1) / (COLUMNS));
    // 2. 5 elements, 8 columns (less than 2x elements) - span
    assertColumnWidthForGallerySize(gallery, cellSize.width * (COLUMNS + 3), cellSize.width * (COLUMNS + 3) / COLUMNS);
    // 2. 5. elements, 12 columns (more than 2x elements) - collapse
    assertColumnWidthForGallerySize(gallery, cellSize.width * (COLUMNS * 2 + 2), cellSize.width);
  }

  public void testCellAtForOneColumnFiveRows() {
    Dimension cellSize = gallery.computeCellSize();
    Insets borderInsets = BORDER.getBorderInsets(gallery);

    gallery.setSize(adjustByInsetsValue(cellSize, borderInsets));
    Dimension preferredSize = gallery.getPreferredSize();
    gallery.setSize(preferredSize); // Multiple rows
    // Corner cases. Literally. Note that cell height does not change with gallery height - so far there was no such requirement.
    assertEquals(0, gallery.getCellAt(new Point(borderInsets.left + 1, borderInsets.top + 1)));
    assertEquals(4, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right - 1, borderInsets.top + cellSize.height * 5 - 1)));
    assertEquals(-1, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right - 1, borderInsets.top + cellSize.height * 5)));
    assertEquals(-1, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right, borderInsets.top + cellSize.height * 5 - 1)));
  }

  public void testCellAtFor3Columns2Rows() {
    Dimension cellSize = gallery.computeCellSize();
    Insets borderInsets = BORDER.getBorderInsets(gallery);
    Dimension preferredSize = setGalleryWidth(cellSize.width * 10 / 3);

    assertEquals(0, gallery.getCellAt(new Point(borderInsets.left + 1, borderInsets.top + 1)));
    assertEquals(2, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right - 1, borderInsets.top + cellSize.height - 1)));
    assertEquals(3, gallery.getCellAt(new Point(borderInsets.left + 1, borderInsets.top + cellSize.height)));
    int clientWidth = preferredSize.width - borderInsets.left - borderInsets.right;
    assertEquals(4, gallery.getCellAt(new Point(gallery.getColumnOffset(2, gallery.getColumnCount(), clientWidth) + borderInsets.left - 1,
                                                borderInsets.top + cellSize.height + 1)));
    assertEquals(4, gallery.getCellAt(new Point(gallery.getColumnOffset(2, gallery.getColumnCount(), clientWidth) + borderInsets.left - 1,
                                                borderInsets.top + (cellSize.height * 2) - 1)));
    assertEquals(-1, gallery.getCellAt(new Point(gallery.getColumnOffset(2, gallery.getColumnCount(), clientWidth) + borderInsets.left,
                                                borderInsets.top + (cellSize.height * 2) - 1)));
  }

  public void testCellAtOneRowSpanning() {
    Dimension cellSize = gallery.computeCellSize();
    Insets borderInsets = BORDER.getBorderInsets(gallery);
    Dimension preferredSize = setGalleryWidth(cellSize.width * (COLUMNS + 3));

    assertEquals(0, gallery.getCellAt(new Point(borderInsets.left + 1, borderInsets.top + 1)));
    assertEquals(4, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right - 1, borderInsets.top + cellSize.height - 1)));
    assertEquals(-1, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right, borderInsets.top + cellSize.height - 1)));
  }

  public void testCellAtOneRowNonSpanning() {
    Dimension cellSize = gallery.computeCellSize();
    Insets borderInsets = BORDER.getBorderInsets(gallery);
    Dimension preferredSize = setGalleryWidth(cellSize.width * (COLUMNS * 2 + 3));

    assertEquals(0, gallery.getCellAt(new Point(borderInsets.left + 1, borderInsets.top + 1)));
    assertEquals(-1, gallery.getCellAt(new Point(preferredSize.width - borderInsets.right - 1, borderInsets.top + cellSize.height - 1)));
    assertEquals(4, gallery.getCellAt(new Point(borderInsets.left + cellSize.width * 5 - 1, borderInsets.top + cellSize.height - 1)));
    assertEquals(-1, gallery.getCellAt(new Point(borderInsets.left + cellSize.width * 5, borderInsets.top + cellSize.height - 1)));
  }

  public void testComputeCellSize() {
    Insets cellMargin = gallery.getCellMargin();
    int fontSize = gallery.getFont().getSize();
    Dimension expected = adjustByInsetsValue(THUMBNAIL_SIZE.width, THUMBNAIL_SIZE.height + (fontSize * 2), cellMargin);
    Dimension actualCellSize = gallery.computeCellSize();
    assertEquals(expected, actualCellSize);
  }

  public void testComputeCellSizeWithCustomMargins() {
    Insets cellMargin = new Insets(10, 20, 30, 40);
    gallery.setCellMargin(cellMargin);
    int fontSize = gallery.getFont().getSize();
    Dimension expected = adjustByInsetsValue(THUMBNAIL_SIZE.width, THUMBNAIL_SIZE.height + (fontSize * 2), cellMargin);
    Dimension actualCellSize = gallery.computeCellSize();
    assertEquals(expected, actualCellSize);
  }

  public void testGetColumnCount() {
    Dimension actualCellSize = gallery.computeCellSize();
    // Less than 2x elements - expand to fill the whole width
    assertColumnCountForWidth(gallery, COLUMNS, actualCellSize.width * COLUMNS * 3 / 2);
    // 2x - collapse
    assertColumnCountForWidth(gallery, COLUMNS * 2, actualCellSize.width * COLUMNS * 2);
    // More than 2x - collapse still
    assertColumnCountForWidth(gallery, COLUMNS * 3, actualCellSize.width * COLUMNS * 3);
  }

  public void testPreferredSize() {
    Dimension dimension = gallery.computeCellSize();
    Insets borderInsets = BORDER.getBorderInsets(gallery);

    // Default width - only suggests a single cell size
    assertEquals(adjustByInsetsValue(dimension, borderInsets), gallery.getPreferredSize());

    // Width greater than 2 cells - width as specified, height to show enough rows
    int twoAndAHalfColumns = dimension.width * COLUMNS / 2;
    gallery.setSize(new Dimension(twoAndAHalfColumns, 10));
    Dimension expectedSize = new Dimension(twoAndAHalfColumns, dimension.height * 3 + borderInsets.top + borderInsets.bottom);
    assertEquals(expectedSize, gallery.getPreferredSize());
    assertEquals(expectedSize, gallery.getPreferredScrollableViewportSize());
  }

  private Dimension setGalleryWidth(int width) {
    gallery.setSize(adjustByInsetsValue(width, gallery.computeCellSize().height, BORDER.getBorderInsets(gallery)));
    Dimension preferredSize = gallery.getPreferredSize();
    gallery.setSize(preferredSize);
    return preferredSize;
  }

  private static final class ModelObject {
    public final int myNumber;

    public ModelObject(int number) {
      myNumber = number;
    }

    @Override
    public String toString() {
      return String.valueOf(myNumber);
    }
  }
}
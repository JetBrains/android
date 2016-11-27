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
package com.android.tools.adtui;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * A layout manager which makes it easy to define components that conform to a table-like
 * layout.
 * <p/>
 * An ideal use-case for this layout is a table with a fixed number of columns and a dynamic number
 * of rows, for example a list of "label/value" pairs where the columns line up neatly.
 * <p/>
 * Unlike {@link GridBagLayout}, which requires setting complex constraints and hard to reason
 * about weights, {@link TabularLayout} works by setting column definitions up front. A column
 * can be fixed, fit-to-size, or proportional. When a layout is requested, fixed and fit-to-size
 * columns are calculated first, and all remaining space is split by the proportional columns.
 * Rows, in contrast, are always fit to their height (although a vertical gap can be specified).
 * Invisible components are skipped over when performing the layout.
 * TODO: Support setting sizing rules on rows as well.
 * <p/>
 * When you register components with a panel using this layout, you must associate it with a
 * {@link TabularLayout.Constraint} telling it which row and column it should fit within.
 * You should usually associate one element per cell, and (unless that cell is sized to fit) the
 * element will be stretched to fully contain the cell. You can additionally specify an element
 * that spans across multiple columns, but be aware that such elements are skipped when calculating
 * the layout.
 * <p/>
 * Columns are pre-allocated, so it is an error to specify a cell whose column index is out of
 * bounds. However, rows are unbounded - you can add a component at row 0 and then another at row
 * 1000. Still, if a row doesn't have any components inside of it, it will simply be skipped during
 * layout (meaning sparse layouts are collapsed).
 */
public final class TabularLayout implements LayoutManager2 {

  public static final int DEFAULT_GAP = 10;

  /**
   * A definition for how to size a single column, indicating how its width will be calculated
   * during a layout.
   */
  public static final class SizingRule {
    public enum Type {
      /**
       * Shrink this column as small as possible to perfectly fit all its contents.
       * <p/>
       * For fit columns, {@link #getValue()} has no meaning.
       */
      FIT,

      /**
       * Set this column to a fixed width in pixels.
       * <p/>
       * For fixed columns, {@link #getValue()} is a width in pixels.
       */
      FIXED,

      // TODO: Add a new definition for spacing based on number of characters, inspired by
      // (but not quite the same as) Android's 'sp' type. See:
      // http://developer.android.com/guide/topics/resources/more-resources.html#Dimension

      /**
       * Have this column eat up any remaining space (split with all other proportional columns).
       * <p/>
       * For proportional columns, {@link #getValue()} is an integer value which should be compared
       * with all other proportional columns to determine how much space it gets. For example, if
       * column A is set to 1 and column B is set to 3, column A gets 25% of all remaining space
       * and column B gets 75%.
       */
      PROPORTIONAL,
    }

    private final Type myType;
    private final int myValue; // Value's meaning depends on this constraint's type

    public SizingRule(Type type) {
      this(type, 0);
    }

    public SizingRule(Type type, int value) {
      myType = type;
      myValue = value;
    }

    public Type getType() {
      return myType;
    }

    public int getValue() {
      return myValue;
    }
  }

  /**
   * Constraints which specify which cell the element is slotted into.
   */
  public static final class Constraint {
    private final int myRow;
    private final int myCol;
    private final int myColSpan;

    /**
     * Create a constraint to fit this component into a grid cell.
     */
    public Constraint(int row, int col) {
      this(row, col, 1);
    }

    /**
     * Create a constraint which can live across multiple cells. Note that components which span
     * across multiple cells aren't included in fit-to-width layout calculations.
     */
    public Constraint(int row, int col, int colSpan) {
      if (colSpan < 1) {
        throw new IllegalArgumentException("TabularLayout column span must be greater than 0");
      }
      myRow = row;
      myCol = col;
      myColSpan = colSpan;
    }

    public int getRow() {
      return myRow;
    }

    public int getCol() {
      return myCol;
    }

    public int getColSpan() {
      return myColSpan;
    }
  }

  /**
   * Creates a tabular layout with a default vertical gap.
   *
   * @see #fromString(String, int)
   * @see #DEFAULT_GAP
   */
  @NotNull
  public static TabularLayout fromString(@NotNull String colSizesString) throws IllegalArgumentException {
    return fromString(colSizesString, DEFAULT_GAP);
  }

  /**
   * Create a {@link TabularLayout} from a comma-delimited string, where each value represents
   * either a Fit, Fixed, or Proportional column.
   * <p/>
   * A Fit column is represented by the string "Fit"
   * A Fixed column is represented by an integer + "px" (e.g. 100px)
   * A Proportional column is represented by an integer value followed by a *
   * <p/>
   * Examples:
   * "Fit,*,*"      - First column fits to size, remaining two columns share leftover space equally
   * "3*,*"         - First column gets 75% of space, second column gets 25% of space
   * "75*,25*"      - Same as above
   * "50px,*,100px" - First column gets 50 pixels, last column gets 100, middle gets remaining
   */
  @NotNull
  public static TabularLayout fromString(@NotNull String colSizesString, int vGap) throws IllegalArgumentException {
    List<String> splits = Lists.newArrayList(Splitter.on(',').split(colSizesString));
    int numColumns = splits.size();

    SizingRule[] colSizes = new SizingRule[numColumns];
    try {
      for (int i = 0; i < splits.size(); i++) {
        String s = splits.get(i);
        if (s.equals("Fit")) {
          colSizes[i] = new SizingRule(SizingRule.Type.FIT);
        }
        else if (s.endsWith("px")) {
          int value = Integer.parseInt(s.substring(0, s.length() - 2)); // e.g. "30px" -> "30"
          colSizes[i] = new SizingRule(SizingRule.Type.FIXED, value);
        }
        else if (s.equals("*")) {
          colSizes[i] = new SizingRule(SizingRule.Type.PROPORTIONAL, 1);
        }
        else if (s.endsWith("*")) {
          int value = Integer.parseInt(s.substring(0, s.length() - 1)); // e.g. "3*" -> "3"
          colSizes[i] = new SizingRule(SizingRule.Type.PROPORTIONAL, value);
        }
        else {
          throw new IllegalArgumentException(String.format("Bad column definition: \"%1$s\" in \"%2$s\"", s, colSizesString));
        }
      }
    }
    catch (NumberFormatException ex) {
      throw new IllegalArgumentException(String.format("Bad column definition: \"%s\"", colSizesString));
    }

    return new TabularLayout(vGap, colSizes);
  }

  private final SizingRule[] myColSizes;
  private final float[] myColPercentages;
  private final int myVGap; // Vertical gap between rows

  private final Map<Component, Constraint> myConstraints = Maps.newHashMap();

  public TabularLayout(int vGap, SizingRule... colSizes) {
    myVGap = vGap;
    myColSizes = colSizes;
    myColPercentages = new float[myColSizes.length];

    float totalProportionalWidth = 0;
    for (SizingRule colSize : myColSizes) {
      if (colSize.getType() == SizingRule.Type.PROPORTIONAL) {
        totalProportionalWidth += colSize.getValue();
      }
    }
    if (totalProportionalWidth > 0) {
      for (int i = 0; i < myColSizes.length; i++) {
        SizingRule colSize = colSizes[i];
        if (colSize.getType() == SizingRule.Type.PROPORTIONAL) {
          myColPercentages[i] = colSize.getValue() / totalProportionalWidth;
        }
      }
    }
  }

  public int getNumColumns() {
    return myColSizes.length;
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraint) {
    if (constraint == null || !(constraint instanceof Constraint)) {
      throw new IllegalArgumentException("Children of ProportionalLayouts must be added with a property constraint");
    }

    Constraint typedConstraint = (Constraint)constraint;
    if (typedConstraint.myCol + typedConstraint.myColSpan > myColSizes.length) {
      throw new IllegalArgumentException(String.format("Component added with invalid column span. col: %1$d, span: %2$d, num cols: %3$d",
                                                       typedConstraint.myCol, typedConstraint.myColSpan, myColSizes.length));
    }

    myConstraints.put(comp, (Constraint)constraint);
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    return 0;
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    return 0.5f;
  }

  @Override
  public void invalidateLayout(Container target) {
    // Do nothing
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    // Do nothing
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    myConstraints.remove(comp);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int[] widths = new int[myColSizes.length];
    int[] heights;

    for (int i = 0; i < myColSizes.length; i++) {
      SizingRule colSize = myColSizes[i];
      if (colSize.getType() == SizingRule.Type.FIXED) {
        widths[i] = colSize.getValue();
      }
    }

    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      int componentCount = parent.getComponentCount();

      int numRows = 1;
      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int rowIndex = myConstraints.get(comp).getRow();
        numRows = Math.max(rowIndex + 1, numRows);
      }
      heights = new int[numRows];

      // Calculate leftover width which, if we had, would fit all proportional columns.
      int maxDesiredWidth = 0;

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        if (!comp.isVisible()) continue;

        Dimension d = comp.getPreferredSize();

        int rowIndex = myConstraints.get(comp).getRow();
        heights[rowIndex] = Math.max(heights[rowIndex], d.height);

        int colIndex = myConstraints.get(comp).getCol();
        int colSpan = myConstraints.get(comp).getColSpan();
        SizingRule colSize = myColSizes[colIndex];
        if (colSize.getType() == SizingRule.Type.FIT && colSpan == 1) {
          widths[colIndex] = Math.max(widths[colIndex], d.width);
        }
        else if (colSize.getType() == SizingRule.Type.PROPORTIONAL) {
          // Calculate how much total leftover width would be needed to fit this cell
          // after it takes its percentage cut
          int desiredWidth = Math.round(d.width / myColPercentages[colIndex]);
          maxDesiredWidth = Math.max(maxDesiredWidth, desiredWidth);
        }
      }

      int h = 0;
      for (int height : heights) {
        if (h > 0 && height > 0) h += myVGap;
        h += height;
      }
      int w = maxDesiredWidth;
      for (int width : widths) {
        w += width;
      }
      return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h);
    }
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    int[] widths = new int[myColSizes.length];
    int[] heights;
    for (int i = 0; i < myColSizes.length; i++) {
      SizingRule colSize = myColSizes[i];
      if (colSize.getType() == SizingRule.Type.FIXED) {
        widths[i] = colSize.getValue();
      }
    }

    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      int componentCount = parent.getComponentCount();

      int numRows = 1;
      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int rowIndex = myConstraints.get(comp).getRow();
        numRows = Math.max(rowIndex + 1, numRows);
      }
      heights = new int[numRows];

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        if (!comp.isVisible()) continue;
        Dimension d = comp.getMinimumSize();

        int rowIndex = myConstraints.get(comp).getRow();
        heights[rowIndex] = Math.max(heights[rowIndex], d.height);

        int colIndex = myConstraints.get(comp).getCol();
        int colSpan = myConstraints.get(comp).getColSpan();
        SizingRule colSize = myColSizes[colIndex];
        if (colSize.getType() == SizingRule.Type.FIT && colSpan == 1) {
          widths[colIndex] = Math.max(widths[colIndex], d.width);
        }
      }

      int h = 0;
      for (int height : heights) {
        if (h > 0 && height > 0) h += myVGap;
        h += height;
      }
      int w = 0;
      for (float width : widths) {
        w += width;
      }

      return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h);
    }
  }

  @Override
  public void layoutContainer(Container parent) {
    int numCols = myColSizes.length;
    int[] colXs = new int[numCols];
    int[] colWs = new int[numCols];
    int[] rowYs;
    int[] rowHs;

    for (int i = 0; i < myColSizes.length; i++) {
      SizingRule column = myColSizes[i];
      if (column.getType() == SizingRule.Type.FIXED) {
        colWs[i] = column.getValue();
      }
    }

    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      int componentCount = parent.getComponentCount();

      int numRows = 1;
      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int rowIndex = myConstraints.get(comp).getRow();
        numRows = Math.max(numRows, rowIndex + 1);
      }
      rowYs = new int[numRows];
      rowHs = new int[numRows];

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        if (!comp.isVisible()) continue;
        Dimension d = comp.getMinimumSize();

        int rowIndex = myConstraints.get(comp).getRow();
        rowHs[rowIndex] = Math.max(rowHs[rowIndex], d.height);

        int colIndex = myConstraints.get(comp).getCol();
        int colSpan = myConstraints.get(comp).getColSpan();
        SizingRule colSize = myColSizes[colIndex];
        if (colSize.getType() == SizingRule.Type.FIT && colSpan == 1) {
          colWs[colIndex] = Math.max(colWs[colIndex], d.width);
        }
      }

      int leftoverWidth = parent.getWidth() - insets.right - insets.left;
      for (int colW : colWs) {
        leftoverWidth -= colW;
      }

      if (leftoverWidth > 0) {
        for (int i = 0; i < numCols; i++) {
          if (myColPercentages[i] > 0) {
            colWs[i] = Math.round(myColPercentages[i] * leftoverWidth);
          }
        }
      }

      rowYs[0] = insets.top;
      for (int i = 1; i < rowYs.length; i++) {
        rowYs[i] = rowYs[i - 1] + rowHs[i - 1];
        if (rowHs[i] > 0 && rowYs[i] > insets.top) {
          rowYs[i] += myVGap;
        }
      }

      colXs[0] = insets.left;
      for (int i = 1; i < colXs.length; i++) {
        colXs[i] = colXs[i - 1] + colWs[i - 1];
      }

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        if (!comp.isVisible()) continue;
        int colIndex = myConstraints.get(comp).getCol();
        int colSpan = myConstraints.get(comp).getColSpan();
        int rowIndex = myConstraints.get(comp).getRow();

        int totalWidth = 0;
        for (int currCol = colIndex; currCol < colIndex + colSpan; currCol++) {
          totalWidth += colWs[currCol];
        }
        comp.setBounds(colXs[colIndex], rowYs[rowIndex], totalWidth, rowHs[rowIndex]);
      }
    }
  }
}

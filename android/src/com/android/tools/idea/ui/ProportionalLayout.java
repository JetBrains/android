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
package com.android.tools.idea.ui;

import com.android.tools.idea.ui.ProportionalLayout.ColumnDefinition.Type;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * A layout manager which makes it easy to specify proportional grid layouts by hand.
 * <p/>
 * Unlike {@link GridBagLayout}, which requires setting complex constraints and hard to reason
 * about weights, {@link ProportionalLayout} works by setting column definitions up front. A column
 * can be fixed, fit-to-size, or proportional. When a layout is requested, fixed and fit-to-size
 * columns are calculated first, and all remaining space is split by the proportional columns.
 * Rows, in contrast, are always fit-to-size.
 * <p/>
 * When you register components with a panel using this layout, you need to associate it with a
 * constraint telling it which row and column it should fit within. You should only associate one
 * element per cell, and (unless that cell is sized to fit) the element will be stretched to fully
 * contain the cell.
 * <p/>
 * Columns are pre-allocated, so it is an error to specify a cell whose column index is out of
 * bounds. However, rows are unbounded - you can add a component at row 0 and then another at row
 * 1000. Still, if a row doesn't have any components inside of it, it will simply be skipped during
 * layout (meaning sparse layouts are collapsed).
 */
public final class ProportionalLayout implements LayoutManager2 {

  /**
   * A definition for a single column, indicating how its width will be calculated during a layout.
   */
  public static final class ColumnDefinition {
    public enum Type {
      /**
       * Shrink this column as small as possible to perfectly fit all its contents.
       * <p/>
       * For fit columns, {@link #getValue()} has no meaning.
       */
      Fit,

      /**
       * Set this column to a fixed width in pixels.
       * <p/>
       * For fixed columns, {@link #getValue()} is a width in pixels.
       */
      Fixed,

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
      Proportional,
    }

    private final Type myType;
    private final int myValue; // Value's meaning depends on this constraint's type

    public ColumnDefinition(Type type) {
      this(type, 0);
    }

    public ColumnDefinition(Type type, int value) {
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

    public Constraint(int row, int col) {
      myRow = row;
      myCol = col;
    }

    public int getRow() {
      return myRow;
    }

    public int getCol() {
      return myCol;
    }
  }

  /**
   * Create a {@link ProportionalLayout} from a comma-delimited string, where each value represents
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
  public static ProportionalLayout fromString(@NotNull String columnDefinitions) throws IllegalArgumentException {
    List<String> splits = Lists.newArrayList(Splitter.on(',').split(columnDefinitions));
    int numColumns = splits.size();

    ColumnDefinition[] definitions = new ColumnDefinition[numColumns];
    try {
      for (int i = 0; i < splits.size(); i++) {
        String s = splits.get(i);
        if (s.equals("Fit")) {
          definitions[i] = new ColumnDefinition(Type.Fit);
        }
        else if (s.endsWith("px")) {
          int value = Integer.parseInt(s.substring(0, s.length() - 2)); // e.g. "30px" -> "30"
          definitions[i] = new ColumnDefinition(Type.Fixed, value);
        }
        else if (s.equals("*")) {
          definitions[i] = new ColumnDefinition(Type.Proportional, 1);
        }
        else if (s.endsWith("*")) {
          int value = Integer.parseInt(s.substring(0, s.length() - 1)); // e.g. "3*" -> "3"
          definitions[i] = new ColumnDefinition(Type.Proportional, value);
        }
        else {
          throw new IllegalArgumentException(String.format("Bad column definition: \"%1$s\" in \"%2$s\"", s, columnDefinitions));
        }
      }
    }
    catch (NumberFormatException ex) {
      throw new IllegalArgumentException(String.format("Bad column definition: \"%s\"", columnDefinitions));
    }

    return new ProportionalLayout(definitions);
  }

  private final ColumnDefinition[] myDefinitions;
  private final float[] myPercentages;

  private final Map<Component, Constraint> myConstraints = Maps.newHashMap();

  public ProportionalLayout(ColumnDefinition... definitions) {
    myDefinitions = definitions;
    myPercentages = new float[myDefinitions.length];

    float total = 0;
    for (ColumnDefinition constraint : myDefinitions) {
      if (constraint.getType() == Type.Proportional) {
        total += constraint.getValue();
      }
    }
    if (total > 0) {
      for (int i = 0; i < myDefinitions.length; i++) {
        ColumnDefinition constraint = definitions[i];
        if (constraint.getType() == Type.Proportional) {
          myPercentages[i] = constraint.getValue() / total;
        }
      }
    }
  }

  public int getNumColumns() {
    return myDefinitions.length;
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraint) {
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
    int[] widths = new int[myDefinitions.length];
    int[] heights;

    for (int i = 0; i < myDefinitions.length; i++) {
      ColumnDefinition defn = myDefinitions[i];
      if (defn.getType() == Type.Fixed) {
        widths[i] = defn.getValue();
      }
    }

    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      int componentCount = parent.getComponentCount();

      int numRows = 1;
      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int row = myConstraints.get(comp).getRow();
        numRows = Math.max(row + 1, numRows);
      }
      heights = new int[numRows];

      // Calculate leftover space which, if we had, would fit all proportional columns.
      int maxDesiredSpace = 0;

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        Dimension d = comp.getPreferredSize();

        int row = myConstraints.get(comp).getRow();
        heights[row] = Math.max(heights[row], d.height);

        int col = myConstraints.get(comp).getCol();
        ColumnDefinition defn = myDefinitions[col];
        if (defn.getType() == Type.Fit) {
          widths[col] = Math.max(widths[col], d.width);
        }
        else if (defn.getType() == Type.Proportional) {
          // Calculate how much total leftover space would be needed to fit this cell
          // after it takes its percentage cut
          int desiredSpace = Math.round(d.width / myPercentages[col]);
          maxDesiredSpace = Math.max(maxDesiredSpace, desiredSpace);
        }
      }

      int h = 0;
      for (int height : heights) {
        h += height;
      }
      int w = maxDesiredSpace;
      for (int width : widths) {
        w += width;
      }
      return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h);
    }
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    int[] widths = new int[myDefinitions.length];
    int[] heights;
    for (int i = 0; i < myDefinitions.length; i++) {
      ColumnDefinition defn = myDefinitions[i];
      if (defn.getType() == Type.Fixed) {
        widths[i] = defn.getValue();
      }
    }

    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      int componentCount = parent.getComponentCount();

      int numRows = 1;
      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int row = myConstraints.get(comp).getRow();
        numRows = Math.max(row + 1, numRows);
      }
      heights = new int[numRows];

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        Dimension d = comp.getMinimumSize();

        int row = myConstraints.get(comp).getRow();
        heights[row] = Math.max(heights[row], d.height);

        int col = myConstraints.get(comp).getCol();
        ColumnDefinition defn = myDefinitions[col];
        if (defn.getType() == Type.Fit) {
          widths[col] = Math.max(widths[col], d.width);
        }
      }

      int h = 0;
      for (int height : heights) {
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
    int numCols = myDefinitions.length;
    int[] colXs = new int[numCols];
    int[] colWs = new int[numCols];
    int[] rowYs;
    int[] rowHs;

    for (int i = 0; i < myDefinitions.length; i++) {
      ColumnDefinition defn = myDefinitions[i];
      if (defn.getType() == Type.Fixed) {
        colWs[i] = defn.getValue();
      }
    }

    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      int componentCount = parent.getComponentCount();

      int numRows = 1;
      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int row = myConstraints.get(comp).getRow();
        numRows = Math.max(numRows, row + 1);
      }
      rowYs = new int[numRows];
      rowHs = new int[numRows];

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        Dimension d = comp.getMinimumSize();

        int row = myConstraints.get(comp).getRow();
        rowHs[row] = Math.max(rowHs[row], d.height);

        int col = myConstraints.get(comp).getCol();
        ColumnDefinition defn = myDefinitions[col];
        if (defn.getType() == Type.Fit) {
          colWs[col] = Math.max(colWs[col], d.width);
        }
      }

      int leftoverWidth = parent.getWidth() - insets.right - insets.left;
      for (int colW : colWs) {
        leftoverWidth -= colW;
      }

      if (leftoverWidth > 0) {
        for (int i = 0; i < numCols; i++) {
          if (myPercentages[i] > 0) {
            colWs[i] = Math.round(myPercentages[i] * leftoverWidth);
          }
        }
      }

      rowYs[0] = insets.top;
      for (int i = 1; i < rowYs.length; i++) {
        rowYs[i] = rowYs[i - 1] + rowHs[i - 1];
      }

      colXs[0] = insets.left;
      for (int i = 1; i < colXs.length; i++) {
        colXs[i] = colXs[i - 1] + colWs[i - 1];
      }

      for (int i = 0; i < componentCount; i++) {
        Component comp = parent.getComponent(i);
        int col = myConstraints.get(comp).getCol();
        int row = myConstraints.get(comp).getRow();
        comp.setBounds(colXs[col], rowYs[row], colWs[col], rowHs[row]);
      }
    }
  }
}

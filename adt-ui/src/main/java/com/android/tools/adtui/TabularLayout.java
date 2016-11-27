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

import com.google.common.collect.Maps;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A layout manager which makes it easy to define components that conform to a table-like
 * layout.
 * <p/>
 * An ideal use-case for this layout is a table with a fixed number of columns and a dynamic number
 * of rows, for example a list of "label/value" pairs where the columns line up neatly.
 * <p/>
 * Unlike {@link GridBagLayout}, which requires setting complex constraints and hard to reason
 * about weights, {@link TabularLayout} works by setting column definitions up front. A column
 * can be fixed, fit-to-size, or proportional. The fit-to-size calculation uses a component's
 * minimum size, not preferred size, which may be useful to keep in mind.
 * <p/>
 * When a layout is requested, fixed and fit-to-width columns are calculated first, and all
 * remaining space is split by the proportional columns. Rows, in contrast, are always
 * fit-to-height (although a vertical gap can be specified). Invisible components are skipped
 * over when performing the layout.
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
     * across multiple cells aren't included in fit-to-size layout calculations.
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

  private final List<SizingRule> myColSizes;
  private int myVGap; // Vertical gap between rows

  private final Map<Component, Constraint> myConstraints = Maps.newHashMap();

  public TabularLayout(SizingRule... colSizes) {
      myColSizes = Arrays.asList(colSizes);
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
  public TabularLayout(String colSizesString) {
    this(parseSizingRules(colSizesString));
  }

  private static SizingRule[] parseSizingRules(String colSizesString) {
    String[] sizeStrings = colSizesString.split(",");
    SizingRule[] colSizes = new SizingRule[sizeStrings.length];
    try {
      for (int i = 0; i < sizeStrings.length; i++) {
        String s = sizeStrings[i];
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

    return colSizes;
  }

  public TabularLayout setVGap(int vGap) {
    myVGap = vGap;
    return this;
  }

  public int getNumColumns() {
    return myColSizes.size();
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraint) {
    if (constraint == null || !(constraint instanceof Constraint)) {
      throw new IllegalArgumentException("Children of ProportionalLayouts must be added with a property constraint");
    }

    Constraint typedConstraint = (Constraint)constraint;
    if (typedConstraint.myCol + typedConstraint.myColSpan > myColSizes.size()) {
      throw new IllegalArgumentException(String.format("Component added with invalid column span. col: %1$d, span: %2$d, num cols: %3$d",
                                                       typedConstraint.myCol, typedConstraint.myColSpan, myColSizes.size()));
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
    LayoutResult result = new LayoutResult(parent);

    int w = result.colCalculator.getTotalSize(true);
    int h = result.rowCalculator.getTotalSize(true);
    Insets insets = result.insets;
    return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    LayoutResult result = new LayoutResult(parent);

    int w = result.colCalculator.getTotalSize(false);
    int h = result.rowCalculator.getTotalSize(false);
    Insets insets = result.insets;
    return new Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h);
  }

  @Override
  public void layoutContainer(Container parent) {
    synchronized (parent.getTreeLock()) {
      LayoutResult result = new LayoutResult(parent);
      int numCols = result.colCalculator.getLength();
      int numRows = result.rowCalculator.getLength();
      if (numRows == 0 || numCols == 0) {
        return;
      }

      Insets insets = parent.getInsets();
      List<PosSize> rowBounds = result.rowCalculator.getBounds(insets.top, parent.getHeight() - insets.bottom - insets.top);
      List<PosSize> colBounds = result.colCalculator.getBounds(insets.left, parent.getWidth() - insets.right - insets.left);

      for (int i = 0; i < parent.getComponentCount(); i++) {
        Component comp = parent.getComponent(i);
        if (!comp.isVisible()) continue;
        int colIndex = myConstraints.get(comp).getCol();
        int colSpan = myConstraints.get(comp).getColSpan();
        int rowIndex = myConstraints.get(comp).getRow();

        int totalWidth = 0;
        for (int currCol = colIndex; currCol < colIndex + colSpan; currCol++) {
          totalWidth += colBounds.get(currCol).size;
        }
        PosSize c = colBounds.get(colIndex);
        PosSize r = rowBounds.get(rowIndex);
        comp.setBounds(c.pos, r.pos, totalWidth, r.size);
      }
    }
  }

  /**
   * Class responsible for calculating the final sizes of columns and rows, given an initial set of
   * size rules and being notified of the sizes of different components that occupy the table.
   * <p/>
   * Note that we create a calculator for each dimension. Rows get one and columns get one.
   */
  private static final class SizeCalculator {
    private final List<SizingRule> myRules;
    private final int myGap;
    private final List<Integer> mySizes;
    private final List<Float> myPercentages;

    // Proportional cells can shrink to 0 if pressed, but ideally we should have enough extra size
    // for them as well, if we could request a preferred size.
    private int myExtraSize;

    /**
     * Creates a default calculator where each rule is just a fit-to-size rule. This is useful for
     * rows which always fit-to-size and don't support fixed / proportional sizes (yet).
     */
    public SizeCalculator(int numRules, int gap) {
      this(Stream.generate(() -> new SizingRule(SizingRule.Type.FIT)).limit(numRules).collect(Collectors.toList()), gap);
    }

    /**
     * Creates a calculator given an initial set of sizing rules. Use {@link #notifySize(int, int)}
     * to fill it out with real values, and then call {@link #getBounds(int, int)} to pull out the
     * final sizing values.
     */
    public SizeCalculator(List<SizingRule> rules, int gap) {
      myRules = rules;
      myGap = gap;
      mySizes = Stream.generate(() -> 0).limit(rules.size()).collect(Collectors.toList());
      myPercentages = Stream.generate(() -> 0f).limit(rules.size()).collect(Collectors.toList());

      float totalProportionalSize = 0;

      for (SizingRule rule : myRules) {
        if (rule.getType() == SizingRule.Type.PROPORTIONAL) {
          totalProportionalSize += rule.getValue();
        }
      }

      for (int i = 0; i < myRules.size(); i++) {
        SizingRule rule = myRules.get(i);
        if (rule.getType() == SizingRule.Type.PROPORTIONAL) {
          assert (totalProportionalSize > 0); // Set above
          // e.g. "3*, *" -> "75%, 25%"
          myPercentages.set(i, rule.getValue() / totalProportionalSize);
        }
        else if (rule.getType() == SizingRule.Type.FIXED) {
          mySizes.set(i, rule.getValue());
        }
      }
    }

    /**
     * Notify this calculator of a component's size, keeping track of it if relevant.
     */
    public void notifySize(int i, int size) {
      SizingRule rule = myRules.get(i);
      if (rule.getType() == SizingRule.Type.FIT) {
        mySizes.set(i, Math.max(mySizes.get(i), size));
      }
      else if (rule.getType() == SizingRule.Type.PROPORTIONAL) {
        // Calculate how much total leftover size would be needed to fit this cell
        // after it takes its percentage cut.
        myExtraSize = Math.max(myExtraSize, Math.round(size / myPercentages.get(i)));
      }
    }

    /**
     * Returns the number of items in this calculator.
     */
    public int getLength() {
      return mySizes.size();
    }

    /**
     * Returns the total size of all cells as well as gaps. Call after you are finished calling
     * {@link #notifySize(int, int)}.
     *
     * @param includeExtraSize Include the size needed to make space for the proportional cells as
     *                         well. Minimum size calculations should ignore it while preferred
     *                         size calculations should include it.
     */
    public int getTotalSize(boolean includeExtraSize) {
      int totalSize = includeExtraSize ? myExtraSize : 0;
      for (Integer size : mySizes) {
        if (totalSize > 0 && size > 0) {
          // Put gap only between non-zero values except for the first one
          totalSize += myGap;
        }
        totalSize += size;
      }
      return totalSize;
    }

    /**
     * Get a list of (pos, size) pairs, useful for setting the bounds of Swing components directly.
     * For example, for columns with no gaps, this would represent
     *
     * x1         x2     x3
     * |----w1----|--w2--|----------w3----------|
     *
     * @param start The initial position of the first cell
     * @param totalSpace The total space of the parent container, used to calculate the final size
     *                   of proportional columns.
     */
    public List<PosSize> getBounds(int start, int totalSpace) {
      List<PosSize> bounds = Stream.generate(PosSize::new).limit(mySizes.size()).collect(Collectors.toList());
      if (bounds.size() == 0) {
        return bounds;
      }

      int remainingSpace = totalSpace;
      for (int i = 0; i < mySizes.size(); i++) {
        bounds.get(i).size = mySizes.get(i);
        remainingSpace -= mySizes.get(i);
      }

      if (remainingSpace > 0) {
        for (int i = 0; i < myRules.size(); i++) {
          SizingRule rule = myRules.get(i);
          if (rule.getType() == SizingRule.Type.PROPORTIONAL) {
            bounds.get(i).size = Math.round(remainingSpace * myPercentages.get(i));
          }
        }
      }

      int pos = start;
      for (PosSize bound : bounds) {
        if (bound.size > 0) {
          bound.pos = pos;
          pos += bound.size + myGap;
        }
      }

      return bounds;
    }
  }

  /**
   * A position/size pair. For columns, this is x/width, and for rows, this is y/height.
   */
  private static final class PosSize {
    public int pos;
    public int size;
  }

  /**
   * Class which, when instantiated on a parent container, runs through all its components,
   * calculates what the sizes of rows and columns should be, and makes that data available
   * through {@link #rowCalculator} and {@link #colCalculator} fields.
   */
  private final class LayoutResult {
    public final Insets insets;
    public final SizeCalculator rowCalculator;
    public final SizeCalculator colCalculator;

    public LayoutResult(Container container) {
      List<Component> components = new ArrayList<>();

      int numRows = 0;
      synchronized (container.getTreeLock()) {
        insets = container.getInsets();
        for (int i = 0; i < container.getComponentCount(); i++) {
          Component c = container.getComponent(i);
          components.add(c);
          numRows = Math.max(numRows, myConstraints.get(c).getRow() + 1);
        }
      }

      colCalculator = new SizeCalculator(myColSizes, 0);
      rowCalculator = new SizeCalculator(numRows, myVGap);

      for (Component c : components) {
        if (!c.isVisible()) {
          continue;
        }

        Constraint constraint = myConstraints.get(c);
        Dimension size = c.getMinimumSize();
        if (constraint.getColSpan() == 1) {
          // Only components that fit in a single column are used in calculating layouts.
          colCalculator.notifySize(constraint.getCol(), size.width);
        }
        rowCalculator.notifySize(constraint.getRow(), size.height);
      }
    }
  }
}

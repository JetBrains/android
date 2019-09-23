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
package com.android.tools.adtui

import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.LayoutManager2
import java.util.ArrayList
import kotlin.math.roundToInt

/**
 * A layout manager which makes it easy to define components that conform to a table-like layout.
 *
 * An ideal use-case for this layout is a table with a fixed number of columns and a dynamic number
 * of rows, for example a list of "label/value" pairs where the columns line up neatly.
 *
 * Unlike [GridBagLayout], which requires setting complex constraints and hard to reason
 * about weights, [TabularLayout] works by setting column definitions up front. A column
 * can be fixed, fit-to-size, or proportional. The fit-to-size calculation uses a component's
 * minimum size, not preferred size, which may be useful to keep in mind.
 *
 * When a layout is requested, fixed and fit-to-width columns are calculated first, and all
 * remaining space is split by the proportional columns. Rows, in contrast, can be added on the
 * fly. By default, they are always fit-to-height (although a vertical gap can be specified), but
 * they can be configured for sizing as well. To set optional row definitions, use the [setRowSizing] method.
 *
 * When you register components with a panel using this layout, you must associate it with a
 * [TabularLayout.Constraint] telling it which row and column it should fit within.
 * You should usually associate one element per cell, and (unless that cell is sized to fit) the
 * element will be stretched to fully contain the cell. You can additionally specify an element
 * that spans across multiple columns, but be aware that such elements are skipped when calculating
 * the layout. It's also worth noting that invisible components are skipped over when performing the layout.
 *
 * Columns are pre-allocated, so it is an error to specify a cell whose column index is out of
 * bounds. However, rows are unbounded - you can add a component at row 0 and then another at row
 * 1000. Still, if a row doesn't have any components inside of it, it will simply be skipped during
 * layout (meaning sparse layouts are collapsed).
 */
class TabularLayout(colSizes: Array<out SizingRule>, initialRowSizes: Array<SizingRule>) : LayoutManager2 {
  private val colSizes: List<SizingRule> = listOf(*colSizes)
  private val rowSizes = hashMapOf<Int, SizingRule>()
  private var vGap: Int = 0 // Vertical gap between rows
  private val constraints = hashMapOf<Component, Constraint>()

  val numColumns: Int get() = colSizes.size

  /**
   * A definition for how to size a single column or row, indicating how its width or height will be calculated during a layout.
   */
  class SizingRule(
    val type: Type,
    val value: Int // Value's meaning depends on this constraint's type
  ) {
    enum class Type {
      /**
       * Shrink this column as small as possible to perfectly fit all its contents.
       *
       * For fit columns, [value] corresponds to its [FitSizing].
       */
      FIT,
      /**
       * Set this column to a fixed width in pixels.
       *
       * For fixed columns, [value] is a width in pixels.
       */
      FIXED,
      /**
       * Have this column eat up any remaining space (split with all other proportional columns).
       *
       * For proportional columns, [value] is an integer value which should be compared
       * with all other proportional columns to determine how much space it gets. For example, if
       * column A is set to 1 and column B is set to 3, column A gets 25% of all remaining space and column B gets 75%.
       */
      PROPORTIONAL
    }

    enum class FitSizing {
      /**
       * Use the elements minimum size for determining spacing.
       *
       * This is represented by a '-' at the end of "Fit".
       * This is the default value for uninitialized rows/columns.
       * TODO (b/77491599) Update unassigned rows to use preferred in place of minimum
       */
      MINIMUM,
      /**
       * Use the elements preferred size for determining spacing.
       *
       * This is the default value for "Fit" sizing.
       */
      PREFERRED
    }

    companion object {
      /**
       * Create a [SizingRule] from a string value, where each value represents either a Fit, Fixed, or Proportional column.
       *
       * A Fit cell is represented by the string "Fit" (or "Fit-" for using min fit sizing).
       * A Fixed cell is represented by an integer + "px" (e.g. "100px").
       * A Proportional cell is represented by an (optional) integer + "*" (e.g. "3*", "*").
       */
      @Throws(IllegalArgumentException::class)
      fun fromString(s: String): SizingRule {
        try {
          return when {
            s.startsWith("Fit") -> SizingRule(Type.FIT, (if (s.endsWith("-")) FitSizing.MINIMUM else FitSizing.PREFERRED).ordinal)
            s.endsWith("px") -> {
              val value = Integer.parseInt(s.substring(0, s.length - 2)) // e.g. "30px" -> "30"
              SizingRule(Type.FIXED, value)
            }
            s == "*" -> SizingRule(Type.PROPORTIONAL, 1)
            s.endsWith("*") -> {
              val value = Integer.parseInt(s.substring(0, s.length - 1)) // e.g. "3*" -> "3"
              SizingRule(Type.PROPORTIONAL, value)
            }
            else -> throw IllegalArgumentException("Bad size value: \"$s\"")
          }
        }
        catch (ex: NumberFormatException) {
          throw IllegalArgumentException("Bad size value: \"$s\"")
        }
      }
    }
  }

  /**
   * Constraints which specify which cell the element is slotted into.
   */
  class Constraint
  /**
   * Create a constraint which can live across multiple cells. Note that components which span
   * across multiple cells aren't included in fit-to-size layout calculations.
   */
  constructor(val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int) {

    /**
     * Create a constraint which can live across multiple columns. Note that components which span
     * across multiple columns aren't included in fit-to-width layout calculations.
     */
    @JvmOverloads
    constructor(row: Int, col: Int, colSpan: Int = 1) : this(row, col, 1, colSpan)

    init {
      require(colSpan >= 1) { "TabularLayout column span must be greater than 0" }
    }
  }

  constructor(vararg colSizes: SizingRule) : this(colSizes, arrayOf<SizingRule>())

  init {
    for (i in initialRowSizes.indices) {
      setRowSizing(i, initialRowSizes[i])
    }
  }

  /**
   * Create a [TabularLayout] from a comma-delimited string of values that are valid for creating a [SizingRule].
   *
   * Examples:
   * - "Fit,*,*"      - First cell fits to size, remaining two cells share leftover space equally
   * - "3*,*"         - First cell gets 75% of space, second cell gets 25% of space
   * - "75*,25*"      - Same as above
   * - "50px,*,100px" - First cell gets 50 pixels, last cell gets 100, middle gets remaining space
   * @see SizingRule.fromString
   */
  constructor(colSizesString: String) : this(*parseSizingRules(colSizesString))

  /**
   * Like constructor(String) but also specifying initial sizing conditions for rows.
   * Unlike cols, row sizing can be added dynamically later (using [setRowSizing]),
   * but for the common case where the whole table size is known at creation time, this constructor
   * allows it to be expressed in a much more succinct manner.
   */
  constructor(
    colSizesString: String, initialRowSizesString: String
  ) : this(parseSizingRules(colSizesString), parseSizingRules(initialRowSizesString))

  fun setVGap(vGap: Int): TabularLayout {
    this.vGap = vGap
    return this
  }

  fun setRowSizing(rowIndex: Int, rowSize: SizingRule): TabularLayout {
    rowSizes[rowIndex] = rowSize
    return this
  }

  /**
   * @see SizingRule.fromString
   */
  fun setRowSizing(rowIndex: Int, rowSizeString: String): TabularLayout = setRowSizing(rowIndex, SizingRule.fromString(rowSizeString))

  override fun addLayoutComponent(comp: Component, constraint: Any) {
    require(constraint is Constraint) { "Children of containers using ${javaClass.simpleName} must be added with a constraint" }

    with(constraint) {
      require(col + colSpan <= colSizes.size) {
        "Component added with invalid column span. col: $col, span: $colSpan, num cols: ${colSizes.size}"
      }
    }

    constraints[comp] = constraint
  }

  override fun maximumLayoutSize(target: Container): Dimension = Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

  override fun getLayoutAlignmentX(target: Container): Float = 0f

  override fun getLayoutAlignmentY(target: Container): Float = 0.5f

  override fun invalidateLayout(target: Container) {
    // Do nothing
  }

  override fun addLayoutComponent(name: String, comp: Component) {
    // Do nothing
  }

  override fun removeLayoutComponent(comp: Component) {
    constraints.remove(comp)
  }

  override fun preferredLayoutSize(parent: Container): Dimension {
    val result = LayoutResult(parent)

    val w = result.colCalculator.getTotalSize(true)
    val h = result.rowCalculator.getTotalSize(true)
    val insets = result.insets
    return Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h)
  }

  override fun minimumLayoutSize(parent: Container): Dimension {
    val result = LayoutResult(parent)

    val w = result.colCalculator.getTotalSize(false)
    val h = result.rowCalculator.getTotalSize(false)
    val insets = result.insets
    return Dimension(insets.left + insets.right + w, insets.top + insets.bottom + h)
  }

  override fun layoutContainer(parent: Container) {
    synchronized(parent.treeLock) {
      val result = LayoutResult(parent)
      val numCols = result.colCalculator.length
      val numRows = result.rowCalculator.length
      if (numRows == 0 || numCols == 0) {
        return
      }

      val insets = parent.insets
      val rowBounds = result.rowCalculator.getBounds(insets.top, parent.height - insets.bottom - insets.top)
      val colBounds = result.colCalculator.getBounds(insets.left, parent.width - insets.right - insets.left)

      for (i in 0 until parent.componentCount) {
        val comp = parent.getComponent(i)
        if (!comp.isVisible) continue
        val colIndex = constraints[comp]!!.col
        val colSpan = constraints[comp]!!.colSpan
        val rowSpan = constraints[comp]!!.rowSpan
        val rowIndex = constraints[comp]!!.row

        var totalWidth = 0
        for (currCol in colIndex until colIndex + colSpan) {
          totalWidth += colBounds[currCol].size
        }
        var totalHeight = 0
        for (currRow in rowIndex until rowIndex + rowSpan) {
          totalHeight += rowBounds[currRow].size
        }

        val c = colBounds[colIndex]
        val r = rowBounds[rowIndex]
        comp.setBounds(c.pos, r.pos, totalWidth, totalHeight)
      }
    }
  }

  /**
   * Class responsible for calculating the final sizes of columns and rows, given an initial set of
   * size rules and being notified of the sizes of different components that occupy the table.
   *
   * Note that we create a calculator for each dimension. Rows get one and columns get one.
   */
  private class SizeCalculator
  /**
   * Creates a calculator given an initial set of sizing rules. Use [notifySize] to fill
   * it out with real values, and then call [getBounds] to pull out the final sizing values.
   */
  constructor(private val rules: List<SizingRule>, private val gap: Int) {
    private val sizes = MutableList(rules.size) { 0 }
    private val percentages = MutableList(rules.size) { 0f }

    // Proportional cells can shrink to 0 if pressed, but if we were able to ask for any size we
    // preferred, we would choose a size so that all proportional columns would fit.
    private var extraSize: Int = 0

    /**
     * Returns the number of items in this calculator.
     */
    val length: Int get() = sizes.size

    /**
     * Creates a calculator given sparse rules (using them to create a full list of rules). This is
     * useful for rows, where rows can be created on the fly and are set to a default sizing rule in that case.
     *
     * @param sparseRules A mapping of indices to rules. Any missing index will be assumed to be fit-to-size.
     * @param numRules The number of rules that we should create. If [sparseRules] happens to
     * have an index even greater than that, then `numRules` will be updated to contain it.
     */
    constructor(sparseRules: Map<Int, SizingRule>, numRules: Int, gap: Int) : this(fromSparseRules(sparseRules, numRules), gap)

    init {
      var totalProportionalSize = 0f

      for (rule in rules) {
        if (rule.type == SizingRule.Type.PROPORTIONAL) {
          totalProportionalSize += rule.value.toFloat()
        }
      }

      for (i in rules.indices) {
        val rule = rules[i]
        if (rule.type == SizingRule.Type.PROPORTIONAL) {
          assert(totalProportionalSize > 0) // Set above
          // e.g. "3*, *" -> "75%, 25%"
          percentages[i] = rule.value / totalProportionalSize
        }
        else if (rule.type == SizingRule.Type.FIXED) {
          sizes[i] = JBUI.scale(rule.value)
        }
      }
    }

    /**
     * Notify this calculator of a component's size, keeping track of it if relevant.
     */
    fun notifySize(i: Int, size: Int) {
      val rule = rules[i]
      if (rule.type == SizingRule.Type.FIT) {
        sizes[i] = sizes[i].coerceAtLeast(size)
      }
      else if (rule.type == SizingRule.Type.PROPORTIONAL) {
        // Calculate how much total leftover size would be needed to fit this cell  after it takes its percentage cut.
        extraSize = extraSize.coerceAtLeast((size / percentages[i]).roundToInt())
      }
    }

    /**
     * Gets the dimensions for a component based on the type for the specified rule.
     * The default sizing rule is minimum.
     * The "Fit" sizing rule uses preferred and is the only exception to this.
     */
    fun getComponentDimension(i: Int, c: Component): Dimension =
      if (rules[i].value == SizingRule.FitSizing.PREFERRED.ordinal)
        c.preferredSize
      else
        c.minimumSize

    /**
     * Returns the total size of all cells as well as gaps. Call after you are finished calling [notifySize].
     *
     * @param includeExtraSize Include the size needed to make space for the proportional cells as well.
     * Minimum size calculations should ignore it while preferred size calculations should include it.
     */
    fun getTotalSize(includeExtraSize: Boolean): Int {
      var totalSize = if (includeExtraSize) extraSize else 0
      for (size in sizes) {
        if (totalSize > 0 && size > 0) {
          // Put gap only between non-zero values except for the first one
          totalSize += gap
        }
        totalSize += size
      }
      return totalSize
    }

    /**
     * Get a list of (pos, size) pairs, useful for setting the bounds of Swing components directly.
     * For example, for columns with no gaps, this would represent
     *
     * x1         x2     x3
     * |----w1----|--w2--|----------w3----------|
     *
     * @param start The initial position of the first cell.
     * @param totalSpace The total space of the parent container, used to calculate the final size of proportional columns.
     */
    fun getBounds(start: Int, totalSpace: Int): List<PosSize> {
      val bounds = MutableList(sizes.size) { PosSize() }
      if (bounds.isEmpty()) {
        return bounds
      }

      var remainingSpace = totalSpace
      for (i in sizes.indices) {
        bounds[i].size = sizes[i]
        remainingSpace -= sizes[i]
      }

      if (remainingSpace > 0) {
        var spaceUsed = 0
        var lastIndex = -1 // Any rounding error adjustments we'll just do on the last cell
        for (i in rules.indices) {
          val rule = rules[i]
          if (rule.type == SizingRule.Type.PROPORTIONAL) {
            bounds[i].size = Math.round(remainingSpace * percentages[i])
            spaceUsed += bounds[i].size
            lastIndex = i
          }
        }

        if (spaceUsed != remainingSpace && lastIndex >= 0) {
          // Due to rounding error, we either didn't use all the space or we used too much. Make
          // adjustments to the final column to account for it (otherwise, you'll get UI that
          // jitters during resize). In practice, this should rarely be more than a couple of
          // pixels.
          bounds[lastIndex].size += remainingSpace - spaceUsed
        }
      }

      var pos = start
      for (bound in bounds) {
        if (bound.size > 0) {
          bound.pos = pos
          pos += bound.size + gap
        }
      }

      return bounds
    }
  }

  /**
   * A position/size pair. For columns, this is x/width, and for rows, this is y/height.
   */
  private class PosSize {
    var pos: Int = 0
    var size: Int = 0
  }

  /**
   * Class which, when instantiated on a parent container, runs through all its components,
   * calculates what the sizes of rows and columns should be, and makes that data available
   * through [rowCalculator] and [colCalculator] fields.
   */
  private inner class LayoutResult(container: Container) {
    val insets: Insets
    val rowCalculator: SizeCalculator
    val colCalculator: SizeCalculator

    init {
      val components = ArrayList<Component>()

      var numRows = 0
      synchronized(container.treeLock) {
        insets = container.insets
        for (i in 0 until container.componentCount) {
          val c = container.getComponent(i)
          components.add(c)
          numRows = numRows.coerceAtLeast(constraints[c]!!.row + 1)
        }
      }

      colCalculator = SizeCalculator(colSizes, 0)
      rowCalculator = SizeCalculator(rowSizes, numRows, vGap)

      for (c in components) {
        if (!c.isVisible) {
          continue
        }

        val constraint = constraints[c]!!
        if (constraint.colSpan == 1) {
          val size = colCalculator.getComponentDimension(constraint.col, c)
          colCalculator.notifySize(constraint.col, size.width)
        }
        if (constraint.rowSpan == 1) {
          val size = rowCalculator.getComponentDimension(constraint.row, c)
          rowCalculator.notifySize(constraint.row, size.height)
        }
      }
    }
  }
}

private fun parseSizingRules(colSizesString: String): Array<TabularLayout.SizingRule> {
  val sizeStrings = colSizesString.split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
  return sizeStrings.map { TabularLayout.SizingRule.fromString(it) }.toTypedArray()
}

private fun fromSparseRules(sparseRules: Map<Int, TabularLayout.SizingRule>, numRules: Int): List<TabularLayout.SizingRule> {
  var numRules = numRules
  for (ruleIndex in sparseRules.keys) {
    if (ruleIndex >= numRules) {
      numRules = ruleIndex + 1
    }
  }

  val rules = ArrayList<TabularLayout.SizingRule>(numRules)
  for (i in 0 until numRules) {
    var rule: TabularLayout.SizingRule? = sparseRules[i]
    if (rule == null) {
      //TODO (b/77491599) Update unassigned rows to use preferred in place of minimum
      rule = TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIT, TabularLayout.SizingRule.FitSizing.MINIMUM.ordinal)
    }
    rules.add(rule)
  }

  return rules
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.android.tools.adtui.event.DelegateMouseEventHandler
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SizeRequirements
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.table.JTableHeader
import kotlin.math.max

/** An identifier for a table row, either a value row or a category row. */
sealed interface RowKey<T> {
  @JvmInline value class ValueRowKey<T>(val key: Any) : RowKey<T>

  @JvmInline value class CategoryListRowKey<T>(val categoryList: CategoryList<T>) : RowKey<T>
}

/** A UI component for a row in a CategoryTable that is either a category or a value. */
internal sealed class RowComponent<T> : JBPanel<RowComponent<T>>(), TableComponent {
  init {
    isFocusCycleRoot = true
  }

  private var rowSelected = false
    set(value) {
      field = value
      isOpaque = value
    }

  /** Updates the display of the row based on the current selection status. */
  override fun updateTablePresentation(
    manager: TablePresentationManager,
    presentation: TablePresentation
  ) {
    rowSelected = presentation.rowSelected
    manager.defaultApplyPresentation(this, presentation)
  }

  abstract var indent: Int

  abstract val rowKey: RowKey<T>
}

internal class CategoryRowComponent<T>(val path: CategoryList<T>) : RowComponent<T>() {

  private val iconLabel = IconLabel(expandedIcon)

  init {
    iconLabel.constrainSize(Dimension(iconWidth, iconHeight))

    border = BorderFactory.createEmptyBorder(JBUI.scale(5), 0, JBUI.scale(2), 0)
    layout = BoxLayout(this, BoxLayout.X_AXIS)

    // Create a strut as the first component to use for indentation
    add(Box.createHorizontalStrut(0))
    add(iconLabel)
    add(Box.createHorizontalStrut(JBUI.scale(4)))
    // TODO: Support custom UI here?
    add(JBLabel(path.last().value.toString()).also { it.horizontalAlignment = SwingConstants.LEFT })
    add(Box.createHorizontalGlue())
  }

  override var indent: Int = 0
    set(value) {
      field = value
      remove(0)
      add(Box.createHorizontalStrut(value), 0)
    }

  var isExpanded: Boolean = true
    set(value) {
      if (field != value) {
        field = value
        iconLabel.baseIcon = if (isExpanded) expandedIcon else collapsedIcon
      }
    }

  override val rowKey = RowKey.CategoryListRowKey(path)

  companion object {
    private val expandedIcon = UIManager.get("Tree.expandedIcon", null) as Icon
    private val collapsedIcon = UIManager.get("Tree.collapsedIcon", null) as Icon
    private val iconWidth: Int = maxOf(expandedIcon.iconWidth, collapsedIcon.iconWidth)
    private val iconHeight: Int = maxOf(expandedIcon.iconHeight, collapsedIcon.iconHeight)
  }
}

/**
 * The UI component of a row in the table representing a value (rather than a category). Contains
 * child components for each column. The value may be mutable; however, changes to the value will
 * only be reflected when the [value] field is updated. Thus, immutable values will generally be
 * less error-prone.
 */
internal class ValueRowComponent<T>(
  val dataProvider: ValueRowDataProvider<T>,
  header: JTableHeader,
  columns: ColumnList<T>,
  initialValue: T,
  primaryKey: Any
) : RowComponent<T>(), UiDataProvider {
  private val mouseDelegate = DelegateMouseEventHandler.delegateTo(this)

  /** The components of this row, in model order. */
  val componentList: List<ColumnComponent<T, *, *>> =
    columns.map { ColumnComponent(it, initialValue, mouseDelegate) }

  init {
    layout = ValueRowLayout(header)
    componentList.forEach { add(it.component) }

    addMouseListener(
      object : MouseAdapter() {
        override fun mouseExited(e: MouseEvent) {
          hoveredComponent = null
        }
      }
    )
    addMouseMotionListener(
      object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          for (i in 0 until componentList.size - 1) {
            // We need to use the next component's x coordinate
            if (componentList[i].component.isVisible && e.x < componentList[i + 1].component.x) {
              hoveredComponent = componentList[i].component
              return
            }
          }
          hoveredComponent = componentList.lastOrNull()?.component
        }
      }
    )
  }

  private val valueRowLayout: ValueRowLayout
    get() = getLayout() as ValueRowLayout

  override var indent: Int by valueRowLayout::indent

  var value = initialValue
    set(value) {
      field = value
      componentList.forEach { it.updateValue(value) }
    }

  internal var hoveredComponent: JComponent? = null
    set(value) {
      if (field != value) {
        field?.isOpaque = false
        field = value
        field?.isOpaque = true
        if (value != null) {
          // Paint this component after all others.
          setComponentZOrder(value, 0)
        }
        revalidate()
      }
    }

  override val rowKey = RowKey.ValueRowKey<T>(primaryKey)

  /**
   * A wrapper around the component created by a [Column] so that it can be updated in a typesafe
   * manner.
   */
  internal class ColumnComponent<T, C, U : JComponent>(
    val column: Column<T, C, U>,
    initialValue: T,
    mouseDelegate: DelegateMouseEventHandler,
  ) {
    val component =
      column.createUi(initialValue).also { column.installMouseDelegate(it, mouseDelegate) }
    fun updateValue(rowValue: T) {
      column.updateValue(rowValue, component, column.attribute.value(rowValue))
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    dataProvider(sink, value)
  }
}

typealias ValueRowDataProvider<T> = (DataSink, T) -> Any?

object NullValueRowDataProvider : ValueRowDataProvider<Any?> {
  override fun invoke(p1: DataSink, p2: Any?): Any? = null
}

class DefaultValueRowDataProvider<T: Any>(private val dataKey: DataKey<T>) : ValueRowDataProvider<T> {
  override fun invoke(sink: DataSink, value: T) {
    sink[dataKey] = value
  }
}

private class ValueRowLayout(val header: JTableHeader) : LayoutManager {
  private var sizeRequirements: Array<SizeRequirements>? = null
  private var totalHeightRequirements: SizeRequirements? = null

  var indent: Int = 0

  private fun computeSizeRequirements(row: ValueRowComponent<*>) {
    if (totalHeightRequirements == null) {
      // We only compute the height requirement, since width is determined by the table header
      sizeRequirements =
        row.componentList
          .map { c ->
            when {
              c.component.isVisible -> c.component.heightRequirements()
              else -> SizeRequirements(0, 0, 0, c.component.alignmentY)
            }
          }
          .toTypedArray()

      totalHeightRequirements = SizeRequirements.getAlignedSizeRequirements(sizeRequirements)
    }
  }

  override fun addLayoutComponent(name: String?, comp: Component?) {}

  override fun removeLayoutComponent(comp: Component?) {}

  @Synchronized
  override fun preferredLayoutSize(parent: Container): Dimension {
    computeSizeRequirements(parent.asRow())
    return Dimension(header.preferredSize.width, totalHeightRequirements!!.preferred).also {
      it.addInsets(parent)
    }
  }

  @Synchronized
  override fun minimumLayoutSize(parent: Container): Dimension {
    computeSizeRequirements(parent.asRow())
    return Dimension(header.preferredSize.width, totalHeightRequirements!!.minimum).also {
      it.addInsets(parent)
    }
  }

  override fun layoutContainer(parent: Container) {
    val row = parent.asRow()
    var remainingIndent = indent
    var x = 0
    val xpos = Array(row.componentList.size) { -1 }
    val width = Array(row.componentList.size) { -1 }
    for (tableColumn in header.columnModel.columnList) {
      xpos[tableColumn.modelIndex] = x + remainingIndent
      width[tableColumn.modelIndex] = (tableColumn.width - remainingIndent).coerceAtLeast(0)
      remainingIndent = (remainingIndent - tableColumn.width).coerceAtLeast(0)
      x += tableColumn.width
    }
    xpos.forEachIndexed { i, x ->
      val component = row.componentList[i].component
      if (x >= 0) {
        component.setBounds(
          xpos[i],
          0,
          when {
            component == row.hoveredComponent -> max(width[i], component.preferredWidth)
            else -> width[i]
          },
          parent.height
        )
        component.isVisible = true
      } else {
        component.isVisible = false
      }
    }
  }

  private fun Container?.asRow() =
    this as? ValueRowComponent<*>
      ?: throw IllegalStateException("ValueRowLayout is only applicable to ValueRow")
}

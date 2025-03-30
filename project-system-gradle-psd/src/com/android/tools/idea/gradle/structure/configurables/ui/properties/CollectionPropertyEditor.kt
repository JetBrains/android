/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.concurrency.getDoneOrNull
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.gradle.structure.configurables.ui.PropertyEditorCoreFactory
import com.android.tools.idea.gradle.structure.configurables.ui.UiUtil
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.whenCompletedInvokeOnEdt
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelCollectionPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.android.tools.idea.gradle.structure.model.meta.valueFormatter
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.getListSelectionBackground
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.FocusListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel

/**
 * A base for editors of properties which are collections of values of type [ValueT].
 */
abstract class CollectionPropertyEditor<out ModelPropertyT : ModelCollectionPropertyCore<*>, ValueT : Any>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<ValueT>,
  protected val editor: PropertyEditorCoreFactory<ModelPropertyCore<ValueT>, ModelPropertyContext<ValueT>, ValueT>,
  variablesScope: PsVariablesScope?,
  private val logValueEdited: () -> Unit
) : PropertyEditorBase<ModelPropertyT, ValueT>(property, propertyContext, variablesScope) {

  override val component: JPanel = JPanel(BorderLayout()).apply {
    isFocusable = false
  }
  open val statusComponent: JComponent? = null

  private var beingLoaded = false
  protected var tableModel: DefaultTableModel? = null; private set
  private val formatter = propertyContext.valueFormatter()
  private val knownValueRenderers: ListenableFuture<Map<ParsedValue<ValueT>, ValueRenderer>> =
    propertyContext.getKnownValues()
      .transform(MoreExecutors.directExecutor()) { buildKnownValueRenderers(it, formatter, null) }
      .also {
        it.whenCompletedInvokeOnEdt {
          UiUtil.revalidateAndRepaint(component)
        }
      }


  protected val table: JBTable = JBTable()
    .apply {
      rowHeight = calculateMinRowHeight()
    }
    .also {
      component.add(
        ToolbarDecorator.createDecorator(it)
          .setAddAction {
            addItem()
            logValueEdited()
          }
          .setRemoveAction {
            removeItem()
            logValueEdited()
          }
          .setPreferredSize(Dimension(450, it.rowHeight * 3))
          .setToolbarPosition(ActionToolbarPosition.RIGHT)
          .createPanel()
      )
    }.also {
      it.patchScrolling()
    }

  protected fun loadValue() {
    beingLoaded = true
    try {
      tableModel = createTableModel()
      table.model = tableModel
      table.columnModel = createColumnModel()
    }
    finally {
      beingLoaded = false
    }
  }

  protected abstract fun createTableModel(): DefaultTableModel
  protected abstract fun createColumnModel(): TableColumnModel
  abstract fun addItem()
  protected abstract fun removeItem()
  protected abstract fun getPropertyAt(row: Int): ModelPropertyCore<ValueT>

  protected fun getValueAt(row: Int): Annotated<ParsedValue<ValueT>> = getPropertyAt(row).getParsedValue()
  protected fun setValueAt(row: Int, value: ParsedValue<ValueT>) = getPropertyAt(row).setParsedValue(value)
  private fun calculateMinRowHeight() = JBUI.scale(24)

  protected fun Annotated<ParsedValue<ValueT>>.toTableModelValue() = Value(this)
  protected fun ParsedValue<ValueT>.toTableModelValue() = Value(this.annotated())

  fun addFocusListener(listener: FocusListener) {
    table.addFocusListener(listener)
  }

  /**
   * An [Annotated] [ParsedValue] wrapper for the table model that defines a [toString] implementation compatible with the implementation
   * in [MyCellEditor].
   */
  protected inner class Value(val value: Annotated<ParsedValue<ValueT>>) {
    override fun toString(): String = buildString {
      append(value.value.getText(formatter))
      if (value.annotation != null) {
        append(" : ")
        append(value.annotation.toString())
      }
    }
  }

  inner class MyCellRenderer: TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      @Suppress("UNCHECKED_CAST")
      val parsedValue = (value as CollectionPropertyEditor<*, ValueT>.Value?)?.value ?: ParsedValue.NotSet.annotated()
      return SimpleColoredComponent().also {
        parsedValue
          .renderTo(
            it.toRenderer().toSelectedTextRenderer(isSelected && hasFocus),
            formatter,
            knownValueRenderers.getDoneOrNull() ?: emptyMap()
          )
        if (isSelected) it.background = getListSelectionBackground(hasFocus)
      }
    }
  }

  inner class MyCellEditor : PropertyCellEditor<ValueT>() {
    override fun Annotated<ParsedValue<ValueT>>.toModelValue(): Any = toTableModelValue()
    override fun initEditorFor(row: Int): ModelPropertyEditor<ValueT> =
        editor(getPropertyAt(row), propertyContext, variablesScope, this)
            .also { table.addTabKeySupportTo(it.component) }
  }
}

private fun JComponent.patchScrolling() {
  fun Component.parentScrollPane() = generateSequence<Component>(parent) { it.parent }.mapNotNull { it as? JScrollPane }.firstOrNull()
  var lastKnownPosition = 0
  val patchedScrollPane = parentScrollPane()
  patchedScrollPane?.addMouseWheelListener(MouseWheelListener { e ->
    val verticalBar = patchedScrollPane.verticalScrollBar
    if (verticalBar.value == lastKnownPosition
        && (e.wheelRotation < 0 && verticalBar.value == 0 || verticalBar.value == verticalBar.maximum - verticalBar.visibleAmount)) {
      val parentScrollPane = patchedScrollPane.parentScrollPane()
      if (parentScrollPane != null) {
        parentScrollPane.dispatchEvent(
          MouseWheelEvent(parentScrollPane, e.id, e.`when`, e.modifiers, e.xOnScreen - parentScrollPane.locationOnScreen.x,
                          e.yOnScreen - parentScrollPane.locationOnScreen.y, e.xOnScreen, e.yOnScreen, e.clickCount, e.isPopupTrigger,
                          e.scrollType, e.scrollAmount, e.wheelRotation, e.preciseWheelRotation))
      }
    }
    lastKnownPosition = verticalBar.value
  })
}

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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.common.selectionBackground
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.android.tools.property.ptable.ColumnFraction
import com.android.tools.property.ptable.ColumnFractionChangeHandler
import com.google.common.html.HtmlEscapers
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ToolbarUpdater
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.border.Border
import kotlin.math.roundToInt

/**
 * A panel to show text and an optional control for collapsing/expanding a section.
 *
 * When the text on this label is too wide for the allowable space, this label can can either
 * show training ellipsis or clip the text. In this way the control can be used in connection
 * with an [ExpandableItemsHandler].
 *
 * The control for collapsing/expanding a section is represented by a button with an image
 * controlled by the specified [model].
 */
class CollapsibleLabelPanel(
  val model: CollapsibleLabelModel,
  fontSize: UIUtil.FontSize,
  fontStyle: Int,
  actions: List<AnAction> = emptyList(),
  private val nameColumnFraction: ColumnFraction = ColumnFraction()
) : JPanel(BorderLayout()) {
  val label = ExpandableLabel()

  // The label wil automatically display ellipsis at the end of a string that is too long for the width
  private val valueWithTrailingEllipsis = model.name
  // As html the value will not have ellipsis at the end but simply cut off when the string is too long
  private val valueWithoutEllipsis = toHtml(model.name)

  private val expandAction = object : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
      toggle()
    }
  }

  private val expandButton = IconWithFocusBorder { if (model.expandable) expandAction else null }
  private val actionButtons = mutableListOf<FocusableActionButton>()
  private val resizeHandler = ColumnFractionChangeHandler(nameColumnFraction, { label.x }, { width }, ::onResizeModeChange)

  val text: String?
    get() = label.text

  val icon: Icon?
    get() = expandButton.icon

  var innerBorder: Border?
    get() = label.border
    set(value) {
      label.border = value
    }

  init {
    background = secondaryPanelBackground
    label.actualText = model.name
    label.font = UIUtil.getLabelFont(fontSize)
    if (fontStyle != Font.PLAIN) {
      label.font = label.font.deriveFont(fontStyle)
    }
    expandButton.border = JBUI.Borders.emptyRight(2)
    add(expandButton, BorderLayout.WEST)
    add(label, BorderLayout.CENTER)
    model.addValueChangedListener { valueChanged() }
    if (actions.isNotEmpty()) {
      val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(2), 0))
      actions.forEach {
        val button = FocusableActionButton(it)
        buttonPanel.add(button)
        actionButtons.add(button)
      }
      add(buttonPanel, BorderLayout.EAST)
      object : ToolbarUpdater(this) {
        override fun updateActionsImpl(forced: Boolean) {
          if (!ApplicationManager.getApplication().isDisposed) {
            actionButtons.forEach { it.update() }
          }
        }
      }
    }
    label.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(event: MouseEvent) {
        if (event.clickCount > 1) {
          toggle()
        }
      }
    })
    label.addMouseListener(resizeHandler)
    label.addMouseMotionListener(resizeHandler)
    valueChanged()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (resizeHandler.resizeMode) {
      paintResizeNibs(g)
    }
  }

  private fun paintResizeNibs(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.color = selectionBackground
    val x = (width * nameColumnFraction.value).roundToInt()
    g2.drawLine(x, 0, x, height)
    g2.dispose()
  }

  private fun onResizeModeChange(newResizeMode: Boolean) {
    label.cursor = Cursor.getPredefinedCursor(if (newResizeMode) Cursor.W_RESIZE_CURSOR else Cursor.DEFAULT_CURSOR)
    repaint()
  }

  private fun toggle() {
    model.expanded = !model.expanded
  }

  private fun valueChanged() {
    val revalidateParent = if (isVisible != model.visible) parent else null
    isVisible = model.visible
    label.isVisible = isVisible
    label.text = if (model.showEllipses) valueWithTrailingEllipsis else valueWithoutEllipsis
    label.foreground = if (model.enabled) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
    expandButton.icon = model.icon
    revalidateParent?.revalidate()
    revalidateParent?.repaint()
  }

  private fun toHtml(text: String): String {
    return "<html><nobr>${HtmlEscapers.htmlEscaper().escape(text)}</nobr></html>"
  }
}

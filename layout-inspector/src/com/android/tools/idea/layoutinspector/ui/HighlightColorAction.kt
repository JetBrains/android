/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.endX
import com.android.tools.idea.layoutinspector.model.startX
import com.android.tools.idea.layoutinspector.model.startY
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.tree.isActionVisible
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

const val COLOR1_START = 0xFF0000 // Red
const val COLOR2_START = 0x4F9EE3 // Blue
const val COLOR3_START = 0x479345 // Green
const val COLOR4_START = 0xFFC66D // Yellow
const val COLOR5_START = 0x871094 // Purple
const val COLOR6_START = 0xE1A336 // Orange

private const val COLOR1_END = 0xF5E6E7
private const val COLOR2_END = 0xEDF6FE
private const val COLOR3_END = 0xF0FAE8
private const val COLOR4_END = 0xFFFFE4
private const val COLOR5_END = 0xE3E7ED
private const val COLOR6_END = 0xF5F0E6

private const val LABEL_DIVIDER_HEIGHT = 10
private const val IMAGE_SIZE = 24 // including borders
private const val IMAGE_DIVIDER_WIDTH = 10
private const val IMAGE_FOCUS_BORDER = 2
private const val TITLE = "Recomposition Highlight Color"
private const val DESCRIPTION = "Click to choose the color highlight used on recomposed nodes"

/**
 * Action for setting the highlight color via a balloon popup.
 *
 * There are 6 predefined colors each presented by a colored button in the popup.
 * Selection and focus are always the same in the popup i.e. focus also indicates the current selection.
 */
object HighlightColorAction : AnAction(TITLE, DESCRIPTION, StudioIcons.LayoutInspector.RECOMPOSITION_COUNT) {

  override fun update(event: AnActionEvent) {
    super.update(event)
    val layoutInspector = LayoutInspector.get(event)
    val isConnected = layoutInspector?.currentClient?.isConnected ?: false
    event.presentation.isVisible = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS.get() &&
                                   StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS.get() &&
                                   StudioFlags.USE_COMPONENT_TREE_TABLE.get() &&
                                   layoutInspector?.treeSettings?.showRecompositions ?: false &&
                                   (!isConnected || isActionVisible(event, Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS))
    event.presentation.isEnabled = isConnected
  }

  override fun actionPerformed(event: AnActionEvent) {
    val popupStatus = event.getData(DEVICE_VIEW_POPUP_STATUS) ?: return
    // If the balloon is already being shown, ignore this request and let the focus change close it.
    // i.e. do NOT create another balloon.
    if (popupStatus.highlightColorBalloon != null) return
    val viewSettings = event.getData(DEVICE_VIEW_SETTINGS_KEY) ?: return
    val component = event.inputEvent?.component as? JComponent ?: return
    val closeAction: () -> Unit = { popupStatus.highlightColorBalloon?.hide() }
    popupStatus.highlightColorBalloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(HighlightColorSelectionPanel(viewSettings, closeAction))
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setFillColor(secondaryPanelBackground)
      .setRequestFocus(true)
      .createBalloon()
    popupStatus.highlightColorBalloon?.apply {
      show(northOf(component), Balloon.Position.above)
      Disposer.register(this) { popupStatus.highlightColorBalloon = null }
    }
  }

  private fun northOf(component: JComponent): RelativePoint {
    val visibleRect = component.visibleRect
    val point = Point(visibleRect.x + visibleRect.width / 2, visibleRect.y)
    return RelativePoint(component, point)
  }
}

/**
 * A color selection panel with a title and a color selection panel.
 */
internal class HighlightColorSelectionPanel(viewSettings: DeviceViewSettings, closeAction: () -> Unit) : JPanel(BorderLayout()) {
  private val colorPanel = JPanel()

  init {
    background = secondaryPanelBackground
    isFocusable = true
    isFocusCycleRoot = true
    focusTraversalPolicy = CyclicFocusTraversalPolicy(colorPanel, viewSettings)
    colorPanel.layout = BoxLayout(colorPanel, BoxLayout.X_AXIS)
    colorPanel.background = secondaryPanelBackground
    colorPanel.isFocusable = false
    colorPanel.add(GradientButton(Color(COLOR1_START), Color(COLOR1_END), viewSettings, closeAction))
    colorPanel.add(Box.createHorizontalStrut(JBUIScale.scale(IMAGE_DIVIDER_WIDTH)))
    colorPanel.add(GradientButton(Color(COLOR2_START), Color(COLOR2_END), viewSettings, closeAction))
    colorPanel.add(Box.createHorizontalStrut(JBUIScale.scale(IMAGE_DIVIDER_WIDTH)))
    colorPanel.add(GradientButton(Color(COLOR3_START), Color(COLOR3_END), viewSettings, closeAction))
    colorPanel.add(Box.createHorizontalStrut(JBUIScale.scale(IMAGE_DIVIDER_WIDTH)))
    colorPanel.add(GradientButton(Color(COLOR4_START), Color(COLOR4_END), viewSettings, closeAction))
    colorPanel.add(Box.createHorizontalStrut(JBUIScale.scale(IMAGE_DIVIDER_WIDTH)))
    colorPanel.add(GradientButton(Color(COLOR5_START), Color(COLOR5_END), viewSettings, closeAction))
    colorPanel.add(Box.createHorizontalStrut(JBUIScale.scale(IMAGE_DIVIDER_WIDTH)))
    colorPanel.add(GradientButton(Color(COLOR6_START), Color(COLOR6_END), viewSettings, closeAction))
    val headerLabel = JBLabel(TITLE).apply {
      background = UIUtil.TRANSPARENT_COLOR
      isOpaque = false
      border = JBUI.Borders.emptyBottom(LABEL_DIVIDER_HEIGHT)
    }
    add(headerLabel, BorderLayout.NORTH)
    add(colorPanel, BorderLayout.CENTER)
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(event: FocusEvent) {
        // The standard Balloon implementation will request focus set on this HighlightColorSelectionPanel.
        // Immediately delegate to one of the buttons.
        focusTraversalPolicy.getDefaultComponent(colorPanel)?.requestFocusInWindow()
      }
    })
    // Provide Home and End key actions:
    registerKeyboardAction({ toFirstBox() }, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0, false), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerKeyboardAction({ toLastBox() }, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0, false), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  private fun toFirstBox() = focusTraversalPolicy.getFirstComponent(colorPanel)?.requestFocusInWindow()
  private fun toLastBox() = focusTraversalPolicy.getLastComponent(colorPanel)?.requestFocusInWindow()
}

@VisibleForTesting
class GradientButton(
  val colorStart: Color,
  private val colorEnd: Color,
  private val viewSettings: DeviceViewSettings,
  private val close: () -> Unit
): JButton() {

  init {
    isFocusable = true
    border = JBUI.Borders.empty(IMAGE_FOCUS_BORDER)
    action = object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        // This action is called on mouse click, and the enter and space keys (and also escape).
        // We simply close the balloon, indicating that the chosen value has been accepted.
        close()
      }
    }
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(event: FocusEvent) {
        // It is a little weird to make changes based on focus.
        // However, the design does not have a separate selection model.
        viewSettings.highlightColor = colorStart.rgb.and(0xFFFFFF)
      }
    })
    registerAdditionalKeyboardActions()
  }

  override fun getPreferredSize() = JBDimension(IMAGE_SIZE, IMAGE_SIZE)
  override fun getMinimumSize() = JBDimension(IMAGE_SIZE, IMAGE_SIZE)
  override fun getMaximumSize() = JBDimension(IMAGE_SIZE, IMAGE_SIZE)

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    // Paint the gradient between the 2 given colors
    val g2 = g as Graphics2D
    val rect = Rectangle(0, 0, width, height).apply(border.getBorderInsets(this))
    g2.paint = GradientPaint(rect.startX, rect.startY, colorStart, rect.endX, rect.startY, colorEnd)
    g2.fillRect(rect.x, rect.y, rect.width, rect.height)
    if (hasFocus()) {
      // Paint the focus border if this is the selected / focused button
      DarculaUIUtil.paintFocusBorder(g2, width, height, 0f, true)
    }
  }

  private fun registerAdditionalKeyboardActions() {
    registerKeyboardAction(action, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), WHEN_FOCUSED)
    registerKeyboardAction(action, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), WHEN_FOCUSED)
    registerKeyboardAction({ transferFocusBackward() }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), WHEN_FOCUSED)
    registerKeyboardAction({ transferFocus() }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), WHEN_FOCUSED)
  }

  private fun Rectangle.apply(insets: Insets): Rectangle {
    x += insets.left
    y += insets.top
    width -= insets.left + insets.right
    height -= insets.top + insets.bottom
    return this
  }
}

/**
 * A [FocusTraversalPolicy] that cycles between the [GradientButton]s in the specified [container].
 */
internal class CyclicFocusTraversalPolicy(
  private val container: Container,
  private val viewSettings: DeviceViewSettings
) : FocusTraversalPolicy() {
  private val boxes: List<GradientButton>
    get() = container.components.filterIsInstance<GradientButton>()

  override fun getComponentAfter(aContainer: Container?, aComponent: Component): Component? =
    if (boxes.isNotEmpty()) boxes[(boxes.indexOf(aComponent) + 1).mod(boxes.size)] else null

  override fun getComponentBefore(aContainer: Container?, aComponent: Component): Component? =
    if (boxes.isNotEmpty()) boxes[(boxes.indexOf(aComponent) - 1).mod(boxes.size)] else null

  override fun getFirstComponent(aContainer: Container?): Component? = boxes.firstOrNull()

  override fun getLastComponent(aContainer: Container?): Component? = boxes.lastOrNull()

  /** Returns the [GradientButton] that represent the current settings in the [DeviceViewSettings]. */
  override fun getDefaultComponent(aContainer: Container?): Component? =
    boxes.firstOrNull { Color(viewSettings.highlightColor) == it.colorStart } ?: boxes.firstOrNull()
}

/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.common

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.event.NestedScrollPaneMouseWheelListener
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.SwingHelper
import org.intellij.lang.annotations.JdkConstants
import org.intellij.lang.annotations.MagicConstant
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.Point
import java.awt.event.InputEvent
import java.util.function.Predicate
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.border.Border
import kotlin.math.roundToInt

/**
 * Contains an assortment of utility functions for the UI tools in this module.
 */
object AdtUiUtils {
  /**
   * Shows the [JBPopup] above the given [component].
   */
  @JvmStatic
  fun JBPopup.showAbove(component: JComponent) {
    val northWest = RelativePoint(component, Point())

    addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        val popup = event.asPopup()
        val location = Point(popup.locationOnScreen).apply { y = northWest.screenPoint.y - popup.size.height }

        popup.setLocation(location)
      }
    })
    show(northWest)
  }

  /**
   * Default font to be used in the profiler UI.
   */
  @JvmField
  val DEFAULT_FONT = JBUI.Fonts.label(10f)

  /**
   * Default font to be used in an empty tool window.
   * eg Device File Explorer when no device is connected and Sqlite Explorer when no database has been opened.
   */
  @JvmField
  val EMPTY_TOOL_WINDOW_FONT = JBUI.Fonts.label(16f)

  /**
   * Default font color of charts, and component labels.
   */
  @JvmField
  val DEFAULT_FONT_COLOR = JBColor.foreground()

  @JvmField
  val DEFAULT_BORDER_COLOR: Color = border

  @JvmField
  val DEFAULT_TOP_BORDER: Border = BorderFactory.createMatteBorder(1, 0, 0, 0, DEFAULT_BORDER_COLOR)

  @JvmField
  val DEFAULT_LEFT_BORDER: Border = BorderFactory.createMatteBorder(0, 1, 0, 0, DEFAULT_BORDER_COLOR)

  @JvmField
  val DEFAULT_BOTTOM_BORDER: Border = BorderFactory.createMatteBorder(0, 0, 1, 0, DEFAULT_BORDER_COLOR)

  @JvmField
  val DEFAULT_RIGHT_BORDER: Border = BorderFactory.createMatteBorder(0, 0, 0, 1, DEFAULT_BORDER_COLOR)

  @JvmField
  val DEFAULT_HORIZONTAL_BORDERS: Border = BorderFactory.createMatteBorder(1, 0, 1, 0, DEFAULT_BORDER_COLOR)

  @JvmField
  val DEFAULT_VERTICAL_BORDERS: Border = BorderFactory.createMatteBorder(0, 1, 0, 1, DEFAULT_BORDER_COLOR)

  @JvmField
  val GBC_FULL = GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, Insets(0, 0, 0, 0), 0, 0)

  /**
   * Collapse a line of text to fit the availableSpace by truncating the string and pad the end with ellipsis.
   *
   * @param text           the original text.
   * @param metrics        the [FontMetrics] used to measure the text's width.
   * @param availableSpace the available space to render the text.
   * @param spaceThreshold if availableSpace is not larger than this threshold, return an empty string.
   * @return the fitted text
   */
  @JvmStatic
  fun shrinkToFit(text: String, metrics: FontMetrics, availableSpace: Float, spaceThreshold: Float): String {
    // FontMetrics#stringWidth(String) has some runtime overhead so the threshold is a performance optimization.
    return shrinkToFit(text) { s: String ->
      availableSpace > spaceThreshold && availableSpace >= metrics.stringWidth(s)
    }
  }

  /**
   * Similar to [.shrinkToFit],
   * but instead of a predicate to fit space it uses the font metrics compared to available space.
   */
  @JvmStatic
  fun shrinkToFit(text: String, metrics: FontMetrics, availableSpace: Float): String {
    return shrinkToFit(text, metrics, availableSpace, 0.0f)
  }

  /**
   * Collapses a line of text to fit the availableSpace by truncating the string and pad the end with ellipsis.
   *
   * @param text             the original text.
   * @param textFitPredicate predicate to test if text fits.
   * @return the fitted text.
   */
  @JvmStatic
  fun shrinkToFit(text: String, textFitPredicate: Predicate<String>): String {
    if (textFitPredicate.test(text)) {
      // Enough space - early return.
      return text
    }
    else if (!textFitPredicate.test(SwingHelper.ELLIPSIS)) {
      // No space to fit "..." - early return.
      return ""
    }
    var smallestLength = 0
    var largestLength = text.length
    var bestLength = smallestLength
    do {
      val midLength = smallestLength + (largestLength - smallestLength) / 2
      if (textFitPredicate.test("${text.substring(0, midLength)}${SwingHelper.ELLIPSIS}")) {
        bestLength = midLength
        smallestLength = midLength + 1
      }
      else {
        largestLength = midLength - 1
      }
    }
    while (smallestLength <= largestLength)

    // Note: Don't return "..." if that's all we could show
    return if (bestLength > 0) "${text.substring(0, bestLength)}${SwingHelper.ELLIPSIS}" else ""
  }

  /**
   * Does the reverse of [JBUIScale.scale]
   */
  @JvmStatic
  fun unscale(i: Int): Int {
    return (i / JBUIScale.scale(1.0f)).roundToInt()
  }

  /**
   * Returns the resulting sRGB color (no alpha) by overlaying a foreground color with a given opacity over a background color.
   *
   * @param backgroundRgb     the sRGB color of the background.
   * @param foregroundRbg     the sRGB color of the foreground.
   * @param foregroundOpacity the opacity of the foreground, in the range of 0.0 - 1.0
   * @return
   */
  @JvmStatic
  fun overlayColor(backgroundRgb: Int, foregroundRbg: Int, foregroundOpacity: Float): Color {
    val background = Color(backgroundRgb)
    val foreground = Color(foregroundRbg)
    return Color(
      (background.red * (1 - foregroundOpacity) + foreground.red * foregroundOpacity).roundToInt(),
      (background.green * (1 - foregroundOpacity) + foreground.green * foregroundOpacity).roundToInt(),
      (background.blue * (1 - foregroundOpacity) + foreground.blue * foregroundOpacity).roundToInt())
  }

  /**
   * Returns if the action key is held by the user for the given event. The action key is defined as the
   * meta key on mac, and control on other platforms.
   */
  @JvmStatic
  fun isActionKeyDown(event: InputEvent): Boolean {
    return if (SystemInfo.isMac) event.isMetaDown else event.isControlDown
  }

  /**
   * Returns the action mask for the current platform.<br></br>
   * On mac it's [InputEvent.META_DOWN_MASK] everything else is [InputEvent.CTRL_DOWN_MASK].
   */
  @JvmStatic
  @JdkConstants.InputEventMask
  @MagicConstant(valuesFromClass = InputEvent::class)
  fun getActionMask(): Int {
    return if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
  }

  /**
   * returns the action mask text for the current platform. On mac, we try to display the unicode char for cmd button.
   */
  @JvmStatic
  fun getActionKeyText(): String {
    if (SystemInfo.isMac) {
      val labelFont = StartupUiUtil.getLabelFont()
      return if (labelFont != null && labelFont.canDisplayUpTo(MacKeymapUtil.COMMAND) == -1) MacKeymapUtil.COMMAND else "Cmd"
    }
    return "Ctrl"
  }

  /**
   * Returns a separator that is vertically centered. It has a consistent size among Mac and Linux platforms, as [JSeparator] on
   * different platforms has different UI and different sizes.
   */
  @JvmStatic
  fun createHorizontalSeparator(): JComponent {
    val separatorWrapper = JPanel(TabularLayout("*", "*,Fit,*"))
    separatorWrapper.add(JSeparator(), TabularLayout.Constraint(1, 0))
    val size = Dimension(1, 2)
    separatorWrapper.minimumSize = size
    separatorWrapper.preferredSize = size
    separatorWrapper.isOpaque = false
    return separatorWrapper
  }

  /**
   * Creates a scroll pane that the vertical scrolling is delegated to upper level scroll pane and its own vertical scroll bar is hidden.
   * This is needed when the outer pane has vertical scrolling and the inner pane has horizontal scrolling.
   */
  @JvmStatic
  fun createNestedVScrollPane(component: JComponent): JBScrollPane {
    val scrollPane = JBScrollPane(component)
    NestedScrollPaneMouseWheelListener.installOn(scrollPane)
    return scrollPane
  }

  /**
   * Traverses up to the TooltipLayeredPane and sets the cursor on it.
   *
   * Returns the TooltipLayeredPane if found. Null otherwise.
   */
  @JvmStatic
  fun setTooltipCursor(container: Container, cursor: Cursor): Container? {
    var p: Container? = container
    while (p != null) {
      if (p is TooltipLayeredPane) {
        p.setCursor(cursor)
        break
      }
      p = p.parent
    }
    return p
  }

  /**
   * Returns all the child components of container recursively.
   */
  @JvmStatic
  fun allComponents(container: Container): Sequence<Component> =
    container.components.asSequence().flatMap {
      if (it is Container && it.componentCount != 0)
        sequenceOf(it) + allComponents(it)
      else sequenceOf(it)
    }
}


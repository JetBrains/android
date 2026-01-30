/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.streaming.core.FloatingToolbarContainer
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.util.Locale
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.math.roundToInt

/** Shows current zoom level of a zoomable component. Expands/collapses the zoom toolbar when clicked on. */
class ZoomLevelIndicator : DumbAwareAction(EmptyIcon.ICON_16), CustomComponentAction {

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    val zoomable = event.getData(ZOOMABLE_KEY)
    presentation.isEnabledAndVisible = zoomable != null
    if (zoomable != null) {
      val scale = zoomable.scale
      val scaleText = String.format(Locale.ROOT, "%d%%", (scale * 100).roundToInt())
      presentation.text = "Zoom Level: $scaleText"
      thisLogger().info("Zoom level indicator updated from ${presentation.description} to $scaleText") // b/479059316
      if (scaleText != presentation.description) {
        zoomLevelChanged = true;
      }
      presentation.description = scaleText
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    FloatingToolbarContainer.toggleActiveState(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      MyActionButton(this, presentation, place)

  private class MyActionButton(
    action: AnAction,
    presentation: Presentation,
    place: String
  ) : ActionButton(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

    private var cachedTextPainter: TextPainter? = null
    private val textPainter: TextPainter
      get() {
        val text = presentation.description ?: ""
        if (cachedTextPainter?.baseFont != font || cachedTextPainter?.text != text) {
          cachedTextPainter = null
        }
        return cachedTextPainter ?: TextPainter(font, text, width).also { cachedTextPainter = it }
      }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)

      val g2 = g.create() as Graphics2D
      g2.color = foreground
      textPainter.paintText(g2, Rectangle(0, 0, width, height))
    }
  }

  private class TextPainter(val baseFont: Font, val text: String, maxWidth: Int) {

    private val fontRenderContext: FontRenderContext = createFontRenderContext()
    private val adjustedFont: Font = baseFont.squeezeToFit(text, maxWidth)
    private val textBounds: Rectangle = computeTextBounds(adjustedFont, text)

    fun paintText(g: Graphics2D, centerIn: Rectangle) {
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, fontRenderContext.getAntiAliasingHint())
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fontRenderContext.getFractionalMetricsHint())
      val textLcdContrast = UIManager.get(RenderingHints.KEY_TEXT_LCD_CONTRAST) ?: UIUtil.getLcdContrastValue()
      g.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, textLcdContrast)
      g.font = adjustedFont
      val textBounds = textBounds
      val textX = centerIn.x - textBounds.x + (centerIn.width - textBounds.width) / 2
      val textY = centerIn.y - textBounds.y + (centerIn.height - textBounds.height) / 2
      if (zoomLevelChanged) { // b/479059316
        thisLogger().info("Painting zoom level indicator $text")
        zoomLevelChanged = false
      }
      g.drawString(text, textX, textY)
    }

    /**
     * If the given text is wider than [maxWidth] when rendered using this font, returns a font that
     * is squeezed horizontally so that it fits in [maxWidth]. Otherwise, returns this font.
     */
    private fun Font.squeezeToFit(text: String, maxWidth: Int): Font {
      var scale = 1.0
      var textWidth = computeTextWidth(text, this, scale)
      while (textWidth > maxWidth) {
        scale /= 2
        textWidth = computeTextWidth(text, this, scale)
      }
      if (textWidth < maxWidth) {
        var minScale = scale
        var maxScale = 1.0
        while (true) {
          val newScale = (minScale + maxScale) / 2
          if (newScale == scale) {
            break
          }
          scale = newScale
          textWidth = computeTextWidth(text, this, scale)
          when {
            textWidth < maxWidth -> minScale = scale
            textWidth > maxWidth -> maxScale = scale
            else -> break
          }
        }
        if (textWidth > maxWidth) {
          scale = minScale
        }
      }
      return scaled(scale)
    }

    private fun computeTextWidth(text: String, font: Font, scale: Double): Int =
        computeTextBounds(font.scaled(scale), text).width

    private fun computeTextBounds(font: Font, text: String): Rectangle =
        computePixelBounds(font, text, fontRenderContext)
  }
}

private fun Font.scaled(scale: Double): Font =
    if (scale == 1.0) this else deriveFont(AffineTransform.getScaleInstance(scale, 1.0))

private fun computePixelBounds(font: Font, text: String, context: FontRenderContext): Rectangle {
  return when {
    font.hasLayoutAttributes() -> TextLayout(text, font, context).getPixelBounds(context, 0f, 0f)
    else -> font.createGlyphVector(context, text).getPixelBounds(context, 0f, 0f)
  }
}

private fun createFontRenderContext(): FontRenderContext {
  val aaHint = UIManager.get(RenderingHints.KEY_TEXT_ANTIALIASING) ?: RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT
  val fmHint = UIManager.get(RenderingHints.KEY_FRACTIONALMETRICS) ?: RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT
  return FontRenderContext(null, aaHint, fmHint)
}

private var zoomLevelChanged = false; // b/479059316
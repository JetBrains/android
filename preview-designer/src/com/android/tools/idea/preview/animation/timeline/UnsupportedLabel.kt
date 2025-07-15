/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.animation.timeline

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.animation.DispatchToTargetAdapter
import com.android.tools.idea.preview.animation.InspectorColors
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

// TODO(b/329254843): remove mentioning of compose from common component
private const val LEARN_MORE_LINK =
  "https://developer.android.com/jetpack/compose/tooling/animation-preview"

/** Label displayed in [TimelinePanel] for unsupported components. */
class UnsupportedLabel(parent: JComponent, rowMinY: Int, minX: Int, maxX: Int) :
  TimelineElement(SupportedAnimationManager.FrozenState(false), minX, maxX) {
  private val scaleChangeListener = PropertyChangeListener { resize() }

  init {
    JBUIScale.addUserScaleChangeListener(scaleChangeListener)
  }

  private fun resize() {
    // Adjust label position if parent component was resized.
    label.location = labelPosition
  }

  private val labelPosition =
    Point(
      minX + InspectorLayout.labelOffset - InspectorLayout.boxedLabelOffset,
      rowMinY + InspectorLayout.labelOffset / 2,
    )

  private val label = LabelPanel.findOrCreateNew(parent).apply { this.location = labelPosition }

  /** [UnsupportedLabel] has a fixed height. */
  override val height = InspectorLayout.UNSUPPORTED_ROW_HEIGHT

  override fun contains(x: Int, y: Int): Boolean = label.bounds.contains(x, y)

  override fun paint(g: Graphics2D) {}

  override fun dispose() {
    // Don't destroy object, just make them invisible.
    label.isVisible = false
    JBUIScale.removeUserScaleChangeListener(scaleChangeListener)
  }

  private class LabelPanel private constructor(parent: JComponent) :
    JPanel(TabularLayout("Fit,5px,Fit,5px,Fit", "Fit")) {

    companion object {
      fun findOrCreateNew(parent: JComponent): LabelPanel {
        val search =
          parent.components
            .filterIsInstance<LabelPanel>()
            .firstOrNull { !it.isVisible }
            .apply { this?.isVisible = true }
        return search ?: LabelPanel(parent).apply { parent.add(this) }
      }
    }

    private val mouseListener = DispatchToTargetAdapter(parent)
    private val labelBorder = 2
    private val resizeAdapter =
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          val width = components.maxOf { it.width + it.location.x }
          val height = components.maxOf { it.height + it.location.y }
          size = Dimension(width + labelBorder * 2, height + labelBorder * 2)
        }
      }

    override fun paintComponent(g: Graphics?) {
      g?.color = InspectorColors.BOXED_LABEL_BACKGROUND
      g?.fillRoundRect(
        0,
        0,
        width,
        height,
        InspectorLayout.boxedLabelColorBoxArc.width,
        InspectorLayout.boxedLabelColorBoxArc.height,
      )
    }

    init {
      background = UIUtil.TRANSPARENT_COLOR
      border = JBEmptyBorder(labelBorder)
      isOpaque = false

      //    ___________________________________
      //   /                                   \
      //   |  ⚠️ Not yet supported Learn More ↗ |
      //   \___________________________________/

      add(JBLabel(StudioIcons.Common.WARNING), TabularLayout.Constraint(0, 0))

      add(
        JBLabel(message("animation.inspector.message.not.supported")).apply {
          font = JBFont.medium()
          foreground = InspectorColors.BOXED_LABEL_NAME_COLOR
        },
        TabularLayout.Constraint(0, 2),
      )

      add(
        HyperlinkLabel().apply {
          font = JBFont.medium()
          setHyperlinkTarget(LEARN_MORE_LINK)
          setHyperlinkText(message("animation.inspector.message.not.supported.learn.more"))
        },
        TabularLayout.Constraint(0, 4),
      )

      components.forEach {
        it.addMouseListener(mouseListener)
        it.addMouseMotionListener(mouseListener)
        it.addComponentListener(resizeAdapter)
      }
      resizeAdapter.componentResized(null)
    }
  }
}

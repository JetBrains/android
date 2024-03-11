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
package com.android.tools.idea.uibuilder.editor

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * The action group contains the actions to change the animation speed. Use the callback to receive
 * the speed factor when it is changed.
 */
class AnimationSpeedActionGroup(callback: (Double) -> Unit) :
  ActionGroup(null, true), TooltipDescriptionProvider {
  private val speedActions: Array<AnAction>
  private var currentSpeed: PlaySpeed

  init {
    currentSpeed = PlaySpeed.x1
    val speedIcon = SpeedIcon(currentSpeed.displayName)
    speedActions =
      PlaySpeed.values()
        .map {
          object : AnAction(it.displayName), Toggleable {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

            override fun update(e: AnActionEvent) {
              val selected = it == currentSpeed
              Toggleable.setSelected(e.presentation, selected)
            }

            override fun actionPerformed(e: AnActionEvent) {
              currentSpeed = it
              speedIcon.text = currentSpeed.displayName
              callback(it.speedFactor)
            }
          }
        }
        .toTypedArray()
    templatePresentation.icon = speedIcon
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = speedActions
}

private const val ICON_SIZE = 22

private class SpeedIcon(var text: String) : Icon {

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val originalColor = g.color
    val originalFont = g.font

    g.color = JBUI.CurrentTheme.Arrow.foregroundColor(c.isEnabled)
    g.font = g.font.deriveFont(Font.BOLD, JBUI.scaleFontSize(14f).toFloat())

    (g as Graphics2D).setRenderingHint(
      RenderingHints.KEY_TEXT_ANTIALIASING,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
    )

    val metrics = g.fontMetrics
    val strWidth = metrics.stringWidth(text)
    val stringY = (iconHeight - metrics.height) / 2 + metrics.ascent
    g.drawString(text, x + (iconWidth - strWidth) / 2, y + stringY - 1)

    g.font = originalFont
    g.color = originalColor
  }

  override fun getIconWidth(): Int = JBUI.scale(ICON_SIZE)

  override fun getIconHeight(): Int = JBUI.scale(ICON_SIZE)
}

@Suppress("EnumEntryName")
enum class PlaySpeed(val displayName: String, val speedFactor: Double) {
  x0_25("¼x", 0.25),
  x0_5("½x", 0.5),
  x1("1x", 1.0),
  x2("2x", 2.0),
  x4("4x", 4.0);

  override fun toString() = displayName
}

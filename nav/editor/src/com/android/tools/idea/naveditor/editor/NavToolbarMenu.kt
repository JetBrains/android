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
package com.android.tools.idea.naveditor.editor

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

abstract class NavToolbarMenu(protected val surface: NavDesignSurface, description: String, icon: Icon) :
    IconWithTextAction("", description, icon), Toggleable {
  protected val BACKGROUND_COLOR: Color = UIUtil.getListBackground()
  protected var balloonHasDisplayedAndClosed = false
  private var balloon: Balloon? = null
  private var button: JComponent? = null

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    button = super.createCustomComponent(presentation, place)
    return button!!
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (isBalloonVisible()) {
      hideBalloon()
    }
    else {
      val showComponent =
        e.inputEvent?.source as? JComponent
        ?: button
        ?: return
      show(showComponent)
    }
  }

  @VisibleForTesting
  fun isBalloonVisible() = balloon?.wasFadedOut() == false

  fun hideBalloon() = balloon?.hide()

  fun show(component: JComponent) {
    balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(mainPanel)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setRequestFocus(true)  // Note that this seems non-functional, since it requests focus before the balloon is shown
      .setBorderColor(secondaryPanelBackground)
      .setFillColor(BACKGROUND_COLOR)
      .createBalloon().also {
        it.addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            balloon = null
          }
        })
        it.show(RelativePoint.getSouthOf(component), Balloon.Position.below)
      }
  }

  abstract val mainPanel: JPanel
}
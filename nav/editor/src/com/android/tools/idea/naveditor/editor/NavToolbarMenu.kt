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
import com.intellij.openapi.ui.popup.JBPopupAdapter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

abstract class NavToolbarMenu(protected val surface: NavDesignSurface, description: String, icon: Icon, protected var buttonPresentation: Presentation? = null) :
    IconWithTextAction("", description, icon), Toggleable {
  protected val BACKGROUND_COLOR: Color = UIUtil.getListBackground()
  var balloon: Balloon? = null

  override fun actionPerformed(e: AnActionEvent) {
    if (isBalloonVisible()) {
      e.presentation.putClientProperty(Toggleable.SELECTED_PROPERTY, false)
      balloon?.hide()
    }
    else {
      e.presentation.putClientProperty(Toggleable.SELECTED_PROPERTY, true)
      show(e.inputEvent!!.source as JComponent)
    }
  }

  @VisibleForTesting
  fun isBalloonVisible() = balloon?.wasFadedOut() == false

  fun show(component: JComponent) {
    val balloonBuilder = JBPopupFactory.getInstance()
      .createBalloonBuilder(mainPanel)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setRequestFocus(true)  // Note that this seems non-functional, since it requests focus before the balloon is shown
    balloonBuilder.setBorderColor(secondaryPanelBackground)
    balloonBuilder.setFillColor(BACKGROUND_COLOR)
    balloon = balloonBuilder.createBalloon().also {
      it.addListener(object : JBPopupAdapter() {
        override fun onClosed(event: LightweightWindowEvent) {
          val presentation = buttonPresentation
          if (presentation != null) {
            presentation.putClientProperty(Toggleable.SELECTED_PROPERTY, false)
          }
        }
      })
      it.show(RelativePoint.getSouthOf(component), Balloon.Position.below)
    }
  }

  abstract val mainPanel: JPanel
}
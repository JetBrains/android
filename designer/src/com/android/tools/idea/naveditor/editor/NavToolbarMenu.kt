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

import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

abstract class NavToolbarMenu(protected val surface: NavDesignSurface, description: String, icon: Icon) : AnAction("", description, icon) {
  override fun actionPerformed(e: AnActionEvent) {
    val balloonBuilder = JBPopupFactory.getInstance()
      .createBalloonBuilder(mainPanel)
      .setShadow(true)
      .setHideOnAction(true)
      .setAnimationCycle(200)
    surface.currentSceneView?.colorSet?.subduedBackground?.let {
      balloonBuilder.setBorderColor(it)
      balloonBuilder.setFillColor(it)
    }
    balloonBuilder.createBalloon().show(RelativePoint.getSouthOf(e.inputEvent.source as JComponent), Balloon.Position.below)
  }

  abstract val mainPanel: JPanel
}
/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.scene.draw

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.scene.SceneContext
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics2D
import javax.swing.Icon

class DrawIcon(private val icon: Icon,
               private val rectangle: SwingRectangle,
               private val color: Color? = null) : DrawCommandBase() {

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.setRenderingHints(HQ_RENDERING_HINTS)
    val coloredIcon = color?.let { ColoredIconGenerator.generateColoredIcon(icon, it) } ?: icon
    val image = IconLoader.toImage(coloredIcon, ScaleContext.create(g)).let {
      ImageUtil.scaleImage(it, rectangle.width.toInt(), rectangle.height.toInt())
    }
    UIUtil.drawImage(g, image, rectangle.x.toInt(), rectangle.y.toInt(), null)
  }
}
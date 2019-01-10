/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.draw

import com.google.common.annotations.VisibleForTesting
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorOrNullToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColorOrNull
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.common.util.iconToImage
import icons.StudioIcons.NavEditor.Surface.DEEPLINK
import icons.StudioIcons.NavEditor.Surface.POP_ACTION
import icons.StudioIcons.NavEditor.Surface.START_DESTINATION
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.Rectangle2D

/**
 * [DrawIcon] is a DrawCommand that draws an icon
 * in the specified rectangle.
 */
data class DrawIcon(@SwingCoordinate private val rectangle: Rectangle2D.Float,
                    @VisibleForTesting val iconType: IconType,
                    private val color: Color? = null) : DrawCommandBase() {
  enum class IconType {
    START_DESTINATION,
    DEEPLINK,
    POP_ACTION
  }

  private val image: Image

  init {
    var icon = when (iconType) {
      DrawIcon.IconType.START_DESTINATION -> START_DESTINATION
      DrawIcon.IconType.DEEPLINK -> DEEPLINK
      DrawIcon.IconType.POP_ACTION -> POP_ACTION
    }

    if (color != null) {
      icon = ColoredIconGenerator.generateColoredIcon(icon, color.rgb)
    }

    image = iconToImage(icon).getScaledInstance(rectangle.width.toInt(), rectangle.height.toInt(), Image.SCALE_SMOOTH)
  }

  private constructor(tokens: Array<String>) : this(stringToRect2D(tokens[0]), IconType.valueOf(tokens[1]),
                                                    stringToColorOrNull((tokens[2])))

  constructor(serialized: String) : this(parse(serialized, 3))

  override fun serialize(): String {
    return buildString(javaClass.simpleName, rect2DToString(rectangle), iconType, colorOrNullToString(color))
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.setRenderingHints(HQ_RENDERING_HINTS)
    g.drawImage(image, rectangle.x.toInt(), rectangle.y.toInt(), null)
  }
}
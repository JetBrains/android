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
package com.android.tools.idea.common.scene.draw

import com.android.tools.idea.common.scene.SceneContext
import com.google.common.collect.ImmutableMap
import java.awt.Graphics2D
import java.awt.RenderingHints

val HQ_RENDERING_HINTS: Map<RenderingHints.Key, Any> =
  ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING,
    RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING,
    RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION,
    RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    RenderingHints.KEY_STROKE_CONTROL,
    RenderingHints.VALUE_STROKE_PURE,
  )

abstract class DrawCommandBase(private val level: Int = 0) : DrawCommand {
  override fun getLevel(): Int = level

  override fun serialize() = ""

  final override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val g2 = g.create() as Graphics2D
    onPaint(g2, sceneContext)
    g2.dispose()
  }

  protected abstract fun onPaint(g: Graphics2D, sceneContext: SceneContext)
}

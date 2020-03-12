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
import java.awt.Graphics2D

/**
 * CompositeDrawCommand is an abstract draw command that aggregates a fixed list
 * of child draw commands that have a predefined order. Derived classes
 * provide the list of child commands as well as custom serialization.
 */
abstract class CompositeDrawCommand(private val level: Int = 0) : DrawCommand {
  val commands: List<DrawCommand> by lazy {
    buildCommands()
  }

  protected abstract fun buildCommands(): List<DrawCommand>

  override fun getLevel(): Int = level

  override fun paint(g: Graphics2D?, sceneContext: SceneContext?) {
    commands.forEach { it.paint(g, sceneContext) }
  }
}
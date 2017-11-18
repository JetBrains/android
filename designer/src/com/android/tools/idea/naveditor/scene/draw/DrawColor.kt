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

import com.android.tools.idea.common.scene.SceneContext
import java.awt.Color

/**
 * [DrawColor] is an enum used to allow colors to be serialized in draw commands.
 */
enum class DrawColor {
  COMPONENT_BACKGROUND {
    override fun color(context: SceneContext): Color = context.colorSet.componentBackground
  },

  FRAMES {
    override fun color(context: SceneContext): Color = context.colorSet.frames
  },

  HIGHLIGHTED_FRAMES {
    override fun color(context: SceneContext): Color = context.colorSet.highlightedFrames
  },

  SELECTED_FRAMES {
    override fun color(context: SceneContext): Color = context.colorSet.selectedFrames
  };

  abstract fun color(context: SceneContext): Color
}

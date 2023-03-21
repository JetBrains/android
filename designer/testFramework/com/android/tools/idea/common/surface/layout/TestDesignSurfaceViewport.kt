/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.common.surface.layout

import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.event.ChangeListener

class TestDesignSurfaceViewport(override val viewSize: Dimension,
                                override val viewRect: Rectangle,
                                override val viewportComponent: Component = JPanel(),
                                override val viewComponent: Component = JPanel(),)
  : DesignSurfaceViewport {

  override var viewPosition: Point
    get() = Point(viewRect.x, viewRect.y)
    set(value) {
      viewRect.x = value.x
      viewRect.y = value.y
    }
  override val extentSize: Dimension
    get() = Dimension(viewRect.width, viewRect.height)
  override fun addChangeListener(changeListener: ChangeListener) = Unit
}

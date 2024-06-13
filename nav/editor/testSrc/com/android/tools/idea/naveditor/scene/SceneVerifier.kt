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
package com.android.tools.idea.naveditor.scene

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.scene.draw.makeGraphicsMock
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import org.mockito.InOrder
import org.mockito.Mockito
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

val ACTION_COLOR = Color(0xdfe1e5)
val SELECTED_COLOR = Color(0x1886f7)
val FRAME_COLOR = Color(0xa7a7a7)
val HANDLE_COLOR = Color(0xf5f5f5)

@Suppress("UndesirableClassUsage")
val BUFFERED_IMAGE = BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB)

fun verifyScene(surface: DesignSurface<*>, verifier: (InOrder, Graphics2D) -> Unit) {
  val root = Mockito.mock(Graphics2D::class.java)

  val child = Mockito.mock(Graphics2D::class.java)
  whenever(root.create()).thenReturn(child)

  val graphics = makeGraphicsMock()
  whenever(child.create()).thenReturn(graphics)

  val inOrder = Mockito.inOrder(graphics)

  val scene = surface.scene!!
  val sceneManager = scene.sceneManager

  val list = DisplayList()
  scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, sceneManager))

  list.paint(root, sceneManager.sceneViews.first().context)
  verifier(inOrder, graphics)

  Mockito.verifyNoMoreInteractions(graphics)
}
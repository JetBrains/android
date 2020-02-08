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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.draw.verifyDrawFragment
import com.android.tools.idea.naveditor.scene.draw.verifyDrawHeader
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

private val SELECTED = Color(0x1886f7)
private const val HEADER_HEIGHT = 22f
private const val ID = "fragment1"

class DecoratorTest : NavTestCase() {
  fun testFragment() {
    val model = model("nav.xml") {
      navigation {
        fragment(ID)
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, false, false)
  }

  fun testFragmentWithHighlight() {
    val model = model("nav.xml") {
      navigation {
        fragment(ID)
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.SELECTED, false, false)
  }

  fun testFragmentWithStartDestination() {
    val model = model("nav.xml") {
      navigation(startDestination = ID) {
        fragment(ID)
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, true, false)
  }

  fun testFragmentWithDeepLink() {
    val model = model("nav.xml") {
      navigation {
        fragment(ID) {
          deeplink("deepLink1", "www.android.com")
        }
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, false, true)
  }

  private fun testFragmentDecorator(model: SyncNlModel, drawState: SceneComponent.DrawState, isStart: Boolean, hasDeepLink: Boolean) {
    val surface = NavDesignSurface(project, myRootDisposable)
    surface.model = model
    val sceneView = surface.focusedSceneView!!

    val sceneComponent = SceneComponent(surface.scene!!, model.find(ID)!!, Mockito.mock(HitProvider::class.java))

    sceneComponent.setPosition(40, 40)
    sceneComponent.setSize(80, 120)
    sceneComponent.drawState = drawState

    val displayList = DisplayList()
    val context = sceneView.context

    FragmentDecorator.buildListComponent(displayList, 0, context, sceneComponent)
    val root = Mockito.mock(Graphics2D::class.java)

    val child = Mockito.mock(Graphics2D::class.java)
    `when`(root.create()).thenReturn(child)

    val graphics = Mockito.mock(Graphics2D::class.java)
    `when`(child.create()).thenReturn(graphics)

    val metrics = Mockito.mock(FontMetrics::class.java)
    `when`(graphics.fontMetrics).thenReturn(metrics)
    `when`(graphics.getFontMetrics(ArgumentMatchers.any())).thenReturn(metrics)

    val inOrder = Mockito.inOrder(graphics)
    displayList.paint(root, context)

    val drawRect = sceneComponent.inlineDrawRect(sceneView).value
    val headerHeight = HEADER_HEIGHT * surface.scale.toFloat()
    val headerRect = Rectangle2D.Float(drawRect.x, drawRect.y - headerHeight, drawRect.width, headerHeight)
    val color = when (drawState) {
      SceneComponent.DrawState.SELECTED -> SELECTED
      else -> null
    }

    verifyDrawHeader(inOrder, graphics, headerRect, surface.scale, ID, isStart, hasDeepLink)
    verifyDrawFragment(inOrder, graphics, drawRect, surface.scale, color)
    Mockito.verifyNoMoreInteractions(graphics)
  }
}
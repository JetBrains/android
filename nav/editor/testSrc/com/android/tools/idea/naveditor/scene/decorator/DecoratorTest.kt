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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.BUFFERED_IMAGE
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.android.tools.idea.naveditor.scene.draw.PreviewType
import com.android.tools.idea.naveditor.scene.draw.makeGraphicsMock
import com.android.tools.idea.naveditor.scene.draw.verifyDrawAction
import com.android.tools.idea.naveditor.scene.draw.verifyDrawActivity
import com.android.tools.idea.naveditor.scene.draw.verifyDrawFragment
import com.android.tools.idea.naveditor.scene.draw.verifyDrawHeader
import com.android.tools.idea.naveditor.scene.draw.verifyDrawHorizontalAction
import com.android.tools.idea.naveditor.scene.draw.verifyDrawNestedGraph
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.scale.ScaleContext
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.verifyNoMoreInteractions
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

private val SELECTED_COLOR = Color(0x1886f7)
private val TEXT_COLOR = Color(0xa7a7a7)
private const val HEADER_HEIGHT = 22f
private const val FRAGMENT_ID = "fragment1"
private const val ACTIVITY_ID = "activity1"
private const val NESTED_ID = "nested1"

private const val ACTIVITY_PADDING = 8f
private const val ACTIVITY_TEXT_HEIGHT = 26f

private val ACTION_COLOR = Color(-0x4d585859, true)

class DecoratorTest : NavTestCase() {
  private lateinit var surface: DesignSurface<*>

  override fun setUp() {
    super.setUp()
    surface = NavDesignSurface(project, myRootDisposable)
  }

  fun testFragment() {
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID)
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL)
  }

  fun testFragmentWithHighlight() {
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID)
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.SELECTED)
  }

  fun testFragmentWithStartDestination() {
    val model = model("nav.xml") {
      navigation(startDestination = FRAGMENT_ID) {
        fragment(FRAGMENT_ID)
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, isStart = true)
  }

  fun testFragmentWithDeepLink() {
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID) {
          deeplink("deepLink1", "www.android.com")
        }
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, hasDeepLink = true)
  }

  fun testFragmentWithLayout() {
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID, layout = "mylayout")
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, previewType = PreviewType.UNAVAILABLE)
  }

  fun testFragmentWithName() {
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID, name = "foo.Bar")
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, previewType = PreviewType.UNAVAILABLE)
  }

  fun testFragmentWithLayoutAndName() {
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID, layout = "mylayout", name = "foo.Bar")
      }
    }
    testFragmentDecorator(model, SceneComponent.DrawState.NORMAL, previewType = PreviewType.UNAVAILABLE)
  }

  private fun testFragmentDecorator(model: NlModel, drawState: SceneComponent.DrawState, isStart: Boolean = false,
                                    hasDeepLink: Boolean = false, previewType: PreviewType = PreviewType.PLACEHOLDER) {
    surface.model = model
    val sceneView = surface.focusedSceneView!!

    val sceneComponent = makeSceneComponent(FRAGMENT_ID, drawState)

    val drawRect = sceneComponent.inlineDrawRect(sceneView).value
    val headerRect = makeHeaderRectangle(drawRect)

    val color = when (drawState) {
      SceneComponent.DrawState.SELECTED -> SELECTED_COLOR
      else -> null
    }

    verifyDecorator(FragmentDecorator, sceneComponent, sceneView.context) { inOrder, g ->
      verifyDrawHeader(inOrder, g, headerRect, surface.scale, FRAGMENT_ID, isStart, hasDeepLink)
      verifyDrawFragment(inOrder, g, drawRect, surface.scale, color, previewType)
    }
  }

  fun testFragmentWithImage() {
    val layoutFile = myFixture.addFileToProject("res/layout/mylayout.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                                           "<android.support.constraint.ConstraintLayout/>") as XmlFile
    waitForResourceRepositoryUpdates()
    val model = model("nav.xml") {
      navigation {
        fragment(FRAGMENT_ID, layout = "mylayout")
      }
    }

    surface.model = model
    val sceneView = surface.focusedSceneView!!

    val refinableImage = RefinableImage(BUFFERED_IMAGE)

    val origThumbnailManager = ThumbnailManager.getInstance(myFacet)
    val thumbnailManager = Mockito.mock(ThumbnailManager::class.java)
    val configuration = model.surface.configurations.single()

    val sceneComponent = makeSceneComponent(FRAGMENT_ID, SceneComponent.DrawState.NORMAL)
    val drawRect = sceneComponent.inlineDrawRect(sceneView).value
    val headerRect = makeHeaderRectangle(drawRect)
    val dimensions = Dimension(drawRect.width.toInt(), drawRect.height.toInt())
    val scaleContext = ScaleContext.createIdentity()

    Mockito.doReturn(refinableImage).whenever(thumbnailManager).getThumbnail(layoutFile, configuration, dimensions, scaleContext)

    try {
      ThumbnailManager.setInstance(myFacet, thumbnailManager)
      verifyDecorator(FragmentDecorator, sceneComponent, sceneView.context) { inOrder, g ->
        verifyDrawHeader(inOrder, g, headerRect, surface.scale, FRAGMENT_ID, false, false)
        verifyDrawFragment(inOrder, g, drawRect, surface.scale, null, PreviewType.IMAGE)
      }
    }
    finally {
      ThumbnailManager.setInstance(myFacet, origThumbnailManager)
    }

    Mockito.verify(thumbnailManager).getThumbnail(layoutFile, configuration, dimensions, scaleContext)
  }

  fun testActivity() {
    val model = model("nav.xml") {
      navigation {
        activity(ACTIVITY_ID)
      }
    }
    testActivityDecorator(model, SceneComponent.DrawState.NORMAL)
  }

  fun testActivityWithHighlight() {
    val model = model("nav.xml") {
      navigation {
        activity(ACTIVITY_ID)
      }
    }
    testActivityDecorator(model, SceneComponent.DrawState.SELECTED)
  }

  fun testActivityWithStartDestination() {
    val model = model("nav.xml") {
      navigation(startDestination = ACTIVITY_ID) {
        activity(ACTIVITY_ID)
      }
    }
    testActivityDecorator(model, SceneComponent.DrawState.NORMAL, isStart = true)
  }

  fun testActivityWithDeepLink() {
    val model = model("nav.xml") {
      navigation {
        activity(ACTIVITY_ID) {
          deeplink("deepLink1", "www.android.com")
        }
      }
    }
    testActivityDecorator(model, SceneComponent.DrawState.NORMAL, hasDeepLink = true)
  }

  private fun testActivityDecorator(model: NlModel, drawState: SceneComponent.DrawState,
                                    isStart: Boolean = false, hasDeepLink: Boolean = false) {
    surface.model = model
    val sceneView = surface.focusedSceneView!!

    val sceneComponent = makeSceneComponent(ACTIVITY_ID, drawState)

    val drawRect = sceneComponent.inlineDrawRect(sceneView).value
    val headerRect = makeHeaderRectangle(drawRect)

    val padding = ACTIVITY_PADDING * surface.scale.toFloat()
    val textHeight = ACTIVITY_TEXT_HEIGHT * surface.scale.toFloat()

    val x = drawRect.x + padding
    val y = drawRect.y + padding
    val width = drawRect.width - padding * 2
    val height = drawRect.height - padding - textHeight
    val imageRect = Rectangle2D.Float(x, y, width, height)

    verifyDecorator(ActivityDecorator, sceneComponent, sceneView.context) { inOrder, g ->
      verifyDrawHeader(inOrder, g, headerRect, surface.scale, ACTIVITY_ID, isStart, hasDeepLink)
      verifyDrawActivity(inOrder, g, drawRect, imageRect, surface.scale, frameColor(drawState), frameThickness(drawState), TEXT_COLOR)
    }
  }

  fun testNestedGraph() {
    val model = model("nav.xml") {
      navigation {
        navigation(NESTED_ID)
      }
    }
    testNavigationDecorator(model, SceneComponent.DrawState.NORMAL)
  }

  fun testNestedGraphWithHighlight() {
    val model = model("nav.xml") {
      navigation {
        navigation(NESTED_ID)
      }
    }
    testNavigationDecorator(model, SceneComponent.DrawState.SELECTED)
  }

  fun testNestedGraphWithStartDestination() {
    val model = model("nav.xml") {
      navigation(startDestination = NESTED_ID) {
        navigation(NESTED_ID)
      }
    }
    testNavigationDecorator(model, SceneComponent.DrawState.NORMAL, isStart = true)
  }

  fun testNestedGraphWithDeepLink() {
    val model = model("nav.xml") {
      navigation {
        navigation(NESTED_ID) {
          deeplink("deepLink1", "www.android.com")
        }
      }
    }
    testNavigationDecorator(model, SceneComponent.DrawState.NORMAL, hasDeepLink = true)
  }

  private fun testNavigationDecorator(model: NlModel, drawState: SceneComponent.DrawState,
                                      isStart: Boolean = false, hasDeepLink: Boolean = false) {
    surface.model = model
    val sceneView = surface.focusedSceneView!!

    val sceneComponent = makeSceneComponent(NESTED_ID, drawState)

    val drawRect = sceneComponent.inlineDrawRect(sceneView).value
    val headerRect = makeHeaderRectangle(drawRect)

    verifyDecorator(NavigationDecorator, sceneComponent, sceneView.context) { inOrder, g ->
      verifyDrawHeader(inOrder, g, headerRect, surface.scale, NESTED_ID, isStart, hasDeepLink)
      verifyDrawNestedGraph(inOrder, g, drawRect, surface.scale, frameColor(drawState), frameThickness(drawState), "Nested Graph",
                            TEXT_COLOR)
    }
  }

  fun testAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("f1_to_f2", destination = "fragment2")
        }
        fragment("fragment2")
      }
    }
    testActionDecorator(model, "f1_to_f2", SceneComponent.DrawState.NORMAL)
  }

  fun testSelectedAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("f1_to_f2", destination = "fragment2")
        }
        fragment("fragment2")
      }
    }
    testActionDecorator(model, "f1_to_f2", SceneComponent.DrawState.SELECTED)
  }

  fun testPopAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("f1_to_f2", destination = "fragment2", popUpTo = "fragment2")
        }
        fragment("fragment2")
      }
    }
    testActionDecorator(model, "f1_to_f2", SceneComponent.DrawState.NORMAL, isPop = true)
  }

  fun testSelfAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("f1_to_f1", destination = "fragment1")
        }
      }
    }
    testActionDecorator(model, "f1_to_f1", SceneComponent.DrawState.NORMAL)
  }

  fun testHorizontalAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        action("root_to_f1", destination = "fragment1")
      }
    }

    surface.model = model
    val sceneView = surface.focusedSceneView!!
    val sceneComponent = makeSceneComponent("root_to_f1", SceneComponent.DrawState.NORMAL)

    verifyDecorator(ActionDecorator, sceneComponent, sceneView.context) { inOrder, g ->
      verifyDrawHorizontalAction(inOrder, g, sceneComponent.inlineDrawRect(sceneView).value, sceneView.scale,
                                 actionColor(SceneComponent.DrawState.NORMAL))
    }
  }

  private fun testActionDecorator(model: NlModel, id: String, drawState: SceneComponent.DrawState, isPop: Boolean = false) {
    surface.model = model
    val sceneView = surface.focusedSceneView!!

    val sceneComponent = SceneComponent(surface.scene!!, surface.models.first().find(id)!!, Mockito.mock(HitProvider::class.java))
    if (drawState == SceneComponent.DrawState.SELECTED) {
      sceneComponent.isSelected = true
    }

    verifyDecorator(ActionDecorator, sceneComponent, sceneView.context) { inOrder, g ->
      verifyDrawAction(inOrder, g, actionColor(drawState), isPop)
    }
  }

  private fun verifyDecorator(decorator: SceneDecorator,
                              component: SceneComponent,
                              context: SceneContext,
                              verifier: (InOrder, Graphics2D) -> Unit) {
    val root = Mockito.mock(Graphics2D::class.java)

    val child = Mockito.mock(Graphics2D::class.java)
    whenever(root.create()).thenReturn(child)

    val graphics = makeGraphicsMock()
    whenever(child.create()).thenReturn(graphics)

    val inOrder = Mockito.inOrder(graphics)

    val displayList = DisplayList()
    decorator.buildListComponent(displayList, 0, context, component)
    displayList.paint(root, context)
    verifier(inOrder, graphics)

    verifyNoMoreInteractions(graphics)
  }

  private fun makeSceneComponent(id: String, state: SceneComponent.DrawState): SceneComponent {
    val sceneComponent = SceneComponent(surface.scene!!, surface.models.first().find(id)!!, Mockito.mock(HitProvider::class.java))

    sceneComponent.setPosition(40, 40)
    sceneComponent.setSize(80, 120)
    sceneComponent.drawState = state

    return sceneComponent
  }

  private fun makeHeaderRectangle(drawRect: Rectangle2D.Float): Rectangle2D.Float {
    val headerHeight = HEADER_HEIGHT * surface.scale.toFloat()
    return Rectangle2D.Float(drawRect.x, drawRect.y - headerHeight, drawRect.width, headerHeight)
  }

  companion object {
    private fun frameColor(state: SceneComponent.DrawState) = when (state) {
      SceneComponent.DrawState.SELECTED -> SELECTED_COLOR
      else -> TEXT_COLOR
    }

    private fun actionColor(state: SceneComponent.DrawState) = when (state) {
      SceneComponent.DrawState.SELECTED -> SELECTED_COLOR
      else -> ACTION_COLOR
    }

    private fun frameThickness(state: SceneComponent.DrawState) = when (state) {
      SceneComponent.DrawState.SELECTED -> 2f
      else -> 1f
    }
  }
}
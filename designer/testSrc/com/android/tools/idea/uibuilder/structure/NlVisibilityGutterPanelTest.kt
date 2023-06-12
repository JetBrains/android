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
package com.android.tools.idea.uibuilder.structure

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.Test
import org.mockito.Mockito
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class NlVisibilityGutterPanelTest: LayoutTestCase() {

  private var myPanel: NlVisibilityGutterPanel? = null
  private var myModel: SyncNlModel? = null
  private var myTree: NlComponentTree? = null
  private var mySurface: NlDesignSurface? = null

  @Volatile
  private var myDisposable: Disposable? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myPanel = NlVisibilityGutterPanel()
    myModel = generateModelWithFlow()
    myModel!!.getUpdateQueue().setPassThrough(true)
    // If using a lambda, it can be reused by the JVM and causing an exception because the Disposable is already disposed.
    myDisposable = object : Disposable {
      override fun dispose() { }
    }
    mySurface = NlDesignSurface.builder(project, myDisposable!!)
      .setSceneManagerProvider { surface: NlDesignSurface, model: NlModel ->
        object : SyncLayoutlibSceneManager(surface, model as SyncNlModel) {
          override fun renderAsync(trigger: LayoutEditorRenderResult.Trigger?, ignore: AtomicBoolean): CompletableFuture<RenderResult> {
            return CompletableFuture.completedFuture(null)
          }
        }
      }
      .build()
    mySurface!!.setModel(myModel)
    myTree = NlComponentTree(project, mySurface, myPanel)
    myTree!!.updateQueue.isPassThrough = true
    myTree!!.updateQueue.flush()
    Disposer.register(myDisposable!!, myTree!!)
    Disposer.register(myDisposable!!, myPanel!!)
  }

  @Throws(java.lang.Exception::class)
  override fun tearDown() {
    try {
      Disposer.dispose(myModel!!)
      Disposer.dispose(myDisposable!!)
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myDisposable = null
      mySurface = null
      myModel = null
      myTree = null
      myPanel = null
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun testConstruction() {
    assertNotNull(myPanel)
    val panel = myPanel!!
    val list = panel.list

    assertEquals(-1, list.currClicked)
    assertEquals(-1, list.currHovered)
    assertEquals(8, panel.list.model.size)
  }

  @Test
  fun testCollapse() {
    assertNotNull(myPanel)
    val panel = myPanel!!
    val tree = myTree!!

    val path = tree.getPathForRow(4)
    val last = path.lastPathComponent

    assertTrue(last is NlComponent)
    assertTrue((last as NlComponent).tagName.contains("ConstraintLayout"))

    tree.collapsePath(path)
    assertEquals(5, panel.list.model.size)

    tree.expandPath(path)
    assertEquals(8, panel.list.model.size)
  }

  @Test
  fun testMove() {
    assertNotNull(myPanel)
    val panel = myPanel!!

    panel.list.mouseListener.mouseMoved(mockEventAt(2))
    assertEquals(2, panel.list.currHovered)
    assertEquals(-1, panel.list.currClicked)

    panel.list.mouseListener.mouseMoved(mockEventAt(3))
    assertEquals(3, panel.list.currHovered)
    assertEquals(-1, panel.list.currClicked)
  }

  @Test
  fun testExit() {
    assertNotNull(myPanel)
    val panel = myPanel!!

    panel.list.mouseListener.mouseMoved(mockEventAt(2))
    panel.list.mouseListener.mouseExited(mockEventAt(2))
    assertEquals(-1, panel.list.currHovered)
    assertEquals(-1, panel.list.currClicked)
  }

  @Test
  fun testClick() {
    assertNotNull(myPanel)
    val panel = myPanel!!

    panel.list.mouseListener.mouseMoved(mockEventAt(2))

    panel.list.mouseListener.mouseClicked(mockEventAt(1))
    assertEquals(1, panel.list.currClicked)
    assertEquals(-1, panel.list.currHovered)
  }

  private fun generateModelWithFlow(): SyncNlModel {
    val builder = model("visibility_gutter_panel.xml",
                        component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
                          .withBounds(0, 0, 1000, 1000)
                          .matchParentWidth()
                          .matchParentHeight()
                          .children(
                            component(SdkConstants.BUTTON)
                              .withBounds(0, 0, 200, 200)
                              .id("@+id/button1")
                              .wrapContentWidth()
                              .wrapContentHeight(),
                            component(SdkConstants.BUTTON)
                              .withBounds(0, 0, 200, 200)
                              .id("@+id/button2")
                              .wrapContentWidth()
                              .wrapContentHeight(),
                            component(SdkConstants.BUTTON)
                              .withBounds(0, 0, 200, 200)
                              .id("@+id/button3")
                              .wrapContentWidth()
                              .wrapContentHeight(),
                            component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
                              .id("@+id/layout1")
                              .withBounds(0, 0, 500, 500)
                              .matchParentWidth()
                              .matchParentHeight()
                              .children(
                                component(SdkConstants.TEXT_VIEW)
                                  .withBounds(0, 0, 100, 100)
                                  .id("@+id/text1")
                                  .wrapContentWidth()
                                  .wrapContentHeight(),
                                component(SdkConstants.TEXT_VIEW)
                                  .withBounds(0, 0, 100, 100)
                                  .id("@+id/text2")
                                  .wrapContentWidth()
                                  .wrapContentHeight(),
                                component(SdkConstants.TEXT_VIEW)
                                  .withBounds(0, 0, 100, 100)
                                  .id("@+id/text3")
                                  .wrapContentWidth()
                                  .wrapContentHeight())))
    return builder.build()
  }

  private fun mockEventAt(index: Int): MouseEvent {
    val x = 10 // arbitrary value
    val y = 10 + index * NlVisibilityButton.HEIGHT

    val event: MouseEvent = Mockito.mock(MouseEvent::class.java)
    whenever(event.button).thenReturn(BUTTON1)
    whenever(event.clickCount).thenReturn(1)
    whenever(event.point).thenReturn(Point(x, y))

    return event
  }
}
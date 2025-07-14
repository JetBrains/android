/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.io.readImage
import com.android.resources.ResourceType
import com.android.test.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.viewWindow
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class InspectorModelTest {
  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val disposableRule = DisposableRule()

  val disposable
    get() = disposableRule.disposable

  @Test
  fun testUpdatePropertiesOnly() {
    val id1A = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "v1A")
    val id1B = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "v1B")
    val model =
      model(disposable) {
        view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType", textValue = "rootA") {
          view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type", textValue = "v1A", viewId = id1A) {
            view(VIEW3, 5, 6, 7, 8, qualifiedName = "v3Type", textValue = "v3A")
          }
          view(VIEW2, 8, 7, 6, 5, qualifiedName = "v2Type", textValue = "v2A")
        }
      }
    val origRoot = model[ROOT]
    var isModified = false
    var newRootReported: ViewNode? = null
    model.addModificationListener { _, newWindow, structuralChange ->
      newRootReported = newWindow?.root
      isModified = structuralChange
    }

    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType", textValue = "rootB") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type", textValue = "v1B", viewId = id1B) {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type", textValue = "v2B")
      }

    val origNodes = model.root.flattenedList().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    // property change doesn't count as "modified."
    // TODO: confirm this behavior is as desired
    assertThat(isModified).isFalse()

    for ((id, orig) in origNodes) {
      assertThat(model[id]).isSameAs(orig)
    }
    assertThat(model[ROOT]?.layoutBounds?.x).isEqualTo(2)
    assertThat(model[ROOT]?.textValue).isEqualTo("rootB")
    assertThat(model[VIEW1]?.textValue).isEqualTo("v1B")
    assertThat(model[VIEW1]?.viewId).isEqualTo(id1B)
    assertThat(model[VIEW2]?.textValue).isEqualTo("v2B")
    assertThat(model[VIEW3]?.layoutBounds?.height).isEqualTo(6)
    assertThat(model[VIEW3]?.textValue).isEqualTo("")
    assertThat(newRootReported).isSameAs(origRoot)
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testChildCreated() {
    val image1 = TestUtils.resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/image1.png").readImage()
    val model =
      model(disposable) {
        view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
          view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") { image(image1) }
        }
      }
    var isModified = false
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    model.addModificationListener { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
      }

    val origNodes = model.root.flattenedList().associateBy { it.drawId }
    // Clear the draw view children, since this test isn't concerned with them and update won't work
    // correctly with them in place.
    ViewNode.writeAccess { origNodes.values.forEach { it.drawChildren.clear() } }

    model.update(newWindow, listOf(ROOT), 0)
    assertThat(isModified).isTrue()
    val view1 = model[VIEW1]!!
    assertThat(model.selection).isSameAs(view1)
    assertThat(model.hoveredNode).isSameAs(view1)

    val newNodes = model.root.flattenedList().associateBy { it.drawId }
    assertThat(newNodes.keys).containsExactlyElementsIn(origNodes.keys.plus(VIEW3))
    assertThat(children(origNodes[VIEW1]!!)).containsExactly(newNodes[VIEW3] ?: fail())
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testXrWindow() =
    withEmbeddedLayoutInspector(false) {
      val largeWindowWidth = WINDOWS_GAP * 10
      val smallWindowWidth = WINDOWS_GAP
      val windowHeight = 5

      val model = model(disposable) {}
      val window1 =
        viewWindow(
          rootViewDrawId = ROOT,
          x = 0,
          y = 0,
          width = largeWindowWidth,
          height = windowHeight,
          isXr = true,
        ) {
          view(VIEW1, 0, 0, 2, 2, qualifiedName = "view1")
        }
      val window2 =
        viewWindow(
          rootViewDrawId = ROOT2,
          x = 0,
          y = 0,
          width = largeWindowWidth,
          height = windowHeight,
          isXr = true,
        ) {
          view(VIEW2, 0, 0, 2, 2, qualifiedName = "view2")
        }
      val window3 =
        viewWindow(
          rootViewDrawId = ROOT3,
          x = 0,
          y = 0,
          width = smallWindowWidth,
          height = windowHeight,
          isXr = true,
        ) {
          view(VIEW3, 0, 0, 2, 2, qualifiedName = "view3")
        }
      val window4 =
        viewWindow(
          rootViewDrawId = ROOT4,
          x = 0,
          y = 0,
          width = smallWindowWidth,
          height = windowHeight,
          isXr = true,
        ) {
          view(VIEW4, 0, 0, 2, 2, qualifiedName = "view4")
        }
      var isModified = false
      model.addModificationListener { _, _, structuralChange -> isModified = structuralChange }

      model.update(window1, listOf(ROOT), 0)
      assertThat(isModified).isTrue()

      val nodes1 = model.root.flattenedList().associateBy { it.drawId }
      assertThat(nodes1).hasSize(3)
      assertThat(nodes1[ROOT]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes1[ROOT]!!.layoutBounds.y).isEqualTo(0)
      assertThat(nodes1[VIEW1]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes1[VIEW1]!!.layoutBounds.y).isEqualTo(0)

      model.update(window2, listOf(ROOT, ROOT2), 1)

      val nodes2 = model.root.flattenedList().associateBy { it.drawId }
      assertThat(nodes2).hasSize(5)
      assertThat(nodes2[ROOT]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes2[ROOT]!!.layoutBounds.y).isEqualTo(0)
      assertThat(nodes2[VIEW1]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes2[VIEW1]!!.layoutBounds.y).isEqualTo(0)

      assertThat(nodes2[ROOT2]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes2[ROOT2]!!.layoutBounds.y).isEqualTo(WINDOWS_GAP + windowHeight - 1)
      assertThat(nodes2[VIEW2]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes2[VIEW2]!!.layoutBounds.y).isEqualTo(WINDOWS_GAP + windowHeight - 1)

      model.update(window3, listOf(ROOT, ROOT2, ROOT3), 2)
      model.update(window4, listOf(ROOT, ROOT2, ROOT3, ROOT4), 3)

      val nodes3 = model.root.flattenedList().associateBy { it.drawId }
      assertThat(nodes3).hasSize(9)
      assertThat(nodes3[ROOT]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes3[ROOT]!!.layoutBounds.y).isEqualTo(0)
      assertThat(nodes3[VIEW1]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes3[VIEW1]!!.layoutBounds.y).isEqualTo(0)

      assertThat(nodes3[ROOT2]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes3[ROOT2]!!.layoutBounds.y).isEqualTo(WINDOWS_GAP + windowHeight - 1)
      assertThat(nodes3[VIEW2]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes3[VIEW2]!!.layoutBounds.y).isEqualTo(WINDOWS_GAP + windowHeight - 1)

      assertThat(nodes3[ROOT3]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes3[ROOT3]!!.layoutBounds.y).isEqualTo((2 * WINDOWS_GAP) + (2 * windowHeight))
      assertThat(nodes3[VIEW3]!!.layoutBounds.x).isEqualTo(0)
      assertThat(nodes3[VIEW3]!!.layoutBounds.y).isEqualTo((2 * WINDOWS_GAP) + (2 * windowHeight))

      assertThat(nodes3[ROOT4]!!.layoutBounds.x).isEqualTo(WINDOWS_GAP + smallWindowWidth)
      assertThat(nodes3[ROOT4]!!.layoutBounds.y).isEqualTo((2 * WINDOWS_GAP) + (2 * windowHeight))
      assertThat(nodes3[VIEW4]!!.layoutBounds.x).isEqualTo(WINDOWS_GAP + smallWindowWidth)
      assertThat(nodes3[VIEW4]!!.layoutBounds.y).isEqualTo((2 * WINDOWS_GAP) + (2 * windowHeight))
    }

  @Test
  fun testNodeDeleted() {
    val model =
      model(disposable) {
        view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
          view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
            view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
          }
        }
      }
    var isModified = false
    model.setSelection(model[VIEW3], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW3]
    model.addModificationListener { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type")
      }

    val origNodes = model.root.flattenedList().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    assertThat(isModified).isTrue()
    assertThat(model.selection).isNull()
    assertThat(model.hoveredNode).isNull()

    val newNodes = model.root.flattenedList().associateBy { it.drawId }
    assertThat(newNodes.keys.plus(VIEW3)).containsExactlyElementsIn(origNodes.keys)
    assertThat(children(origNodes[VIEW1]!!).isEmpty()).isTrue()
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testNodeChanged() {
    val model =
      model(disposable) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
          view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
            view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type") {
              compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 15, composeSkips = 12) {
                compose(COMPOSE1, "Text", "text.kt", 234, composeCount = 5, composeSkips = 22)
              }
            }
          }
          view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
        }
      }
    var isModified = false
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    model.addModificationListener { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW4, 8, 6, 4, 2, qualifiedName = "v4Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type") {
            compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 16, composeSkips = 52) {
              compose(COMPOSE1, "Text", "text.kt", 234, composeCount = 35, composeSkips = 33)
            }
          }
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }

    val origNodes = model.root.flattenedList().associateBy { it.drawId }

    model.update(newWindow, listOf(ROOT), 0)
    assertThat(model.maxRecomposition.count).isEqualTo(35)
    assertThat(model.maxRecomposition.skips).isEqualTo(52)
    assertThat(isModified).isTrue()
    assertThat(model.selection).isNull()
    assertThat(model.hoveredNode).isNull()

    assertThat(model[ROOT]).isSameAs(origNodes[ROOT])
    assertThat(model[VIEW2]).isSameAs(origNodes[VIEW2])

    assertThat(model[VIEW4]).isNotSameAs(origNodes[VIEW1])
    assertThat(children(model[ROOT]!!).map { it.drawId }).containsExactly(VIEW4, VIEW2)
    assertThat(model[VIEW4]?.qualifiedName).isEqualTo("v4Type")
    assertThat(model[VIEW3]?.qualifiedName).isEqualTo("v3Type")
    assertThat(model[VIEW3]?.layoutBounds?.y).isEqualTo(8)
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testWindows() {
    val model = InspectorModel(mock(), AndroidCoroutineScope(disposable))
    assertThat(model.isEmpty).isTrue()

    // add first window
    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type")
      }
    model.update(newWindow, listOf(ROOT), 0)
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    assertThat(model.isEmpty).isFalse()
    assertThat(model[VIEW1]).isNotNull()
    assertThat(children(model.root).map { it.drawId }).isEqualTo(listOf(ROOT))

    // add second window
    var window2 =
      window(VIEW2, VIEW2, 2, 4, 6, 8, rootViewQualifiedName = "root2Type") {
        view(VIEW3, 8, 6, 4, 2, qualifiedName = "v3Type")
      }
    model.update(window2, listOf(ROOT, VIEW2), 0)
    assertThat(model.isEmpty).isFalse()
    assertThat(model[VIEW1]).isNotNull()
    assertThat(model[VIEW3]).isNotNull()
    assertThat(model.selection).isSameAs(model[VIEW1])
    assertThat(model.hoveredNode).isSameAs(model[VIEW1])
    assertThat(children(model.root).map { it.drawId }).isEqualTo(listOf(ROOT, VIEW2))

    // reverse order of windows
    // same content but new instances, so model.update sees a change
    window2 =
      window(VIEW2, VIEW2, 2, 4, 6, 8, rootViewQualifiedName = "root2Type") {
        view(VIEW3, 8, 6, 4, 2, qualifiedName = "v3Type")
      }
    model.update(window2, listOf(VIEW2, ROOT), 1)
    assertThat(children(model.root).map { it.drawId }).isEqualTo(listOf(VIEW2, ROOT))

    // remove a window
    model.update(null, listOf(VIEW2), 0)
    assertThat(children(model.root).map { it.drawId }).isEqualTo(listOf(VIEW2))
    assertThat(model[VIEW1]).isNull()
    assertThat(model[VIEW3]).isNotNull()
    assertThat(model.selection).isNull()
    assertThat(model.hoveredNode).isNull()

    // clear
    model.update(null, listOf<Any>(), 0)
    assertThat(children(model.root)).isEmpty()
    assertThat(model.isEmpty).isTrue()
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testDrawTreeOnInitialCreateAndUpdate() {
    val model = model(disposable) {}
    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }
    // Clear out the drawChildren added by the test fixture so we only get the ones generated by
    // production code
    ViewNode.writeAccess { model.root.flatten().forEach { node -> node.drawChildren.clear() } }
    model.update(newWindow, listOf(ROOT), 0)

    // Verify that drawChildren are created corresponding to the tree
    ViewNode.readAccess {
      model.root.flatten().forEach { node ->
        assertThat(node.drawChildren.map { (it as DrawViewChild).unfilteredOwner })
          .containsExactlyElementsIn(node.children)
      }
    }

    val view2 = model[VIEW2]
    val newWindow2 =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW4, 10, 11, 12, 13, qualifiedName = "v4Type")
      }

    // Now update the model and verify that the draw tree is still as it was before.
    model.update(newWindow2, listOf(ROOT), 0)

    ViewNode.readAccess {
      model.root.flatten().forEach { node ->
        assertThat(node.drawChildren.map { (it as DrawViewChild).unfilteredOwner })
          .containsExactlyElementsIn(node.children.map { if (it.drawId == VIEW4) view2 else it })
      }
    }
  }

  @Test
  fun testThreadSafetyInModelLookup() {
    val model =
      model(disposable) {
        view(ROOT, x = 2, y = 4, width = 6, height = 8, qualifiedName = "root") {
          view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
            view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
          }
          view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
        }
      }

    val window1 =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW4, 8, 6, 4, 2, qualifiedName = "v4Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }

    val window2 =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }

    var stop = false
    var modelLoops = 0
    var lookupLoops = 0

    val modelRunnable: () -> Unit = {
      while (!stop) {
        model.update(window1, listOf(ROOT), 0)
        model.update(window2, listOf(ROOT), 0)
        modelLoops++
      }
    }

    val lookupRunnable: () -> Unit = {
      while (!stop) {
        model[VIEW1]
        lookupLoops++
      }
    }

    var exception: Throwable? = null
    val handler = Thread.UncaughtExceptionHandler { _, ex -> exception = ex }
    val t1 = Thread(modelRunnable)
    val t2 = Thread(lookupRunnable)
    t1.uncaughtExceptionHandler = handler
    t2.uncaughtExceptionHandler = handler
    t1.start()
    t2.start()
    while (t1.isAlive && modelLoops < 1000 || t2.isAlive && lookupLoops < 1000) {
      Thread.sleep(10L)
    }
    stop = true
    t1.join(1000L)
    t2.join(1000L)
    exception?.let { throw it }
  }

  @Test
  fun fireAttachStateEvent() {
    val model = InspectorModel(mock(), AndroidCoroutineScope(disposable))
    val mockListener = mock<(DynamicLayoutInspectorErrorInfo.AttachErrorState) -> Unit>()
    model.addAttachStageListener(mockListener)

    model.fireAttachStateEvent(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)

    verify(mockListener).invoke(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }

  @Test
  fun testHighlightCounts() {
    val virtualTimeScheduler = VirtualTimeScheduler()
    val scheduler = MoreExecutors.listeningDecorator(virtualTimeScheduler)

    val model =
      model(disposable, scheduler = scheduler) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
          compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
            compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 0, composeSkips = 0)
          }
        }
      }
    val compose1 = model[COMPOSE1] as ComposeViewNode
    val compose2 = model[COMPOSE2] as ComposeViewNode

    // Receive an update from the device with recomposition counts:
    val window1 =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 4, composeSkips = 0) {
          compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 1, composeSkips = 0)
        }
      }
    model.update(window1, listOf(ROOT), 0)

    // Check recomposition counts and highlight counts:
    assertThat(model.maxHighlight).isEqualTo(4.0f)
    assertThat(compose1.recompositions.count).isEqualTo(4)
    assertThat(compose1.recompositions.highlightCount).isEqualTo(4.0f)
    assertThat(compose2.recompositions.count).isEqualTo(1)
    assertThat(compose2.recompositions.highlightCount).isEqualTo(1f)

    // Advance timer to the next highlight decrease:
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(model.maxHighlight).isEqualTo(4.0f)
    assertThat(compose1.recompositions.count).isEqualTo(4)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(3.36f)
    assertThat(compose2.recompositions.count).isEqualTo(1)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(0.84f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(2.83f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(0.71f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(2.38f)
    assertThat(compose2.recompositions.highlightCount).isEqualTo(0f)

    // Receive another update from the device:
    val window2 =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 6, composeSkips = 2) {
          compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 3, composeSkips = 2)
        }
      }
    model.update(window2, listOf(ROOT), 0)

    // Check recomposition counts and highlight counts:
    assertThat(model.maxHighlight).isWithin(0.01f).of(4.38f)
    assertThat(compose1.recompositions.count).isEqualTo(6)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(4.38f)
    assertThat(compose2.recompositions.count).isEqualTo(3)
    assertThat(compose2.recompositions.highlightCount).isEqualTo(2f)

    // Advance timer and check:
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(model.maxHighlight).isWithin(0.01f).of(4.38f)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(3.68f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(1.68f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(3.10f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(1.41f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(2.60f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(1.19f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(2.19f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(1.00f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(1.84f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(0.84f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(1.55f)
    assertThat(compose2.recompositions.highlightCount).isWithin(0.01f).of(0.71f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(1.30f)
    assertThat(compose2.recompositions.highlightCount).isEqualTo(0f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(1.09f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(0.92f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(0.77f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(compose1.recompositions.highlightCount).isWithin(0.01f).of(0.65f)
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
    assertThat(model.maxHighlight).isEqualTo(0f)
    assertThat(compose1.recompositions.highlightCount).isEqualTo(0f)
    assertThat(compose2.recompositions.highlightCount).isEqualTo(0f)
  }

  @Test
  fun testHighlightCountDownDoNotStop() {
    val virtualTimeScheduler = VirtualTimeScheduler()
    val scheduler = MoreExecutors.listeningDecorator(virtualTimeScheduler)
    var stop = false
    var countdownStopped = false

    val model =
      model(disposable, scheduler = scheduler) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
          compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
            compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 0, composeSkips = 0)
          }
        }
      }
    // In this test do not spend time refreshingImages. We want to focus on the recomposition
    // countdown logic:
    model.windows.values.filterIsInstance<FakeAndroidWindow>().forEach { it.refreshImages = null }

    // Generate an update with a new number for the composeCount of COMPOSE2
    fun window1(count: Int) =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
          compose(COMPOSE2, "Text", "text.kt", 234, composeCount = count, composeSkips = 0)
        }
      }

    // Check that we are never in a state where neither thread can advance
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val lock = Object()
    fun check() {
      synchronized(lock) {
        val running =
          ViewNode.readAccess {
            model.maxHighlight > 0f || compose2.recompositions.highlightCount < DECREASE_BREAK_OFF
          }
        if (!running) {
          stop = true
          countdownStopped = true
        }
      }
    }

    // Start a thread that constantly runs the scheduler to decrease the highlight count in COMPOSE2
    val thread1 =
      Thread {
          while (!stop) {
            Thread.yield()
            if (model.maxHighlight > 0f) {
              virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
            } else {
              check()
            }
          }
        }
        .apply { start() }

    // Start a thread that constantly runs an update to increase the highlight count of COMPOSE2
    var count = 0
    val thread2 =
      Thread {
          while (!stop) {
            Thread.yield()
            if (compose2.recompositions.highlightCount < DECREASE_BREAK_OFF) {
              model.update(window1(++count), listOf(ROOT), 0)
            } else {
              check()
            }
          }
        }
        .apply { start() }

    // Run the 2 threads for 2 seconds:
    Thread.sleep(2000)

    // Stop both threads
    stop = true
    thread1.join(1000)
    thread2.join(1000)

    // At no time should both threads be waiting for each other:
    assertThat(countdownStopped).isFalse()
  }

  @Test
  fun testClear() {
    val model =
      model(disposable) {
        view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
          view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
            view(VIEW3, 5, 6, 7, 8, qualifiedName = "v3Type")
          }
          view(VIEW2, 8, 7, 6, 5, qualifiedName = "v2Type")
        }
      }

    model.foldInfo =
      InspectorModel.FoldInfo(
        97,
        InspectorModel.Posture.HALF_OPEN,
        InspectorModel.FoldOrientation.VERTICAL,
      )
    model.clear()
    assertThat(model.foldInfo).isNull()
  }

  @Test
  fun testModelIsClearedOnProcessChange() {
    val latch = CountDownLatch(1)
    val processModel = ProcessesModel(TestProcessDiscovery())
    val inspectorModel =
      InspectorModel(mock(), AndroidCoroutineScope(disposable), processesModel = processModel)
    assertThat(inspectorModel.isEmpty).isTrue()

    val observedNewWindows = mutableListOf<AndroidWindow?>()
    inspectorModel.addModificationListener { _, newWindow, _ ->
      observedNewWindows.add(newWindow)

      if (newWindow == null) {
        latch.countDown()
      }
    }

    // add first window
    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type")
      }
    inspectorModel.update(newWindow, listOf(ROOT), 0)

    assertThat(observedNewWindows).containsExactly(newWindow)

    processModel.selectedProcess = MODERN_DEVICE.createProcess()
    latch.await(2, TimeUnit.SECONDS)

    assertThat(observedNewWindows).containsExactly(newWindow, newWindow, null)
  }

  @Test
  fun testListenersAreInvoked() {
    val model =
      model(disposable) {
        view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
          view(VIEW1) { view(VIEW2) { view(VIEW3) } }
        }
      }
    val root = model[ROOT]
    val view1 = model[VIEW1]
    val view2 = model[VIEW2]
    val view3 = model[VIEW3]

    // Selection
    model.setSelection(root, SelectionOrigin.INTERNAL)
    val observedSelectedNodes = mutableListOf<Triple<ViewNode?, ViewNode?, SelectionOrigin>>()

    model.addSelectionListener { oldNode, newNode, origin ->
      observedSelectedNodes.add(Triple(oldNode, newNode, origin))
    }
    assertThat(observedSelectedNodes).containsExactly(Triple(root, root, SelectionOrigin.INTERNAL))

    model.setSelection(view2, SelectionOrigin.COMPONENT_TREE)
    assertThat(observedSelectedNodes)
      .containsExactly(
        Triple(root, root, SelectionOrigin.INTERNAL),
        Triple(root, view2, SelectionOrigin.COMPONENT_TREE),
      )

    model.setSelection(view3, SelectionOrigin.INTERNAL)
    assertThat(observedSelectedNodes)
      .containsExactly(
        Triple(root, root, SelectionOrigin.INTERNAL),
        Triple(root, view2, SelectionOrigin.COMPONENT_TREE),
        Triple(view2, view3, SelectionOrigin.INTERNAL),
      )

    model.setSelection(view3, SelectionOrigin.INTERNAL)
    assertThat(observedSelectedNodes)
      .containsExactly(
        Triple(root, root, SelectionOrigin.INTERNAL),
        Triple(root, view2, SelectionOrigin.COMPONENT_TREE),
        Triple(view2, view3, SelectionOrigin.INTERNAL),
        Triple(view3, view3, SelectionOrigin.INTERNAL),
      )

    model.setSelection(null, SelectionOrigin.COMPONENT_TREE)
    assertThat(observedSelectedNodes)
      .containsExactly(
        Triple(root, root, SelectionOrigin.INTERNAL),
        Triple(root, view2, SelectionOrigin.COMPONENT_TREE),
        Triple(view2, view3, SelectionOrigin.INTERNAL),
        Triple(view3, view3, SelectionOrigin.INTERNAL),
        Triple(view3, null, SelectionOrigin.COMPONENT_TREE),
      )

    // Hover
    model.hoveredNode = view1
    val observedHoverNodes = mutableListOf<Pair<ViewNode?, ViewNode?>>()

    model.addHoverListener { oldNode, newNode -> observedHoverNodes.add(Pair(oldNode, newNode)) }
    assertThat(observedHoverNodes).containsExactly(Pair(view1, view1))

    model.hoveredNode = view2
    assertThat(observedHoverNodes).containsExactly(Pair(view1, view1), Pair(view1, view2))

    model.hoveredNode = view2
    assertThat(observedHoverNodes).containsExactly(Pair(view1, view1), Pair(view1, view2))

    model.hoveredNode = null
    assertThat(observedHoverNodes)
      .containsExactly(Pair(view1, view1), Pair(view1, view2), Pair(view2, null))

    // Modification
    val newWindow = window(VIEW2, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType")
    model.update(newWindow, listOf(ROOT), 0)

    val observedNewWindows = mutableListOf<AndroidWindow?>()
    model.addModificationListener { old, new, isStructuralChange ->
      assertThat(old).isEqualTo(new)
      assertThat(isStructuralChange).isFalse()
      observedNewWindows.add(new)
    }
    assertThat(observedNewWindows).isEqualTo(model.windows.values.toList())

    // Connection
    model.updateConnection(DisconnectedClient)
    val observedClients = mutableListOf<InspectorClient>()

    model.addConnectionListener { observedClients.add(it) }
    assertThat(observedClients).containsExactly(DisconnectedClient)

    // Attach stage
    model.fireAttachStateEvent(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
    val observedStates = mutableListOf<DynamicLayoutInspectorErrorInfo.AttachErrorState>()
    model.addAttachStageListener { observedStates.add(it) }
    assertThat(observedStates)
      .containsExactly(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }

  @Test
  fun testUpdateWithNewIds() {
    val virtualTimeScheduler = VirtualTimeScheduler()
    val scheduler = MoreExecutors.listeningDecorator(virtualTimeScheduler)
    val model =
      model(disposable, scheduler = scheduler) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
          view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
            compose(COMPOSE1, "Column", "App.kt", 123) {
              compose(COMPOSE2, "Button", "App.kt", 123) {
                compose(COMPOSE3, "Text", "Button.kt", 234)
              }
            }
          }
        }
      }

    // Update the recomposition counts:
    val windowWithRecompositionCounts =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          compose(COMPOSE1, "Column", "App.kt", 123, composeCount = 1, composeSkips = 20) {
            compose(COMPOSE2, "Button", "App.kt", 123, composeCount = 15, composeSkips = 12) {
              compose(COMPOSE3, "Text", "Button.kt", 234, composeCount = 5, composeSkips = 22)
            }
          }
        }
      }
    model.update(windowWithRecompositionCounts, listOf(ROOT), 0)

    // Decrease the recomposition counts once:
    virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)

    // When Live Edit is used on one of the top nodes in compose, the compose runtime will generate
    // anchors for each composable, which in turn will create new draw ids for those composables
    // and their children. This test should verify that if the tree is identical in names we get
    // the same ViewNode instances after such an update such that the component tree has a change
    // to keep the same nodes open.
    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          compose(COMPOSE4, "Column", "App.kt", 123, composeCount = 3, composeSkips = 21) {
            compose(COMPOSE5, "Button", "App.kt", 123, composeCount = 16, composeSkips = 52) {
              compose(COMPOSE6, "Text", "Button.kt", 234, composeCount = 35, composeSkips = 33)
            }
          }
        }
      }

    val originalNodes = Collections.newSetFromMap<ViewNode>(IdentityHashMap())
    originalNodes.addAll(model.root.flattenedList())
    var hadStructuralChange = false
    model.addModificationListener { _, _, structuralChange ->
      hadStructuralChange = structuralChange
    }
    model.update(newWindow, listOf(ROOT), 1)

    // Check that all the node instances were maintained:
    assertThat(model.root.flattenedList().all { originalNodes.contains(it) })
    assertThat(hadStructuralChange).isFalse()

    // Check that we continue decreasing the recomposition counts:
    assertThat(model[COMPOSE4]!!.recompositions.count).isEqualTo(3)
    assertThat(model[COMPOSE4]!!.recompositions.highlightCount).isWithin(0.01f).of(2.84f)
    assertThat(model[COMPOSE5]!!.recompositions.count).isEqualTo(16)
    assertThat(model[COMPOSE5]!!.recompositions.highlightCount).isWithin(0.01f).of(13.61f)
    assertThat(model[COMPOSE6]!!.recompositions.count).isEqualTo(35)
    assertThat(model[COMPOSE6]!!.recompositions.highlightCount).isWithin(0.01f).of(34.20f)
  }

  private fun children(view: ViewNode): List<ViewNode> = ViewNode.readAccess { view.children }

  private fun ViewNode.ReadAccess.flattenDrawChildren(node: DrawViewNode): List<DrawViewNode> =
    listOf(node).plus(node.unfilteredOwner.drawChildren.flatMap { flattenDrawChildren(it) })

  private fun assertSingleRoot(model: InspectorModel, treeSettings: TreeSettings) {
    ViewNode.readAccess {
      val allDrawChildren = model.root.drawChildren.flatMap { flattenDrawChildren(it) }
      // Check that the drawView tree contains exactly the drawChildren of every element of the view
      // tree
      assertThat(allDrawChildren)
        .containsExactlyElementsIn(
          model.root.flatten().flatMap { it.drawChildren.asSequence() }.toList()
        )
      // Check that the unfiltered owners of the drawViews are all in the view tree
      assertThat(model.root.flattenedList())
        .containsAllIn(allDrawChildren.map { it.unfilteredOwner })
      // Check that the owners of the drawViews are all in the view tree or null
      assertThat(model.root.flattenedList().plus(null).toList())
        .containsAllIn(allDrawChildren.map { it.findFilteredOwner(treeSettings) })
    }
  }
}

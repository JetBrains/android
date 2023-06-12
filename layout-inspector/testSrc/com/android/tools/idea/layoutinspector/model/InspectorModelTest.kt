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

import com.android.flags.junit.FlagRule
import com.android.io.readImage
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import kotlin.test.fail

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class InspectorModelTest {
  @get:Rule
  val highlightFlag =
    FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS, true)

  @Test
  fun testUpdatePropertiesOnly() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          view(VIEW3, 5, 6, 7, 8, qualifiedName = "v3Type")
        }
        view(VIEW2, 8, 7, 6, 5, qualifiedName = "v2Type")
      }
    }
    val origRoot = model[ROOT]
    var isModified = false
    var newRootReported: ViewNode? = null
    model.modificationListeners.add { _, newWindow, structuralChange ->
      newRootReported = newWindow?.root
      isModified = structuralChange
    }

    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
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
    assertThat(model[VIEW3]?.layoutBounds?.height).isEqualTo(6)
    assertThat(newRootReported).isSameAs(origRoot)
    assertSingleRoot(model, FakeTreeSettings())
  }

  @Test
  fun testChildCreated() {
    val image1 = TestUtils.resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/image1.png").readImage()
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
        }
      }
    }
    var isModified = false
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newWindow =
      window(ROOT, 1, 2, 3, 4, rootViewQualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          image(image1)
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
      }

    val origNodes = model.root.flattenedList().associateBy { it.drawId }
    // Clear the draw view children, since this test isn't concerned with them and update won't work correctly with them in place.
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
  fun testNodeDeleted() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
      }
    }
    var isModified = false
    model.setSelection(model[VIEW3], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW3]
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

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
    val model = model {
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
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

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
    val model = InspectorModel(mock())
    assertThat(model.isEmpty).isTrue()

    // add first window
    val newWindow = window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
      view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type")
    }
    model.update(newWindow, listOf(ROOT), 0)
    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]
    assertThat(model.isEmpty).isFalse()
    assertThat(model[VIEW1]).isNotNull()
    assertThat(children(model.root).map { it.drawId }).isEqualTo(listOf(ROOT))

    // add second window
    var window2 = window(VIEW2, VIEW2, 2, 4, 6, 8, rootViewQualifiedName = "root2Type") {
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
    window2 = window(VIEW2, VIEW2, 2, 4, 6, 8, rootViewQualifiedName = "root2Type") {
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
    val model = model { }
    val newWindow =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
          view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
      }
    // Clear out the drawChildren added by the test fixture so we only get the ones generated by production code
    ViewNode.writeAccess {
      model.root.flatten().forEach { node -> node.drawChildren.clear() }
    }
    model.update(newWindow, listOf(ROOT), 0)

    // Verify that drawChildren are created corresponding to the tree
    ViewNode.readAccess {
      model.root.flatten().forEach { node ->
        assertThat(node.drawChildren.map { (it as DrawViewChild).unfilteredOwner }).containsExactlyElementsIn(node.children)
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
    val model = model {
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
    val handler = Thread.UncaughtExceptionHandler { _, ex ->
      exception = ex
    }
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
    val model = InspectorModel(mock())
    val mockListener = mock<(DynamicLayoutInspectorErrorInfo.AttachErrorState) -> Unit>()
    model.attachStageListeners.add(mockListener)

    model.fireAttachStateEvent(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)

    verify(mockListener).invoke(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }

  @Test
  fun testHighlightCounts() {
    val virtualTimeScheduler = VirtualTimeScheduler()
    val scheduler = MoreExecutors.listeningDecorator(virtualTimeScheduler)

    val model = model(scheduler = scheduler) {
      view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
          compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 0, composeSkips = 0)
        }
      }
    }
    val compose1 = model[COMPOSE1] as ComposeViewNode
    val compose2 = model[COMPOSE2] as ComposeViewNode

    // Receive an update from the device with recomposition counts:
    val window1 = window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
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
    val window2 = window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
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

    val model = model(scheduler = scheduler) {
      view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
          compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 0, composeSkips = 0)
        }
      }
    }
    // In this test do not spend time refreshingImages. We want to focus on the recomposition countdown logic:
    model.windows.values.filterIsInstance<FakeAndroidWindow>().forEach { it.refreshImages = null }

    // Generate an update with a new number for the composeCount of COMPOSE2
    fun window1(count: Int) = window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
      compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
        compose(COMPOSE2, "Text", "text.kt", 234, composeCount = count, composeSkips = 0)
      }
    }

    // Check that we are never in a state where neither thread can advance
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val lock = Object()
    fun check() {
      synchronized(lock) {
        val running = ViewNode.readAccess {
          model.maxHighlight > 0f || compose2.recompositions.highlightCount < DECREASE_BREAK_OFF
        }
        if (!running) {
          stop = true
          countdownStopped = true
        }
      }
    }

    // Start a thread that constantly runs the scheduler to decrease the highlight count in COMPOSE2
    val thread1 = Thread {
      while (!stop) {
        Thread.yield()
        if (model.maxHighlight > 0f) {
          virtualTimeScheduler.advanceBy(DECREASE_DELAY, DECREASE_TIMEUNIT)
        } else {
          check()
        }
      }
    }.apply { start() }

    // Start a thread that constantly runs an update to increase the highlight count of COMPOSE2
    var count = 0
    val thread2 = Thread {
      while (!stop) {
        Thread.yield()
        if (compose2.recompositions.highlightCount < DECREASE_BREAK_OFF) {
          model.update(window1(++count), listOf(ROOT), 0)
        } else {
          check()
        }
      }
    }.apply { start() }

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
    val model = model {
      view(ROOT, 1, 2, 3, 4, qualifiedName = "rootType") {
        view(VIEW1, 4, 3, 2, 1, qualifiedName = "v1Type") {
          view(VIEW3, 5, 6, 7, 8, qualifiedName = "v3Type")
        }
        view(VIEW2, 8, 7, 6, 5, qualifiedName = "v2Type")
      }
    }

    model.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    model.clear()
    assertThat(model.foldInfo).isNull()
  }

  private fun children(view: ViewNode): List<ViewNode> =
    ViewNode.readAccess { view.children }

  private fun ViewNode.ReadAccess.flattenDrawChildren(node: DrawViewNode): List<DrawViewNode> =
    listOf(node).plus(node.unfilteredOwner.drawChildren.flatMap { flattenDrawChildren(it) })

  private fun assertSingleRoot(model: InspectorModel, treeSettings: TreeSettings) {
    ViewNode.readAccess {
      val allDrawChildren = model.root.drawChildren.flatMap { flattenDrawChildren(it) }
      // Check that the drawView tree contains exactly the drawChildren of every element of the view tree
      assertThat(allDrawChildren).containsExactlyElementsIn(
        model.root.flatten().flatMap { it.drawChildren.asSequence() }.toList())
      // Check that the unfiltered owners of the drawViews are all in the view tree
      assertThat(model.root.flattenedList()).containsAllIn(allDrawChildren.map { it.unfilteredOwner })
      // Check that the owners of the drawViews are all in the view tree or null
      assertThat(model.root.flattenedList().plus(null).toList()).containsAllIn(allDrawChildren.map { it.findFilteredOwner(treeSettings) })
    }
  }
}
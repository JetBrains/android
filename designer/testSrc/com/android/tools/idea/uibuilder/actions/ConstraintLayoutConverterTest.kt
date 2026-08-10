/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.SdkConstants.FD_RES_LAYOUT
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction.ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction.ATTR_LAYOUT_CONVERSION_WRAP_WIDTH
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction.ConstraintLayoutConverter
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture
import com.android.tools.idea.uibuilder.handlers.FakeViewEditor
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.component
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ConstraintLayoutConverterTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val edtRule = EdtRule()

  @Test
  fun testMeasureChildrenCallbackDispatchesToEdt() {
    val model =
      NlModelBuilderUtil.model(
          projectRule,
          FD_RES_LAYOUT,
          "layout.xml",
          component(LINEAR_LAYOUT)
            .id("@+id/linear")
            .withBounds(0, 0, 1000, 1000)
            .children(component(TEXT_VIEW).id("@+id/text").withBounds(0, 0, 100, 100)),
        )
        .build()

    val screenView = ScreenFixture(model).screen
    val layoutComponent = model.treeReader.components[0]
    val childComponent = layoutComponent.children[0]

    val latch = CountDownLatch(1)
    val executor = Executors.newSingleThreadExecutor()
    val fakeEditor =
      object : FakeViewEditor() {
        override fun measureChildren(
          parent: NlComponent,
          filter: RenderTask.AttributeFilter?,
        ): CompletableFuture<MutableMap<NlComponent, Dimension>> {
          val future = CompletableFuture.supplyAsync({ mutableMapOf(childComponent to Dimension(120, 240)) }, executor)
          future.thenRun { latch.countDown() }
          return future
        }
      }

    val converter = ConstraintLayoutConverter(screenView, layoutComponent, false, false, false, fakeEditor)

    WriteCommandAction.runWriteCommandAction(projectRule.project) { converter.postLayoutRun() }

    // Trigger model listeners
    model.notifyListenersModelDerivedDataChanged()

    // Wait for the background task to complete
    latch.await()
    executor.shutdown()

    // Dispatch events on EDT so the invokeLater runnable in the callback runs
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals("120", childComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_WIDTH))
    assertEquals("240", childComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT))
  }

  @Test
  fun testMeasureChildrenCallbackHandlesEmptyResult() {
    val model =
      NlModelBuilderUtil.model(
          projectRule,
          FD_RES_LAYOUT,
          "layout.xml",
          component(LINEAR_LAYOUT)
            .id("@+id/linear")
            .withBounds(0, 0, 1000, 1000)
            .children(component(TEXT_VIEW).id("@+id/text").withBounds(0, 0, 100, 100)),
        )
        .build()

    val screenView = ScreenFixture(model).screen
    val layoutComponent = model.treeReader.components[0]
    val childComponent = layoutComponent.children[0]

    val fakeEditor =
      object : FakeViewEditor() {
        override fun measureChildren(
          parent: NlComponent,
          filter: RenderTask.AttributeFilter?,
        ): CompletableFuture<MutableMap<NlComponent, Dimension>> = CompletableFuture.completedFuture(mutableMapOf())
      }

    val converter = ConstraintLayoutConverter(screenView, layoutComponent, false, false, false, fakeEditor)

    WriteCommandAction.runWriteCommandAction(projectRule.project) { converter.postLayoutRun() }

    // Trigger model listeners
    model.notifyListenersModelDerivedDataChanged()

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify attributes were not set and no NPE was thrown
    assertNull(childComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_WIDTH))
    assertNull(childComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT))
  }

  @Test
  fun testMeasureChildrenCallbackHandlesError() {
    val model =
      NlModelBuilderUtil.model(
          projectRule,
          FD_RES_LAYOUT,
          "layout.xml",
          component(LINEAR_LAYOUT)
            .id("@+id/linear")
            .withBounds(0, 0, 1000, 1000)
            .children(component(TEXT_VIEW).id("@+id/text").withBounds(0, 0, 100, 100)),
        )
        .build()

    val screenView = ScreenFixture(model).screen
    val layoutComponent = model.treeReader.components[0]
    val childComponent = layoutComponent.children[0]

    val fakeEditor =
      object : FakeViewEditor() {
        override fun measureChildren(
          parent: NlComponent,
          filter: RenderTask.AttributeFilter?,
        ): CompletableFuture<MutableMap<NlComponent, Dimension>> = CompletableFuture.failedFuture(RuntimeException("Measurement failed"))
      }

    val converter = ConstraintLayoutConverter(screenView, layoutComponent, false, false, false, fakeEditor)

    WriteCommandAction.runWriteCommandAction(projectRule.project) { converter.postLayoutRun() }

    // Trigger model listeners
    model.notifyListenersModelDerivedDataChanged()

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify attributes were not set and no exception escaped
    assertNull(childComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_WIDTH))
    assertNull(childComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_WRAP_HEIGHT))
  }
}

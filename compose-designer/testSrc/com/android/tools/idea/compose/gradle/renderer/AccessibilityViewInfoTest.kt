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
package com.android.tools.idea.compose.gradle.renderer

import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SingleComposePreviewElementInstance
import com.android.tools.idea.compose.preview.renderer.createRenderTaskFuture
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.scene.getAccessibilityText
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccessibilityViewInfoTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  /**
   * Tests that passing [accessibilityBasedHierarchyParser] to a render task for Compose correctly
   * creates a [ViewInfo] hierarchy from the [AccessibilityNodeInfo] tree.
   */
  @Test
  fun testAccessibilityViewInfo() {
    val renderTaskFuture =
      createRenderTaskFuture(
        projectRule.androidFacet(":app"),
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.TwoElementsPreview"
        ),
        false
      )
    val renderTask = renderTaskFuture.get(1, TimeUnit.MINUTES)
    try {
      renderTask.setCustomContentHierarchyParser(accessibilityBasedHierarchyParser)

      val renderResult = renderTask.render().get(1, TimeUnit.MINUTES)
      val rootView = renderResult.rootViews[0]
      var children = rootView.children
      assertEquals(1, children.size)
      assertEquals(rootView.viewObject, children[0].viewObject)
      assertNotNull(children[0].accessibilityObject)

      children = children[0].children
      assertEquals(2, children.size)

      val textView = children[0]
      assertEquals("android.widget.TextView", textView.className)
      assertEquals(rootView.viewObject, textView.viewObject)
      assertEquals("Hello 2", textView.getAccessibilityText())
      assertTrue(textView.children.isEmpty())

      children = children[1].children
      assertEquals(2, children.size)

      val buttonTextView = children[0]
      assertEquals("android.widget.TextView", buttonTextView.className)
      assertEquals(rootView.viewObject, buttonTextView.viewObject)
      assertEquals("Hello World", buttonTextView.getAccessibilityText())
      assertTrue(buttonTextView.children.isEmpty())

      val buttonView = children[1]
      assertEquals("android.widget.Button", buttonView.className)
      assertEquals(rootView.viewObject, buttonView.viewObject)
      assertTrue(buttonView.children.isEmpty())
    } finally {
      renderTask.dispose().get(5, TimeUnit.SECONDS)
    }
  }
}

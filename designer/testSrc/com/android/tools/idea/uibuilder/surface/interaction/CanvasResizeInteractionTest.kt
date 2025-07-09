/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.interaction

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.fixtures.MouseEventBuilder
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.InteractionInformation
import com.android.tools.idea.common.surface.MouseReleasedEvent
import com.android.tools.idea.uibuilder.analytics.ResizeTracker
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.testFramework.ExtensionTestUtil

class CanvasResizeInteractionTest : SceneTest() {
  override fun createModel(): ModelBuilder =
    model(
      "linear.xml",
      component(SdkConstants.LINEAR_LAYOUT)
        .withMockView()
        .withBounds(0, 0, 90, 90)
        .matchParentWidth()
        .matchParentHeight(),
    )

  fun testCanvasClickWithoutDragKeepsStartingSize() {
    var resizeReported = false
    ExtensionTestUtil.maskExtensions(
      ResizeTracker.EP_NAME,
      listOf(
        object : ResizeTracker {
          override fun isApplicable(sceneManager: SceneManager): Boolean = true

          override fun reportResizeStopped(
            sceneManager: SceneManager,
            widthDp: Int,
            heightDp: Int,
            dpi: Int,
          ) {
            assertTrue(widthDp != 0)
            assertTrue(heightDp != 0)
            resizeReported = true
          }
        }
      ),
      myFixture.testRootDisposable,
    )

    val configuration = mySceneManager.model.configuration
    val canvasResizeInteraction =
      CanvasResizeInteraction(
        myScene.designSurface as NlDesignSurface,
        myScreen.screen,
        configuration,
      )

    val mouseEvent =
      MouseReleasedEvent(MouseEventBuilder(90, 90).build(), InteractionInformation(90, 90, 0))
    canvasResizeInteraction.commit(mouseEvent)
    assertFalse("Resize should not happen if the mouse is not dragged", resizeReported)
  }
}

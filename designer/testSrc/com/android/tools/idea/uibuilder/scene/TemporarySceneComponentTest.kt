/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.TemporarySceneComponent

open class TemporarySceneComponentTest : SceneTest() {

  fun testSetDragging() {
    val mockNlComponent = LayoutTestUtilities.createMockComponent()
    whenever(mockNlComponent.tagName).thenReturn(TEXT_VIEW)
    val sceneComponent = TemporarySceneComponent(myScene, mockNlComponent)

    assertFalse(sceneComponent.isDragging)

    sceneComponent.isDragging = true
    assertTrue(sceneComponent.isDragging)

    sceneComponent.isDragging = false
    assertFalse(sceneComponent.isDragging)
  }

  override fun createModel(): ModelBuilder {
    return model(
      "temporary_scene_test.xml",
      component(LINEAR_LAYOUT).withBounds(0, 0, 2000, 2000).matchParentWidth().matchParentHeight(),
    )
  }
}

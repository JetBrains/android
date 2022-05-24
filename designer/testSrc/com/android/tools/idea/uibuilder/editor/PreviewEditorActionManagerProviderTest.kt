/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.type.AnimatedStateListFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListTempFileType
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.AnimationListFileType
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val ANIMATION_TYPES = listOf(AnimatedStateListFileType, AnimatedStateListTempFileType, AnimatedVectorFileType, AnimationListFileType)

class PreviewEditorActionManagerProviderTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun noSceneViewContextToolbarForAllAnimations() {
    val surface = Mockito.mock(NlDesignSurface::class.java)
    val sceneView = Mockito.mock(SceneView::class.java)
    for (type in ANIMATION_TYPES) {
      val actionProvider = PreviewEditorActionManagerProvider(surface, type)
      assertNull(actionProvider.getSceneViewContextToolbar(sceneView))
    }

    val nonAnimationTypes = DESIGNER_PREVIEW_FILE_TYPES - ANIMATION_TYPES
    for (type in nonAnimationTypes) {
      val actionProvider = PreviewEditorActionManagerProvider(surface, type)
      assertNotNull(actionProvider.getSceneViewContextToolbar(sceneView))
    }
  }
}

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
package com.android.tools.idea.preview

import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import org.mockito.kotlin.mock

class CommonPreviewRenderQualityManagerTest {
  private val layoutlibSceneManagerMock = mock<LayoutlibSceneManager>()
  private val previewElement = TestPreviewElement()

  @Test
  fun `quality manager does not apply when on Interactive Mode`() {
    val qualityManager = createManager(PreviewMode.Interactive(previewElement))
    assertThat(qualityManager.needsQualityChange(layoutlibSceneManagerMock)).isFalse()
  }

  @Test
  fun `quality manager applies on Focus Mode`() {
    val qualityManager = createManager(PreviewMode.Focus(previewElement))
    assertThat(qualityManager.needsQualityChange(layoutlibSceneManagerMock)).isTrue()
  }

  @Test
  fun `quality manager applies on Default Mode`() {
    val qualityManager = createManager(PreviewMode.Default())
    assertThat(qualityManager.needsQualityChange(layoutlibSceneManagerMock)).isTrue()
  }

  @Test
  fun `quality manager applies on Animation Inspection Mode`() {
    val qualityManager = createManager(PreviewMode.AnimationInspection(previewElement))
    assertThat(qualityManager.needsQualityChange(layoutlibSceneManagerMock)).isTrue()
  }

  /**
   * Creates a [CommonPreviewRenderQualityManager] whose [PreviewModeManager] starts in the given [PreviewMode], it then checks if it's
   * possible to apply a quality change based on the given mode and the delegated [RenderQualityManager]'s [needsQualityChange] return
   * value.
   *
   * @param mode The initial [PreviewMode] for the [PreviewModeManager].
   * @param needsQualityChange The boolean value that the delegated [RenderQualityManager]'s [needsQualityChange] method will return.
   */
  private fun createManager(mode: PreviewMode, needsQualityChange: Boolean = true): CommonPreviewRenderQualityManager {
    val previewModeManager =
      object : PreviewModeManager {
        private val _mode: MutableStateFlow<PreviewMode> = MutableStateFlow(mode)
        override val mode: StateFlow<PreviewMode>
          get() = _mode

        override fun restorePrevious() {}

        override fun setMode(mode: PreviewMode) {
          _mode.value = mode
        }
      }

    return CommonPreviewRenderQualityManager(
      previewModeManager = previewModeManager,
      delegateRenderQualityManager =
        object : RenderQualityManager {
          override fun getTargetQuality(sceneManager: LayoutlibSceneManager): Float = 1f

          override fun needsQualityChange(sceneManager: LayoutlibSceneManager): Boolean = needsQualityChange
        },
    )
  }
}

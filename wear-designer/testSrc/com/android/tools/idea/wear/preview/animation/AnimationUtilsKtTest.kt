/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.animation

import androidx.wear.protolayout.expression.pipeline.DynamicTypeAnimator
import com.android.flags.junit.FlagRule
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.util.MockNlComponent
import com.android.tools.idea.wear.preview.PsiWearTilePreviewElement
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.wear.preview.WearTilePreviewElement
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import junit.framework.TestCase.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AnimationUtilsKtTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val flagRule = FlagRule(StudioFlags.WEAR_TILE_ANIMATION_INSPECTOR, true)

  private val previewElement: PsiWearTilePreviewElement =
    WearTilePreviewElement(
      displaySettings =
        PreviewDisplaySettings(
          "some name",
          "some base name",
          "parameter name",
          "some group",
          false,
          false,
          "0xffabcd",
        ),
      previewElementDefinition = mock<SmartPsiElementPointer<PsiElement>>(),
      previewBody = mock<SmartPsiElementPointer<PsiElement>>(),
      methodFqn = "someMethodFqn",
      configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
    )

  @Test
  fun `detectAnimations - preview element not found`() {
    val layoutlibSceneManager = mock<LayoutlibSceneManager>()
    val model = mock<NlModel>()
    val dataProvider =
      object : NlDataProvider() {
        override fun getData(dataId: String): Any? = null
      }

    whenever(layoutlibSceneManager.model).thenReturn(model)
    whenever(model.dataProvider).thenReturn(dataProvider)

    detectAnimations(layoutlibSceneManager)

    assertThat(previewElement.tileServiceViewAdapter.value).isNull()
  }

  @Test
  fun `detectAnimations - updates preview element and flag`() {
    val layoutlibSceneManager = mock<LayoutlibSceneManager>()

    val scene = mock<Scene>()
    val root = mock<SceneComponent>()
    val nlComponent =
      MockNlComponent.create(
        runReadAction {
          XmlTagUtil.createTag(projectRule.project, "<FrameLayout/>").apply {
            putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module)
          }
        }
      )
    val dataProvider =
      object : NlDataProvider(PREVIEW_ELEMENT_INSTANCE) {
        override fun getData(dataId: String): Any? =
          previewElement.takeIf { dataId == PREVIEW_ELEMENT_INSTANCE.name }
      }

    val model = nlComponent.model
    whenever(model.dataProvider).thenReturn(dataProvider)

    whenever(layoutlibSceneManager.model).thenReturn(model)
    whenever(layoutlibSceneManager.scene).thenReturn(scene)
    whenever(scene.root).thenReturn(root)
    whenever(root.nlComponent).thenReturn(nlComponent)

    // With animations
    val tileServiceViewAdapter =
      object {
        fun getAnimations() = listOf(TestDynamicTypeAnimator())
      }

    val viewInfo = ViewInfo("View", null, 0, 0, 30, 20, tileServiceViewAdapter, null, null)

    nlComponent.viewInfo = viewInfo

    detectAnimations(layoutlibSceneManager)

    assertThat(previewElement.tileServiceViewAdapter.value).isEqualTo(tileServiceViewAdapter)
    assertThat(previewElement.hasAnimations).isTrue()

    // Without animations
    val tileServiceViewAdapterNoAnimations =
      object {
        fun getAnimations() = emptyList<DynamicTypeAnimator>()
      }

    val viewInfoNoAnimation =
      ViewInfo("View", null, 0, 0, 30, 20, tileServiceViewAdapterNoAnimations, null, null)

    nlComponent.viewInfo = viewInfoNoAnimation

    detectAnimations(layoutlibSceneManager)

    assertThat(previewElement.tileServiceViewAdapter.value)
      .isEqualTo(tileServiceViewAdapterNoAnimations)
    assertThat(previewElement.hasAnimations).isFalse()
  }

  // Regression test for b/373681154
  @Test
  fun `detectAnimations - handles view adapter without getAnimations method`() {
    val layoutlibSceneManager = mock<LayoutlibSceneManager>()

    val scene = mock<Scene>()
    val root = mock<SceneComponent>()
    val nlComponent =
      MockNlComponent.create(
        runReadAction {
          XmlTagUtil.createTag(projectRule.project, "<FrameLayout/>").apply {
            putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module)
          }
        }
      )
    val dataProvider =
      object : NlDataProvider(PREVIEW_ELEMENT_INSTANCE) {
        override fun getData(dataId: String): Any? =
          previewElement.takeIf { dataId == PREVIEW_ELEMENT_INSTANCE.name }
      }
    val model = nlComponent.model
    whenever(model.dataProvider).thenReturn(dataProvider)
    whenever(layoutlibSceneManager.model).thenReturn(model)
    whenever(layoutlibSceneManager.scene).thenReturn(scene)
    whenever(scene.root).thenReturn(root)
    whenever(root.nlComponent).thenReturn(nlComponent)

    val tileServiceViewAdapter =
      object {
        // no getAnimations here
      }

    val viewInfo = ViewInfo("View", null, 0, 0, 30, 20, tileServiceViewAdapter, null, null)

    nlComponent.viewInfo = viewInfo

    try {
      detectAnimations(layoutlibSceneManager)
    } catch (_: Exception) {
      fail(
        "Detect animations should not throw an exception when there is no getAnimations method in the ViewAdapter"
      )
    }

    assertThat(previewElement.tileServiceViewAdapter.value).isEqualTo(tileServiceViewAdapter)
    assertThat(previewElement.hasAnimations).isFalse()
  }
}

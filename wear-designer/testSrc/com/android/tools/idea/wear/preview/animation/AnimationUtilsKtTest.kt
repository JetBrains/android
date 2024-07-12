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

import com.android.flags.junit.FlagRule
import com.android.ide.common.rendering.api.ViewInfo
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
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
import com.android.tools.idea.wear.preview.WearTilePreviewElement
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.junit.Rule
import org.junit.Test

class AnimationUtilsKtTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val flagRule = FlagRule(StudioFlags.WEAR_TILE_ANIMATION_INSPECTOR, true)

  private val previewElement: PsiWearTilePreviewElement =
    WearTilePreviewElement(
      displaySettings = PreviewDisplaySettings("some name", "some group", false, false, "0xffabcd"),
      previewElementDefinition = mock<SmartPsiElementPointer<PsiElement>>(),
      previewBody = mock<SmartPsiElementPointer<PsiElement>>(),
      methodFqn = "someMethodFqn",
      configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
    )

  @Test
  fun `detectAnimations - preview element not found`() {
    val layoutlibSceneManager = mock<LayoutlibSceneManager>()
    val model = mock<NlModel>()
    val dataContext = SimpleDataContext.builder().add(PREVIEW_ELEMENT_INSTANCE, null).build()

    whenever(layoutlibSceneManager.model).thenReturn(model)
    whenever(model.dataContext).thenReturn(dataContext)

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
    val model = nlComponent.model
    whenever(model.dataContext)
      .thenReturn(SimpleDataContext.getSimpleContext(PREVIEW_ELEMENT_INSTANCE, previewElement))

    val tileServiceViewAdapter = mock<Any>()
    val viewInfo = ViewInfo("View", null, 0, 0, 30, 20, tileServiceViewAdapter, null, null)

    nlComponent.viewInfo = viewInfo

    whenever(layoutlibSceneManager.model).thenReturn(model)
    whenever(layoutlibSceneManager.scene).thenReturn(scene)
    whenever(scene.root).thenReturn(root)
    whenever(root.nlComponent).thenReturn(nlComponent)

    detectAnimations(layoutlibSceneManager)

    assertThat(previewElement.tileServiceViewAdapter.value).isEqualTo(tileServiceViewAdapter)
    assertThat(previewElement.hasAnimations).isTrue()
  }
}

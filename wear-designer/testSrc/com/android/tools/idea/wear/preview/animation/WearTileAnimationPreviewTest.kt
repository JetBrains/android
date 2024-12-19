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

import com.android.ide.common.rendering.api.Result.Status
import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.wear.preview.PsiWearTilePreviewElement
import com.android.tools.idea.wear.preview.animation.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.wear.preview.animation.analytics.WearTileAnimationTracker
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.android.tools.wear.preview.WearTilePreviewElement
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.JComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

class WearTileAnimationPreviewTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var animationPreview: WearTileAnimationPreview

  private val wearTilePreviewElement: PsiWearTilePreviewElement =
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

  private suspend fun WearTileAnimationPreview.updateAnimations(
    animations: List<TestDynamicTypeAnimator>
  ) {
    val tileServiceViewAdapter =
      object {
        fun getAnimations(): List<Any> = animations
      }

    wearTilePreviewElement.tileServiceViewAdapter.value = tileServiceViewAdapter
    val terminal = animations.filter { it.isTerminal() }
    val maxTime =
      terminal.maxOfOrNull { it.startDelay + it.duration }
        ?: DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS
    delayUntilCondition(200) {
      this.animations.size == terminal.size && this.maxDurationPerIteration.value == maxTime
    }
  }

  private fun createAnimator(
    durationMs: Long,
    type: ProtoAnimation.TYPE = ProtoAnimation.TYPE.FLOAT,
    isTerminal: Boolean = true,
  ): TestDynamicTypeAnimator {
    return TestDynamicTypeAnimator(type).apply {
      this.duration = durationMs
      this.isTerminalInternal = isTerminal
      this.startDelay = 0
    }
  }

  @Before
  fun setUp() = runBlocking {
    val psiFile = projectRule.fixture.addFileToProject("res/layout/layout.xml", "")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val model =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        AndroidBuildTargetReference.gradleOnly(facet),
        psiFile.virtualFile,
      )

    model.dataProvider =
      object : NlDataProvider(PREVIEW_ELEMENT_INSTANCE) {
        override fun getData(dataId: String): Any? =
          wearTilePreviewElement.takeIf { dataId == PREVIEW_ELEMENT_INSTANCE.name }
      }

    val successfulRenderResultMock =
      Mockito.mock(RenderResult::class.java).apply {
        whenever(this.sourceFile).thenReturn(psiFile)
        whenever(this.logger).thenReturn(RenderLogger())
        whenever(this.renderResult).thenReturn(Status.SUCCESS.createResult())
        whenever(this.module).doAnswer { projectRule.module }
      }

    val surface =
      NlSurfaceBuilder(
          projectRule.project,
          projectRule.testRootDisposable,
          { s, m ->
            SyncLayoutlibSceneManager(s, m).apply {
              Disposer.register(projectRule.testRootDisposable, this)
              renderResult = successfulRenderResultMock
            }
          },
        )
        .build()

    surface.setModel(model)
    delayUntilCondition(200) { surface.models.contains(model) }

    animationPreview =
      WearTileAnimationPreview(
        projectRule.project,
        surface,
        wearTilePreviewElement,
        tracker = WearTileAnimationTracker(AnimationToolingUsageTracker.getInstance(null)),
      )

    Disposer.register(projectRule.testRootDisposable, model)
    Disposer.register(projectRule.testRootDisposable, surface)
    Disposer.register(projectRule.testRootDisposable, animationPreview)
  }

  @Test
  fun setClockTime_updatesAnimationTime() = runTest {
    val animation1 = createAnimator(durationMs = 1000L, type = ProtoAnimation.TYPE.COLOR)
    val animation2 = createAnimator(durationMs = 2000L, type = ProtoAnimation.TYPE.INT)
    val animations = listOf(animation1, animation2)
    animationPreview.updateAnimations(animations)

    animationPreview.clockControl.incrementClockBy(500)
    delayUntilCondition(200) { animation2.currentTime == 500L }
    assertThat(animation1.currentTime).isEqualTo(500L)
    assertThat(animation2.currentTime).isEqualTo(500L)

    animationPreview.clockControl.incrementClockBy(1500)
    delayUntilCondition(200) { animation2.currentTime == 2000L }
    assertThat(animation1.currentTime).isEqualTo(2000L)
    assertThat(animation2.currentTime).isEqualTo(2000L)
  }

  @Test
  fun setClockTime_frozenAnimation_staysAtFrozenTime() = runTest {
    val animation1 = createAnimator(durationMs = 3000L, type = ProtoAnimation.TYPE.COLOR)
    val animation2 = createAnimator(durationMs = 2000L, type = ProtoAnimation.TYPE.INT)
    val animations = listOf(animation1, animation2)
    animationPreview.updateAnimations(animations)

    // Move clock to 500ms
    animationPreview.clockControl.incrementClockBy(500)
    delayUntilCondition(200) { animation2.currentTime == 500L }
    assertThat(animation1.currentTime).isEqualTo(500L)

    // Freeze the second animation at 500ms
    val animation2Manager =
      animationPreview.animations.find { it.animation.name == "INT Animation" }!!
    animation2Manager.frozenState.value = SupportedAnimationManager.FrozenState(true, 500)

    animationPreview.clockControl.incrementClockBy(1500)
    delayUntilCondition(200) { animation1.currentTime == 2000L }
    assertThat(animation1.currentTime).isEqualTo(2000L)
    assertThat(animation2.currentTime).isEqualTo(500L) // Remains frozen at 500ms
  }

  @Test
  fun updateMaxDuration_updatesFromAnimations() = runTest {
    val animation1 = createAnimator(durationMs = 1000L)
    val animation2 = createAnimator(durationMs = 2000L)
    val animations = listOf(animation1, animation2)
    animationPreview.updateAnimations(animations)

    delayUntilCondition(200) { animationPreview.maxDurationPerIteration.value == 2000L }
  }

  @Test
  fun updateMaxDuration_emptyAnimations() = runTest {
    animationPreview.updateAnimations(emptyList())

    assertThat(animationPreview.maxDurationPerIteration.value)
      .isEqualTo(DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS)
  }

  @Test
  fun createAnimationManager_addsManagerAndUpdateTimeline() = runTest {
    val animation = createAnimator(durationMs = 1000L, isTerminal = true)
    animationPreview.updateAnimations(listOf(animation))

    assertThat(animationPreview.animations.size).isEqualTo(1)
  }

  @Test
  fun updateAllAnimations_addsTerminalAnimations() = runTest {
    val animation1 = createAnimator(durationMs = 1000L, isTerminal = true)
    val animation2 = createAnimator(durationMs = 2000L, isTerminal = false)
    val animation3 = createAnimator(durationMs = 5000L, isTerminal = true)

    val animations: List<TestDynamicTypeAnimator> = listOf(animation1, animation2, animation3)

    animationPreview.updateAnimations(animations)

    assertThat(animationPreview.animations.map { it.animation.durationMs })
      .containsExactly(1000L, 5000L)
  }

  @Test
  fun updateAllAnimations_clearsExistingAnimations() = runTest {
    val animation1 = createAnimator(durationMs = 1000L, type = ProtoAnimation.TYPE.COLOR)
    val animation2 = createAnimator(durationMs = 2000L, type = ProtoAnimation.TYPE.INT)
    val animations = listOf(animation1, animation2)
    animationPreview.updateAnimations(animations)

    val newAnimation = createAnimator(durationMs = 500L, type = ProtoAnimation.TYPE.FLOAT)
    val newAnimations = listOf(newAnimation)
    animationPreview.updateAnimations(newAnimations)

    delayUntilCondition(200) {
      animationPreview.animations.size == 1 &&
        animationPreview.animations[0].animation.name == "FLOAT Animation"
    }

    assertThat(animationPreview.maxDurationPerIteration.value).isEqualTo(500L)
  }

  @Test
  fun errorInSurface_showErrorPanel() = runTest {
    // mock renderResult to return ERROR
    val errorRenderResultMock =
      Mockito.mock(RenderResult::class.java).apply {
        whenever(this.sourceFile).thenReturn(mock(PsiFile::class.java))
        whenever(this.logger).thenReturn(RenderLogger())
        whenever(this.renderResult).thenReturn(Status.ERROR_RENDER_TASK.createResult())
        whenever(this.module).thenReturn(projectRule.module)
      }

    (animationPreview.sceneManagerProvider() as SyncLayoutlibSceneManager).renderResult =
      errorRenderResultMock

    // trigger collect
    wearTilePreviewElement.tileServiceViewAdapter.value =
      object {
        fun getAnimations() = listOf(TestDynamicTypeAnimator())
      }

    delayUntilCondition(200) {
      val errorPanel =
        withContext(uiThread) {
          FakeUi(animationPreview.component).findComponent<JComponent> { it.name == "Error Panel" }
        }
      errorPanel != null && errorPanel.isVisible
    }
  }
}

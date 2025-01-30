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
package com.android.tools.idea.preview.focus

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.preview.TestPreviewElement
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.AtomicInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommonFocusEssentialsModeManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  private val previewModeManager = CommonPreviewModeManager()

  private lateinit var scope: CoroutineScope
  private lateinit var lifecycleManager: PreviewLifecycleManager
  private lateinit var settings: AndroidEditorSettings.GlobalState

  @Before
  fun setup() {
    scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    lifecycleManager =
      PreviewLifecycleManager(
        project = project,
        parentScope = scope,
        onInitActivate = {},
        onResumeActivate = {},
        onDeactivate = {},
        onDelayedDeactivate = {},
      )
    settings = AndroidEditorSettings.getInstance().globalState
  }

  @After
  fun tearDown() {
    settings.isPreviewEssentialsModeEnabled = false
  }

  @Test
  fun testNoUpdatesOccurWhenThePreviewIsInactive() {
    val refreshCount = AtomicInteger(0)
    focusEssentialsModeManager { refreshCount.incrementAndGet() }

    triggerPreviewEssentialsModeUpdate(true)
    assertEquals(0, refreshCount.get())
  }

  @Test(expected = IllegalStateException::class)
  fun testLifecycleShouldBeActiveWhenCallingActivate() {
    val manager = focusEssentialsModeManager {}

    manager.activate()
  }

  @Test
  fun testNoUpdatesOccurWhenFocusModeAndEssentialsModeAreAlreadySet() {
    triggerPreviewEssentialsModeUpdate(true)
    previewModeManager.setMode(PreviewMode.Focus(null))

    val refreshCount = AtomicInteger(0)
    val manager = focusEssentialsModeManager { refreshCount.incrementAndGet() }
    lifecycleManager.activate()

    manager.activate()
    triggerPreviewEssentialsModeUpdate(true)

    assertEquals(0, refreshCount.get())
  }

  @Test
  fun testFocusModeShouldBeSetWhenPreviewEssentialsModeIsEnabled() {
    val previewElements = listOf(TestPreviewElement("element 1"), TestPreviewElement("element 2"))
    testRefreshIsRequested(
      previewElements = previewElements,
      expectedUpdatedFromPreviewEssentialsModeCount = 1,
    ) {
      triggerPreviewEssentialsModeUpdate(true)
    }
    assertEquals(PreviewMode.Focus(previewElements.first()), previewModeManager.mode.value)
  }

  @Test
  fun testFocusModeShouldBeSetWhenManagerIsActivated() {
    val previewElements = listOf(TestPreviewElement("element 1"), TestPreviewElement("element 2"))
    triggerPreviewEssentialsModeUpdate(true)
    testRefreshIsRequested(
      previewElements = previewElements,
      expectedUpdatedFromPreviewEssentialsModeCount = 0,
    ) { manager ->
      manager.activate()
    }
    assertEquals(PreviewMode.Focus(previewElements.first()), previewModeManager.mode.value)
  }

  @Test
  fun testFocusModeStaysAfterExitingPreviewEssentialsMode() {
    triggerPreviewEssentialsModeUpdate(true)
    previewModeManager.setMode(PreviewMode.Focus(null))
    testRefreshIsRequested(
      previewElements = listOf(TestPreviewElement("element 1"), TestPreviewElement("element 2")),
      expectedUpdatedFromPreviewEssentialsModeCount = 1,
    ) {
      triggerPreviewEssentialsModeUpdate(false)
    }
    assertEquals(PreviewMode.Focus(null), previewModeManager.mode.value)
  }

  @Test
  fun testFocusModeStaysAfterManagerIsActivated() {
    previewModeManager.setMode(PreviewMode.Focus(null))
    testRefreshIsRequested(
      previewElements = listOf(TestPreviewElement("element 1"), TestPreviewElement("element 2")),
      expectedUpdatedFromPreviewEssentialsModeCount = 0,
    ) { manager ->
      manager.activate()
    }
    assertEquals(PreviewMode.Focus(null), previewModeManager.mode.value)
  }

  private fun testRefreshIsRequested(
    previewElements: Collection<PreviewElement<*>>,
    expectedUpdatedFromPreviewEssentialsModeCount: Int,
    trigger: (CommonFocusEssentialsModeManager<*>) -> Unit,
  ) {
    val refreshCount = AtomicInteger(0)
    val updatedFromPreviewEssentialsModeCount = AtomicInteger(0)
    val manager =
      focusEssentialsModeManager(
        previewElements = previewElements,
        onUpdatedFromPreviewEssentialsMode = {
          updatedFromPreviewEssentialsModeCount.incrementAndGet()
        },
      ) {
        refreshCount.incrementAndGet()
      }
    lifecycleManager.activate()

    trigger(manager)

    assertEquals(1, refreshCount.get())
    assertEquals(
      expectedUpdatedFromPreviewEssentialsModeCount,
      updatedFromPreviewEssentialsModeCount.get(),
    )
  }

  private fun focusEssentialsModeManager(
    previewElements: Collection<PreviewElement<*>> = listOf(),
    onUpdatedFromPreviewEssentialsMode: () -> Unit = {},
    requestRefresh: () -> Unit,
  ): CommonFocusEssentialsModeManager<PreviewElement<*>> {
    val previewFlowManager = mock<PreviewFlowManager<PreviewElement<*>>>()
    whenever(previewFlowManager.allPreviewElementsFlow)
      .thenReturn(MutableStateFlow(FlowableCollection.Present(previewElements)))

    return CommonFocusEssentialsModeManager(
        project = project,
        lifecycleManager = lifecycleManager,
        previewFlowManager = previewFlowManager,
        previewModeManager = previewModeManager,
        onUpdatedFromPreviewEssentialsMode = onUpdatedFromPreviewEssentialsMode,
        requestRefresh = requestRefresh,
      )
      .also { Disposer.register(projectRule.testRootDisposable, it) }
  }

  private fun triggerPreviewEssentialsModeUpdate(value: Boolean) {
    settings.isPreviewEssentialsModeEnabled = value
    ApplicationManager.getApplication()
      .messageBus
      .syncPublisher(NlOptionsConfigurable.Listener.TOPIC)
      .onOptionsChanged()
  }
}

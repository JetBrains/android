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
package com.android.tools.idea.compose.preview.scene

import com.android.tools.idea.compose.preview.InteractiveNavigationHandler
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FakeComposeViewAdapter {
  @Suppress("unused", "PrivatePropertyName") // This property is called via reflection
  private val FakeOnBackPressedDispatcherOwner =
    object : Any() {
      // We can perform back navigation
      fun onBackPressCompleted() {}
    }
}

class InteractivePreviewBackNavigationUpdaterTest {
  lateinit var interactiveNavigationHandler: InteractiveNavigationHandler

  @Before
  fun setUp() {
    interactiveNavigationHandler = InteractiveNavigationHandler()
  }

  val composable =
    SingleComposePreviewElementInstance(
      "composableMethodName",
      PreviewDisplaySettings(
        name = "a name",
        baseName = "BaseName",
        parameterName = "ParameterName",
        group = null,
        showDecoration = false,
        background = PreviewDisplaySettings.Background.None,
        organizationGroup = "organizationGroup",
      ),
      null,
      null,
      PreviewConfiguration.cleanAndGet(),
    )

  @Test
  fun `check backDispatcher from ComposeViewAdapter is only set in interactive mode`() {
    val previewManager = TestComposePreviewManager().apply { setMode(PreviewMode.Default()) }
    val layoutlibSceneManagerMock = mock<LayoutlibSceneManager>().apply { whenever(viewObject).thenReturn(FakeComposeViewAdapter()) }
    InteractivePreviewBackNavigationUpdater.update(
      previewManager = previewManager,
      layoutlibSceneManager = layoutlibSceneManagerMock,
      interactiveNavigationHandler = interactiveNavigationHandler,
    )
    assertThat(interactiveNavigationHandler.canPerformBackNavigation()).isFalse()

    previewManager.setMode(PreviewMode.Interactive(composable))

    InteractivePreviewBackNavigationUpdater.update(
      previewManager = previewManager,
      layoutlibSceneManager = layoutlibSceneManagerMock,
      interactiveNavigationHandler = interactiveNavigationHandler,
    )
    assertThat(interactiveNavigationHandler.canPerformBackNavigation()).isTrue()
  }
}

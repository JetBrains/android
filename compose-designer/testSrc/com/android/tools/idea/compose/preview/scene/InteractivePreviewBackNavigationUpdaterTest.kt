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

import com.android.ide.common.rendering.api.ViewInfo
import com.android.mockito.kotlin.whenever
import com.android.tools.idea.compose.preview.ComposeViewInfo
import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.android.tools.idea.compose.preview.PxBounds
import com.android.tools.idea.compose.preview.SourceLocationImpl
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.preview.analytics.InteractiveNopTracker
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.rendering.RenderResult
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
  lateinit var myInteractivePreviewNavigationController: InteractivePreviewNavigationController

  @Before
  fun setUp() {
    myInteractivePreviewNavigationController =
      InteractivePreviewNavigationController({ InteractiveNopTracker() }, fpsUpdater = MutableSharedFlow())
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
      interactivePreviewNavigationController = myInteractivePreviewNavigationController,
    )
    assertThat(myInteractivePreviewNavigationController.canPerformBackNavigation()).isFalse()

    previewManager.setMode(PreviewMode.Interactive(composable))

    InteractivePreviewBackNavigationUpdater.update(
      previewManager = previewManager,
      layoutlibSceneManager = layoutlibSceneManagerMock,
      interactivePreviewNavigationController = myInteractivePreviewNavigationController,
    )
    assertThat(myInteractivePreviewNavigationController.canPerformBackNavigation()).isTrue()
  }

  @Test
  fun `check updateObjects is called with no NavDisplay in code`() {
    val previewManager = TestComposePreviewManager().apply { setMode(PreviewMode.Interactive(composable)) }

    runWithMockedParseViewInfo(viewInfoChildName = "Text") { layoutlibSceneManagerMock, controllerMock ->
      InteractivePreviewBackNavigationUpdater.update(
        previewManager = previewManager,
        layoutlibSceneManager = layoutlibSceneManagerMock,
        interactivePreviewNavigationController = controllerMock,
      )

      // We expect updateObject method is calling with false [hasNavDisplay].
      verify(controllerMock)
        .updateObjects(
          currentNavigationEventDispatcherOwnerObj = anyOrNull(),
          currentComposeViewAdapterObj = any(),
          hasNavDisplay = eq(false),
        )
    }
  }

  @Test
  fun `check updateObjects is called with NavDisplay in code`() {
    val previewManager = TestComposePreviewManager().apply { setMode(PreviewMode.Interactive(composable)) }

    runWithMockedParseViewInfo(viewInfoChildName = "NavDisplay") { layoutlibSceneManagerMock, controllerMock ->
      InteractivePreviewBackNavigationUpdater.update(
        previewManager = previewManager,
        layoutlibSceneManager = layoutlibSceneManagerMock,
        interactivePreviewNavigationController = controllerMock,
      )

      // We expect updateObject method is calling with true [hasNavDisplay].
      verify(controllerMock)
        .updateObjects(
          currentNavigationEventDispatcherOwnerObj = anyOrNull(),
          currentComposeViewAdapterObj = any(),
          hasNavDisplay = eq(true),
        )
    }
  }

  /** Helper to run a test block with a mocked `parseViewInfo` returning a view tree containing a child with the given name. */
  private fun runWithMockedParseViewInfo(
    viewInfoChildName: String,
    block: (LayoutlibSceneManager, InteractivePreviewNavigationController) -> Unit,
  ) {
    val layoutlibSceneManagerMock = createLayoutLibManager()
    val controllerMock = mock<InteractivePreviewNavigationController>()

    val sourceLocation = SourceLocationImpl("file", 1, 1)
    val childInfo = ComposeViewInfo(sourceLocation, PxBounds(0, 0, 0, 0), emptyList(), viewInfoChildName)
    val rootInfo = ComposeViewInfo(sourceLocation, PxBounds(0, 0, 0, 0), listOf(childInfo), "Root")

    // Mock parseViewInfo using Class.forName because ComposeViewInfoParserKt is not on the test classpath.
    // This is needed because the local ComposeViewAdapter package mismatch causes findComposeViewAdapter to return null.
    val parserClass = Class.forName("com.android.tools.idea.compose.preview.ComposeViewInfoParserKt")
    Mockito.mockStatic(parserClass).use { mockParser ->
      mockParser.whenever<List<ComposeViewInfo>> { parseViewInfo(any(), any()) }.thenReturn(listOf(rootInfo))

      block(layoutlibSceneManagerMock, controllerMock)
    }
  }

  /**
   * Creates a mocked [LayoutlibSceneManager] that simulates a render result returning a mocked [ViewInfo].
   *
   * @return a mocked [LayoutlibSceneManager] instance.
   */
  private fun createLayoutLibManager(): LayoutlibSceneManager =
    mock<LayoutlibSceneManager>().apply {

      // Create a fake [ComposeViewAdapter] with the specified navigation display state
      val fakeComposeViewAdapter = FakeComposeViewAdapter()

      val fakeViewInfo = ViewInfo("androidx.compose.ui.tooling.ComposeViewAdapter", null, 0, 0, 0, 0, fakeComposeViewAdapter, null, null)

      // Mock the render result to return the fake [ViewInfo] as its root view
      val renderResultMock = mock<RenderResult>().apply { whenever(rootViews).thenReturn(ImmutableList.of(fakeViewInfo)) }

      // Set up the layoutlib scene manager mock to return the fake view adapter and render result
      whenever(viewObject).thenReturn(fakeComposeViewAdapter)
      whenever(renderResult).thenReturn(renderResultMock)
    }
}

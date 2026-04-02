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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.preview.analytics.InteractiveNopTracker
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Test

class InteractivePreviewNavigationControllerTest {

  @Test
  fun testBackPressCompletedFromViewAdapterObj() {
    var backPress = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressCompletedCallback = { backPress = true })
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(null, composeViewAdapterObjFake)
    controller.backPressCompleted()
    assertThat(backPress).isTrue()
  }

  @Test
  fun testBackPressStartedFromViewAdapterObj() {
    var startedEdge: String? = null
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressStartedCallback = { startedEdge = it })
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(null, composeViewAdapterObjFake)
    controller.backPressStart(BackNavigationEdge.LEFT_EDGE)
    assertThat(startedEdge).isEqualTo(BackNavigationEdge.LEFT_EDGE.name)
  }

  @Test
  fun testBackPressProgressFromViewAdapterObj() {
    var progressValue = -1f
    var progressEdge: String? = null
    val composeViewAdapterObjFake =
      TestComposeViewAdapterViewObj(
        onBackPressProgressCallback = { progress, edge ->
          progressValue = progress
          progressEdge = edge
        }
      )
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(null, composeViewAdapterObjFake)
    controller.backPressProgress(0.5f, BackNavigationEdge.RIGHT_EDGE)
    assertThat(progressValue).isEqualTo(0.5f)
    assertThat(progressEdge).isEqualTo(BackNavigationEdge.RIGHT_EDGE.name)
  }

  @Test
  fun testBackPressCancelledFromViewAdapterObj() {
    var cancelled = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressCancelledCallback = { cancelled = true })
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(null, composeViewAdapterObjFake)
    controller.backPressCancelled()
    assertThat(cancelled).isTrue()
  }

  @Test
  fun testCanBackPressFromViewAdapterObj() {
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = true)
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(null, composeViewAdapterObjFake)
    assertThat(controller.canBackPress()).isTrue()
  }

  @Test
  fun testBackPressCompletedFromLocalNavigationDispatcher() {
    var backPress = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(canBackPress = true, onBackPressCompletedCallback = { backPress = true })
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    controller.backPressCompleted()
    assertThat(backPress).isTrue()
  }

  @Test
  fun testBackPressStartedFromLocalNavigationDispatcher() {
    var startedEdge: String? = null
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(canBackPress = true, onBackPressStartedCallback = { startedEdge = it })
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    controller.backPressStart(BackNavigationEdge.LEFT_EDGE)
    assertThat(controller.canBackPress()).isTrue()
    assertThat(startedEdge).isEqualTo(BackNavigationEdge.LEFT_EDGE.name)
  }

  @Test
  fun testBackPressCancelledFromLocalNavigationDispatcher() {
    var cancelled = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(canBackPress = true, onBackPressCancelledCallback = { cancelled = true })
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    controller.backPressCancelled()
    assertThat(cancelled).isTrue()
  }

  @Test
  fun testCanBackPressFromLocalNavigationDispatcher() {
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj = TestNavigationEventDispatcherObj(canBackPress = true)
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    assertThat(controller.canBackPress()).isTrue()
  }

  @Test
  fun testBackPressFromLocalNavigationDispatcher() {
    var progressValue = -1f
    var progressEdge: String? = null

    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(
        canBackPress = true,
        onBackPressProgressCallback = { progress, edge ->
          progressValue = progress
          progressEdge = edge
        },
      )
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    controller.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    controller.backPressProgress(0.5f, BackNavigationEdge.RIGHT_EDGE)
    assertThat(controller.canBackPress()).isTrue()
    assertThat(progressValue).isEqualTo(0.5f)
    assertThat(progressEdge).isEqualTo(BackNavigationEdge.RIGHT_EDGE.name)
  }

  @Test
  fun testCanPerformBackNavigation() {
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressCompletedCallback = {})
    controller.updateObjects(null, composeViewAdapterObjFake)
    assertThat(controller.canPerformBackNavigation()).isTrue()
  }

  @Test
  fun testIsPredictiveBackReady() {
    val controller = InteractivePreviewNavigationController({ InteractiveNopTracker() })
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressProgressCallback = { _, _ -> })
    controller.updateObjects(null, composeViewAdapterObjFake)
    assertThat(controller.isPredictiveBackReady()).isTrue()
  }

  @Test
  fun testShowAndHideNavigationControls() {
    var panelUpdated = false
    val controller =
      InteractivePreviewNavigationController(
        onAfterPanelUpdate = { panelUpdated = true },
        usageTrackerProvider = { InteractiveNopTracker() },
      )
    val instance =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings(
          name = "A name",
          baseName = "A base name",
          parameterName = null,
          group = "group1",
          showDecoration = true,
          background = PreviewDisplaySettings.Background.Color("#000"),
          organizationName = "organizationName",
          organizationGroup = "organizationGroup",
        ),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    assertThat(controller.isNavigationControlsShown()).isFalse()
    assertThat(controller.getBottomPanelComponent()).isNull()

    runInEdtAndWait { controller.showNavigationControls(instance) }
    assertThat(controller.isNavigationControlsShown()).isTrue()
    assertThat(controller.getBottomPanelComponent()).isNotNull()
    assertThat(panelUpdated).isTrue()

    panelUpdated = false
    runInEdtAndWait { controller.hideNavigationControls() }
    assertThat(controller.isNavigationControlsShown()).isFalse()
    assertThat(controller.getBottomPanelComponent()).isNull()
    assertThat(panelUpdated).isTrue()
  }
}

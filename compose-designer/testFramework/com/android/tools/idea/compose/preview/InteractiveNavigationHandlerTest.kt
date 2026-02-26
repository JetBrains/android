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

import com.google.common.truth.Truth
import org.junit.Test

class InteractiveNavigationHandlerTest {

  @Test
  fun testBackPressCompletedFromViewAdapterObj() {
    var backPress = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressCompletedCallback = { backPress = true })
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(null, composeViewAdapterObjFake)
    handler.backPressCompleted()
    Truth.assertThat(backPress).isTrue()
  }

  @Test
  fun testBackPressStartedFromViewAdapterObj() {
    var startedEdge: String? = null
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressStartedCallback = { startedEdge = it })
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(null, composeViewAdapterObjFake)
    handler.backPressStart(BackNavigationEdge.LEFT_EDGE)
    Truth.assertThat(startedEdge).isEqualTo(BackNavigationEdge.LEFT_EDGE.name)
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
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(null, composeViewAdapterObjFake)
    handler.backPressProgress(0.5f, BackNavigationEdge.RIGHT_EDGE)
    Truth.assertThat(progressValue).isEqualTo(0.5f)
    Truth.assertThat(progressEdge).isEqualTo(BackNavigationEdge.RIGHT_EDGE.name)
  }

  @Test
  fun testBackPressCancelledFromViewAdapterObj() {
    var cancelled = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(onBackPressCancelledCallback = { cancelled = true })
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(null, composeViewAdapterObjFake)
    handler.backPressCancelled()
    Truth.assertThat(cancelled).isTrue()
  }

  @Test
  fun testCanBackPressFromViewAdapterObj() {
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = true)
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(null, composeViewAdapterObjFake)
    Truth.assertThat(handler.canBackPress()).isTrue()
  }

  @Test
  fun testBackPressCompletedFromLocalNavigationDispatcher() {
    var backPress = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(canBackPress = true, onBackPressCompletedCallback = { backPress = true })
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    handler.backPressCompleted()
    Truth.assertThat(backPress).isTrue()
  }

  @Test
  fun testBackPressStartedFromLocalNavigationDispatcher() {
    var startedEdge: String? = null
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(canBackPress = true, onBackPressStartedCallback = { startedEdge = it })
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    handler.backPressStart(BackNavigationEdge.LEFT_EDGE)
    Truth.assertThat(handler.canBackPress()).isTrue()
    Truth.assertThat(startedEdge).isEqualTo(BackNavigationEdge.LEFT_EDGE.name)
  }

  @Test
  fun testBackPressCancelledFromLocalNavigationDispatcher() {
    var cancelled = false
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj =
      TestNavigationEventDispatcherObj(canBackPress = true, onBackPressCancelledCallback = { cancelled = true })
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    handler.backPressCancelled()
    Truth.assertThat(cancelled).isTrue()
  }

  @Test
  fun testCanBackPressFromLocalNavigationDispatcher() {
    val composeViewAdapterObjFake = TestComposeViewAdapterViewObj(canBackPress = false)
    val localNavigationEventDispatcherObj = TestNavigationEventDispatcherObj(canBackPress = true)
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    Truth.assertThat(handler.canBackPress()).isTrue()
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
    val handler = InteractiveNavigationHandler()
    handler.updateObjects(localNavigationEventDispatcherObj, composeViewAdapterObjFake)
    handler.backPressProgress(0.5f, BackNavigationEdge.RIGHT_EDGE)
    Truth.assertThat(handler.canBackPress()).isTrue()
    Truth.assertThat(progressValue).isEqualTo(0.5f)
    Truth.assertThat(progressEdge).isEqualTo(BackNavigationEdge.RIGHT_EDGE.name)
  }
}

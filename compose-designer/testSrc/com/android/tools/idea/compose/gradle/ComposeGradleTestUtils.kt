/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Activates the [ComposePreviewRepresentation] and waits for scenes to complete rendering. */
suspend fun ComposePreviewRepresentation.activateAndWaitForRender(fakeUi: FakeUi) =
  withTimeout(timeout = 30.seconds) {
    onActivate()

    val sceneViewPeerPanels = mutableSetOf<SceneViewPeerPanel>()
    while (isActive && sceneViewPeerPanels.isEmpty()) {
      withContext(Dispatchers.Main) {
        delay(250)
        invokeAndWaitIfNeeded { fakeUi.root.validate() }
        sceneViewPeerPanels.addAll(fakeUi.findAllComponents())
      }
    }

    // Now wait for them to be rendered
    waitForRender(sceneViewPeerPanels)
  }

suspend fun ComposePreviewRepresentation.waitForRender(
  sceneViewPeerPanels: Set<SceneViewPeerPanel>
) =
  withTimeout(timeout = 30.seconds) {
    while (isActive &&
      sceneViewPeerPanels.any {
        (it.sceneView.sceneManager as? LayoutlibSceneManager)?.renderResult == null
      }) {
      delay(250)
    }
  }

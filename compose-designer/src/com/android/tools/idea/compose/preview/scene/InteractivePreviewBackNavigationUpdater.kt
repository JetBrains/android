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

import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.VisibleForTesting

object InteractivePreviewBackNavigationUpdater {
  /**
   * Sets the `backPressDispatcher` property of the [previewElement] to the ComposeViewAdapter's
   * FakeOnBackPressedDispatcherOwner#onBackPressedDispatcher() method. This will make sure that
   * back events triggered in interactive preview will be dispatched to Composable.
   */
  @VisibleForTesting
  fun update(
    viewObj: Any,
    previewManager: PreviewModeManager,
    previewElement: ComposePreviewElementInstance<*>,
  ) {
    if (previewManager.mode.value !is PreviewMode.Interactive) return
    try {
      val fakeOnBackPressedDispatcherOwner =
        viewObj::class
          .java
          .declaredFields
          .single { it.name == "FakeOnBackPressedDispatcherOwner" }
          .also { it.isAccessible = true }
          .get(viewObj)

      val onBackPressedDispatcher =
        fakeOnBackPressedDispatcherOwner::class
          .java
          .declaredFields
          .single { it.name == "onBackPressedDispatcher" }
          .also { it.isAccessible = true }
          .get(fakeOnBackPressedDispatcherOwner)

      previewElement.mutableBackPressDispatcher = onBackPressedDispatcher
    } catch (e: Throwable) {
      Logger.getInstance(InteractivePreviewBackNavigationUpdater::class.java)
        .debug("Could not get the Composable OnBackPressedDispatcher.", e)
    }
  }

  fun update(previewManager: PreviewModeManager, layoutlibSceneManager: LayoutlibSceneManager) {
    val previewElementInstance =
      layoutlibSceneManager.model.dataProvider?.previewElement() ?: return
    val viewObj = layoutlibSceneManager.viewObject ?: return
    update(viewObj, previewManager, previewElementInstance)
  }
}

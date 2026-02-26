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
import com.android.tools.idea.compose.preview.scene.InteractivePreviewBackNavigationUpdater.currentNavigationEventDispatcherOwner
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.rendering.classloading.LocalNavigationEventTransform
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.ComposePreviewElementInstance

/**
 * Sets up the [InteractiveNavigationHandler] on the [ComposePreviewElementInstance] responsible for handling interactive back navigation
 * events in the preview.
 */
object InteractivePreviewBackNavigationUpdater {

  private var _currentNavigationEventDispatcherOwner: Any? = null

  /**
   * Returns the current `androidx.navigationevent.compose.FakeNavigationEventDispatcherOwner` previously created by
   * [LocalNavigationEventTransform]
   */
  val currentNavigationEventDispatcherOwner
    get() = _currentNavigationEventDispatcherOwner

  /**
   * Sets the current [androidx.navigationevent.NavigationEventDispatcherOwner].
   *
   * This method is invoked by [LocalNavigationEventTransform] via bytecode injection to store the
   * `androidx.navigationevent.compose.FakeNavigationEventDispatcherOwner` it creates. This makes the fake dispatcher accessible via
   * [currentNavigationEventDispatcherOwner].
   *
   * WARNING: Do not rename or delete this method. It is accessed by name using a Java [org.objectweb.asm.MethodVisitor] within
   * [LocalNavigationEventTransform.visitMethod].
   */
  @Suppress("unused") // Field names are accessed through [LocalNavigationEventTransform]
  fun setNavigationEventDispatcherOwner(dispatcher: Any) {
    _currentNavigationEventDispatcherOwner = dispatcher
  }

  /**
   * Updates the [InteractiveNavigationHandler] for the current [ComposePreviewElementInstance] using the provided [LayoutlibSceneManager].
   *
   * This method retrieves the [ComposePreviewElementInstance] and the underlying object of `androidx.compose.ui-tooling.ComposeViewAdapter`
   * from the [LayoutlibSceneManager] to update the [InteractiveNavigationHandler].
   *
   * Call this method on every preview render to ensure the [InteractiveNavigationHandler] has the most current navigation information from
   * the [ComposeViewAdapter].
   *
   * @param previewManager The [PreviewModeManager] to check the current preview mode.
   * @param layoutlibSceneManager The [LayoutlibSceneManager] providing the [ComposePreviewElementInstance] and the [ComposeViewAdapter]
   *   object.
   */
  fun update(
    previewManager: PreviewModeManager,
    layoutlibSceneManager: LayoutlibSceneManager,
    interactiveNavigationHandler: InteractiveNavigationHandler,
  ) {
    val composeViewAdapterObj = layoutlibSceneManager.viewObject ?: return
    if (previewManager.mode.value !is PreviewMode.Interactive) return
    interactiveNavigationHandler.updateObjects(
      currentNavigationEventDispatcherOwnerObj = currentNavigationEventDispatcherOwner,
      currentComposeViewAdapterObj = composeViewAdapterObj,
    )
  }
}

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
import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.compose.preview.scene.InteractivePreviewBackNavigationUpdater.currentNavigationEventDispatcherOwner
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.rendering.classloading.LocalNavigationEventTransform
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.diagnostic.thisLogger

private const val NAV_DISPLAY_NAME = "NavDisplay"

/**
 * Sets up the [InteractivePreviewNavigationController] on the [ComposePreviewElementInstance] responsible for handling interactive back
 * navigation events in the preview.
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
   * Updates the [InteractivePreviewNavigationController] for the current [ComposePreviewElementInstance] using the provided
   * [LayoutlibSceneManager].
   *
   * This method retrieves the underlying object of [ComposeViewAdapter] and the hierarchy information from the [LayoutlibSceneManager] to
   * check for the presence of a navigation3 [NavDisplay] element, and then propagates this status along with the resolved dispatcher
   * objects to the [InteractivePreviewNavigationController].
   *
   * Call this method on every preview render to ensure the [InteractivePreviewNavigationController] has the most current navigation
   * information from the [ComposeViewAdapter].
   *
   * @param previewManager The [PreviewModeManager] to check the current preview mode.
   * @param layoutlibSceneManager The [LayoutlibSceneManager] providing the [ComposePreviewElementInstance] and the [ComposeViewAdapter]
   *   object.
   */
  fun update(
    previewManager: PreviewModeManager,
    layoutlibSceneManager: LayoutlibSceneManager,
    interactivePreviewNavigationController: InteractivePreviewNavigationController,
  ) {
    val composeViewAdapterObj = layoutlibSceneManager.viewObject ?: return
    if (previewManager.mode.value !is PreviewMode.Interactive) return
    val rootViews = layoutlibSceneManager.renderResult?.rootViews ?: emptyList()
    val viewInfo: ViewInfo? = rootViews.firstOrNull()
    val hasNavDisplayInViewTree: Boolean =
      viewInfo?.let { info ->
        val composeViewInfos = parseViewInfo(rootViewInfo = info, logger = thisLogger())
        //  Check in the viewInfo if we have a navigation3 NavDisplay.
        composeViewInfos.flatMap { it.allChildren() }.any { it.name == NAV_DISPLAY_NAME }
      } ?: false

    interactivePreviewNavigationController.updateObjects(
      currentNavigationEventDispatcherOwnerObj = currentNavigationEventDispatcherOwner,
      currentComposeViewAdapterObj = composeViewAdapterObj,
      hasNavDisplay = hasNavDisplayInViewTree,
    )
  }
}

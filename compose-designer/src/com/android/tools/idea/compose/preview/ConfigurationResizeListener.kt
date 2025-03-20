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
package com.android.tools.idea.compose.preview

import com.android.resources.ScreenOrientation
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeInRenderSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Invokes rendering of the scene when the device size changes in [Configuration]. The lifecycle is
 * tied to corresponding [SceneManger]
 *
 * @param sceneManager SceneManager
 * @param configuration Configuration
 * @param defaultDispatcher CoroutineDispatcher
 */
class ConfigurationResizeListener(
  private val sceneManager: LayoutlibSceneManager,
  private val configuration: Configuration,
  defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ConfigurationListener, Disposable {

  private val scope =
    CoroutineScope(defaultDispatcher + SupervisorJob() + CoroutineName(javaClass.simpleName))

  private val deviceSizeChangedFlow = MutableStateFlow<Dimension>(configuration.deviceSize())

  init {
    Disposer.register(sceneManager, this)
    scope.launch { deviceSizeChangedFlow.drop(1).collectLatest(::requestRender) }
  }

  override fun changed(changeType: Int): Boolean {
    if (changeType and ConfigurationListener.CFG_DEVICE != 0) {
      deviceSizeChangedFlow.value = configuration.deviceSize()
    }
    return true
  }

  private suspend fun requestRender(newDeviceSize: Dimension) {
    if (!sceneManager.sceneRenderConfiguration.showDecorations) {
      val viewObj = sceneManager.viewObject ?: return
      sceneManager.executeInRenderSession(false) { updateLayoutParams(viewObj, newDeviceSize) }
    }
    sceneManager.requestRenderWithNewSize(newDeviceSize.width, newDeviceSize.height)
  }

  /**
   * Update the layout parameters of the given view object to the new size. It's needed to override
   * any values(width/height) that were set in Preview annotation.
   *
   * View object is actual android.view.View (or child class) object. Uses reflection to access
   * fields.
   *
   * @param viewObj the view object whose layout parameters will be updated.
   * @param newDeviceSize the new size of the device.
   */
  private fun updateLayoutParams(viewObj: Any, newDeviceSize: Dimension) {
    val layoutParams = viewObj.javaClass.getMethod("getLayoutParams").invoke(viewObj)
    layoutParams.javaClass.getField("width").set(layoutParams, newDeviceSize.width)
    layoutParams.javaClass.getField("height").set(layoutParams, newDeviceSize.height)
  }

  private fun calculateDimensions(
    x: Int,
    y: Int,
    mScreenOrientation: ScreenOrientation?,
  ): Dimension {
    // Determine if the desired orientation needs a swap.
    val shouldSwapDimensions = (x > y) != (mScreenOrientation == ScreenOrientation.LANDSCAPE)

    return if (shouldSwapDimensions) {
      Dimension(y, x)
    } else {
      Dimension(x, y)
    }
  }

  private fun Configuration.deviceSize(): Dimension {
    val deviceState = deviceState ?: return Dimension(0, 0)
    val orientation = deviceState.orientation
    val x = deviceState.hardware.screen.xDimension
    val y = deviceState.hardware.screen.yDimension
    return calculateDimensions(x, y, orientation)
  }

  override fun dispose() {
    scope.cancel()
    configuration.removeListener(this)
  }
}

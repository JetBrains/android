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
package com.android.tools.idea.compose.preview.resize

import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.util.updateLayoutParams
import com.android.tools.idea.common.util.updateLayoutParamsToWrapContent
import com.android.tools.idea.compose.preview.util.deviceSize
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeInRenderSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
  private val logger = Logger.getInstance(ConfigurationResizeListener::class.java)

  private val scope = CoroutineScope(defaultDispatcher + CoroutineName(javaClass.simpleName))

  private val deviceSizeChangedFlow = MutableStateFlow<Dimension>(configuration.deviceSize())

  init {
    Disposer.register(sceneManager, this)
    scope.launch {
      deviceSizeChangedFlow.drop(1).collectLatest { newDeviceSize ->
        try {
          requestRender(newDeviceSize)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          logger.warn("Error inside requestRender: ", e)
        }
      }
    }
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
      sceneManager.executeInRenderSession(false) {
        if (sceneManager.forceNextResizeToWrapContent) {
          // The SceneManager has been flagged to force the next resize (typically a revert of
          // resizing) to wrap content for this shrink-mode preview.
          // This ensures the Composable measures itself based on its content, how it's done by
          // default
          // see [com.android.tools.preview.ComposePreviewElementInstance.toPreviewXml].
          updateLayoutParamsToWrapContent(viewObj)
          // Reset the flag immediately after acting on it to ensure it only affects this single
          // operation.
          sceneManager.forceNextResizeToWrapContent = false
        } else {
          updateLayoutParams(viewObj, newDeviceSize)
        }
      }
    }
    sceneManager.requestRenderWithNewSize(newDeviceSize.width, newDeviceSize.height)
  }

  override fun dispose() {
    scope.cancel()
    configuration.removeListener(this)
  }
}

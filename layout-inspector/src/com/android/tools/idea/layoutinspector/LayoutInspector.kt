/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.common.MostRecentExecutor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.EditorRenderSettings
import com.android.tools.idea.layoutinspector.ui.InspectorRenderSettings
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel.Status
import java.awt.Component
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@VisibleForTesting const val SHOW_ERROR_MESSAGES_IN_DIALOG = false

val LAYOUT_INSPECTOR_DATA_KEY = DataKey.create<LayoutInspector>(LayoutInspector::class.java.name)
val NO_COMPOSE_SOURCE_INFO_APP_KEY = "no.compose.source.info.app"

/** Create a [DataProvider] for the specified [layoutInspector]. */
fun dataProviderForLayoutInspector(layoutInspector: LayoutInspector): DataProvider {
  return DataProvider { dataId ->
    when {
      LAYOUT_INSPECTOR_DATA_KEY.`is`(dataId) -> layoutInspector
      else -> null
    }
  }
}

private val logger = Logger.getInstance(LayoutInspector::class.java)

/** Top level class which manages the high level state of layout inspection. */
class LayoutInspector
private constructor(
  val inspectorModel: InspectorModel,
  val notificationModel: NotificationModel,
  val coroutineScope: CoroutineScope,
  val processModel: ProcessesModel?,
  val deviceModel: DeviceModel?,
  val foregroundProcessDetection: ForegroundProcessDetection?,
  val inspectorClientSettings: InspectorClientSettings,
  val treeSettings: TreeSettings,
  val isSnapshot: Boolean,
  val launcher: InspectorClientLauncher?,
  private val currentClientProvider: () -> InspectorClient,
  workerExecutor: Executor = AndroidExecutors.getInstance().workerThreadExecutor,
  val renderModel: RenderModel,
  val renderLogic: RenderLogic,
) {

  /**
   * Construct a LayoutInspector that can launch new [InspectorClient]s as needed using [launcher].
   */
  constructor(
    coroutineScope: CoroutineScope,
    processModel: ProcessesModel,
    deviceModel: DeviceModel,
    foregroundProcessDetection: ForegroundProcessDetection?,
    inspectorClientSettings: InspectorClientSettings,
    launcher: InspectorClientLauncher,
    layoutInspectorModel: InspectorModel,
    notificationModel: NotificationModel,
    treeSettings: TreeSettings,
    executor: Executor = AndroidExecutors.getInstance().workerThreadExecutor,
    renderModel: RenderModel =
      RenderModel(layoutInspectorModel, notificationModel, treeSettings) { launcher.activeClient },
    renderLogic: RenderLogic = RenderLogic(renderModel, InspectorRenderSettings()),
  ) : this(
    layoutInspectorModel,
    notificationModel,
    coroutineScope,
    processModel,
    deviceModel,
    foregroundProcessDetection,
    inspectorClientSettings,
    treeSettings,
    false,
    launcher,
    { launcher.activeClient },
    executor,
    renderModel,
    renderLogic,
  ) {
    launcher.addClientChangedListener(::onClientChanged)
  }

  /**
   * Construct a LayoutInspector tied to a specific [InspectorClient], e.g. for viewing a snapshot
   * file.
   */
  constructor(
    coroutineScope: CoroutineScope,
    layoutInspectorClientSettings: InspectorClientSettings,
    client: InspectorClient,
    layoutInspectorModel: InspectorModel,
    notificationModel: NotificationModel,
    treeSettings: TreeSettings,
    executor: Executor = AndroidExecutors.getInstance().workerThreadExecutor,
    renderModel: RenderModel =
      RenderModel(layoutInspectorModel, notificationModel, treeSettings) { client },
    renderLogic: RenderLogic = RenderLogic(renderModel, EditorRenderSettings()),
  ) : this(
    inspectorModel = layoutInspectorModel,
    notificationModel = notificationModel,
    coroutineScope = coroutineScope,
    processModel = null,
    deviceModel = null,
    foregroundProcessDetection = null,
    inspectorClientSettings = layoutInspectorClientSettings,
    treeSettings = treeSettings,
    isSnapshot = true,
    launcher = null,
    currentClientProvider = { client },
    workerExecutor = executor,
    renderModel,
    renderLogic,
  ) {
    onClientChanged(client)
  }

  init {
    // refresh the rendering each time the inspector model changes
    inspectorModel.addModificationListener { _, newAndroidWindow, _ ->
      coroutineScope.launch {
        newAndroidWindow?.refreshImages(renderLogic.renderSettings.scaleFraction)
        renderModel.refresh()
      }
    }
  }

  val currentClient
    get() = currentClientProvider()

  private val latestLoadTime = AtomicLong(-1)

  private val recentExecutor = MostRecentExecutor(workerExecutor)

  private var composeSourceInfoMissingWarningGiven = false

  val stopInspectorListeners: MutableList<() -> Unit> = mutableListOf()

  /**
   * Stops LayoutInspector. If a device is selected, stops foreground process detection. If a device
   * is not selected, stops process inspection by setting the selected process to null.
   *
   * A process can be selected when a device does not support foreground process detection.
   */
  fun stopInspector() {
    coroutineScope.launch(AndroidDispatchers.workerThread) {
      val selectedDevice = deviceModel?.selectedDevice
      if (selectedDevice != null) {
        foregroundProcessDetection?.stopPollingSelectedDevice()
      } else {
        processModel?.stop()
        processModel?.selectedProcess = null
      }

      stopInspectorListeners.forEach { it() }
    }
  }

  private fun onClientChanged(client: InspectorClient) {
    if (client !== DisconnectedClient) {
      client.registerErrorCallback(::showErrorMessage)
      client.registerRootsEventCallback(::adjustRoots)
      client.registerTreeEventCallback(::loadComponentTree)
      client.registerStateCallback { state ->
        if (state == InspectorClient.State.CONNECTED) updateConnection(client)
      }
      client.registerConnectionTimeoutCallback { state ->
        inspectorModel.fireAttachStateEvent(state)
      }
      client.stats.start()
    } else {
      // If disconnected, e.g. stopped, force models to clear their state and, by association, the
      // UI
      inspectorModel.updateConnection(DisconnectedClient)
      ApplicationManager.getApplication().invokeLater {
        if (currentClient === DisconnectedClient) {
          inspectorModel.update(null, listOf<Any>(), 0)
        }
      }
    }
    composeSourceInfoMissingWarningGiven = false
  }

  private fun updateConnection(client: InspectorClient) {
    inspectorModel.updateConnection(client)
    client.stats.currentModeIsLive = client.inLiveMode
    client.stats.hideSystemNodes = treeSettings.hideSystemNodes
    client.stats.showRecompositions = treeSettings.showRecompositions
  }

  private fun adjustRoots(roots: List<*>) {
    recentExecutor.execute {
      if (!roots.containsAll(inspectorModel.windows.keys)) {
        // remove the roots that are no longer present
        inspectorModel.update(null, roots, 0)
      }
    }
  }

  private fun loadComponentTree(event: Any) {
    recentExecutor.execute {
      val time = System.currentTimeMillis()
      val treeLoader = currentClient.treeLoader
      val allIds = treeLoader.getAllWindowIds(event)
      val data =
        treeLoader.loadComponentTree(event, inspectorModel.resourceLookup, currentClient.process)
          ?: return@execute
      currentClient.updateProgress(AttachErrorState.PARSED_COMPONENT_TREE)
      currentClient.addDynamicCapabilities(data.dynamicCapabilities)
      if (allIds != null) {
        synchronized(latestLoadTime) {
          if (latestLoadTime.get() > time) {
            return@execute
          }
          latestLoadTime.set(time)
          // If we've disconnected, don't continue with the update.
          if (currentClient.state <= InspectorClient.State.CONNECTED) {
            inspectorModel.update(data.window, allIds, data.generation) {
              currentClient.updateProgress(AttachErrorState.MODEL_UPDATED)
              if (logger.isDebugEnabled) {
                // This logger.debug statement is for integration tests
                logger.debug(
                  "g:${data.generation} Model Updated for process: ${currentClient.process.name}"
                )
              }
            }
            if (
              !composeSourceInfoMissingWarningGiven &&
                currentClient.capabilities.contains(InspectorClient.Capability.SUPPORTS_COMPOSE) &&
                !currentClient.capabilities.contains(
                  InspectorClient.Capability.HAS_LINE_NUMBER_INFORMATION
                )
            ) {
              composeSourceInfoMissingWarningGiven = true
              notificationModel.addNotification(
                NO_COMPOSE_SOURCE_INFO_APP_KEY,
                LayoutInspectorBundle.message(NO_COMPOSE_SOURCE_INFO_APP_KEY),
                Status.Warning,
              )
            }
          }
          // Check one more time to see if we've disconnected.
          if (currentClient.state > InspectorClient.State.CONNECTED) {
            inspectorModel.clear()
            Logger.getInstance(LayoutInspector::class.java)
          }
        }
      }
    }
  }

  private fun showErrorMessage(errorMessage: String) {
    notificationModel.addNotification(errorMessage, errorMessage, Status.Error)

    if (SHOW_ERROR_MESSAGES_IN_DIALOG) {
      invokeLater {
        Messages.showErrorDialog(inspectorModel.project, errorMessage, "Inspector Error")
      }
    }
  }

  companion object {
    fun get(component: Component): LayoutInspector? =
      DataManager.getInstance().getDataContext(component).getData(LAYOUT_INSPECTOR_DATA_KEY)

    fun get(event: AnActionEvent): LayoutInspector? = event.getData(LAYOUT_INSPECTOR_DATA_KEY)
  }
}

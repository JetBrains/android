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

import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.common.MostRecentExecutor
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

@VisibleForTesting
const val SHOW_ERROR_MESSAGES_IN_DIALOG = false

/**
 * Create the top level class which manages the high level state of layout inspection.
 *
 * @param executor An executor used for doing background work like loading tree data and
 *    initializing the model. Exposed mainly for testing, where a direct executor can provide
 *    consistent behavior over performance.
 */
class LayoutInspector(
  private val launcher: InspectorClientLauncher,
  val layoutInspectorModel: InspectorModel,
  val stats: SessionStatistics,
  @TestOnly private val executor: Executor = AndroidExecutors.getInstance().workerThreadExecutor
) {

  val currentClient: InspectorClient get() = launcher.activeClient

  private val latestLoadTime = AtomicLong(-1)

  private val recentExecutor = MostRecentExecutor(executor)

  init {
    launcher.addClientChangedListener(::clientChanged)
  }

  private fun clientChanged(client: InspectorClient) {
    if (client !== DisconnectedClient) {
      client.registerErrorCallback(::logError)
      client.registerTreeEventCallback(::loadComponentTree)
      client.registerStateCallback { state -> if (state == InspectorClient.State.CONNECTED) updateConnection(client) }
      stats.start(client.isCapturing)
    }
    else {
      // If disconnected, e.g. stopped, force models to clear their state and, by association, the UI
      layoutInspectorModel.updateConnection(DisconnectedClient)
      ApplicationManager.getApplication().invokeLater {
        if (currentClient === DisconnectedClient) {
          layoutInspectorModel.update(null, listOf<Any>(), 0)
        }
      }
    }
  }

  private fun updateConnection(client: InspectorClient) {
    layoutInspectorModel.updateConnection(client)
  }

  private fun loadComponentTree(event: Any) {
    recentExecutor.execute {
      val time = System.currentTimeMillis()
      val treeLoader = currentClient.treeLoader
      val allIds = treeLoader.getAllWindowIds(event)
      val (window, generation) = treeLoader.loadComponentTree(event, layoutInspectorModel.resourceLookup) ?: return@execute
      if (allIds != null) {
        synchronized(latestLoadTime) {
          if (latestLoadTime.get() > time) {
            return@execute
          }
          latestLoadTime.set(time)
          layoutInspectorModel.update(window, allIds, generation)
        }
      }
    }
  }

  private fun logError(error: String) {
    Logger.getInstance(LayoutInspector::class.java.canonicalName).warn(error)

    @Suppress("ConstantConditionIf")
    if (SHOW_ERROR_MESSAGES_IN_DIALOG) {
      ApplicationManager.getApplication().invokeLater {
        Messages.showErrorDialog(layoutInspectorModel.project, error, "Inspector Error")
      }
    }
  }

  companion object {
    fun get(component: Component): LayoutInspector? =
      DataManager.getInstance().getDataContext(component).getData(LAYOUT_INSPECTOR_DATA_KEY)

    fun get(event: AnActionEvent): LayoutInspector? = event.getData(LAYOUT_INSPECTOR_DATA_KEY)
  }
}

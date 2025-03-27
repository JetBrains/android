/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.NO_COMPOSE_SOURCE_INFO_APP_KEY
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.pom.Navigatable
import com.intellij.ui.EditorNotificationPanel.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VIEW_NOT_FOUND_KEY = "view.not.found"
const val NO_COMPOSE_SOURCE_INFO_NODE_KEY = "no.compose.source.info.node"
const val NO_COMPOSE_SOURCE_INFO_NODE_INLINED_KEY = "no.compose.source.info.node.inlined"

/** Action for navigating to the currently selected node in the layout inspector. */
object GotoDeclarationAction : AnAction("Go To Declaration") {
  @get:VisibleForTesting var lastAction: Job? = null

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val inspector = LayoutInspector.get(event) ?: return
    inspector.currentClient.stats.gotoSourceFromTreeActionMenu(event)
    navigateToSelectedView(
      inspector.coroutineScope,
      inspector.inspectorModel,
      inspector.currentClient,
      inspector.notificationModel,
    )
  }

  override fun update(event: AnActionEvent) {
    val inspector = LayoutInspector.get(event)
    val hasSourceCodeInfo = inspector?.inspectorModel?.selection?.hasSourceCodeInformation ?: true
    // Finding the navigatable is expensive. Only perform a cheap check on update:
    event.presentation.isEnabled =
      hasSourceCodeInfo && (inspector?.inspectorModel?.resourceLookup?.hasResolver == true)
    event.presentation.text =
      if (hasSourceCodeInfo) "Go To Declaration"
      else "Go To Declaration (No Source Information Found)"
  }

  fun navigateToSelectedView(
    coroutineScope: CoroutineScope,
    inspectorModel: InspectorModel,
    client: InspectorClient,
    notificationModel: NotificationModel,
  ) {
    lastAction =
      coroutineScope.launch {
        val navigatable =
          findNavigatable(inspectorModel, client, notificationModel) ?: return@launch
        withContext(AndroidDispatchers.uiThread) { navigatable.navigate(true) }
      }
  }

  @Slow
  private suspend fun findNavigatable(
    model: InspectorModel,
    client: InspectorClient,
    notificationModel: NotificationModel,
  ): Navigatable? =
    withContext(AndroidDispatchers.workerThread) {
      val resourceLookup = model.resourceLookup
      val node = model.selection ?: return@withContext null
      val navigatable =
        if (node is ComposeViewNode) {
          resourceLookup.findComposableNavigatable(node)
        } else {
          withContext(AndroidDispatchers.uiThread) {
            resourceLookup.findFileLocation(node)?.navigatable
          }
        }
      updateNotifications(notificationModel, navigatable, client, node)
      navigatable
    }

  private fun updateNotifications(
    notificationModel: NotificationModel,
    navigatable: Navigatable?,
    client: InspectorClient,
    node: ViewNode,
  ) {
    val layout = node.layout?.name
    when {
      navigatable == null && node is ComposeViewNode ->
        reportMissingSourceInformation(notificationModel, client, node)
      navigatable == null && node.viewId == null && layout != null && !node.isSystemNode ->
        reportViewNotFound(notificationModel, node, layout)
      else -> {
        notificationModel.removeWarnings()
      }
    }
  }

  private fun reportViewNotFound(
    notificationModel: NotificationModel,
    node: ViewNode,
    layout: String,
  ) {
    notificationModel.showWarning(VIEW_NOT_FOUND_KEY, node, layout)
  }

  private fun reportMissingSourceInformation(
    notificationModel: NotificationModel,
    client: InspectorClient,
    node: ComposeViewNode,
  ) {
    val appHasNoSourceInformation =
      client.capabilities.contains(InspectorClient.Capability.SUPPORTS_COMPOSE) &&
        !client.capabilities.contains(InspectorClient.Capability.HAS_LINE_NUMBER_INFORMATION)
    if (appHasNoSourceInformation) {
      notificationModel.showWarning(NO_COMPOSE_SOURCE_INFO_APP_KEY, node)
    } else if (node.isInlined) {
      notificationModel.showWarning(NO_COMPOSE_SOURCE_INFO_NODE_INLINED_KEY, node)
    } else {
      notificationModel.showWarning(NO_COMPOSE_SOURCE_INFO_NODE_KEY, node)
    }
  }

  private fun NotificationModel.showWarning(key: String, node: ViewNode, layout: String = "") {
    removeWarnings(key)
    addNotification(
      key,
      LayoutInspectorBundle.message(key, node.unqualifiedName, layout),
      Status.Warning,
    )
  }

  private fun NotificationModel.removeWarnings(except: String = "") {
    for (msg in
      listOf(
        VIEW_NOT_FOUND_KEY,
        NO_COMPOSE_SOURCE_INFO_NODE_KEY,
        NO_COMPOSE_SOURCE_INFO_NODE_INLINED_KEY,
      )) {
      if (msg != except) {
        removeNotification(msg)
      }
    }
  }
}

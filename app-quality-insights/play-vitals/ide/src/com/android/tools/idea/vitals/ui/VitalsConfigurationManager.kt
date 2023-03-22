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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerImpl
import com.android.tools.idea.insights.AppInsightsService
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.VariantConnection
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.events.actions.AppInsightsActionQueueImpl
import com.android.tools.idea.insights.ui.AppInsightsToolWindowFactory
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.vitals.VitalsConnectionInferrer
import com.android.tools.idea.vitals.client.VitalsClient
import com.android.tools.idea.vitals.createVitalsFilters
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.MessageType
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

class VitalsConfigurationManager(override val project: Project) :
  AppInsightsConfigurationManager, Disposable {
  private val client = VitalsClient()

  // TODO(b/265153845): implement getting of connections when API is ready
  private val module = project.modules.first { it.isHolderModule() }
  private val CONNECTION1 = Connection("app1", "app-id1", project.name, "123")
  private val VARIANT1 = VariantConnection(module, "N/A", CONNECTION1)
  private val insightsService = service<AppInsightsService>()

  private val controller =
    AppInsightsProjectLevelControllerImpl(
      AndroidCoroutineScope(this, AndroidDispatchers.uiThread),
      AndroidDispatchers.workerThread,
      client,
      flowOf(listOf(VARIANT1)),
      insightsService.offlineStatus,
      setOfflineMode = { status -> insightsService.enterMode(status) },
      onIssuesChanged = { DaemonCodeAnalyzer.getInstance(project).restart() },
      tracker = project.service(),
      clock = Clock.systemDefaultZone(),
      project = project,
      queue = AppInsightsActionQueueImpl(ConcurrentLinkedQueue()),
      onErrorAction = { msg, hyperlinkListener ->
        AppInsightsToolWindowFactory.showBalloon(project, MessageType.ERROR, msg, hyperlinkListener)
      },
      connectionInferrer = VitalsConnectionInferrer(),
      defaultFilters = createVitalsFilters(),
      // TODO(b/275428405): this likely needs to be a project service and separate from other
      // caches.
      cache = service<AppInsightsCache>()
    )
  override val configuration =
    MutableSharedFlow<AppInsightsModel>(1).apply {
      tryEmit(AppInsightsModel.Authenticated(controller))
    }

  override fun getController() = controller

  override fun dispose() = Unit
}

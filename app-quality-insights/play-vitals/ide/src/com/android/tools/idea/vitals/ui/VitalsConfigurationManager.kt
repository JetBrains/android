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
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.VITALS_KEY
import com.android.tools.idea.insights.VariantConnection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.AppInsightsTrackerImpl
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.events.actions.AppInsightsActionQueueImpl
import com.android.tools.idea.insights.ui.AppInsightsToolWindowFactory
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.vitals.VitalsConnectionInferrer
import com.android.tools.idea.vitals.client.VitalsClient
import com.android.tools.idea.vitals.createVitalsFilters
import com.google.gct.login.LoginState
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.MessageType
import com.intellij.serviceContainer.NonInjectable
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class VitalsConfigurationManager
@NonInjectable
@TestOnly
constructor(
  override val project: Project,
  createVitalsClient: (Disposable) -> AppInsightsClient,
  loginState: Flow<Boolean> = LoginState.loggedIn
) : AppInsightsConfigurationManager, Disposable {
  @Suppress("unused")
  constructor(project: Project) : this(project, { disposable -> VitalsClient(disposable) })

  private val client = createVitalsClient(this)
  private val scope = AndroidCoroutineScope(this)
  private val refreshConfigurationFlow =
    MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val insightsService = service<AppInsightsService>()

  private val queryConnectionsFlow =
    flow {
        emit(client.listConnections())
        refreshConfigurationFlow.collect { emit(client.listConnections()) }
      }
      .distinctUntilChanged()
      .shareIn(scope, SharingStarted.Eagerly, 1)

  private val controller =
    AppInsightsProjectLevelControllerImpl(
      key = VITALS_KEY,
      AndroidCoroutineScope(this, AndroidDispatchers.uiThread),
      AndroidDispatchers.workerThread,
      client,
      queryConnectionsFlow.mapConnectionsToVariantConnectionsIfReady(),
      insightsService.offlineStatus,
      setOfflineMode = { status -> insightsService.enterMode(status) },
      onIssuesChanged = { DaemonCodeAnalyzer.getInstance(project).restart() },
      tracker = AppInsightsTrackerImpl(project, AppInsightsTracker.ProductType.PLAY_VITALS),
      clock = Clock.systemDefaultZone(),
      project = project,
      queue = AppInsightsActionQueueImpl(ConcurrentLinkedQueue()),
      onErrorAction = { msg, hyperlinkListener ->
        AppInsightsToolWindowFactory.showBalloon(project, MessageType.ERROR, msg, hyperlinkListener)
      },
      connectionInferrer = VitalsConnectionInferrer(),
      defaultFilters = createVitalsFilters(),
      cache = AppInsightsCacheImpl()
    )

  init {
    scope.launch { loginState.collect { refreshConfiguration() } }
  }

  override val configuration =
    queryConnectionsFlow
      .mapAvailableAppsToModel()
      .stateIn(scope, SharingStarted.Eagerly, AppInsightsModel.Uninitialized)

  override fun refreshConfiguration() {
    refreshConfigurationFlow.tryEmit(Unit)
  }

  override fun dispose() = Unit

  private fun Flow<LoadingState.Done<List<Connection>>>
    .mapConnectionsToVariantConnectionsIfReady() = mapNotNull { result ->
    (result as? LoadingState.Ready)?.let { ready ->
      // TODO(b/265153845): pair connection to its app module if possible.
      val module = project.modules.first { it.isHolderModule() }
      ready.value.map { connection -> VariantConnection(module, "N/A", connection) }
    }
  }

  private fun Flow<LoadingState.Done<List<Connection>>>.mapAvailableAppsToModel() = map {
    when (it) {
      is LoadingState.Ready -> {
        AppInsightsModel.Authenticated(controller)
      }
      // TODO(b/274775776): disambiguate between different failures. Ex: authentication, grpc, etc.
      is LoadingState.Failure -> {
        AppInsightsModel.Unauthenticated
      }
    }
  }
}

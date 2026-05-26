/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.insights.inspection

import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.InsightsProvider.Source
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Represents the preference level of a connection.
 *
 * The levels are ordered by importance, from least to most preferred.
 */
enum class ConnectionPreference(val value: Int) {
  /** The connection is not available. */
  UNAVAILABLE(0),
  /** The connection is configured. */
  CONFIGURED(1),
  /** The connection matches the current project. */
  MATCHING(3),
  /** The connection is the preferred one for the current project. */
  PREFERRED(7),
}

/**
 * Data class representing a filter for connections.
 *
 * @property appId The ID of the application.
 * @property title The display title for the filter.
 * @property preferences A map of connection preferences for each source.
 */
data class ConnectionFilter(val appId: String, val title: String, val preferences: Map<Source, ConnectionPreference>)

/**
 * Service responsible for selecting and managing app insights filters.
 *
 * This service collects connection information from all available [AppInsightsTabProvider]s and combines them into a list of
 * [ConnectionFilter]s.
 *
 * @property project The current project.
 * @property scope The coroutine scope to use for collecting flows.
 */
@Service(Level.PROJECT)
class AppInsightsFilterSelector(val project: Project, val scope: CoroutineScope) {

  private val _filters: MutableStateFlow<List<ConnectionFilter>> = MutableStateFlow(listOf())
  /** State flow emitting the current list of [ConnectionFilter]s. */
  val filters: StateFlow<List<ConnectionFilter>> = _filters

  val selectedAppId: MutableStateFlow<String?> = MutableStateFlow(null)

  /**
   * Starts collecting connection information from all tab providers.
   *
   * This method launches a coroutine that combines the states of all authenticated [AppInsightsModel]s and updates the [filters] flow
   * accordingly.
   */
  fun startCollection() {
    scope.launch {
      // Collect controllers from all tab providers with authenticated [AppInsightsModel].
      val combinedControllers =
        combine(
          AppInsightsTabProvider.EP_NAME.extensionList.map { tabProvider -> tabProvider.getConfigurationManager(project).configuration }
        ) { configurations ->
          configurations.filterIsInstance<AppInsightsModel.Authenticated>().map { it.controller }
        }

      combinedControllers.collectLatest { controllers ->
        if (controllers.isEmpty()) {
          _filters.value = emptyList()
          return@collectLatest
        }
        combine(controllers.map { controller -> controller.state.map { state -> controller.provider.source to state } }) { sourceStatePairs
            ->
            val sourceConnectionPairs = sourceStatePairs.flatMap { (source, state) -> state.connections.items.map { source to it } }
            val filters =
              sourceConnectionPairs
                .groupBy { (_, connection) -> connection.appId }
                .map { (appId, sourceConnectionPairsForApp) ->
                  val representativeConnection = sourceConnectionPairsForApp.first().second

                  val preferencesBySource =
                    sourceConnectionPairsForApp
                      .groupBy { (source, _) -> source }
                      .mapValues { (_, sourceAndConnections) -> sourceAndConnections.maxOf { (_, connection) -> connection.preference() } }

                  ConnectionFilter(appId, representativeConnection.displayName, preferencesBySource)
                }

            _filters.value = filters
            // Choose the most preferred application when no one is selected.
            if (selectedAppId.value == null && filters.isNotEmpty()) {
              selectedAppId.compareAndSet(null, filters.maxBy { it.preferences.values.sumOf { preference -> preference.value } }.appId)
            }
          }
          .collect()
      }
    }
  }
}

fun Connection.preference() =
  when {
    isPreferredConnection() -> ConnectionPreference.PREFERRED
    isMatchingProject() -> ConnectionPreference.MATCHING
    isConfigured -> ConnectionPreference.CONFIGURED
    else -> ConnectionPreference.UNAVAILABLE
  }

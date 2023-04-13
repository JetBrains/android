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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`

private val CONNECTION1 = Connection("app1", "app-id1", "project1", "123")
private val CONNECTION2 = Connection("app2", "app-id2", "project2", "456")

class VitalsConfigurationManagerTest {

  private val projectRule = AndroidProjectRule.inMemory()

  private val executorsRule = AndroidExecutorsRule()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(executorsRule)

  @Test
  fun getConnections() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(CONNECTION1)))
      val configManager = VitalsConfigurationManager(projectRule.project, { client }, emptyFlow())
      Disposer.register(projectRule.testRootDisposable, configManager)

      val model = configManager.configuration.first { it is AppInsightsModel.Authenticated }
      assertThat(model).isInstanceOf(AppInsightsModel.Authenticated::class.java)
      val controller = (model as AppInsightsModel.Authenticated).controller

      assertThat(
          controller.state.map { state -> state.connections.items.map { it.connection } }.first()
        )
        .isEqualTo(listOf(CONNECTION1))
    }

  @Test
  fun failureToGetConnections_returnUnauthenticated() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.UnknownFailure("error"))
      val configManager = VitalsConfigurationManager(projectRule.project, { client }, emptyFlow())
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
    }

  @Test
  fun refreshConnections_returnsNewConnections() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(CONNECTION1)))

      val configManager = VitalsConfigurationManager(projectRule.project, { client }, emptyFlow())
      Disposer.register(projectRule.testRootDisposable, configManager)

      val model = configManager.configuration.first { it is AppInsightsModel.Authenticated }
      val controller = (model as AppInsightsModel.Authenticated).controller

      controller.state
        .map { state -> state.connections.items.map { it.connection } }
        .take(2)
        .collectIndexed { index, value ->
          when (index) {
            0 -> {
              assertThat(value).containsExactly(CONNECTION1)
              `when`(client.listConnections())
                .thenReturn(LoadingState.Ready(listOf(CONNECTION1, CONNECTION2)))
              configManager.refreshConfiguration()
            }
            1 -> {
              assertThat(value).containsExactly(CONNECTION1, CONNECTION2)
            }
          }
        }
    }
}

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
import com.android.tools.idea.insights.CONNECTION2
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.gct.login.LoginStatus
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`

private val APP_CONNECTION1 = AppConnection("app1", "Test App 1")
private val APP_CONNECTION2 = AppConnection("app2", "Test App 2")

class VitalsConfigurationManagerTest {

  private val projectRule = AndroidProjectRule.inMemory()

  private val executorsRule = AndroidExecutorsRule()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(executorsRule)

  @Test
  fun getConnections() =
    runBlocking<Unit> {
      val cache = AppInsightsCacheImpl()
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1)))
      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          cache,
          MutableStateFlow(LoginStatus.LoggedIn("test@goog.com")),
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      val model = configManager.configuration.first { it is AppInsightsModel.Authenticated }
      assertThat(model).isInstanceOf(AppInsightsModel.Authenticated::class.java)
      val controller = (model as AppInsightsModel.Authenticated).controller

      assertThat(
          controller.state
            .map { state -> state.connections.items.map { it.appId } }
            .first { it.isNotEmpty() }
        )
        .isEqualTo(listOf(APP_CONNECTION1.appId))
    }

  @Test
  fun failureToGetConnections_returnUnauthenticated() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.UnknownFailure("error"))
      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          MutableStateFlow(LoginStatus.LoggedIn("goo@goo.com")),
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
    }

  @Test
  fun `should return unauthenticated configuration when user is not logged in`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.UnknownFailure("error"))
      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          MutableStateFlow(LoginStatus.LoggedOut),
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
    }

  @Test
  fun refreshConnections_returnsNewConnections() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1)))

      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          MutableStateFlow(LoginStatus.LoggedIn("goo@goo.com")),
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      val model = configManager.configuration.first { it is AppInsightsModel.Authenticated }
      val controller = (model as AppInsightsModel.Authenticated).controller

      controller.state
        .map { state -> state.connections.items.map { it.appId } }
        .filterNot { it.isEmpty() }
        .take(2)
        .collectIndexed { index, value ->
          when (index) {
            0 -> {
              assertThat(value).containsExactly(APP_CONNECTION1.appId)
              `when`(client.listConnections())
                .thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1, APP_CONNECTION2)))
              configManager.refreshConfiguration()
            }
            1 -> {
              assertThat(value).containsExactly(APP_CONNECTION1.appId, APP_CONNECTION2.appId)
            }
          }
        }
    }

  @Test
  fun `user log in then out then in, the proper config should be emitted`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1)))
      val loggedInFlow = MutableStateFlow<LoginStatus>(LoginStatus.LoggedIn("goo@goog.com"))

      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          loggedInFlow,
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      configManager.configuration.first { it is AppInsightsModel.Authenticated }
      loggedInFlow.value = LoginStatus.LoggedOut
      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
      loggedInFlow.value = LoginStatus.LoggedIn("goo@goog.com")
      configManager.configuration.first { it is AppInsightsModel.Authenticated }
    }

  @Test
  fun `network failure in getting connections should cause manager to use cached connections`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.NetworkFailure("error"))

      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          MutableStateFlow(LoginStatus.LoggedIn("goo@goo.com")),
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.cache.populateConnections(listOf(CONNECTION2))

      configManager.refreshConfiguration()
      val model = configManager.configuration.first { it is AppInsightsModel.Authenticated }
      val controller = (model as AppInsightsModel.Authenticated).controller

      controller.state.first {
        it.connections.items == listOf(CONNECTION2) && it.mode == ConnectionMode.OFFLINE
      }
    }

  @Test
  fun `controller refresh causes manager to refresh app connections, and offline status is updated`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.NetworkFailure("error"))

      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          MutableStateFlow(LoginStatus.LoggedIn("goo@goo.com")),
          projectRule.testRootDisposable,
          client
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.cache.populateConnections(listOf(CONNECTION2))

      // Connection fails and causes offline mode.
      configManager.refreshConfiguration()
      val model = configManager.configuration.first { it is AppInsightsModel.Authenticated }
      val controller = (model as AppInsightsModel.Authenticated).controller

      controller.state.first { it.mode == ConnectionMode.OFFLINE }

      // Simulate refresh is successfully performed.
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1)))
      controller.refresh()
      controller.state.first {
        it.mode == ConnectionMode.ONLINE &&
          it.connections.items.single().appId == APP_CONNECTION1.appId
      }
    }
}

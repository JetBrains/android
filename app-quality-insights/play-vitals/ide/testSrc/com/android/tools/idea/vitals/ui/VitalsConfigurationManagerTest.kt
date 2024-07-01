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
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.vitals.VitalsLoginFeature
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.LoginUsersRule
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`

private val APP_CONNECTION1 = AppConnection("app1", "Test App 1")

class VitalsConfigurationManagerTest {

  private val projectRule = AndroidProjectRule.inMemory()

  private val executorsRule = AndroidExecutorsRule()

  private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(executorsRule).around(loginUsersRule)!!

  @Test
  fun `should return unauthenticated configuration when user is not logged in`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.UnknownFailure("error"))
      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          MutableStateFlow(true),
          projectRule.testRootDisposable,
          client,
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
    }

  @Test
  fun `user log in then out then in, the proper config should be emitted`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1)))
      loginUsersRule.setActiveUser(
        "foo@goo.com",
        features = listOf(LoginFeature.feature<VitalsLoginFeature>()),
      )

      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          parentDisposable = projectRule.testRootDisposable,
          testClient = client,
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      configManager.configuration.first { it is AppInsightsModel.Authenticated }
      loginUsersRule.logOut("foo@goo.com")
      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
      loginUsersRule.setActiveUser(
        "foo@goo.com",
        features = listOf(LoginFeature.feature<VitalsLoginFeature>()),
      )
      configManager.configuration.first { it is AppInsightsModel.Authenticated }
    }

  @Test
  fun `user logs in and then obtains login feature`() =
    runBlocking<Unit> {
      val client = mock<AppInsightsClient>()
      `when`(client.listConnections()).thenReturn(LoadingState.Ready(listOf(APP_CONNECTION1)))
      loginUsersRule.setActiveUser("foo@goo.com", features = emptyList())

      val configManager =
        VitalsConfigurationManager(
          projectRule.project,
          AppInsightsCacheImpl(),
          parentDisposable = projectRule.testRootDisposable,
          testClient = client,
        )
      Disposer.register(projectRule.testRootDisposable, configManager)

      configManager.refreshConfiguration()
      configManager.configuration.first { it is AppInsightsModel.Unauthenticated }
      loginUsersRule.setActiveUser(
        "foo@goo.com",
        features = listOf(LoginFeature.feature<VitalsLoginFeature>()),
      )
      configManager.configuration.first { it is AppInsightsModel.Authenticated }
    }
}

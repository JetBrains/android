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

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsCrashState
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.FakeInsightsProvider
import com.android.tools.idea.insights.InsightsProvider.Source
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.StubAppInsightsCrashController
import com.android.tools.idea.insights.TestConnection
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.util.PlatformIcons
import javax.swing.Icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

private val connections1 =
  listOf(
    TestConnection(appId = "app1", displayName = "App1", variantName = "variant1", isMatching = false, isPreferred = false),
    TestConnection(appId = "app1", displayName = "App1", variantName = "variant2", isMatching = true, isPreferred = false),
    TestConnection(appId = "app2", displayName = "App2", variantName = "variant1", isMatching = true, isPreferred = true),
    TestConnection(appId = "app3", displayName = "App3", isMatching = false, isPreferred = false),
    TestConnection(appId = "app4", displayName = "App4", isMatching = true, isPreferred = false),
  )

private val connections2 =
  listOf(
    TestConnection(appId = "app1", displayName = "App1", variantName = "variant1", isMatching = false, isPreferred = false),
    TestConnection(appId = "app2", displayName = "App2", variantName = "variant1", isMatching = true, isPreferred = true),
    TestConnection(appId = "app4", displayName = "App4", isMatching = false, isPreferred = false),
    TestConnection(appId = "app5", displayName = "App5", isMatching = false, isPreferred = false),
  )

open class TabProvider(
  private val connections: List<Connection>,
  override val insightsProvider: FakeInsightsProvider,
  override val icon: Icon,
) : TestTabProvider(insightsProvider.displayName) {

  override fun getConfigurationManager(project: Project): AppInsightsConfigurationManager {
    return object : AppInsightsConfigurationManager {
      override val project: Project = project

      override val configuration: StateFlow<AppInsightsModel> =
        MutableStateFlow<AppInsightsModel>(
          AppInsightsModel.Authenticated(
            StubAppInsightsCrashController(
              provider = insightsProvider,
              state = MutableStateFlow(AppInsightsCrashState(Selection(null, connections), mock(), LoadingState.Loading)),
              connections = MutableStateFlow(Selection(null, connections)),
            )
          )
        )

      override val offlineStatusManager = OfflineStatusManagerImpl()
    }
  }
}

class AppInsightsFilterSelectorTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `startCollection collects filters from providers`() = runBlocking {
    val scope = projectRule.disposable.createCoroutineScope(Dispatchers.IO)
    val firebaseProvider = TabProvider(connections1, FakeInsightsProvider("firebase", true, Source.FIREBASE), PlatformIcons.ADD_ICON)
    val playProvider = TabProvider(connections2, FakeInsightsProvider("play", true, Source.PLAY), PlatformIcons.DELETE_ICON)
    ExtensionTestUtil.maskExtensions(AppInsightsTabProvider.EP_NAME, listOf(firebaseProvider, playProvider), projectRule.disposable)

    val selector = AppInsightsFilterSelector(projectRule.project, scope)
    selector.startCollection()

    val filters = selector.filters.first { it.size == 5 }.sortedBy { it.appId }
    filters[0].let { filter ->
      assertThat(filter.appId).isEqualTo("app1")
      assertThat(filter.title).isEqualTo("App1")
      assertThat(filter.preferences[Source.FIREBASE]).isEqualTo(ConnectionPreference.MATCHING)
      assertThat(filter.preferences[Source.PLAY]).isEqualTo(ConnectionPreference.CONFIGURED)
    }
    filters[1].let { filter ->
      assertThat(filter.appId).isEqualTo("app2")
      assertThat(filter.title).isEqualTo("App2")
      assertThat(filter.preferences[Source.FIREBASE]).isEqualTo(ConnectionPreference.PREFERRED)
      assertThat(filter.preferences[Source.PLAY]).isEqualTo(ConnectionPreference.PREFERRED)
    }
    filters[2].let { filter ->
      assertThat(filter.appId).isEqualTo("app3")
      assertThat(filter.title).isEqualTo("App3")
      assertThat(filter.preferences[Source.FIREBASE]).isEqualTo(ConnectionPreference.CONFIGURED)
      assertThat(filter.preferences[Source.PLAY]).isNull()
    }
    filters[3].let { filter ->
      assertThat(filter.appId).isEqualTo("app4")
      assertThat(filter.title).isEqualTo("App4")
      assertThat(filter.preferences[Source.FIREBASE]).isEqualTo(ConnectionPreference.MATCHING)
      assertThat(filter.preferences[Source.PLAY]).isEqualTo(ConnectionPreference.CONFIGURED)
    }
    filters[4].let { filter ->
      assertThat(filter.appId).isEqualTo("app5")
      assertThat(filter.title).isEqualTo("App5")
      assertThat(filter.preferences[Source.FIREBASE]).isNull()
      assertThat(filter.preferences[Source.PLAY]).isEqualTo(ConnectionPreference.CONFIGURED)
    }
    Unit
  }
}

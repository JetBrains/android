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

import com.android.testutils.MockitoKt
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.FakeAppInsightsProjectLevelController
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import java.awt.Component
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class VitalsTabProviderTest {

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `model change triggers different UI presentation`() = runTest {
    // Setup
    val manager =
      object : AppInsightsConfigurationManager {
        override val project = projectRule.project
        override val configuration =
          MutableStateFlow<AppInsightsModel>(AppInsightsModel.Uninitialized)
        override val offlineStatusManager = OfflineStatusManagerImpl()

        override fun refreshConfiguration() {}
      }

    val mockService = MockitoKt.mock<VitalsConfigurationService>()
    Mockito.`when`(mockService.manager).thenReturn(manager)
    projectRule.project.replaceService(
      VitalsConfigurationService::class.java,
      mockService,
      projectRule.disposable,
    )

    val tabProvider = VitalsTabProvider()
    val tabPanel = AppInsightsTabPanel()
    Disposer.register(projectRule.disposable, tabPanel)

    val callbackFlow =
      callbackFlow<Component> {
        tabPanel.addContainerListener(
          object : ContainerAdapter() {
            override fun componentAdded(e: ContainerEvent) {
              trySendBlocking(e.child)
            }
          }
        )
        tabProvider.populateTab(projectRule.project, tabPanel)
        awaitClose()
      }

    callbackFlow.take(3).collectIndexed { index, component ->
      when (index) {
        0 -> {
          assertThat(component.toString()).contains("placeholderContent")
          manager.configuration.value =
            AppInsightsModel.Authenticated(FakeAppInsightsProjectLevelController())
        }
        1 -> {
          assertThat(component).isInstanceOf(VitalsTab::class.java)
          manager.configuration.value = AppInsightsModel.Unauthenticated
        }
        2 -> assertThat(component.toString()).contains("loggedOutErrorStateComponent")
      }
    }
  }
}

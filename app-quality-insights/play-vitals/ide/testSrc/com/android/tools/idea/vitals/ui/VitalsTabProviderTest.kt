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

import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.StubAppInsightsProjectLevelController
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.ServiceDeprecatedPanel
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import java.awt.Component
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class VitalsTabProviderTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var modelStateFlow: MutableStateFlow<AppInsightsModel>
  private lateinit var manager: AppInsightsConfigurationManager
  private lateinit var service: VitalsConfigurationService
  private lateinit var tabPanel: AppInsightsTabPanel
  private lateinit var tabProvider: VitalsTabProvider
  private lateinit var deprecation: DevServicesDeprecationData

  @Before
  fun setUp() {
    modelStateFlow = MutableStateFlow(AppInsightsModel.Uninitialized)
    deprecation =
      DevServicesDeprecationData("", "", "", false, DevServicesDeprecationStatus.SUPPORTED)
    manager =
      object : AppInsightsConfigurationManager {
        override val project = projectRule.project
        override val configuration = modelStateFlow
        override val offlineStatusManager = OfflineStatusManagerImpl()
        override val deprecationData: DevServicesDeprecationData
          get() = deprecation

        override fun refreshConfiguration() {}
      }

    service = mock()
    Mockito.`when`(service.manager).thenReturn(manager)
    projectRule.project.replaceService(
      VitalsConfigurationService::class.java,
      service,
      projectRule.disposable,
    )

    tabProvider = VitalsTabProvider()
    tabPanel = AppInsightsTabPanel()
    Disposer.register(projectRule.disposable, tabPanel)
  }

  private fun populateTabAndGetComponentsFlow(): Flow<Component> = callbackFlow {
    tabPanel.addContainerListener(
      object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) {
          trySendBlocking(e.child)
        }
      }
    )
    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    awaitClose()
  }

  @Test
  fun `model change triggers different UI presentation`() = runTest {
    val callbackFlow = populateTabAndGetComponentsFlow()

    callbackFlow.take(3).collectIndexed { index, component ->
      when (index) {
        0 -> {
          assertThat(component.toString()).contains("placeholderContent")
          val stubController =
            object : StubAppInsightsProjectLevelController() {
              override val project = projectRule.project
            }
          modelStateFlow.value = AppInsightsModel.Authenticated(stubController)
        }
        1 -> {
          assertThat(component).isInstanceOf(VitalsTab::class.java)
          modelStateFlow.value = AppInsightsModel.Unauthenticated
        }
        2 -> {
          assertThat(component.toString()).contains("loggedOutErrorStateComponent")
          modelStateFlow.value = AppInsightsModel.InitializationFailed
        }
        3 -> {
          assertThat(component.toString()).contains("initializationFailedComponent")
        }
      }
    }
  }

  @Test
  fun `controller is refreshed after reauthentication`() = runTest {
    val controller =
      object : StubAppInsightsProjectLevelController() {
        var refreshCount = 0
        override val project = projectRule.project

        override fun refresh() {
          refreshCount++
        }
      }

    val flow = populateTabAndGetComponentsFlow()
    val collection = launch {
      flow.collectIndexed { index, _ ->
        when (index) {
          0 -> modelStateFlow.value = AppInsightsModel.Authenticated(controller)
          1 -> modelStateFlow.value = AppInsightsModel.Unauthenticated
          2 -> modelStateFlow.value = AppInsightsModel.Authenticated(controller)
          3 -> modelStateFlow.value = AppInsightsModel.InitializationFailed
          4 -> modelStateFlow.value = AppInsightsModel.Authenticated(controller)
          else -> cancel()
        }
      }
    }

    collection.join()
    assertThat(controller.refreshCount).isEqualTo(2)
  }

  @Test
  fun `show service deprecated panel when service is deprecated`() = runTest {
    deprecation =
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.UNSUPPORTED,
      )
    val flow = populateTabAndGetComponentsFlow()
    flow.take(1).collect { component ->
      assertThat(component).isInstanceOf(ServiceDeprecatedPanel::class.java)
    }
  }
}

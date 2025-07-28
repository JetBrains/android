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

import com.android.flags.junit.FlagRule
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DeprecationBanner
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.StubAppInsightsProjectLevelController
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.ServiceUnsupportedPanel
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

class VitalsTabProviderTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val flagRule = FlagRule(StudioFlags.USE_1P_LOGIN_UI, false)

  private lateinit var modelStateFlow: MutableStateFlow<AppInsightsModel>
  private lateinit var manager: AppInsightsConfigurationManager
  private lateinit var service: VitalsConfigurationService
  private lateinit var tabPanel: AppInsightsTabPanel
  private lateinit var tabProvider: VitalsTabProvider
  private lateinit var deprecationDataFlow: MutableStateFlow<DevServicesDeprecationData>

  @Before
  fun setUp() {
    deprecationDataFlow = MutableStateFlow(DevServicesDeprecationData.EMPTY)
    val mockDeprecationDataProvider =
      mock<DevServicesDeprecationDataProvider>().apply {
        doReturn(deprecationDataFlow.value).whenever(this).getCurrentDeprecationData(any(), any())
        runBlocking {
          doReturn(deprecationDataFlow)
            .whenever(this@apply)
            .registerServiceForChange(any(), any(), any<Disposable>())
        }
      }
    application.replaceService(
      DevServicesDeprecationDataProvider::class.java,
      mockDeprecationDataProvider,
      projectRule.disposable,
    )

    modelStateFlow = MutableStateFlow(AppInsightsModel.Uninitialized)
    manager =
      object : AppInsightsConfigurationManager {
        override val project = projectRule.project
        override val configuration = modelStateFlow
        override val offlineStatusManager = OfflineStatusManagerImpl()

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

  @Test
  fun `model change triggers different UI presentation`() = runTest {
    // Setup
    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })

    delayUntilCondition(200) {
      tabPanel.components.firstOrNull().toString().contains("placeholderContent")
    }
    val stubController =
      object : StubAppInsightsProjectLevelController() {
        override val project = projectRule.project
      }

    modelStateFlow.value = AppInsightsModel.Authenticated(stubController)
    delayUntilCondition(200) {
      val single = tabPanel.components.firstOrNull()
      single is VitalsTab
    }

    modelStateFlow.value = AppInsightsModel.Unauthenticated
    val expect =
      if (StudioFlags.USE_1P_LOGIN_UI.get()) {
        "loggedOut1pPanel"
      } else {
        "loggedOutErrorStateComponent"
      }
    delayUntilCondition(200) { tabPanel.components.firstOrNull().toString().contains(expect) }

    modelStateFlow.value = AppInsightsModel.InitializationFailed
    delayUntilCondition(200) {
      tabPanel.components.firstOrNull().toString().contains("initializationFailedComponent")
    }
    println("InitializationFailed delay complete")
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

    modelStateFlow.value = AppInsightsModel.Authenticated(controller)
    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    modelStateFlow.value = AppInsightsModel.Unauthenticated
    delayUntilCondition(200) { tabPanel.components.firstOrNull().toString().contains("loggedOut") }
    assertThat(controller.refreshCount).isEqualTo(0)

    modelStateFlow.value = AppInsightsModel.Authenticated(controller)
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { controller.refreshCount == 1 }

    modelStateFlow.value = AppInsightsModel.InitializationFailed
    delayUntilCondition(200) {
      tabPanel.components.firstOrNull().toString().contains("initializationFailedComponent")
    }
    assertThat(controller.refreshCount).isEqualTo(1)

    modelStateFlow.value = AppInsightsModel.Authenticated(controller)
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { controller.refreshCount == 2 }
  }

  @Test
  fun `show service unsupported panel when service is unsupported`() = runTest {
    deprecationDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.UNSUPPORTED,
      )
    }
    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { tabPanel.components.any { it is ServiceUnsupportedPanel } }
  }

  @Test
  fun `show deprecated banner when service is deprecated`() = runTest {
    deprecationDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.DEPRECATED,
      )
    }
    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { tabPanel.components.any { it is DeprecationBanner } }

    val banner = tabPanel.components.first { it is DeprecationBanner }
    assertThat(banner.background).isEqualTo(JBUI.CurrentTheme.Banner.WARNING_BACKGROUND)

    val placeHolder = tabPanel.components.firstOrNull { it is JPanel }
    assertThat(placeHolder.toString()).contains("placeholderContent")
  }

  @Test
  fun `service restored when deprecation data changes from UNSUPPORTED to SUPPORTED`() = runTest {
    modelStateFlow.value = AppInsightsModel.Authenticated(StubAppInsightsProjectLevelController())
    deprecationDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.UNSUPPORTED,
      )
    }

    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { tabPanel.components.any { it is ServiceUnsupportedPanel } }

    deprecationDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.SUPPORTED,
      )
    }
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { tabPanel.components.none { it is ServiceUnsupportedPanel } }
    delayUntilCondition(200) { tabPanel.components.any { it is VitalsTab } }
  }

  @Test
  fun `service restored when deprecation data changes from UNSUPPORTED to DEPRECATED`() = runTest {
    modelStateFlow.value = AppInsightsModel.Authenticated(StubAppInsightsProjectLevelController())
    deprecationDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.UNSUPPORTED,
      )
    }

    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { tabPanel.components.any { it is ServiceUnsupportedPanel } }

    deprecationDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "desc",
        "url",
        true,
        DevServicesDeprecationStatus.DEPRECATED,
      )
    }
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    delayUntilCondition(200) { tabPanel.components.none { it is ServiceUnsupportedPanel } }
    delayUntilCondition(200) { tabPanel.components.any { it is DeprecationBanner } }
    delayUntilCondition(200) { tabPanel.components.any { it is VitalsTab } }
  }

  @Test
  fun `deprecation banner hidden when deprecation data changes from DEPRECATED TO SUPPORTED`() =
    runTest {
      modelStateFlow.value = AppInsightsModel.Authenticated(StubAppInsightsProjectLevelController())
      deprecationDataFlow.update {
        DevServicesDeprecationData(
          "header",
          "desc",
          "url",
          true,
          DevServicesDeprecationStatus.DEPRECATED,
        )
      }
      tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
      withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
      delayUntilCondition(200) { tabPanel.components.any { it is DeprecationBanner } }
      delayUntilCondition(200) { tabPanel.components.any { it is VitalsTab } }

      deprecationDataFlow.update {
        DevServicesDeprecationData(
          "header",
          "desc",
          "url",
          true,
          DevServicesDeprecationStatus.SUPPORTED,
        )
      }

      delayUntilCondition(200) { tabPanel.components.none { it is DeprecationBanner } }
    }

  @Test
  fun `test 1p login screen`() = runTest {
    StudioFlags.USE_1P_LOGIN_UI.override(true)
    tabProvider.populateTab(projectRule.project, tabPanel, flow { true })
    modelStateFlow.value = AppInsightsModel.Unauthenticated

    delayUntilCondition(200) {
      tabPanel.components.firstOrNull().toString().contains("loggedOut1pPanel")
    }
  }
}

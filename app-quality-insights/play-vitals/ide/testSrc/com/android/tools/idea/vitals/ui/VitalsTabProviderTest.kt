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
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.waitForCondition
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth
import com.google.gct.login.GoogleLogin
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.studiogrpc.testutils.ForwardingInterceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito

class VitalsTabProviderTest {

  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule)!!

  @Test
  fun `model change triggers different UI presentation`() = runBlocking {
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
      projectRule.disposable
    )
    val googleLoginMock = MockitoKt.mock<GoogleLogin>()
    Mockito.`when`(googleLoginMock.getActiveUserAuthInterceptor()).thenReturn(ForwardingInterceptor)
    ApplicationManager.getApplication()
      .registerServiceInstance(GoogleLogin::class.java, googleLoginMock)

    val tabProvider = VitalsTabProvider()
    val tabPanel = AppInsightsTabPanel()
    Disposer.register(projectRule.disposable, tabPanel)
    tabProvider.populateTab(projectRule.project, tabPanel)

    // Check uninitialized state
    Truth.assertThat(tabPanel.components.single().toString()).contains("placeholderContent")

    // Check authenticated state
    manager.configuration.value = AppInsightsModel.Authenticated(controllerRule.controller)
    waitForCondition(5000) {
      val single = tabPanel.components.firstOrNull()
      single is VitalsTab
    }

    // Check unauthenticated state
    manager.configuration.value = AppInsightsModel.Unauthenticated
    waitForCondition(5000) {
      tabPanel.components.firstOrNull().toString().contains("loggedOutErrorStateComponent")
    }
  }
}

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
package com.android.tools.idea.insights.inspection

import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.FakeAppInsightsProjectLevelController
import com.android.tools.idea.insights.OfflineStatusManagerImpl
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformIcons
import javax.swing.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class TestTabProvider(override val displayName: String) : AppInsightsTabProvider {
  override val icon: Icon = PlatformIcons.ADD_ICON

  private val fakeInsights = mutableListOf<AppInsight>()

  private val modelFlow =
    MutableStateFlow<AppInsightsModel>(
      AppInsightsModel.Authenticated(
        FakeAppInsightsProjectLevelController(retrieveInsights = { _ -> fakeInsights })
      )
    )

  private val configManager: AppInsightsConfigurationManager =
    object : AppInsightsConfigurationManager {
      override val project: Project
        get() = throw RuntimeException()

      override val configuration: StateFlow<AppInsightsModel>
        get() = modelFlow

      override val offlineStatusManager = OfflineStatusManagerImpl()
    }

  override fun populateTab(project: Project, tabPanel: AppInsightsTabPanel) = Unit

  override fun getConfigurationManager(project: Project) = configManager

  fun returnInsights(insights: List<AppInsight>) {
    fakeInsights.clear()
    fakeInsights.addAll(insights)
  }
}

class TestTabProvider1 : TestTabProvider("Test 1")

class TestTabProvider2 : TestTabProvider("Test 2")

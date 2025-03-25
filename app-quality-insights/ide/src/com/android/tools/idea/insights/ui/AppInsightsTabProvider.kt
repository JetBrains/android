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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.Icon
import kotlinx.coroutines.flow.Flow

interface AppInsightsTabProvider {
  val displayName: String
  val icon: Icon

  /** Populates the provided [tabPanel] with content. */
  fun populateTab(project: Project, tabPanel: AppInsightsTabPanel, activeTabFlow: Flow<Boolean>)

  fun isApplicable(): Boolean = true

  /** Returns the active configuration manager for this insights tab for [project]. */
  fun getConfigurationManager(project: Project): AppInsightsConfigurationManager

  companion object {
    @JvmField
    val EP_NAME =
      ExtensionPointName<AppInsightsTabProvider>(
        "com.android.tools.idea.insights.ui.appInsightsTabProvider"
      )
  }
}

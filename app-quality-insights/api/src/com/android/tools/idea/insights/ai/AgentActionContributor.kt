/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.ai

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Event
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface AgentActionContributor {
  fun provideActions(
    event: Event,
    issue: AppInsightsIssue,
    project: Project,
  ): List<Pair<String, () -> Unit>>

  companion object {
    val EP_NAME =
      ExtensionPointName<AgentActionContributor>(
        "com.android.tools.idea.insights.ai.agentActionContributor"
      )
  }
}

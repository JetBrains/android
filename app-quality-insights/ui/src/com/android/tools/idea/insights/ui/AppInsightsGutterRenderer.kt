/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.services.firebase.insights.ui

import com.google.services.firebase.insights.CrashlyticsInsight
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import icons.StudioIcons
import javax.swing.Icon

data class AppInsightsGutterRenderer(val project: Project, val insights: List<CrashlyticsInsight>) :
  GutterIconRenderer() {

  override fun getIcon(): Icon = StudioIcons.AppQualityInsights.ISSUE

  override fun getTooltipText(): String {
    val eventsCount = insights.sumOf { it.issue.issueDetails.eventsCount }
    val issuesCount = insights.size

    val eventsString =
      if (eventsCount == 1L) {
        "event"
      } else "events"
    val issuesString =
      if (issuesCount == 1) {
        "issue"
      } else "issues"

    return "App Quality Insights found $eventsCount $eventsString caused by $issuesCount $issuesString"
  }

  override fun isNavigateAction() = true

  override fun getClickAction() = AppInsightsGutterIconAction(project, insights)
}

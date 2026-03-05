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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import javax.swing.JPanel

class AutoGenerateInsightPanel(private val controller: AppInsightsProjectLevelController) : JPanel(VerticalLayout(16)) {
  init {
    val label =
      JBLabel().apply {
        text = "<html><i>Insight auto generation is disabled by default to avoid unintentional charges.</i></html>"
        foreground = NamedColorUtil.getInactiveTextColor()
      }
    add(label)

    val generateInsight =
      createLink("Generate insight") { controller.refreshInsight(regenerateWithContext = false, forceGenerateNewInsight = true) }
    val enableAutoGenerate =
      createLink("Enable auto-generation") {
        controller.aiInsightToolkit.setAutoGenerate(true)
        controller.refreshInsight(false)
      }

    val linksPanel = JPanel(HorizontalLayout(JBUI.scale(16)))
    linksPanel.add(generateInsight)
    linksPanel.add(enableAutoGenerate)

    add(linksPanel)
  }

  private fun createLink(text: String, onClick: () -> Unit) = HyperlinkLabel(text).apply { addHyperlinkListener { onClick() } }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.idea.common.error.DesignerCommonIssuePanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.util.text.StringUtil
import org.fest.swing.util.TextMatcher

/**
 * Fixture for the ProblemsPane in the IDE
 */

class ProblemsPaneFixture(ideFrameFixture: IdeFrameFixture) :
  ToolWindowFixture(ProblemsView.ID, ideFrameFixture.project, ideFrameFixture.robot()) {

  init {
    activate()
    waitUntilIsVisible()
  }

  fun isTabExist(tabTitle: String): Boolean {
    val nameMatcher = TabNameMatcher(tabTitle)
    return contents.any { nameMatcher.isMatching(it.displayName) }
  }

  fun isTabSelected(tabTitle: String) = getContent(TabNameMatcher(tabTitle))?.isSelected ?: false

  fun sharedPanelIssueCount(): Int {
    val tab = getContent(TabNameMatcher("Layout and Qualifiers")) ?: return 0
    val panel = tab.component as? DesignerCommonIssuePanel ?: return 0
    return panel.issueProvider.getFilteredIssues().size
  }

  public override fun waitUntilIsVisible(): ToolWindowFixture {
    return super.waitUntilIsVisible()
  }

  fun getAvailableTabsCount(): Int {
    return super.getContents().size
  }
}

/**
 * The tab name of problems pane can be a html text, and may have an issue count at the tail.
 * So we check the prefix of content string.
 */
private class TabNameMatcher(private val tabName: String) : TextMatcher {
  override fun isMatching(text: String?): Boolean {
    val textWithoutHtml = text?.let { StringUtil.removeHtmlTags(it) } ?: return false
    return textWithoutHtml.trim().startsWith(tabName)
  }

  override fun description() = "Cannot find the tab $tabName"

  override fun formattedValues() = "\"$tabName\"";
}

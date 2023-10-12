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
package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppVcsInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.vcs.log.impl.HashImpl
import icons.StudioIcons
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

const val NO_DATA = "No data"

class VcsCommitLabel : JPanel() {
  private val commitLabel = JLabel(NO_DATA, AllIcons.Vcs.CommitNode, SwingConstants.LEFT)
  private val infoIconForCommitLabel = JLabel(StudioIcons.Common.INFO, SwingConstants.LEFT)

  init {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.X_AXIS)

    infoIconForCommitLabel.isVisible = false

    add(commitLabel)
    add(Box.createHorizontalStrut(4))
    add(infoIconForCommitLabel)
  }

  fun updateOnIssueChange(issue: AppInsightsIssue) {
    when (val appVcsInfo = issue.sampleEvent.appVcsInfo) {
      is AppVcsInfo.ValidInfo -> {
        commitLabel.text = HashImpl.build(appVcsInfo.repoInfo.first().revision).toShortString()

        HelpTooltip.dispose(infoIconForCommitLabel)
        infoIconForCommitLabel.isVisible = false
      }
      is AppVcsInfo.Error -> {
        commitLabel.text = NO_DATA

        HelpTooltip().apply {
          setTitle("Version control information is missing")
          setDescription("<html><div width=\"250\">${appVcsInfo.message}</html>")
          installOn(infoIconForCommitLabel)
        }
        infoIconForCommitLabel.isVisible = true
      }
      is AppVcsInfo.NONE -> {
        commitLabel.text = NO_DATA

        HelpTooltip.dispose(infoIconForCommitLabel)
        infoIconForCommitLabel.isVisible = false
      }
    }
  }
}

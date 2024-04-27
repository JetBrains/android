package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.GenerateErrorReason
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.HelpTooltip
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.HyperlinkLabel
import javax.swing.JLabel
import org.junit.Rule
import org.junit.Test

class VcsCommitLabelTest {
  @get:Rule val projectRule = ProjectRule()
  private var commitLabel: HyperlinkLabel? = null
  private var noDataLabel: JLabel? = null
  private var infoIconForCommitLabel: JLabel? = null

  private fun VcsCommitLabel.refreshUnderlyingLabels() {
    val underlyingLabels = flatten().filterIsInstance<JLabel>()
    val underlyingHyperlinks = flatten().filterIsInstance<HyperlinkLabel>()

    if (underlyingLabels.isEmpty()) {
      noDataLabel = null
      infoIconForCommitLabel = null
    } else {
      assertThat(underlyingLabels.size).isEqualTo(2)

      noDataLabel = underlyingLabels[0]
      infoIconForCommitLabel = underlyingLabels[1]
    }

    commitLabel = underlyingHyperlinks.firstOrNull()
  }

  private val tooltip
    get() = infoIconForCommitLabel?.let { HelpTooltip.getTooltipFor(it) }

  @Test
  fun `check ui on issue change`() {
    val project = projectRule.project
    val label = VcsCommitLabel()

    // Check valid vcs info case
    label.updateOnIssueChange(ISSUE1.sampleEvent.appVcsInfo, project)
    label.refreshUnderlyingLabels()

    assertThat(commitLabel?.text).isEqualTo("74081e5f")
    assertThat(noDataLabel).isNull()
    assertThat(infoIconForCommitLabel).isNull()
    assertThat(tooltip).isNull()

    // Check error/debug info case
    label.updateOnIssueChange(AppVcsInfo.Error(GenerateErrorReason.NO_VALID_GIT_FOUND), project)
    label.refreshUnderlyingLabels()

    assertThat(noDataLabel?.text).isEqualTo("No data")
    assertThat(infoIconForCommitLabel).isNotNull()
    assertThat(tooltip).isNotNull()

    // Check no vcs info case
    label.updateOnIssueChange(AppVcsInfo.NONE, project)
    label.refreshUnderlyingLabels()
    assertThat(noDataLabel?.text).isEqualTo("No data")
    assertThat(infoIconForCommitLabel).isNotNull()
    assertThat(tooltip).isNotNull()
  }
}

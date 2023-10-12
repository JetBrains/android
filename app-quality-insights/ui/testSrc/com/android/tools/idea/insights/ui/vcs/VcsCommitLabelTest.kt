package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.HelpTooltip
import javax.swing.JLabel
import org.junit.Test

class VcsCommitLabelTest {
  private lateinit var commitLabel: JLabel
  private lateinit var infoIconForCommitLabel: JLabel

  private fun VcsCommitLabel.refreshUnderlyingLabels() {
    val underlyingLabels = flatten(false).filterIsInstance<JLabel>()

    assertThat(underlyingLabels.size).isEqualTo(2)
    commitLabel = underlyingLabels[0]
    infoIconForCommitLabel = underlyingLabels[1]
  }

  private val tooltip
    get() = HelpTooltip.getTooltipFor(infoIconForCommitLabel)

  @Test
  fun `check ui on issue change`() {
    val label = VcsCommitLabel()

    // Check valid vcs info case
    label.updateOnIssueChange(ISSUE1)
    label.refreshUnderlyingLabels()

    assertThat(commitLabel.text).isEqualTo("74081e5f")
    assertThat(infoIconForCommitLabel.isVisible).isFalse()
    assertThat(tooltip).isNull()

    // Check error/debug info case
    label.updateOnIssueChange(
      ISSUE1.copy(sampleEvent = ISSUE1.sampleEvent.copy(appVcsInfo = AppVcsInfo.Error("Invalid"))),
    )
    label.refreshUnderlyingLabels()
    assertThat(commitLabel.text).isEqualTo(NO_DATA)
    assertThat(infoIconForCommitLabel.isVisible).isTrue()
    assertThat(tooltip).isNotNull()

    // Check no vcs info case
    label.updateOnIssueChange(ISSUE2)
    label.refreshUnderlyingLabels()
    assertThat(commitLabel.text).isEqualTo(NO_DATA)
    assertThat(infoIconForCommitLabel.isVisible).isFalse()
    assertThat(tooltip).isNull()
  }
}

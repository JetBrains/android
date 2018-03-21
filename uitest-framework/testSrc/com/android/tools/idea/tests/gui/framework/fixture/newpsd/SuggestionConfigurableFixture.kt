/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.gradle.structure.configurables.suggestions.AndroidModuleSuggestionsConfigurable
import com.android.tools.idea.tests.gui.framework.find
import com.android.tools.idea.tests.gui.framework.finder
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.matcher
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.ui.components.JBLabel
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JLabelFixture
import org.fest.swing.timing.Wait
import java.awt.Container
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.border.TitledBorder

open class SuggestionConfigurableFixture(
    val dialog: JDialog,
    ideFrameFixture: IdeFrameFixture,
    container: Container
) : BasePerspectiveConfigurableFixture(ideFrameFixture, container) {

  private fun suggestionGroups() =
      finder()
          .findAll(container, matcher<JPanel> { isMessageGroup(it) })
          .map { SuggestionGroupFixture(dialog, ideFrameFixture, it) }

  fun noSuggestionsLabel(): JLabelFixture =
      JLabelFixture(robot(), finder().findByType(container, JBLabel::class.java, false))

  fun loadingLabel(): JLabelFixture =
      JLabelFixture(robot(), finder().find<JBLabel>(container, requireShowing = false) { it.text == "Loading... " })

  fun waitAnalysesCompleted(timeout: Wait) {
    timeout
        .expecting("Analyses completed and no 'Loading...' message visible")
        .until { GuiQuery.get { !loadingLabel().target().isVisible }!! }
    waitForIdle()
  }

  fun groups(): List<String> = suggestionGroups().map { it.title() }.filter { it != null }.map { it!! }

  fun waitForGroups(vararg requiredGroupTitles: String) {
    Wait
        .seconds(10)
        .expecting("Exact groups: ${requiredGroupTitles}")
        .until {
          groups() == requiredGroupTitles.asList()
        }
    waitForIdle()
  }

  fun findGroup(messageGroupTitle: String): SuggestionGroupFixture =
      suggestionGroups().firstOrNull { it.title() == messageGroupTitle } ?: throw AssertionError()

  private fun isMessageGroup(panel: JPanel) =
      panel.isShowing && !(panel.border as? TitledBorder)?.title.isNullOrEmpty()
}

fun ProjectStructureDialogFixture.selectSuggestionsConfigurable(): SuggestionConfigurableFixture {
  selectConfigurable("Suggestions")
  return SuggestionConfigurableFixture(
      container,
      ideFrameFixture,
      findConfigurable(AndroidModuleSuggestionsConfigurable.SUGGESTIONS_VIEW))
}


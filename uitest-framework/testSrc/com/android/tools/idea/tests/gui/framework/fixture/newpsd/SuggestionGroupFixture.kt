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

import com.android.tools.idea.gradle.structure.configurables.suggestions.SuggestionViewerUi.SUGGESTION_VIEWER_NAME
import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.finder
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.matcher
import org.fest.swing.edt.GuiQuery
import org.fest.swing.timing.Wait
import org.junit.Assert.fail
import java.util.regex.Pattern
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.border.TitledBorder

open class SuggestionGroupFixture(val dialog: JDialog,
                                  override val ideFrameFixture: IdeFrameFixture,
                                  override val container: JPanel
) : IdeFrameContainerFixture {

  fun title(): String? = GuiQuery.get<String> { (container.border as? TitledBorder)?.title }

  fun findMessageMatching(pattern: String): SuggestionFixture? {
    val compiledPattern = Pattern.compile(pattern)
    val matchingMessageFixtures = findMatchingMessages(compiledPattern)
    if (matchingMessageFixtures.isEmpty()) {
      return null
    }
    if (matchingMessageFixtures.size > 1) {
      val joinedMatchingMessages = getHtmlMessages(matchingMessageFixtures).joinToString("'\n'", "'", "'")
      fail("Multiple messages\n$joinedMatchingMessages\nmatch\n'$pattern'")
    }
    return matchingMessageFixtures[0]
  }

  private fun suggestions() =
      finder()
          .findAll(container, matcher<JPanel> { it.name == SUGGESTION_VIEWER_NAME })
          .map { SuggestionFixture(ideFrameFixture, it) }

  private fun findMatchingMessages(compiledPattern: Pattern) =
      suggestions().filter { compiledPattern.matcher(it.plainTextMessage()).find() }

}


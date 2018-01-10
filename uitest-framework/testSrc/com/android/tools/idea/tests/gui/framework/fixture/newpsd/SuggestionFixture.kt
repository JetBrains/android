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

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.finder
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.google.common.truth.Truth.assertThat
import org.fest.swing.edt.GuiQuery
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.JButtonFixture
import org.junit.Assert.fail
import javax.swing.JButton
import javax.swing.JPanel

class SuggestionFixture(
    override val ideFrameFixture: IdeFrameFixture,
    override val container: JPanel
) : IdeFrameContainerFixture {

  private fun robot() = ideFrameFixture.robot()

  fun plainTextMessage(): String {
    val htmlLabel = finder().findByType(container, HtmlLabel::class.java)
    assertThat(htmlLabel).named("HtmlLabel").isNotNull()
    return GuiQuery.get { htmlLabel.plainText() }!!
  }

  fun isActionActionAvailable(): Boolean {
    val buttonFixture = findButton()
    return buttonFixture.isEnabled && GuiQuery.get { buttonFixture.target().isVisible }!!
  }

  fun requireActionUnavailable(): SuggestionFixture {
    try {
      findButton()
      fail("No action associated with '${plainTextMessage()}' is expected to be available.")
      return this  // Unreachable.
    }
    catch (ex: ComponentLookupException) {
      return this
    }
  }

  fun clickAction() {
    val buttonFixture = findButton()
    buttonFixture.click()
    waitForIdle()
  }

  private fun findButton(): JButtonFixture {
    val button = finder().findByType(container, JButton::class.java, true)
    return JButtonFixture(robot(), button)
  }
}

fun getHtmlMessages(suggestionFixtures: List<SuggestionFixture>): List<String> =
    suggestionFixtures.map { it.plainTextMessage() }

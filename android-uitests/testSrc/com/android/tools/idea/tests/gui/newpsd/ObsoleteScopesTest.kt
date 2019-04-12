/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.getHtmlMessages
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectSuggestionsConfigurable
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class ObsoleteScopesTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
    GradleExperimentalSettings.getInstance().USE_NEW_PSD = true
  }

  @After
  fun tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride()
    GradleExperimentalSettings.getInstance().USE_NEW_PSD = GradleExperimentalSettings().USE_NEW_PSD
  }

  @Test
  fun testQuickFixesOffered() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    val psd = ide.openPsd()
    val suggestionsConfigurable = psd.selectSuggestionsConfigurable()

    // TODO(xof) investigate weird only-analyses-the-second-time-almost-always problem
    // suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(100))

    // our obsolete scopes messages are warnings.  There may be other warnings, but none
    // of them should have the same messages as ours.  There may also be suggestions in
    // other groups.
    suggestionsConfigurable.waitForGroup("Warnings")
    val warningsGroup = suggestionsConfigurable.findGroup("Warnings")
    val warningSuggestions = warningsGroup.suggestions()

    val expectedMessages = listOf(
      Pair("\napp » com.google.guava:guava:23.0\nObsolete scope found: compile View usage",
           arrayOf("Update compile to implementation")),
      Pair("\napp » compile/libs\nObsolete scope found: compile View usage",
           arrayOf("Update compile to implementation")),
      Pair("\napp » junit:junit:4.11\nObsolete scope found: testCompile View usage",
           arrayOf("Update testCompile to testImplementation")),
      Pair("\napp » mylibrary\nObsolete scope found: compile View usage",
           arrayOf("Update compile to implementation")),
      Pair("\nmylibrary » com.android.support:appcompat-v7:26.0.1\nObsolete scope found: compile View usage",
           arrayOf("Update compile to api", "Update compile to implementation")),
      Pair("\nmylibrary » compile/libs\nObsolete scope found: compile View usage",
           arrayOf("Update compile to api", "Update compile to implementation")),
      Pair("\nmylibrary » junit:junit:4.11\nObsolete scope found: testCompile View usage",
           arrayOf("Update testCompile to testImplementation")))

    assertThat(getHtmlMessages(warningSuggestions),
               hasItems(*(expectedMessages.map { it.first }).toTypedArray()))
    warningSuggestions.forEach { suggestion ->
      val expected = expectedMessages.find { it.first == suggestion.plainTextMessage() }
      assertNotNull(expected)
      suggestion.findButton().requireText(expected!!.second[0])
      val options = suggestion.getOptionNames()
      if (options.isEmpty()) {
        assertThat(expected.second.size, equalTo(1))
      } else {
        assertThat(options, equalTo(expected.second.sliceArray(1 until expected.second.size)))
      }
    }

    psd.clickCancel()
  }
}

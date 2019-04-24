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
import org.fest.swing.timing.Wait
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class ObsoleteScopesTest {
  data class IssueAndFixes (
    val moduleName : String,
    val dependencyName : String,
    val dependencyGradleText : String,
    val message : String,
    val fixes : Array<Pair<String, String>>
  )
  private val expectedIssuesAndFixes = listOf(
    IssueAndFixes("app", "com.google.guava:guava:23.0", "'com.google.guava:guava:23.0'",
                  "Obsolete dependency configuration found: compile", arrayOf(Pair("compile", "implementation"))),
    IssueAndFixes("app", "compile/libs", "fileTree(dir: 'libs', include: ['*.jar'])",
                  "Obsolete dependency configuration found: compile", arrayOf(Pair("compile", "implementation"))),
    IssueAndFixes("app", "junit:junit:4.11", "'junit:junit:4.11'",
                  "Obsolete dependency configuration found: testCompile", arrayOf(Pair("testCompile", "testImplementation"))),
    IssueAndFixes("app", "mylibrary", "project(path: ':mylibrary')",
                  "Obsolete dependency configuration found: compile", arrayOf(Pair("compile", "implementation"))),
    IssueAndFixes("mylibrary", "com.android.support:appcompat-v7:26.0.1", "'com.android.support:appcompat-v7:26.0.1'",
                  "Obsolete dependency configuration found: compile", arrayOf(Pair("compile", "api"), Pair("compile", "implementation"))),
    IssueAndFixes("mylibrary", "compile/libs", "fileTree(dir: 'libs', include: ['*.jar'])",
                  "Obsolete dependency configuration found: compile", arrayOf(Pair("compile", "api"), Pair("compile", "implementation"))),
    IssueAndFixes("mylibrary", "junit:junit:4.11", "'junit:junit:4.11'",
                  "Obsolete dependency configuration found: testCompile", arrayOf(Pair("testCompile", "testImplementation"))))

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

    suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(5))

    // our obsolete scopes messages are warnings.  There may be other warnings, but none
    // of them should have the same messages as ours.  There may also be suggestions in
    // other groups.
    suggestionsConfigurable.waitForGroup("Warnings")
    val warningsGroup = suggestionsConfigurable.findGroup("Warnings")
    val warningSuggestions = warningsGroup.suggestions()

    val expectedMessages = expectedIssuesAndFixes
      .map { Pair("\n${it.moduleName} » ${it.dependencyName}\n${it.message} View usage",
                  it.fixes.map { fix -> "Update ${fix.first} to ${fix.second}"}.toTypedArray() ) }

    assertThat(getHtmlMessages(warningSuggestions),
               hasItems(*(expectedMessages.map { it.first }).toTypedArray()))
    warningSuggestions.forEach { suggestion ->
      val expected = expectedMessages.find { it.first == suggestion.plainTextMessage() }
      assertNotNull(expected)
      suggestion.findButton().requireText(expected.second[0])
      val options = suggestion.getOptionNames()
      if (options.isEmpty()) {
        assertThat(expected.second.size, equalTo(1))
      } else {
        assertThat(options, equalTo(expected.second.sliceArray(1 until expected.second.size)))
      }
    }

    psd.clickCancel()
  }

  @Test
  fun testQuickFixDisappearOnClick() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    val psd = ide.openPsd()
    val suggestionsConfigurable = psd.selectSuggestionsConfigurable()
    expectedIssuesAndFixes
      .forEachIndexed { i, issueAndFix ->
        suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(5))
        suggestionsConfigurable.waitForGroup("Warnings")
        var warningsGroup = suggestionsConfigurable.findGroup("Warnings")
        val pattern = issueAndFix.let { "${it.moduleName} » ${it.dependencyName}\nObsolete dependency configuration found" }
        var message = warningsGroup.findMessageMatching(pattern)
        assertNotNull(message)
        assertTrue(message.isActionActionAvailable())
        message.clickAction()
        suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(5))
        // if we have just acted on the last issueAndFix, then the "Warnings" group might no longer be present
        // (it might still be if there are other, unrelated warnings, so simply skip the test)
        if (i != expectedIssuesAndFixes.lastIndex) {
          suggestionsConfigurable.waitForGroup("Warnings")
          warningsGroup = suggestionsConfigurable.findGroup("Warnings")
          message = warningsGroup.findMessageMatching(pattern)
          assertNull(message)
        }
      }
    psd.clickCancel()
  }

  @Test
  fun testQuickFixEffect() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    expectedIssuesAndFixes
      .forEach { issueAndFix ->
        val origBuildGradleContent = issueAndFix.let { ide.editor.open("/${it.moduleName}/build.gradle").currentFileContents }
        val psd = ide.openPsd()
        val suggestionsConfigurable = psd.selectSuggestionsConfigurable()
        suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(5))
        suggestionsConfigurable.waitForGroup("Warnings")
        val warningsGroup = suggestionsConfigurable.findGroup("Warnings")
        val pattern = issueAndFix.let { "${it.moduleName} » ${it.dependencyName}\nObsolete dependency configuration found" }
        val message = warningsGroup.findMessageMatching(pattern)
        assertNotNull(message)
        assertTrue(message.isActionActionAvailable())
        // TODO(xof): test drop-down and clicking actions other than the default
        message.clickAction()
        psd.clickOk(Wait.seconds(3))
        val newAppBuildGradleContent = issueAndFix.let { ide.editor.open("/${it.moduleName}/build.gradle").currentFileContents }
        val expectedAppBuildGradleContent =
          issueAndFix.let {
            origBuildGradleContent
              .replace("${it.fixes[0].first} ${it.dependencyGradleText}", "${it.fixes[0].second} ${it.dependencyGradleText}")
          }
        assertThat(newAppBuildGradleContent, equalTo(expectedAppBuildGradleContent))
      }
  }

}

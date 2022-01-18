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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectSuggestionsConfigurable
import com.android.tools.idea.tests.gui.framework.waitForIdle
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class SuggestionsViewTest {

  @Rule
  @JvmField
  val guiTest = PsdGuiTestRule()

  @Ignore("b/77848741")
  @Test
  fun showsAndProcessesMessages() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")
    val originalBuildGradleFileContent = fixture
        .editor
        .open("/app/build.gradle")
        .currentFileContents

    val psd = fixture.openPsd()

    val suggestionsConfigurable = psd.selectSuggestionsConfigurable()
    suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(30))

    suggestionsConfigurable.waitForGroups("Information", "Updates")

    val informationGroup = suggestionsConfigurable.findGroup("Information")
    var message = informationGroup.findMessageMatching("support-compat:26.0.0 \\(app\\) : Gradle promoted library version from 26.0.0 to")
    assertNotNull(message)
    message.requireActionUnavailable()

    var updatesGroup = suggestionsConfigurable.findGroup("Updates")

    assertNotNull(updatesGroup.findMessageMatching("appcompat-v7:26.0.1 \\(app\\) : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("appcompat-v7:26.0.1 \\(mylibrary\\) : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("proguard-base:4.9 \\(app\\) : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("support-compat:26.0.0 \\(app\\) : Newer version available:"))

    message = updatesGroup.findMessageMatching("support-compat:26.0.0 \\(app\\) : Newer version available:")
    assertNotNull(message)
    assertTrue (message.isActionActionAvailable())
    message.clickAction()
    suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(30))

    suggestionsConfigurable.waitForGroups("Updates")
    updatesGroup = suggestionsConfigurable.findGroup("Updates");
    Wait
        .seconds(3)
        .expecting("No message matching: support-compat:26.0.0 \\\\(app\\\\) : Newer version available:\")")
        .until { updatesGroup.findMessageMatching("support-compat:26.0.0 \\(app\\) : Newer version available:") == null }
    waitForIdle()

    message = updatesGroup.findMessageMatching("appcompat-v7:26.0.1 \\(app\\) : Newer version available:")
    assertNotNull(message)
    assertTrue (message.isActionActionAvailable())
    message.clickAction()
    suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(30))

    updatesGroup = suggestionsConfigurable.findGroup("Updates")
    Wait
        .seconds(3)
        .expecting("No message matching: \"appcompat-v7:26.0.1 \\\\(app\\\\) : Newer version available:\"")
        .until { updatesGroup.findMessageMatching("\"appcompat-v7:26.0.1 \\\\(app\\\\) : Newer version available:\"") == null }

    psd.clickOk(Wait.seconds(30))
    val updatedBuildGradleFileContent = fixture.editor
        .open("/app/build.gradle")
        .currentFileContents

    assertThat(updatedBuildGradleFileContent).isEqualTo(originalBuildGradleFileContent.replace("26.0.(0|1)".toRegex(), "27.0.2"))
  }

  @Test
  @Ignore("b/74443500")
  fun filtersMessagesByModule() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    val psd = fixture.openPsd()

    val suggestionsConfigurable = psd.selectSuggestionsConfigurable()
    suggestionsConfigurable.waitAnalysesCompleted(Wait.seconds(30))

    assertThat(suggestionsConfigurable.isModuleSelectorMinimized()).isFalse()
    var moduleSelector = suggestionsConfigurable.findModuleSelector()
    assertThat(moduleSelector.modules()).containsExactly("<All Modules>", "app", "mylibrary")
    suggestionsConfigurable.waitForGroups("Information", "Updates")
    var informationGroup = suggestionsConfigurable.findGroup("Information")
    assertNotNull(informationGroup.findMessageMatching("support-compat:26.0.0 \\(app\\) : Gradle promoted library version from 26.0.0 to"))
    var updatesGroup = suggestionsConfigurable.findGroup("Updates")
    assertNotNull(updatesGroup.findMessageMatching("appcompat-v7:26.0.1 \\(app\\) : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("appcompat-v7:26.0.1 \\(mylibrary\\) : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("proguard-base:4.9 \\(app\\) : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("support-compat:26.0.0 \\(app\\) : Newer version available:"))

    assertThat(suggestionsConfigurable.isModuleSelectorMinimized()).isFalse()
    moduleSelector = suggestionsConfigurable.findModuleSelector()
    moduleSelector.selectModule("app")
    suggestionsConfigurable.waitForGroups("Information", "Updates")
    informationGroup = suggestionsConfigurable.findGroup("Information")
    assertNotNull(informationGroup.findMessageMatching(("support-compat:26.0.0 : Gradle promoted library version from 26.0.0 to")))
    updatesGroup = suggestionsConfigurable.findGroup("Updates")
    assertNotNull(updatesGroup.findMessageMatching("appcompat-v7:26.0.1 : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("proguard-base:4.9 : Newer version available:"))
    assertNotNull(updatesGroup.findMessageMatching("support-compat:26.0.0 : Newer version available:"))

    assertThat(suggestionsConfigurable.isModuleSelectorMinimized()).isFalse()
    moduleSelector = suggestionsConfigurable.findModuleSelector()
    moduleSelector.selectModule("mylibrary")
    suggestionsConfigurable.waitForGroups("Updates")
    updatesGroup = suggestionsConfigurable.findGroup("Updates")
    assertNotNull(updatesGroup.findMessageMatching(("appcompat-v7:26.0.1 : Newer version available:")))

    psd.clickCancel()
  }
}

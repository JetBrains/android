/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.HyperlinkLabel
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ModuleDownloadConditionsTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Test
  fun testHelpLink() {
    val project = projectRule.project
    val launcher = TestBrowserLauncher()
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, launcher, projectRule.fixture.testRootDisposable)

    val conditions = ModuleDownloadConditions()
    val fakeUi = FakeUi(conditions.rootComponent)

    val link = fakeUi.findComponent<HyperlinkLabel>()!!
    link.doClick()

    assertThat(launcher.lastUrl).isEqualTo("https://developer.android.com/r/studio-ui/dynamic-delivery/conditional-delivery")
  }

  private class TestBrowserLauncher : BrowserLauncher() {
    var lastUrl: String? = null

    override fun open(url: String) {
      lastUrl = url
    }

    override fun browse(file: Path) {}

    override fun browse(url: String, browser: WebBrowser?, project: Project?) {
      lastUrl = url
    }
  }
}

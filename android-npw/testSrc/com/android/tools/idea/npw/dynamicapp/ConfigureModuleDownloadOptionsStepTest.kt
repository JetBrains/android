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
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ide.HelpTooltip
import com.intellij.ui.ContextHelpLabel
import java.nio.file.Path
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ConfigureModuleDownloadOptionsStepTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Test
  fun testHeaderLink() {
    val project = projectRule.project
    val launcher = TestBrowserLauncher()
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, launcher, projectRule.fixture.testRootDisposable)

    val model =
      DynamicFeatureModel(
        project = project,
        moduleParent = ":",
        projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker(),
        templateName = "Dynamic Feature",
        templateDescription = "Creates a dynamic feature module",
      )
    val step = ConfigureModuleDownloadOptionsStep(model)
    Disposer.register(projectRule.fixture.testRootDisposable, step)
    val fakeUi = FakeUi(step.component)

    val editorPane = fakeUi.findComponent<JEditorPane> { it.text.contains("Dynamic feature modules can be delivered") }!!

    val url = "https://developer.android.com/studio/projects/dynamic-delivery/overview"
    editorPane.fireHyperlinkUpdate(HyperlinkEvent(editorPane, HyperlinkEvent.EventType.ACTIVATED, java.net.URL(url), url))

    assertThat(launcher.lastUrl).isEqualTo(url)
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

  @Test
  fun testFusingHelpLink() {
    val project = projectRule.project
    val launcher = TestBrowserLauncher()
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, launcher, projectRule.fixture.testRootDisposable)

    val model = DynamicFeatureModel(
      project = project,
      moduleParent = ":",
      projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker(),
      templateName = "Dynamic Feature",
      templateDescription = "Creates a dynamic feature module",
    )
    val step = ConfigureModuleDownloadOptionsStep(model)
    Disposer.register(projectRule.fixture.testRootDisposable, step)
    val fakeUi = FakeUi(step.component, createFakeWindow = true, parentDisposable = projectRule.fixture.testRootDisposable)

    val helpLabel = fakeUi.findAllComponents<ContextHelpLabel>().firstOrNull {
      HelpTooltip.getTooltipFor(it)?.link != null
    } ?: error("Fusing help label not found")
    val tooltip = HelpTooltip.getTooltipFor(helpLabel)!!
    val link = tooltip.link!!
    link.doClick()

    val expectedUrl = "https://developer.android.com/r/studio-ui/dynamic-delivery/fusing"
    assertThat(launcher.lastUrl).isEqualTo(expectedUrl)
  }
}

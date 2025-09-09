/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.gradle.structure.configurables.issues.NavigationHyperlinkListener
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import java.io.File
import java.nio.file.Path
import javax.swing.event.HyperlinkEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class HtmlLabelTest {

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var htmlLabel: HtmlLabel
  val browserLauncher = createFakeBrowserLauncher()
  val launchedUrls = mutableListOf<String>()

  @Before
  fun setup() {
    htmlLabel = HtmlLabel()
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, browserLauncher, disposableRule.disposable)
    launchedUrls.clear()
    htmlLabel.text = DEMO_TEXT_WITH_LINK
  }

  @Test
  fun `hyperlink opens browser once`() {
    htmlLabel.addHyperlinkListener(NavigationHyperlinkListener(mock()))
    emitDemoEvent()
    assertThat(launchedUrls).containsExactly(DEMO_URL)
  }

  @Test
  fun `hyperlink opens browser twice if default handling preserved`() {
    htmlLabel.addHyperlinkListener(NavigationHyperlinkListener(mock()), false)
    emitDemoEvent()
    assertThat(launchedUrls).containsExactly(DEMO_URL, DEMO_URL)
  }

  @Test
  fun `hyperlink opens browser once if default handling explicitly not preserved`() {
    htmlLabel.addHyperlinkListener(NavigationHyperlinkListener(mock()), true)
    emitDemoEvent()
    assertThat(launchedUrls).containsExactly(DEMO_URL)
  }

  private fun createFakeBrowserLauncher(): BrowserLauncher = object : BrowserLauncher() {
    override fun open(url: String) {
      launchedUrls += url
    }

    override fun browse(file: File) = throw IllegalStateException()

    override fun browse(file: Path) = throw IllegalStateException()

    override fun browse(url: String, browser: WebBrowser?, project: Project?) {
      launchedUrls += url
    }
  }

  private fun emitDemoEvent() {
    val event = HyperlinkEvent(htmlLabel, HyperlinkEvent.EventType.ACTIVATED, null, DEMO_URL)
    htmlLabel.hyperlinkListeners.onEach { it.hyperlinkUpdate(event) }
  }


  companion object {
    const val DEMO_URL = "http://android.com"
    const val DEMO_TEXT_WITH_LINK = "Some test with a <a href=\"$DEMO_URL\">link</a>"
  }
}
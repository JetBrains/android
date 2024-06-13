/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.help

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.help.WebHelpProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import java.io.File
import java.nio.file.Path

class AndroidStudioHelpTest : AndroidTestCase() {

  fun testAndroidTopic() {
    val androidWebHelpProvider = ApplicationManager.getApplication().extensionArea
      .getExtensionPoint<WebHelpProvider>("com.intellij.webHelpProvider")
      .extensionList.filterIsInstance<AndroidWebHelpProvider>().first()

    assertThat(androidWebHelpProvider.getHelpPageUrl("org.jetbrains.android.foo/bar"))
      .isEqualTo("https://developer.android.com/foo/bar")
  }

  fun testAndroidHelp() {
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, TestBrowserLauncher, testRootDisposable)
    HelpManager.getInstance().invokeHelp("org.jetbrains.android.r/studio-ui/rundebugconfig.html")

    assertThat(TestBrowserLauncher.lastUrl)
      .isEqualTo("https://developer.android.com/r/studio-ui/rundebugconfig.html")
  }

  fun testIdeaHelp() {
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, TestBrowserLauncher, testRootDisposable)
    HelpManager.getInstance().invokeHelp(null)

    val version = "${ApplicationInfo.getInstance().majorVersion}.${ApplicationInfo.getInstance().minorVersion}"
    assertThat(TestBrowserLauncher.lastUrl)
      .isEqualTo("https://www.jetbrains.com/help/idea/$version/?top")
  }

  object TestBrowserLauncher : BrowserLauncher() {
    var lastUrl: String? = null

    override fun open(url: String) {
      lastUrl = url
    }

    override fun browse(file: File) { }

    override fun browse(file: Path) { }

    override fun browse(url: String, browser: WebBrowser?, project: Project?) {
      lastUrl = url
    }
  }
}

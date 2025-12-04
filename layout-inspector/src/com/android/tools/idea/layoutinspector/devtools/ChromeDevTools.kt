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
package com.android.tools.idea.layoutinspector.devtools

import com.intellij.ide.browsers.BrowserFamily
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.util.Condition
import java.util.UUID
import org.jetbrains.annotations.VisibleForTesting

/**
 * Same as WebBrowserManager.PREDEFINED_CHROME_ID. This can be removed if a
 * WebBrowserManager.isChrome(@NotNull WebBrowser browser) existed.
 */
private val CHROME_ID = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3")

/** URL to a built-in tool in the Chrome browser */
private const val INSPECT = "chrome://inspect"

/** Access the chrome devtools. */
object ChromeDevTools {
  val isChromeAvailable: Boolean
    get() = findChromeBrowser() != null

  fun navigateTo() {
    val browser = findChromeBrowser() ?: return
    BrowserLauncher.instance.browse(INSPECT, browser)
  }

  /** Find a Chrome browser on the current system even if it is not the users preferred browser */
  private fun findChromeBrowser(): WebBrowser? {
    val manager = WebBrowserManager.getInstance()
    return manager.getBrowsers(Condition { isChrome(it) }, true).firstOrNull()
  }

  @VisibleForTesting
  fun isChrome(browser: WebBrowser): Boolean {
    return browser.family == BrowserFamily.CHROME && browser.id == CHROME_ID
  }
}

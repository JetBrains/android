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
package com.android.tools.idea.browser

import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.browsers.BrowserLauncherImpl
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.project.Project
import java.net.URISyntaxException
import org.apache.http.client.utils.URIBuilder

class AndroidStudioBrowserLauncher : BrowserLauncherImpl() {
  override fun browse(url: String, browser: WebBrowser?, project: Project?) {
    super.browse(addUtmParameters(url), browser, project)
  }

  companion object {
    @VisibleForTesting
    fun addUtmParameters(urlString: String): String {
      val uriBuilder = try {
        URIBuilder(urlString)
      }
      catch (_: URISyntaxException) {
        return urlString
      }

      val scheme = uriBuilder.scheme
      if (scheme !in listOf("http", "https")) {
        return urlString
      }

      if (uriBuilder.host != "developer.android.com") {
        return urlString
      }

      val queryParams = uriBuilder.queryParams
      if (queryParams.any { it.name in listOf("utm_source", "utm_medium", "utm_content") }) {
        return urlString
      }

      uriBuilder.addParameter("utm_source", "android-studio-app")
      uriBuilder.addParameter("utm_medium", "app")

      return uriBuilder.build().toString()
    }
  }
}
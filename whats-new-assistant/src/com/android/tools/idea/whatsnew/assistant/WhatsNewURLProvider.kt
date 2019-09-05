/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant

import com.intellij.openapi.application.PathManager
import com.intellij.util.PathUtil
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

const val WNA_CACHE_DIR_KEY = "whatsnew"

open class WhatsNewURLProvider {
  /**
   * Gets URL for config xml for the current version from webserver
   * @return URL for config xml file
   */
  open fun getWebConfig(version: String): URL {
    return try {
      URL("https://developer.android.com/studio/releases/assistant/$version.xml")
    }
    catch (e: MalformedURLException) {
      throw RuntimeException("Could not get path for web WNA xml file")
    }
  }

  /**
   * Gets path for config xml for the current version on disk. The file may or may not exist.
   * @return URL for the local config file where xml will be stored
   */
  open fun getLocalConfig(version: String): Path {
    return getConfigCacheDir().resolve("$version.xml")
  }

  open fun getResourceFileAsStream(bundleCreator: WhatsNewBundleCreator?, version: String): InputStream? {
    return bundleCreator?.javaClass?.getResourceAsStream("/whats-new-assistant.xml")
  }

  /**
   * @return path to directory where local xml config will be stored
   */
  private fun getConfigCacheDir(): Path {
    val path = Paths.get(PathUtil.getCanonicalPath(PathManager.getSystemPath()), WNA_CACHE_DIR_KEY)
    path.toFile().mkdirs()
    return path
  }
}

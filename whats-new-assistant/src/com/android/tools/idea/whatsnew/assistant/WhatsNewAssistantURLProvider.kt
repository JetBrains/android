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

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths
import javax.swing.filechooser.FileSystemView

open class WhatsNewAssistantURLProvider {
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
   * Gets URL for config xml for the current version on disk. The file may or may not exist.
   * @return URL for the local config file where xml will be stored
   */
  open fun getLocalConfig(version: String): URL {
    val localConfigPath = Paths.get(createAndGetConfigDir(), "$version.xml")
    try {
      return localConfigPath.toUri().toURL()
    }
    catch (e: MalformedURLException) {
      throw RuntimeException("Could not get path for local WNA xml file")
    }
  }

  open fun getResourceFile(bundleCreator: WhatsNewAssistantBundleCreator?, version: String): URL? {
    return bundleCreator?.javaClass?.getResource("/$version.xml")
  }

  /**
   * @return path to directory where local xml config will be stored
   */
  private fun createAndGetConfigDir(): String {
    val userHome = System.getProperty("user.home")
    val path: String
    path = when {
      SystemInfo.isWindows -> FileSystemView.getFileSystemView().defaultDirectory.path
      SystemInfo.isMac -> FileUtil.join(userHome, "Documents")
      SystemInfo.isLinux -> userHome
      else -> throw RuntimeException("Platform is not supported")
    }

    val configPath = Paths.get(path, "AndroidStudio", "WhatsNewAssistant").toString()
    File(configPath).mkdirs()

    return configPath
  }
}

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
package com.android.tools.adtui.compose

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File

/**
 * This is a hacky fix for b/460147546 until we can get a better fix upstream in IntelliJ. IntelliJ
 * 2026.1 commit https://github.com/JetBrains/intellij-community/commit/fc5237c8da will help, but
 * it's not perfect, and we still need to support older platforms for aiplugin anyway.
 *
 * The intent is to initialize 'skiko.library.path' earlier, before any code uses Skiko.
 */
class InitializeSkikoLibPathEarly : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String>) {
    if (System.getProperty("skiko.library.path") != null) {
      thisLogger().info("skiko.library.path is already set")
      return
    }
    if (ApplicationInfo.getInstance().build.baselineVersion < 252) {
      // We started using the platform-bundled Jewel/Skiko libs in IntelliJ 2025.2 onward.
      thisLogger().info("Not setting skiko.library.path for older platform")
      return
    }
    // Note: after merging IntelliJ commit fc5237c8da we could call setSkikoLibraryPath() instead.
    val bundledSkikoFolder = File(PathManager.getLibPath(), "/skiko-awt-runtime-all")
    if (!bundledSkikoFolder.isDirectory || !bundledSkikoFolder.canRead()) {
      thisLogger().error("Bundled Skiko library not found at: $bundledSkikoFolder")
      return
    }
    thisLogger().info("Configuring skiko.library.path early to address b/460147546")
    System.setProperty("skiko.library.path", PathManager.getLibPath() + "/skiko-awt-runtime-all")
  }
}

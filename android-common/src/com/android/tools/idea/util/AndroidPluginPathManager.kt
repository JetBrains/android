// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginPathManager
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JetBrains patch: this util resolves files that the Android Plugin bundles in its own /resources dir.
 *
 * For example, this addresses the AND-80 bug, where "images/asset_studio/ic_launcher_foreground.xml" was resolved to non-existing
 * "<ide>/plugins/android/resources/images/asset_studio/ic_launcher_foreground.xml". That path only exists in Android Studio.
 */
object AndroidPluginPathManager {
  @JvmStatic
  fun getResource(relativePath: String): Path =
    getPluginResource(relativePath) ?: getIdeBundledResource(relativePath)

  private fun getPluginResource(relativePath: String): Path? =
    PluginPathManager.getPluginResource(javaClass, "resources/$relativePath")?.toPath()

  private fun getIdeBundledResource(relativePath: String): Path =
    Paths.get(PathManager.getHomePath(), "plugins/android/resources", relativePath)
}

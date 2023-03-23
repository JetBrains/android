/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.sdk

import com.android.sdklib.IAndroidTarget
import com.android.tools.idea.util.StudioPathManager
import com.android.tools.sdk.CompatibilityRenderTarget
import com.android.tools.sdk.EmbeddedRenderTarget
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File

class StudioEmbeddedRenderTarget {
  companion object {
    private val LOG = Logger.getInstance(StudioEmbeddedRenderTarget::class.java)

    private var ourDisableEmbeddedTargetForTesting = false

    /**
     * Method that allows to disable the use of the embedded render target. Only for testing.
     *
     * @param value if true, the embedded layoutlib won't be used
     */
    @JvmStatic
    @VisibleForTesting
    fun setDisableEmbeddedTarget(value: Boolean) {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      ourDisableEmbeddedTargetForTesting = value
    }

    /**
     * Returns a CompatibilityRenderTarget that will use StudioEmbeddedRenderTarget to do the rendering.
     */
    @JvmStatic
    fun getCompatibilityTarget(target: IAndroidTarget): CompatibilityRenderTarget {
      if (ourDisableEmbeddedTargetForTesting) {
        return CompatibilityRenderTarget (target, target.version.apiLevel, target)
      }

      return EmbeddedRenderTarget.getCompatibilityTarget(target) { getEmbeddedLayoutLibPath() }
    }

    /**
     * Returns the URL for the embedded layoutlib distribution.
     */
    @JvmStatic
    fun getEmbeddedLayoutLibPath(): String? {
      val homePath = FileUtil.toSystemIndependentName(PluginPathManager.getPluginHomePath("design-tools"))
      var path = FileUtil.join(homePath, "/resources/layoutlib/")
      if (StudioPathManager.isRunningFromSources()) {
        path = StudioPathManager.resolvePathFromSourcesRoot("prebuilts/studio/layoutlib/").toString()
      }
      val root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path))
      if (root != null) {
        val rootFile = VfsUtilCore.virtualToIoFile(root)
        if (rootFile.exists() && rootFile.isDirectory) {
          LOG.debug("Embedded layoutlib found at $path")
          return rootFile.absolutePath + File.separator
        }
      }
      LOG.error("Unable to find embedded layoutlib in path: $path")
      return null
    }
  }
}
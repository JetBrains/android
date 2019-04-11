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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Dimension
import java.awt.Image
import java.util.concurrent.CompletableFuture

/**
 * Interface to extend the rendering capabilities of the resources explorer
 * for arbitrary type of files.
 */
interface DesignAssetRenderer {

  /**
   * Implementing class should return true if it can handle the provided [file].
   */
  fun isFileSupported(file: VirtualFile): Boolean

  /**
   * Implementing class should return an image corresponding to the [file] with
   * dimension optimized to be displayed at the provided [dimension] (this usually means equals or smaller)
   */
  fun getImage(file: VirtualFile, module: Module?, dimension: Dimension): CompletableFuture<out Image?>

}

class DesignAssetRendererManager private constructor() {

  companion object {
    private val EP_NAME = ExtensionPointName.create<DesignAssetRenderer>("com.android.resourceViewer")

    fun getInstance(): DesignAssetRendererManager {
      return ServiceManager.getService(DesignAssetRendererManager::class.java)!!
    }
  }

  fun getViewer(file: VirtualFile): DesignAssetRenderer {
    return EP_NAME.extensions.firstOrNull { it.isFileSupported(file) } ?: NullDesignAssetRenderer
  }

  fun hasViewer(file: VirtualFile): Boolean = getViewer(file) != NullDesignAssetRenderer

  fun <T : DesignAssetRenderer> getViewer(clazz: Class<T>): T? {
    return EP_NAME.findExtension(clazz)
  }
}

private object NullDesignAssetRenderer : DesignAssetRenderer {
  override fun isFileSupported(file: VirtualFile) = false
  override fun getImage(file: VirtualFile, module: Module?, dimension: Dimension): CompletableFuture<out Image?> =
    CompletableFuture.completedFuture(null)
}

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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

/**
 * Interface to extend the rendering capabilities of the resources explorer
 * for arbitrary type of files. Use the [DesignAssetRendererManager] to
 * get a instance of a renderer for a given [VirtualFile].
 */
interface DesignAssetRenderer {

  /**
   * Implementing class should return true if it can handle the provided [file].
   */
  fun isFileSupported(file: VirtualFile): Boolean

  /**
   * Implementing class should return an image corresponding to the [file] with
   * dimension optimized to be displayed at the provided [dimension] (this usually means equals or smaller).
   *
   * Use the [context] parameter to provide any additional information that the expected renderer could use. E.g: [DrawableAssetRenderer]
   * may take a VirtualFile to render the Drawable in a different Configuration context.
   */
  fun getImage(file: VirtualFile, module: Module?, dimension: Dimension, context: Any? = null): CompletableFuture<out BufferedImage?>

}

class DesignAssetRendererManager private constructor() {

  companion object {
    private val EP_NAME = ExtensionPointName.create<DesignAssetRenderer>("com.android.resourceViewer")

    fun getInstance(): DesignAssetRendererManager {
      return ApplicationManager.getApplication().getService(DesignAssetRendererManager::class.java)!!
    }
  }

  /**
   * Returns a renderer for the given [file]. If not renderer is found, it falls back
   * on a renderer that always returns a null image.
   */
  fun getViewer(file: VirtualFile): DesignAssetRenderer {
    return EP_NAME.extensions.firstOrNull { it.isFileSupported(file) } ?: NullDesignAssetRenderer
  }

  /**
   * Returns true if a renderer for the given [file] is available.
   */
  fun hasViewer(file: VirtualFile): Boolean = getViewer(file) != NullDesignAssetRenderer

  /**
   * Returns the shared instance of a renderer of class [T].
   */
  fun <T : DesignAssetRenderer> getViewer(clazz: Class<T>): T? {
    return EP_NAME.findExtension(clazz)
  }
}

/**
 * A renderer that always returns null and used as a fallback when
 * no renderer are available for a given file.
 */
private object NullDesignAssetRenderer : DesignAssetRenderer {
  override fun isFileSupported(file: VirtualFile) = false
  override fun getImage(file: VirtualFile,
                        module: Module?,
                        dimension: Dimension,
                        context: Any?): CompletableFuture<out BufferedImage?> =
    CompletableFuture.completedFuture(null)
}

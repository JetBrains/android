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

import com.android.SdkConstants
import com.android.tools.idea.rendering.DrawableRenderer
import com.android.utils.XmlUtils
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.text.ParseException
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import org.jetbrains.android.facet.AndroidFacet

private val LOG = Logger.getInstance(DrawableAssetRenderer::class.java)

private val SUPPORTED_DRAWABLE_TAG =
  arrayOf(
    SdkConstants.TAG_ADAPTIVE_ICON,
    SdkConstants.TAG_ANIMATED_SELECTOR,
    SdkConstants.TAG_ANIMATED_VECTOR,
    SdkConstants.TAG_BITMAP,
    SdkConstants.TAG_INSET,
    SdkConstants.TAG_LAYER_LIST,
    SdkConstants.TAG_NINE_PATCH,
    SdkConstants.TAG_RIPPLE,
    SdkConstants.TAG_ROTATE,
    SdkConstants.TAG_SELECTOR,
    SdkConstants.TAG_SHAPE,
    SdkConstants.TAG_TRANSITION,
    SdkConstants.TAG_VECTOR,
  )

/** [DesignAssetRenderer] to display Vector Drawable. */
class DrawableAssetRenderer : DesignAssetRenderer {

  private fun createRenderer(module: Module, targetFile: VirtualFile): CompletableFuture<DrawableRenderer> {
    if (module.isDisposed || module.project.isDisposed) {
      return CompletableFuture<DrawableRenderer>().also { it.completeExceptionally(IllegalStateException("Module or project is disposed")) }
    }
    val facet =
      AndroidFacet.getInstance(module)
        ?: return CompletableFuture<DrawableRenderer>().also {
          it.completeExceptionally(NullPointerException("Facet for module $module couldn't be found for use in DrawableRenderer."))
        }

    return CompletableFuture.supplyAsync(
      Supplier {
        return@Supplier DrawableRenderer(facet, targetFile)
      },
      AppExecutorUtil.getAppExecutorService(),
    )
  }

  override fun isFileSupported(file: VirtualFile): Boolean {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE) || file.length == 0L) {
      return false
    }

    return try {
      // Only the root tag name is needed — use the non-expanding string-scan helper
      // (same one used by ImageAsset/VectorAsset) instead of a full DOM parse.
      XmlUtils.getRootTagName(String(file.contentsToByteArray())) in SUPPORTED_DRAWABLE_TAG
    } catch (ex: Exception) {
      LOG.warn("${ex::class.simpleName} in ${file.path}", ex)
      false
    }
  }

  override fun getImage(file: VirtualFile, module: Module?, dimension: Dimension, context: Any?): CompletableFuture<out BufferedImage?> {
    try {
      if (module == null) {
        return CompletableFuture<BufferedImage?>().also {
          it.completeExceptionally(NullPointerException("Module cannot be null to render a Drawable."))
        }
      }

      if (!isFileSupported(file)) {
        return CompletableFuture<BufferedImage?>().also {
          it.completeExceptionally(ParseException("${file.path} couldn't be parsed as a drawable.", 0))
        }
      }

      val contextFile = context as? VirtualFile
      val targetFile = contextFile ?: file // A file representing a target that includes required resources.
      val renderer = createRenderer(module, targetFile)

      val xmlContent = String(file.contentsToByteArray())

      return renderer
        .thenCompose { drawableRenderer -> drawableRenderer.renderDrawable(xmlContent, dimension) }
        .whenComplete { _, _ ->
          // Dispose of the renderer after the rendering is completed
          Disposer.dispose(renderer.get())
        }
    } catch (ex: Exception) {
      return failedFuture(ex)
    }
  }

  private fun failedFuture(exception: Throwable): CompletableFuture<out BufferedImage?> {
    LOG.warn(exception)
    val failedFuture = CompletableFuture<BufferedImage?>()
    failedFuture.completeExceptionally(exception)
    return failedFuture
  }
}

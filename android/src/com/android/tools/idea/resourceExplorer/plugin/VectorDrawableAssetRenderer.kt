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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.SdkConstants
import com.android.tools.idea.npw.assetstudio.DrawableRenderer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [DesignAssetRenderer] to display Vector Drawable.
 */
class VectorDrawableAssetRenderer : DesignAssetRenderer {

  private var drawableRenderer: DrawableRenderer? = null
  private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

  override fun isFileSupported(file: VirtualFile): Boolean {
    return try {
      val document = documentBuilder.parse(file.inputStream)
      document.documentElement.nodeName == SdkConstants.TAG_VECTOR
    } catch (ex: Exception) {
      false
    }
  }

  override fun getImage(
    file: VirtualFile,
    module: Module?,
    dimension: Dimension
  ): ListenableFuture<out Image?> {
    if (module == null) {
      return Futures.immediateFuture(null)
    }
    if (drawableRenderer?.let { Disposer.isDisposed(it) } != false) {
      val facet = AndroidFacet.getInstance(module) ?: return Futures.immediateFuture(null)
      drawableRenderer = DrawableRenderer(facet).apply {
        Disposer.register(facet, this)
      }

    }
    return drawableRenderer?.renderDrawable(String(file.contentsToByteArray()), dimension)
        ?: return Futures.immediateFuture(null)
  }
}

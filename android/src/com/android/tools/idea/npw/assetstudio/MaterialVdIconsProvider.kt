/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio

import com.android.tools.idea.material.icons.MaterialIconsMetadata
import com.android.tools.idea.material.icons.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.MaterialIconsUrlProviderImpl
import com.android.tools.idea.material.icons.MaterialIconsUtils.MATERIAL_ICONS_PATH
import com.android.tools.idea.material.icons.MaterialVdIcons
import com.android.tools.idea.material.icons.MaterialVdIconsLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

private const val METADATA_FILE_NAME = "icons_metadata.txt"

/**
 * Gets [MaterialIconsMetadata] and handles calls [MaterialVdIconsLoader], returns a [CompletableFuture] to be consumed by the ui from the
 * [getMaterialIcons] method.
 *
 * TODO: Change to receiving a callback that takes MaterialVdIcons, and call it every time more icons are loaded. Instead of having the ui
 *  call #getMaterialIcons manually once.
 * @param urlMetadataProvider Url provider for the metadata file. For testing only.
 * @param urlLoaderProvider Url provider for [MaterialVdIconsLoader]. For testing only.
 */
class MaterialVdIconsProvider(
  urlMetadataProvider: MaterialIconsMetadataUrlProvider = MaterialIconsMetadataUrlProviderImpl(),
  private val urlLoaderProvider: MaterialIconsUrlProvider = MaterialIconsUrlProviderImpl()
) {
  private val LOG = Logger.getInstance(MaterialVdIconsProvider::class.java)

  private val metadata: MaterialIconsMetadata? = kotlin.run {
    val url = urlMetadataProvider.getMetadataUrl()
    return@run url?.let {
      val reader = try {
        BufferedReader(InputStreamReader(url.openStream()))
      }
      catch (e: Exception) {
        LOG.error("Error obtaining metadata file", e)
        return@let null
      }
      return@let MaterialIconsMetadata.parse(reader)
    }
  }

  /**
   * Returns a [CompletableFuture] of [MaterialVdIcons] that runs in the app execution thread pool since
   * [MaterialVdIconsLoader.getMaterialVdIcons] is a slow operation.
   */
  fun getMaterialIcons(): CompletableFuture<MaterialVdIcons> {
    if (metadata == null) {
      LOG.warn("No metadata for material icons.")
      return CompletableFuture.completedFuture(MaterialVdIcons.EMPTY)
    }
    return CompletableFuture.supplyAsync(
      Supplier {
        try {
          return@Supplier MaterialVdIconsLoader(metadata, urlLoaderProvider).getMaterialVdIcons()
        }
        catch (e: Exception) {
          LOG.error("Failed to load material icons", e)
          return@Supplier MaterialVdIcons.EMPTY
        }
      }, AppExecutorUtil.getAppExecutorService()
    )
  }
}

/**
 * Provides a [URL] that is used to get the metadata file and then parse it.
 */
interface MaterialIconsMetadataUrlProvider {
  fun getMetadataUrl(): URL?
}

/**
 * The default implementation of [MaterialIconsMetadataUrlProvider], returns the [URL] for the bundled metadata file in Android Studio.
 */
internal class MaterialIconsMetadataUrlProviderImpl : MaterialIconsMetadataUrlProvider {
  override fun getMetadataUrl(): URL? =
    MaterialVdIconsProvider::class.java.classLoader.getResource("${MATERIAL_ICONS_PATH}/$METADATA_FILE_NAME")

}
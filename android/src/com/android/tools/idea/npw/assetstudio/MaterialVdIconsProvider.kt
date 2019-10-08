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
import com.android.tools.idea.npw.assetstudio.MaterialVdIconsProvider.Status
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.EdtExecutorService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Supplier

private const val METADATA_FILE_NAME = "icons_metadata.txt"

/**
 * Gets [MaterialIconsMetadata] and handles calls to [MaterialVdIconsLoader]. Invokes the given ui-callback when more icons are loaded.
 *
 * @param refreshUiCallback Called whenever more icons are loaded, with the updated [MaterialVdIcons] object and a [Status] to indicate
 *  whether to expect more calls with more icons.
 * @param urlMetadataProvider Url provider for the metadata file. For testing only.
 * @param urlLoaderProvider Url provider for [MaterialVdIconsLoader]. For testing only.
 */
class MaterialVdIconsProvider(
  refreshUiCallback: ((MaterialVdIcons, Status) -> Unit),
  urlMetadataProvider: MaterialIconsMetadataUrlProvider = MaterialIconsMetadataUrlProviderImpl(),
  private val urlLoaderProvider: MaterialIconsUrlProvider = MaterialIconsUrlProviderImpl()
) {
  /**
   * Enum to indicate the status of this provider class to the given UI callback.
   */
  enum class Status {
    /**
     * There are still more icons to load, it is expected that there will be more invocations to the ui-callback.
     */
    LOADING,
    /**
     * There are no more icons to load, it should be the last call to the ui-callback.
     */
    FINISHED
  }

  private val LOG = Logger.getInstance(MaterialVdIconsProvider::class.java)

  private val metadata: MaterialIconsMetadata? = kotlin.run {
    val url = urlMetadataProvider.getMetadataUrl()
    return@run url?.let {
      val reader = try {
        BufferedReader(InputStreamReader(url.openStream()))
      } catch (e: Exception) {
        LOG.error("Error obtaining metadata file", e)
        return@let null
      }
      return@let MaterialIconsMetadata.parse(reader)
    }
  }

  /**
   * Single-threaded queued executor to orderly load icons on a background thread.
   */
  private val loaderExecutor = ThreadPoolExecutor(
    0,
    1,
    1,
    TimeUnit.MINUTES,
    LinkedBlockingQueue<Runnable>(),
    ThreadFactoryBuilder().setNameFormat(
      "${MaterialVdIconsProvider::class.java.simpleName}-loadMaterialIcons-%d"
    ).build()
  )

  init {
    when {
      metadata == null -> {
        LOG.warn("No metadata for material icons.")
        refreshUiCallback(MaterialVdIcons.EMPTY, Status.FINISHED)
      }
      metadata.families.isEmpty() -> {
        LOG.warn("Empty metadata for material icons.")
        refreshUiCallback(MaterialVdIcons.EMPTY, Status.FINISHED)
      }
      else -> {
        val iconsLoader = MaterialVdIconsLoader(metadata, urlLoaderProvider)
        metadata.families.forEachIndexed { index, style ->
          // Load icons by style/family.
          CompletableFuture.supplyAsync(Supplier {
            iconsLoader.loadMaterialVdIcons(style)
          }, loaderExecutor).whenCompleteAsync(BiConsumer { icons, throwable ->
            val status = if (index == metadata.families.lastIndex) Status.FINISHED else Status.LOADING
            if (throwable != null) {
              LOG.error("Error loading icons.", throwable)
              refreshUiCallback(MaterialVdIcons.EMPTY, status)
            } else {
              if (icons.styles.isEmpty()) {
                LOG.warn("No icons loaded.")
              }
              // Invoke the ui-callback with the loaded icons and current status value.
              refreshUiCallback(icons, status)
            }
          }, EdtExecutorService.getScheduledExecutorInstance())
        }
      }
    }
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
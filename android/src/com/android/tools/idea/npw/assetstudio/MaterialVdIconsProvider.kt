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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.material.icons.MaterialIconsMetadata
import com.android.tools.idea.material.icons.MaterialIconsMetadataUrlProvider
import com.android.tools.idea.material.icons.BundledMetadataUrlProvider
import com.android.tools.idea.material.icons.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.BundledIconsUrlProvider
import com.android.tools.idea.material.icons.MaterialIconsCopyHandler
import com.android.tools.idea.material.icons.MaterialIconsUtils.getIconsSdkTargetPath
import com.android.tools.idea.material.icons.MaterialVdIcons
import com.android.tools.idea.material.icons.MaterialVdIconsLoader
import com.android.tools.idea.npw.assetstudio.MaterialVdIconsProvider.Status
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.EdtExecutorService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Supplier

private val LOG = Logger.getInstance(MaterialVdIconsProvider::class.java)

/**
 * Provider class for [MaterialVdIcons].
 */
class MaterialVdIconsProvider {

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

  companion object {
    /**
     * Gets [MaterialIconsMetadata] and handles calls to [MaterialVdIconsLoader]. Invokes the given ui-callback when more icons are loaded.
     *
     * @param refreshUiCallback Called whenever more icons are loaded, with the updated [MaterialVdIcons] object and a [Status] to indicate
     *  whether to expect more calls with more icons.
     * @param metadataUrlProvider Url provider for the metadata file.
     * @param iconsUrlProvider Url provider for [MaterialVdIconsLoader].
     */
    @JvmStatic
    fun loadMaterialVdIcons(refreshUiCallback: (MaterialVdIcons, Status) -> Unit,
                            metadataUrlProvider: MaterialIconsMetadataUrlProvider?,
                            iconsUrlProvider: MaterialIconsUrlProvider?) {
      val metadata = getMetadata(metadataUrlProvider ?: BundledMetadataUrlProvider())
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
          loadMaterialVdIcons(
            metadata, iconsUrlProvider ?: BundledIconsUrlProvider(), StudioFlags.ASSET_COPY_MATERIAL_ICONS.get(), refreshUiCallback)
        }
      }
    }
  }
}

private fun loadMaterialVdIcons(metadata: MaterialIconsMetadata,
                                iconsUrlProvider: MaterialIconsUrlProvider,
                                copyToSdkFolder: Boolean,
                                refreshUiCallback: (MaterialVdIcons, Status) -> Unit) {
  val iconsLoader = MaterialVdIconsLoader(metadata, iconsUrlProvider)
  val backgroundExecutor = createBackgroundExecutor()
  metadata.families.forEachIndexed { index, style ->
    // Load icons by style/family.
    CompletableFuture.supplyAsync(Supplier {
      // Load icons in a background thread.
      iconsLoader.loadMaterialVdIcons(style)
    }, backgroundExecutor).whenCompleteAsync(BiConsumer { icons, throwable ->
      val status = if (index == metadata.families.lastIndex) Status.FINISHED else Status.LOADING
      if (throwable != null) {
        LOG.error("Error loading icons.", throwable)
        refreshUiCallback(MaterialVdIcons.EMPTY, status)
      }
      else {
        if (icons.styles.isEmpty()) {
          LOG.warn("No icons loaded.")
        }
        // Invoke the ui-callback with the loaded icons and current status value.
        refreshUiCallback(icons, status)

        if (copyToSdkFolder && status == Status.FINISHED) {
          // When finished loading, copy icons to the Android/Sdk directory.
          copyBundledIcons(metadata, icons, backgroundExecutor)
        }
      }
    }, EdtExecutorService.getScheduledExecutorInstance())
  }
}

private fun copyBundledIcons(metadata: MaterialIconsMetadata, icons: MaterialVdIcons, executor: ExecutorService) {
  val targetPath = getIconsSdkTargetPath()
  if (targetPath == null) {
    LOG.warn("No Android Sdk folder, can't copy Material Icons.")
    return
  }
  CompletableFuture.supplyAsync(Supplier {
    MaterialIconsCopyHandler(metadata, icons).copyTo(targetPath)
  }, executor).whenComplete { _, throwable ->
    if (throwable != null) {
      LOG.error("Error while copying icons", throwable)
    }
  }
}

/**
 * Single-threaded queued executor to run tasks on a background thread.
 */
private fun createBackgroundExecutor() = ThreadPoolExecutor(
  0,
  1,
  1,
  TimeUnit.MINUTES,
  LinkedBlockingQueue<Runnable>(),
  ThreadFactoryBuilder().setNameFormat(
    "${MaterialVdIconsProvider::class.java.simpleName}-backgroundMaterialIconsTasks-%d"
  ).build()
)

/**
 * @see [MaterialIconsMetadata.parse]
 * @return The [MaterialIconsMetadata] parsed from the URL provided.
 */
private fun getMetadata(urlProvider: MaterialIconsMetadataUrlProvider): MaterialIconsMetadata? {
  val url = urlProvider.getMetadataUrl() ?: return null
  try {
    return MaterialIconsMetadata.parse(BufferedReader(InputStreamReader(url.openStream())))
  }
  catch (e: Exception) {
    LOG.error("Error obtaining metadata file", e)
    return null
  }
}
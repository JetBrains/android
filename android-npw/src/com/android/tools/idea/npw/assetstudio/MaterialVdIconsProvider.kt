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
import com.android.tools.idea.npw.assetstudio.MaterialVdIconsProvider.Status
import com.android.tools.idea.npw.assetstudio.material.icons.MaterialIconsCopyHandler
import com.android.tools.idea.npw.assetstudio.material.icons.MaterialVdIcons
import com.android.tools.idea.npw.assetstudio.material.icons.MaterialVdIconsLoader
import com.android.tools.idea.npw.assetstudio.material.icons.common.BundledIconsUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.BundledMetadataUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.MaterialIconsMetadataUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.SdkMaterialIconsUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.SdkMetadataUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.download.updateIconsAtDir
import com.android.tools.idea.npw.assetstudio.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.npw.assetstudio.material.icons.metadata.MaterialIconsMetadataDownloadCacheService
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.getIconsSdkTargetPath
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.getMetadata
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.hasMetadataFileInSdkPath
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
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
     * @param parentDisposable When disposed, the background thread used for loading/copying/downloading icons is shutdown.
     */
    @JvmStatic
    fun loadMaterialVdIcons(refreshUiCallback: (MaterialVdIcons, Status) -> Unit,
                            metadataUrlProvider: MaterialIconsMetadataUrlProvider?,
                            iconsUrlProvider: MaterialIconsUrlProvider?,
                            parentDisposable: Disposable) {
      val metadataUrl = (metadataUrlProvider ?: getMetadataUrlProvider()).getMetadataUrl()
      val metadata = metadataUrl?.let { getMetadata(it) }
      when {
        metadata == null -> {
          LOG.warn("No metadata for material icons.")
          refreshUiCallback(MaterialVdIcons.EMPTY, Status.FINISHED)
        }
        metadata === MaterialIconsMetadata.EMPTY || metadata.families.isEmpty() -> {
          LOG.warn("Empty metadata for material icons.")
          refreshUiCallback(MaterialVdIcons.EMPTY, Status.FINISHED)
        }
        else -> {
          loadMaterialVdIcons(
            metadata, iconsUrlProvider ?: getIconsUrlProvider(), refreshUiCallback, parentDisposable)
        }
      }
    }
  }
}

private fun loadMaterialVdIcons(metadata: MaterialIconsMetadata,
                                iconsUrlProvider: MaterialIconsUrlProvider,
                                refreshUiCallback: (MaterialVdIcons, Status) -> Unit,
                                parentDisposable: Disposable) {
  val iconsLoader = MaterialVdIconsLoader(metadata, iconsUrlProvider)
  val progressIndicator = EmptyProgressIndicator()
  val backgroundExecutor = createBackgroundExecutor()
  val disposable = Disposable {
    progressIndicator.cancel()
    backgroundExecutor.shutdown()

    // There might an IO process pending, wait for them to finish and release resources
    backgroundExecutor.awaitTermination(2L, TimeUnit.SECONDS)
  }
  Disposer.register(parentDisposable, disposable)
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

        if (status == Status.FINISHED) {
          if (StudioFlags.ASSET_COPY_MATERIAL_ICONS.get()) {
            // When finished loading, copy icons to the Android/Sdk directory.
            copyBundledIcons(metadata, icons, backgroundExecutor, progressIndicator)
          }
          if (StudioFlags.ASSET_DOWNLOAD_MATERIAL_ICONS.get()) {
            // Then, download the most recent metadata file and any new icons.
            updateMetadataAndIcons(metadata, backgroundExecutor, progressIndicator)
          }
        }
      }
    }, EdtExecutorService.getScheduledExecutorInstance())
  }
}


private fun getMetadataUrlProvider(): MaterialIconsMetadataUrlProvider {
  return if (hasMetadataFileInSdkPath()) {
    SdkMetadataUrlProvider()
  }
  else {
    BundledMetadataUrlProvider()
  }
}

private fun getIconsUrlProvider(): MaterialIconsUrlProvider {
  return if (hasMetadataFileInSdkPath()) {
    SdkMaterialIconsUrlProvider()
  }
  else {
    BundledIconsUrlProvider()
  }
}

private fun copyBundledIcons(
  metadata: MaterialIconsMetadata,
  icons: MaterialVdIcons,
  executor: ExecutorService,
  progressIndicator: ProgressIndicator
) {
  val targetPath = getIconsSdkTargetPath()
  if (targetPath == null) {
    LOG.warn("No Android Sdk folder, can't copy material icons.")
    return
  }
  CompletableFuture.supplyAsync(Supplier {
    ProgressManager.getInstance().runProcess(
      { MaterialIconsCopyHandler(metadata, icons).copyTo(targetPath) },
      progressIndicator
    )
  }, executor).whenComplete { _, throwable ->
    if (throwable != null) {
      LOG.error("Error while copying icons", throwable)
    }
  }
}

private fun updateMetadataAndIcons(
  existingMetadata: MaterialIconsMetadata,
  executor: ExecutorService,
  progressIndicator: ProgressIndicator
) {
  val targetPath = getIconsSdkTargetPath()
  if (targetPath == null) {
    LOG.warn("No Android Sdk folder, can't download any material icons.")
    return
  }
  ApplicationManager.getApplication().getService(MaterialIconsMetadataDownloadCacheService::class.java).getMetadata().whenCompleteAsync(
    { newMetadata, _ ->
      ProgressManager.getInstance().runProcess(
        { updateIconsAtDir(existingMetadata, newMetadata, targetPath.toPath()) },
        progressIndicator
      )
    },
    executor
  )
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
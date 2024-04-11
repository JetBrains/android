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
package com.android.tools.idea

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.MaterialVdIconsProvider.Status
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.material.icons.MaterialIconsCopyHandler
import com.android.tools.idea.material.icons.MaterialVdIcons
import com.android.tools.idea.material.icons.MaterialVdIconsLoader
import com.android.tools.idea.material.icons.common.BundledIconsUrlProvider
import com.android.tools.idea.material.icons.common.BundledMetadataUrlProvider
import com.android.tools.idea.material.icons.common.MaterialIconsMetadataUrlProvider
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.common.SdkMaterialIconsUrlProvider
import com.android.tools.idea.material.icons.common.SdkMetadataUrlProvider
import com.android.tools.idea.material.icons.download.updateIconsAtDir
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadataDownloadCacheService
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.getIconsSdkTargetPath
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.getMetadata
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.hasMetadataFileInSdkPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.progress.getCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    @JvmStatic
      /**
       * Gets [MaterialIconsMetadata] and handles calls to [MaterialVdIconsLoader]. Invokes the given ui-callback when more icons are loaded.
       *
       * @param refreshUiCallback Called whenever more icons are loaded, with the updated [MaterialVdIcons] object and a [Status] to indicate
       *  whether to expect more calls with more icons.
       * @param parentDisposable When disposed, the background thread used for loading/copying/downloading icons is shutdown.
       * @param metadataUrlProvider Url provider for the metadata file.
       * @param iconsUrlProvider Url provider for [MaterialVdIconsLoader].
       * @param onNewIconsAvailable this method might trigger a metadata update even after the local icons have been loaded. After the download
       *  finishes, this method will be called if the metadata does not match the local copy and a UI update is needed.
       */
    fun loadMaterialVdIcons(refreshUiCallback: @UiThread (MaterialVdIcons, Status) -> Unit,
                            parentDisposable: Disposable,
                            metadataUrlProvider: MaterialIconsMetadataUrlProvider? = null,
                            iconsUrlProvider: MaterialIconsUrlProvider? = null,
                            onNewIconsAvailable: @UiThread () -> Unit = {}) {
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
            metadata, iconsUrlProvider ?: getIconsUrlProvider(), refreshUiCallback, onNewIconsAvailable, parentDisposable)
        }
      }
    }
  }
}

private fun loadMaterialVdIcons(metadata: MaterialIconsMetadata,
                                iconsUrlProvider: MaterialIconsUrlProvider,
                                refreshUiCallback: @UiThread (MaterialVdIcons, Status) -> Unit,
                                onNewIconsAvailable: @UiThread () -> Unit,
                                parentDisposable: Disposable) {
  val iconsLoader = MaterialVdIconsLoader(metadata, iconsUrlProvider)
  val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "${MaterialVdIconsProvider::class.java.simpleName}-backgroundMaterialIconsTasks",
    AppExecutorUtil.getAppExecutorService(),
    1,
    parentDisposable)
  AndroidCoroutineScope(parentDisposable).launch {
    var icons = MaterialVdIcons.EMPTY
    metadata.families.forEachIndexed { index, style ->
      val status = if (index == metadata.families.lastIndex) Status.FINISHED else Status.LOADING

      // Load icons in a background thread.
      @Suppress("UnstableApiUsage")
      blockingContextScope {
        backgroundExecutor.submit {
          try {
            LOG.debug("Loading icons for style=$style.")
            icons = iconsLoader.loadMaterialVdIcons(style)
            if (icons.styles.isEmpty()) {
              LOG.warn("No icons loaded for style=$style.")
            }
          }
          catch (_: ProcessCanceledException) {}
          catch (t: Throwable) {
            LOG.error("Error loading icons.", t)
          }
        }
      }

      // Invoke the ui-callback with the loaded icons and current status value.
      withContext(uiThread(ModalityState.any())) {
        refreshUiCallback(icons, status)
      }
    }

    var iconsUpdated = false
    @Suppress("UnstableApiUsage")
    blockingContextScope {
      backgroundExecutor.submit {
        try {
          // When finished loading, copy icons to the Android/Sdk directory.
          copyBundledIcons(metadata, icons)

          // Then, download the most recent metadata file and any new icons.
          iconsUpdated = updateMetadataAndIcons(metadata)
        }
        catch (_: ProcessCanceledException) {}
        catch (t: Throwable) {
          LOG.error( "Error updating icons.", t)
        }
      }
    }
    if (iconsUpdated) {
      withContext(uiThread(ModalityState.any())) {
        onNewIconsAvailable()
      }
    }
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

@WorkerThread
private fun copyBundledIcons(
  metadata: MaterialIconsMetadata,
  icons: MaterialVdIcons,
) {
  val targetPath = getIconsSdkTargetPath()
  if (targetPath == null) {
    LOG.warn("No Android Sdk folder, can't copy material icons.")
    return
  }
  MaterialIconsCopyHandler(metadata, icons).copyTo(targetPath)
}

/**
 * Returns true if any icons were updated.
 */
@WorkerThread
private fun updateMetadataAndIcons(
  existingMetadata: MaterialIconsMetadata,
): Boolean {
  val targetPath = getIconsSdkTargetPath()
  if (targetPath == null) {
    LOG.warn("No Android Sdk folder, can't download any material icons.")
    return false
  }
  val newMetadata = ApplicationManager.getApplication().getService(MaterialIconsMetadataDownloadCacheService::class.java)
    .getMetadata()
    .getCancellable()
  return updateIconsAtDir(existingMetadata, newMetadata, targetPath.toPath())
}

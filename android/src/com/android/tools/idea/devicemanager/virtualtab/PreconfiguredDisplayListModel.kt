/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab

import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.repository.impl.meta.RepositoryPackages
import com.android.sdklib.devices.Device
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.progress.StudioProgressRunner
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.util.ui.ListTableModel


fun getDevice(deviceName: String): Device =
  DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.first { it.displayName == deviceName }

// TODO(qumeric): consider making it a separate class or integrating into some existing class
private fun List<SystemImageDescription>.toPreconfiguredDevices(): List<PreconfiguredDeviceDefinition> {
  fun pixel4withPlayStoreApi29Filter(s: SystemImageDescription) = with(s) {
    version.apiLevel == 29 &&
    abiType == "x86" &&
    systemImage.hasPlayStore()
  }

  val localImages = this.filter { !it.isRemote }

  // TODO(b/157699623): provide more images
  val pixel4latestWithPlayStore = localImages.find(::pixel4withPlayStoreApi29Filter) ?: this.find(::pixel4withPlayStoreApi29Filter)

  // runs twice: first time with only local images, second time with local and remote images
  return listOf(
    // TODO(qumeric): we should try to estimate better than hardcoding
    PreconfiguredDeviceDefinition(getDevice("Pixel 4"), pixel4latestWithPlayStore, 9300),
    PreconfiguredDeviceDefinition(getDevice("Pixel 3"), this.last(), 9300), // FIXME: a test for remote image download
    PreconfiguredDeviceDefinition(getDevice("Pixel 3a"), null, 9300) // FIXME: a test for null value (e.g. no internet)
  )
}

// TODO(qumeric): there is a lot of redundant stuff here like columnInfos. It should be removed
/**
 * A table model for a [PreconfiguredDisplayList]. Heavily influenced by [com.android.tools.idea.avdmanager.SystemImageListModel]
 */
class PreconfiguredDisplayListModel(
  private val project: Project?
) : ListTableModel<PreconfiguredDeviceDefinition>() {
  private val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

  var isUpdating = false
    private set

  init {
    isSortable = true
  }

  override fun setItems(items: List<PreconfiguredDeviceDefinition>) {
    isUpdating = true
    super.setItems(items)
    isUpdating = false
  }

  private fun setItemsFromImages(items: List<SystemImageDescription>) = setItems(items.toPreconfiguredDevices())

  fun refreshImages(forceRefresh: Boolean) {
    val items = mutableListOf<SystemImageDescription>()
    val localComplete = RepoLoadedListener {
      invokeLater(ModalityState.any()) {
        // getLocalImages() doesn't use SdkPackages, so it's ok that we're not using what's passed in.
        items.addAll(localImages)
        // Update list in the UI immediately with the locally available system images
        setItemsFromImages(items)
      }
    }
    val remoteComplete = RepoLoadedListener { packages: RepositoryPackages ->
      invokeLater(ModalityState.any()) {
        val remotes = getRemoteImages(packages)
        if (remotes != null) {
          items.addAll(remotes)
          setItemsFromImages(items)
        }
      }
    }
    val error = Runnable {
      invokeLater(ModalityState.any()) {
        // TODO(qumeric): show information that we were not able to fetch remote system images. Provide an opportunity to retry.
      }
    }
    val runner = StudioProgressRunner(false, false, "Loading Images", project)
    sdkHandler.getSdkManager(LOGGER).load(
      if (forceRefresh) 0 else RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      listOf(localComplete), listOf(remoteComplete), listOf(error),
      runner, StudioDownloader(), StudioSettingsController.getInstance())
  }

  private val localImages: List<SystemImageDescription>
    get() = sdkHandler.getSystemImageManager(LOGGER).images.map { SystemImageDescription(it) }

  companion object {
    private val LOGGER: ProgressIndicator = StudioLoggerProgressIndicator(PreconfiguredDisplayListModel::class.java)

    private fun getRemoteImages(packages: RepositoryPackages): List<SystemImageDescription>? {
      if (packages.newPkgs.isEmpty()) {
        return null
      }

      return packages.newPkgs
        .filter { SystemImageDescription.hasSystemImage(it) }
        .map { SystemImageDescription(it) }
    }
  }
}

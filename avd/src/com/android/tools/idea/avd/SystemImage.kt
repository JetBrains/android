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
package com.android.tools.idea.avd

import androidx.compose.runtime.Immutable
import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoPackage
import com.android.repository.api.UpdatablePackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Storage
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.google.common.annotations.VisibleForTesting
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Immutable
internal data class SystemImage
@VisibleForTesting
internal constructor(
  internal val isRemote: Boolean,
  internal val path: String,
  internal val name: String,
  internal val androidVersion: AndroidVersion,
  internal val services: Services,
  internal val abis: ImmutableCollection<Abi>,
  internal val translatedAbis: ImmutableCollection<Abi>,
  internal val tags: ImmutableList<IdDisplay>,
  private val size: Storage,
) {
  internal fun matches(device: Device): Boolean {
    // TODO(b/326294450) - Try doing this in device and system image declarations
    if (!Device.isTablet(device)) {
      if (tags.contains(SystemImageTags.TABLET_TAG)) {
        return false
      }
    }

    // Unknown/generic device?
    if (device.tagId == null || device.tagId == SystemImageTags.DEFAULT_TAG.id) {
      // If so include all system images, except those we *know* not to match this type
      // of device. Rather than just checking
      // "imageTag.getId().equals(SystemImage.DEFAULT_TAG.getId())"
      // here (which will filter out system images with a non-default tag, such as the Google API
      // system images (see issue #78947), we instead deliberately skip the other form factor images
      return !SystemImageTags.isTvImage(tags) &&
        !SystemImageTags.isWearImage(tags) &&
        !SystemImageTags.isAutomotiveImage(tags) &&
        !SystemImageTags.isDesktopImage(tags) &&
        !tags.contains(SystemImageTags.CHROMEOS_TAG)
    }

    // Android TV / Google TV and vice versa
    if (
      device.tagId == SystemImageTags.ANDROID_TV_TAG.id ||
        device.tagId == SystemImageTags.GOOGLE_TV_TAG.id
    ) {
      return SystemImageTags.isTvImage(tags)
    }

    // The device has a tag; the system image must have a corresponding tag
    return tags.any { it.id == device.tagId }
  }

  internal fun matches(device: VirtualDevice): Boolean {
    // TODO: http://b/347053479
    if (androidVersion.isPreview) {
      return false
    }

    if (androidVersion.apiLevel != device.androidVersion.apiLevel) {
      return false
    }

    return matches(device.device)
  }

  override fun toString() = if (isRemote) "$name (${size.toUiString()})" else name

  internal companion object {
    internal fun getSystemImages(): Collection<SystemImage> {
      val indicator = StudioLoggerProgressIndicator(SystemImage::class.java)
      val manager = AndroidSdks.getInstance().tryToChooseSdkHandler().getSdkManager(indicator)

      manager.loadSynchronously(
        0,
        indicator,
        StudioDownloader(),
        StudioSettingsController.getInstance(),
      )

      return manager.packages.consolidatedPkgs.values
        .map(UpdatablePackage::getRepresentative)
        .filter(RepoPackage::hasSystemImage)
        .map(::from)
        .toList()
    }

    @VisibleForTesting
    internal fun from(repoPackage: RepoPackage): SystemImage {
      val isRemote: Boolean
      val size: Long

      when (repoPackage) {
        is RemotePackage -> {
          isRemote = true
          size = requireNotNull(repoPackage.archive).complete.size
        }
        is LocalPackage -> {
          isRemote = false
          size = 0
        }
        else -> throw IllegalArgumentException(repoPackage.toString())
      }

      val details = repoPackage.typeDetails as ApiDetailsType

      return SystemImage(
        isRemote,
        repoPackage.path,
        repoPackage.displayName,
        details.androidVersion,
        repoPackage.getServices(details.androidVersion),
        details.abis.map(::valueOfString).toImmutableSet(),
        details.translatedAbis.map(::valueOfString).toImmutableSet(),
        SystemImageTags.getTags(repoPackage).toImmutableList(),
        Storage(size),
      )
    }

    private fun RepoPackage.getServices(androidVersion: AndroidVersion): Services {
      val tags = SystemImageTags.getTags(this)

      if (SystemImageTags.hasGooglePlay(tags, androidVersion, this)) {
        return Services.GOOGLE_PLAY_STORE
      }

      if (SystemImageTags.hasGoogleApi(tags)) {
        return Services.GOOGLE_APIS
      }

      return Services.ANDROID_OPEN_SOURCE
    }

    private fun valueOfString(string: String) =
      requireNotNull(Abi.values().firstOrNull { it.toString() == string })
  }
}

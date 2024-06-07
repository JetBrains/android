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
import com.android.sdklib.devices.Storage
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.google.common.annotations.VisibleForTesting
import kotlinx.collections.immutable.ImmutableCollection
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
  private val size: Storage,
) {
  internal fun matches(device: VirtualDevice): Boolean {
    if (androidVersion.apiLevel != device.apiRange.upperEndpoint()) {
      return false
    }

    if (
      androidVersion.featureLevel < AndroidVersion.MIN_EMULATOR_FOLDABLE_DEVICE_API &&
        device.isFoldable
    ) {
      return false
    }

    return true
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

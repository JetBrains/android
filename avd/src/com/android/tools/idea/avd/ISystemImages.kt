/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageSupplier
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.utils.CpuArchitecture
import com.android.utils.osArchitecture
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal data class SystemImageState(
  val hasLocal: Boolean,
  val hasRemote: Boolean,
  val images: ImmutableList<ISystemImage>,
  val error: String? = null,
) {
  companion object {
    val INITIAL = SystemImageState(false, false, persistentListOf(), null)
  }
}

internal object ISystemImages {
  fun systemImageFlow(sdkHandler: AndroidSdkHandler): Flow<SystemImageState> {
    val indicator = StudioLoggerProgressIndicator(ISystemImages::class.java)
    val repoManager = sdkHandler.getRepoManager(indicator)

    fun systemImages(): ImmutableList<ISystemImage> {
      // The SystemImageManager gets destroyed and recreated every time the packages change; do
      // not cache it.
      return SystemImageSupplier(
          repoManager,
          sdkHandler.getSystemImageManager(indicator),
          LogWrapper(ISystemImages.thisLogger<ISystemImages>()),
        )
        .get()
        .toImmutableList()
    }

    // Load local and remote packages and emit the corresponding SystemImageState.
    return flow<SystemImageState> {
        coroutineScope {
          val localPackages = async { repoManager.loadLocalPackages(indicator, 1.days) }
          val remotePackages = async {
            repoManager.loadRemotePackages(
              indicator,
              1.days,
              StudioDownloader(),
              StudioSettingsController.getInstance(),
            )
          }

          val state =
            try {
              localPackages.await()
              SystemImageState.INITIAL.copy(hasLocal = true, images = systemImages())
            } catch (e: Exception) {
              thisLogger().warn("Loading local images", e)
              SystemImageState.INITIAL.copy(error = "Error loading images.")
            }
          emit(state)
          try {
            remotePackages.await()
            emit(state.copy(hasRemote = true, images = systemImages()))
          } catch (e: Exception) {
            thisLogger().warn("Loading remote images", e)
            emit(state.copy(error = "Error loading remote images."))
          }
        }
      }
      .flowOn(Dispatchers.IO)
  }
}

internal fun ISystemImage.getServices(): Services {
  if (hasPlayStore()) {
    return Services.GOOGLE_PLAY_STORE
  }

  if (hasGoogleApis()) {
    return Services.GOOGLE_APIS
  }

  return Services.ANDROID_OPEN_SOURCE
}

internal fun ISystemImage.isSupported(): Boolean = imageWarnings().isEmpty()

private fun ISystemImage.incompatibleArchitectureWarning(): String? =
  when (osArchitecture) {
    CpuArchitecture.X86_64 ->
      when (Abi.getEnum(primaryAbiType)) {
        in listOf(Abi.X86_64, Abi.X86) -> null
        in listOf(Abi.ARMEABI, Abi.ARMEABI_V7A, Abi.ARM64_V8A) ->
          "ARM images will run very slowly on x86 hosts."
        else -> "Compatibility with $primaryAbiType images is unknown."
      }
    // An ARM host can only run ARM64 images (not 32-bit ARM).
    CpuArchitecture.X86_ON_ARM,
    CpuArchitecture.ARM ->
      when (Abi.getEnum(primaryAbiType)) {
        Abi.ARM64_V8A -> null
        in listOf(Abi.ARMEABI, Abi.ARMEABI_V7A) ->
          if (SystemInfo.isMac) "32-bit ARM images are not supported on Apple Silicon."
          else "Compatibility with $primaryAbiType images is unknown."
        in listOf(Abi.X86_64, Abi.X86) -> "$primaryAbiType images are not supported on ARM hosts."
        else -> "Compatibility with $primaryAbiType images is unknown."
      }
    // We don't support 32-bit x86 hosts.
    else -> "The Android Emulator requires a 64-bit host."
  }

private fun ISystemImage.atdWarning(): String? =
  "Automated Test Device (ATD) images are intended for headless testing only."
    .takeIf { SystemImageTags.isAtd(tags) }

internal fun ISystemImage.imageWarnings(): List<String> =
  listOfNotNull(incompatibleArchitectureWarning(), atdWarning())

internal fun ISystemImage?.allAbiTypes(): PersistentList<String> =
  if (this == null) persistentListOf() else abiTypes.toPersistentList().plus(translatedAbiTypes)

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

import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageSupplier
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.utils.CpuArchitecture
import com.android.utils.osArchitecture
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform

private sealed class SystemImageLoadingEvent

private object LocalImagesLoaded : SystemImageLoadingEvent()

private object RemoteImagesLoaded : SystemImageLoadingEvent()

private object Error : SystemImageLoadingEvent()

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
  fun systemImageFlow(sdkHandler: AndroidSdkHandler, project: Project?): Flow<SystemImageState> {
    val indicator = StudioLoggerProgressIndicator(ISystemImages::class.java)
    val repoManager = sdkHandler.getSdkManager(indicator)

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
    var state = SystemImageState.INITIAL

    // Transform callbacks from RepoManager to SystemImageLoadingEvents that are processed serially.
    return callbackFlow {
        repoManager.loadSynchronously(
          1.days.inWholeMilliseconds,
          listOf(RepoLoadedListener { trySend(LocalImagesLoaded) }),
          listOf(RepoLoadedListener { trySend(RemoteImagesLoaded) }),
          listOf(Runnable { trySend(Error) }),
          StudioProgressRunner(false, false, "Loading Images", project),
          StudioDownloader(),
          StudioSettingsController.getInstance(),
        )

        // At this point, we've sent local and remote images (or an error). Now just listen for
        // package downloads.
        val listener = RepoLoadedListener { trySend(LocalImagesLoaded) }
        repoManager.addLocalChangeListener(listener)
        awaitClose { repoManager.removeLocalChangeListener(listener) }
      }
      .transform { event ->
        state =
          when (event) {
            is LocalImagesLoaded -> state.copy(hasLocal = true, images = systemImages())
            is RemoteImagesLoaded -> state.copy(hasRemote = true, images = systemImages())
            is Error -> state.copy(error = "Error loading remote images.")
          }
        emit(state)
      }
      .flowOn(AndroidDispatchers.workerThread)
      .conflate()
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

internal fun ISystemImage.isRecommendedForHost(): Boolean =
  when (osArchitecture) {
    CpuArchitecture.X86_64 -> Abi.getEnum(primaryAbiType) in listOf(Abi.X86_64, Abi.X86)
    // An ARM host can only run ARM64 images (not 32-bit ARM).
    CpuArchitecture.X86_ON_ARM,
    CpuArchitecture.ARM -> Abi.getEnum(primaryAbiType) == Abi.ARM64_V8A
    // We don't support 32-bit x86 hosts.
    else -> false
  }

internal fun ISystemImage.isRecommended(): Boolean =
  isRecommendedForHost() && !SystemImageTags.isAtd(tags)

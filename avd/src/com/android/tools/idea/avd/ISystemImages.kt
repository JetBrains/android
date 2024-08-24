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

import com.android.repository.api.RepoManager
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageSupplier
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

internal object ISystemImages {
  fun systemImageFlow(sdkHandler: AndroidSdkHandler): Flow<ImmutableList<ISystemImage>> =
    callbackFlow {
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

        val listener =
          RepoManager.RepoLoadedListener {
            // Due to conflate() below, this should always succeed
            trySend(systemImages())
          }
        repoManager.addLocalChangeListener(listener)
        repoManager.addRemoteChangeListener(listener)
        send(systemImages())

        awaitClose {
          repoManager.removeLocalChangeListener(listener)
          repoManager.removeRemoteChangeListener(listener)
        }
      }
      .conflate()

  internal fun get(): ImmutableCollection<ISystemImage> {
    val handler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val indicator = StudioLoggerProgressIndicator(ISystemImages::class.java)
    val repoManager = handler.getSdkManager(indicator)

    repoManager.loadSynchronously(
      0,
      indicator,
      StudioDownloader(),
      StudioSettingsController.getInstance(),
    )

    val systemImageManager = handler.getSystemImageManager(indicator)
    val logger = LogWrapper(ISystemImages.thisLogger())

    return SystemImageSupplier(repoManager, systemImageManager, logger).get().toImmutableList()
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

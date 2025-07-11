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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.repository.targets.SystemImage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class ISystemImagesTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun systemImageFlow_empty() {
    with(SdkFixture()) {
      val imageFlow = ISystemImages.systemImageFlow(sdkHandler)
      runBlocking {
        withTimeout(10.seconds) {
          val state = imageFlow.first { it.hasLocal && it.hasRemote }
          assertThat(state.images).isEmpty()
        }
      }
    }
  }

  @Test
  fun systemImageFlow_local() {
    with(SdkFixture()) {
      repoPackages.setLocalPkgInfos(
        listOf(createLocalSystemImage("google_apis", listOf(), AndroidVersion(34)))
      )

      val imageFlow = ISystemImages.systemImageFlow(sdkHandler)
      runBlocking {
        withTimeout(10.seconds) {
          val state = imageFlow.first { it.hasLocal && it.hasRemote }
          assertThat(state.images).hasSize(1)
        }
      }
    }
  }

  @Test
  fun systemImageFlow_download() {
    with(SdkFixture()) {
      repoPackages.setRemotePkgInfos(
        listOf(createRemoteSystemImage("google_apis", listOf(), AndroidVersion(34)))
      )

      val flowScope = CoroutineScope(EmptyCoroutineContext)
      val imageFlow =
        ISystemImages.systemImageFlow(sdkHandler)
          .stateIn(flowScope, SharingStarted.Eagerly, SystemImageState.INITIAL)

      runBlocking {
        withTimeout(5.seconds) {
          // Initially we should see a remote system image
          val state = imageFlow.first { it.hasLocal && it.hasRemote }
          assertThat(state.images).hasSize(1)
          assertThat(state.images[0]).isInstanceOf(RemoteSystemImage::class.java)
        }

        // Simulate download of the system image
        repoManager.updateLocalPackages(
          listOf(createLocalSystemImage("google_apis", listOf(), AndroidVersion(34)))
        )

        withTimeout(5.seconds) {
          // Now the flow should update and it should be represented as a local system image
          val state = imageFlow.first { it.images.any { it is SystemImage } }
          assertThat(state.images).hasSize(1)
          assertThat(state.hasLocal).isTrue()
          assertThat(state.hasRemote).isTrue()
        }
      }
      flowScope.cancel()
    }
  }
}

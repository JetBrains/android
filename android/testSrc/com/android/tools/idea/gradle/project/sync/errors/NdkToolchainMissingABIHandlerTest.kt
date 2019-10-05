/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class NdkToolchainMissingABIHandlerTest : AndroidGradleTestCase() {
  private var syncMessagesStub: GradleSyncMessagesStub? = null
  private var expectedError = "This version of the NDK may be incompatible with the Android Gradle plugin version 3.0 or older.\n" +
                              "Please use plugin version 3.1 or newer."

  @Before
  override fun setUp() {
    super.setUp()
    syncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(project)
  }

  @Test
  fun testHandlerWithOldGradle32bits() {
    verifyOld("mipsel-linux-android");
  }

  @Test
  fun testHandlerWithOldGradle64bits() {
    verifyOld("mips64el-linux-android");
  }

  @Test
  fun testHandleWithNewGradle32bit() {
    verifyNew("mipsel-linux-android");
  }

  @Test
  fun testHandleWithNewGradle64bit() {
    verifyNew("mips64el-linux-android");
  }

  @Test
  fun testIsArtifactVersionOver3dot0() {
    assertThat(NdkToolchainMissingABIHandler.isVersionOver3dot0("4.1")).isTrue();
    assertThat(NdkToolchainMissingABIHandler.isVersionOver3dot0("4.0")).isTrue();
    assertThat(NdkToolchainMissingABIHandler.isVersionOver3dot0("3.1")).isTrue();
    assertThat(NdkToolchainMissingABIHandler.isVersionOver3dot0("3.0")).isFalse();
    assertThat(NdkToolchainMissingABIHandler.isVersionOver3dot0("2.1")).isFalse();
    assertThat(NdkToolchainMissingABIHandler.isVersionOver3dot0("2.0")).isFalse();
  }

  private fun verifyOld(ABI: String) {
    val errMsg = "No toolchains found in the NDK toolchains folder for ABI with prefix: $ABI"
    registerSyncErrorToSimulate(errMsg)

    prepareProjectForImport(SIMPLE_APPLICATION_PRE30)
    AndroidGradleTests.updateGradleVersions(projectFolderPath, "3.0.0")
    requestSyncAndGetExpectedFailure()

    val notificationUpdate = syncMessagesStub!!.notificationUpdate
    assertNotNull(notificationUpdate)
    assertThat(notificationUpdate!!.text).isEqualTo("$errMsg\n$expectedError")

    // Verify hyperlinks are correct.
    val quickFixes = notificationUpdate.fixes
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionHyperlink::class.java)
  }

  private fun verifyNew(ABI: String) {
    val errMsg = "No toolchains found in the NDK toolchains folder for ABI with prefix: $ABI"
    registerSyncErrorToSimulate(errMsg)

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION)

    val notificationUpdate = syncMessagesStub!!.notificationUpdate
    assertNull(notificationUpdate)
  }
}

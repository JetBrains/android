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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.repository.api.RepoManager
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.File

class SdkSyncUtilTest : AndroidGradleTestCase() {
  private val androidSdks = mock(AndroidSdks::class.java)
  private val sdk = mock(Sdk::class.java)
  private val ideSdks = mock(IdeSdks::class.java)

  override fun setUp() {
    super.setUp()
    loadSimpleApplication()

    `when`(sdk.name).thenReturn("WantedSdkName")
    `when`(ideSdks.androidSdkPath).thenReturn(File("some/sdk/path/for/test"))
  }

  private fun <T> any(): T {
    return Mockito.any()
  }

  @Test
  fun testComputeSdkRepoReloads() {
    val repoManager = mock(RepoManager::class.java)
    val sdkHandler = AndroidSdkHandler(null, null, MockFileOp(), repoManager)
    `when`(androidSdks.findSuitableAndroidSdk(eq("WantedCompileTarget"))).thenReturn(null)
    `when`(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    `when`(androidSdks.tryToCreate(any(), any())).thenAnswer {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      sdk
    }

    assertEquals(sdk, androidSdks.computeSdkReloadingAsNeeded(
      project,
      "ModuleName",
      "WatchedCompileTarget",
      listOf(),
      ideSdks
    ))

    verify(repoManager).reloadLocalIfNeeded(any())
  }
}
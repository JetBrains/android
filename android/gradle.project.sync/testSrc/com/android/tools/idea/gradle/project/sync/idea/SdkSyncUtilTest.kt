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
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.RootProvider
import com.intellij.testFramework.replaceService
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class SdkSyncUtilTest : AndroidGradleTestCase() {
  private val androidSdks = mock<AndroidSdks>()
  private val sdk = mock<Sdk>()
  private val ideSdks = mock<IdeSdks>()

  override fun setUp() {
    super.setUp()
    loadSimpleApplication()

    whenever(sdk.name).thenReturn("WantedSdkName")
    whenever(ideSdks.androidSdkPath).thenReturn(File("some/sdk/path/for/test"))
  }

  @Test
  fun testComputeSdkRepoReloads() {
    val repoManager = mock<RepoManager>()
    val sdkHandler = AndroidSdkHandler(null, null, repoManager)
    whenever(androidSdks.findSuitableAndroidSdk("WantedCompileTarget")).thenReturn(null)
    whenever(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    whenever(androidSdks.tryToCreate(any(), any())).thenAnswer {
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

  @Test
  fun testComputeSdkRepoReloadsNoRoots() {
    val compileTarget = "WantedCompileTarget"
    val repoManager = mock<RepoManager>()
    val sdkHandler = AndroidSdkHandler(null, null, repoManager)
    whenever(androidSdks.findSuitableAndroidSdk(eq(compileTarget))).thenReturn(sdk)
    val rootProvider = mock<RootProvider>()
    whenever(rootProvider.getFiles(any())).thenReturn(arrayOf())
    whenever(sdk.rootProvider).thenReturn(rootProvider)
    whenever(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    whenever(androidSdks.tryToCreate(any(), any())).thenAnswer {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      sdk
    }

    val mockTable = spy(ProjectJdkTable.getInstance())
    doNothing().whenever(mockTable).removeJdk(any())
    ApplicationManager.getApplication().replaceService(ProjectJdkTable::class.java, mockTable, project)

    assertEquals(sdk, androidSdks.computeSdkReloadingAsNeeded(
      project,
      "ModuleName",
      compileTarget,
      listOf(),
      ideSdks
    ))

    verify(mockTable).removeJdk(eq(sdk))
    verify(repoManager).reloadLocalIfNeeded(any())
  }

  @Test
  fun testInvalidSdkSetup() {
    val repoManager = mock<RepoManager>()
    val sdkHandler = AndroidSdkHandler(null, null, repoManager)
    whenever(androidSdks.findSuitableAndroidSdk(eq("WantedCompileTarget"))).thenReturn(null)
    whenever(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    whenever(androidSdks.tryToCreate(any(), any())).thenAnswer { null }

    assertEquals(null, androidSdks.computeSdkReloadingAsNeeded(
      project,
      "ModuleName",
      "WatchedCompileTarget",
      listOf("android.jar", "foo.jar"), // need bootclasspath with at least 2 elements
      ideSdks
    ))
  }
}
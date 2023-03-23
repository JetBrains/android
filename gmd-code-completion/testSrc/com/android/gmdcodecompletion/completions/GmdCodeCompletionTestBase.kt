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
package com.android.gmdcodecompletion.completions

import com.android.gmdcodecompletion.BuildFileName
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogService
import com.android.gmdcodecompletion.fullManagedVirtualDeviceCatalogState
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogService
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogState
import com.android.testutils.MockitoKt
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase

abstract class GmdCodeCompletionTestBase : AndroidTestCase() {

  override fun tearDown() {
    ApplicationManager.getApplication().runWriteAction {
      val table = ProjectJdkTable.getInstance()
      table.allJdks.forEach {
        table.removeJdk(it)
      }
    }
    super.tearDown()
  }

  protected fun createFakeFtlDeviceCatalogService(): FtlDeviceCatalogService {
    val mockFtlDeviceCatalogService = MockitoKt.mock<FtlDeviceCatalogService>()
    ApplicationManager.getApplication().replaceService(
      FtlDeviceCatalogService::class.java,
      mockFtlDeviceCatalogService,
      myFixture.testRootDisposable
    )
    return mockFtlDeviceCatalogService
  }

  protected fun createFakeManagedVirtualDeviceCatalogService(): ManagedVirtualDeviceCatalogService {
    val mockManagedVirtualDeviceCatalogService = MockitoKt.mock<ManagedVirtualDeviceCatalogService>()
    ApplicationManager.getApplication().replaceService(
      ManagedVirtualDeviceCatalogService::class.java,
      mockManagedVirtualDeviceCatalogService,
      myFixture.testRootDisposable
    )
    return mockManagedVirtualDeviceCatalogService
  }

  protected fun createFakeGradleModelProvider(): GradleModelProvider {
    val mockGradleModelProvider = MockitoKt.mock<GradleModelProvider>()
    ApplicationManager.getApplication().replaceService(
      GradleModelProvider::class.java,
      mockGradleModelProvider,
      myFixture.testRootDisposable
    )
    return mockGradleModelProvider
  }

  protected fun gmdCodeCompletionContributorTestHelper(buildFileName: String, buildFileContent: String, callback: () -> Unit) {
    val buildFile = myFixture.addFileToProject("app/$buildFileName", buildFileContent)
    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()
    callback()

  }

  protected fun managedVirtualDevicePropertyNameCompletionTestHelper(
    expectedProperties: List<String>, buildFileContent: String,
    deviceCatalogState: ManagedVirtualDeviceCatalogState = fullManagedVirtualDeviceCatalogState()) {
    val mockService = createFakeManagedVirtualDeviceCatalogService()
    MockitoKt.whenever(mockService.state).thenReturn(deviceCatalogState)

    gmdCodeCompletionContributorTestHelper(BuildFileName.GROOVY_BUILD_FILE.fileName, buildFileContent) {
      val prioritizedLookupElements = myFixture.lookupElementStrings!!.subList(0, expectedProperties.size)
      assertTrue(prioritizedLookupElements == expectedProperties)
    }
  }
}
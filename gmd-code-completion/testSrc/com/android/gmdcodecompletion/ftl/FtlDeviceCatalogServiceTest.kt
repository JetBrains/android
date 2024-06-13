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
package com.android.gmdcodecompletion.ftl

import com.android.gmdcodecompletion.freshFtlDeviceCatalogState
import com.android.gmdcodecompletion.fullAndroidDeviceCatalog
import com.android.gmdcodecompletion.isFtlPluginEnabled
import com.android.gmdcodecompletion.matchFtlDeviceCatalog
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.gct.testing.launcher.CloudAuthenticator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class FtlDeviceCatalogServiceTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  val project: Project
    get() = projectRule.project

  private val mockCloudAuthenticator: CloudAuthenticator = mock(CloudAuthenticator::class.java)

  private val mockProgressIndicator: ProgressIndicator = mock(ProgressIndicator::class.java)

  @Before
  fun setUp() {
    projectRule.replaceService(CloudAuthenticator::class.java, mockCloudAuthenticator)
  }

  @Test
  fun testObtainAndroidDeviceCatalog() {
    whenever(mockCloudAuthenticator.androidDeviceCatalog).thenReturn(fullAndroidDeviceCatalog)
    val ftlDeviceCatalogService = FtlDeviceCatalogService()
    assertFalse(ftlDeviceCatalogService.state.isCacheFresh())

    ftlDeviceCatalogService.updateDeviceCatalogTaskAction(project, mockProgressIndicator)

    assertTrue(ftlDeviceCatalogService.state.isCacheFresh())
    assertTrue(
      matchFtlDeviceCatalog(ftlDeviceCatalogService.state.myDeviceCatalog, fullAndroidDeviceCatalog)
    )
    verify(mockCloudAuthenticator).androidDeviceCatalog
  }

  @Test
  fun testCacheIsFresh() {
    val ftlDeviceCatalogService = FtlDeviceCatalogService()
    ftlDeviceCatalogService.loadState(freshFtlDeviceCatalogState())
    ftlDeviceCatalogService.updateDeviceCatalogTaskAction(project, mockProgressIndicator)

    assertTrue(ftlDeviceCatalogService.state.isCacheFresh())
    verifyNoInteractions(mockCloudAuthenticator)
  }

  @Test
  fun testFtlNotEnabled_gradlePropertyNotSet() {
    val ftlDeviceCatalogService = FtlDeviceCatalogService()

    ftlDeviceCatalogService.updateDeviceCatalog(project)

    verifyNoInteractions(mockCloudAuthenticator)
    assertFalse(ftlDeviceCatalogService.state.isCacheFresh())
  }

  @Test
  fun testFtlNotEnabled_noMatchingPluginInModule() {
    val ftlDeviceCatalogService = FtlDeviceCatalogService()

    ftlDeviceCatalogService.updateDeviceCatalog(project)

    verifyNoInteractions(mockCloudAuthenticator)
    assertFalse(ftlDeviceCatalogService.state.isCacheFresh())
  }

  @Test
  fun testFtlEnabled_moduleLevelSetting() {
    val fixture = projectRule.fixture
    fixture.addFileToProject(
      "build.gradle",
      """
      plugins {
        id 'com.google.firebase.testlab`
      }
    """
        .trimIndent(),
    )
    fixture.addFileToProject(
      "gradle.properties",
      """
      android.experimental.testOptions.managedDevices.customDevice=true
    """
        .trimIndent(),
    )

    assertTrue(isFtlPluginEnabled(project, project.modules))
  }
}

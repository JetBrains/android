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
import com.android.gmdcodecompletion.matchFtlDeviceCatalog
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.google.gct.testing.launcher.CloudAuthenticator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoAnnotations.openMocks

class FtlDeviceCatalogServiceTest : LightPlatformTestCase() {
  @Mock
  private lateinit var mockProject: Project

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockProjectBuildModel: ProjectBuildModel

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockPluginModel: PluginModel

  @Mock
  private lateinit var mockCloudAuthenticator: CloudAuthenticator

  @Mock
  private lateinit var mockProgressIndicator: ProgressIndicator

  override fun setUp() {
    super.setUp()
    openMocks(this)

    whenever(mockProjectBuildModel.projectBuildModel!!.plugins()).thenReturn(listOf(mockPluginModel))
    whenever(mockPluginModel.psiElement!!.text).thenReturn("com.google.firebase.testlab")
    whenever(mockCloudAuthenticator.androidDeviceCatalog).thenReturn(fullAndroidDeviceCatalog)
    TestApplicationManager.getInstance()
  }

  private fun ftlDeviceCatalogServiceTestHelper(callback: () -> Unit) {
    mockStatic<ProjectBuildModel>().use {
      whenever(ProjectBuildModel.get(any())).thenReturn(mockProjectBuildModel)
      mockStatic<CloudAuthenticator>().use {
        whenever(CloudAuthenticator.getInstance()).thenReturn(mockCloudAuthenticator)
        callback()
      }
    }
  }

  fun testObtainAndroidDeviceCatalog() {
    ftlDeviceCatalogServiceTestHelper {
      val ftlDeviceCatalogService = FtlDeviceCatalogService()
      assertFalse(ftlDeviceCatalogService.state.isCacheFresh())
      ftlDeviceCatalogService.updateDeviceCatalogTaskAction(mockProject, mockProgressIndicator)
      assertTrue(ftlDeviceCatalogService.state.isCacheFresh())
      assertTrue(matchFtlDeviceCatalog(ftlDeviceCatalogService.state.myDeviceCatalog, fullAndroidDeviceCatalog))
      verify(mockCloudAuthenticator).androidDeviceCatalog
    }
  }

  fun testCacheIsFresh() {
    ftlDeviceCatalogServiceTestHelper {
      val ftlDeviceCatalogService = FtlDeviceCatalogService()
      ftlDeviceCatalogService.loadState(freshFtlDeviceCatalogState())
      ftlDeviceCatalogService.updateDeviceCatalogTaskAction(mockProject, mockProgressIndicator)
      assertTrue(ftlDeviceCatalogService.state.isCacheFresh())
      verifyNoInteractions(mockCloudAuthenticator)
    }
  }

  fun testFtlNotEnabled() {
    ftlDeviceCatalogServiceTestHelper {
      whenever(mockPluginModel.psiElement!!.text).thenReturn("com.google.testPlugin")
      val ftlDeviceCatalogService = FtlDeviceCatalogService()
      ftlDeviceCatalogService.updateDeviceCatalog(mockProject)
      verifyNoInteractions(mockCloudAuthenticator)
      assertFalse(ftlDeviceCatalogService.state.isCacheFresh())
    }
  }
}
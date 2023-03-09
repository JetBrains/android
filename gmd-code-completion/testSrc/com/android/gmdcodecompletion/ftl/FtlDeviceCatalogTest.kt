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

import com.android.gmdcodecompletion.fullAndroidDeviceCatalog
import com.android.gmdcodecompletion.matchFtlDeviceCatalog
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.MockitoKt.whenever
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.testing.model.AndroidRuntimeConfiguration
import com.google.api.services.testing.model.AndroidVersion
import com.google.api.services.testing.model.Locale
import com.google.api.services.testing.model.Orientation
import com.google.gct.testing.launcher.CloudAuthenticator
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import junit.framework.Assert.fail
import org.junit.Test

class FtlDeviceCatalogTest {

  private fun ftlDeviceCatalogTestHelper(deviceCatalog: AndroidDeviceCatalog? = AndroidDeviceCatalog(),
                                         failMessage: String,
                                         testCallback: () -> Unit) {
    mockStatic<CloudAuthenticator>().use{
      val mockCloudAuthenticator = mock<CloudAuthenticator>()
      whenever(mockCloudAuthenticator.androidDeviceCatalog).thenReturn(deviceCatalog)
      whenever(CloudAuthenticator.getInstance()).thenReturn(mockCloudAuthenticator)
      try {
        testCallback()
      } catch (e: Exception) {
        fail(failMessage)
      }
    }
  }

  @Test
  fun testEmptyAndroidDeviceCatalog() {
    assertTrue(FtlDeviceCatalog().isEmpty())
  }

  @Test
  fun testEmptyModelFieldsInAndroidDeviceCatalog() {
    val testAndroidModel = AndroidModel().setSupportedVersionIds(listOf("33")).setId("test")
    val testAndroidDeviceCatalog = AndroidDeviceCatalog().setModels(listOf(testAndroidModel))
    ftlDeviceCatalogTestHelper(testAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to handle null fields in AndroidModel in AndroidDeviceCatalog") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertFalse(testFtlDeviceCatalog.isEmpty())
      assertTrue(testFtlDeviceCatalog.devices.isNotEmpty())
    }
  }

  @Test
  fun testNullModelsInAndroidDeviceCatalog() {
    val testAndroidDeviceCatalog = AndroidDeviceCatalog().setVersions(listOf(AndroidVersion()))
    ftlDeviceCatalogTestHelper(testAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to handle null AndroidModel in AndroidDeviceCatalog") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testEmptyAndroidModelId() {
    val emptyModelId = AndroidModel().setSupportedVersionIds(listOf("33"))
    val emptyModelIdDeviceCatalog = AndroidDeviceCatalog().setModels(listOf(emptyModelId))
    ftlDeviceCatalogTestHelper(emptyModelIdDeviceCatalog,
                               "FtlDeviceCatalog fails to handle null model id AndroidModel") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.devices.isEmpty())
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testEmptyAndroidModelVersionId() {
    val emptyVersionId = AndroidModel().setId("test")
    val emptyVersionIdDeviceCatalog = AndroidDeviceCatalog().setModels(listOf(emptyVersionId))
    ftlDeviceCatalogTestHelper(emptyVersionIdDeviceCatalog,
                               "FtlDeviceCatalog fails to handle null supported version id in AndroidModel") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.devices.isEmpty())
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testEmptyApiLevelAndroidDeviceCatalog() {
    val testAndroidVersion = AndroidVersion()
    val testAndroidDeviceCatalog = AndroidDeviceCatalog().setVersions(listOf(testAndroidVersion))
    ftlDeviceCatalogTestHelper(testAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to handle empty AndroidVersion") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.apiLevels.isEmpty())
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testEmptyAndroidRuntimeConfiguration() {
    val testRunConfiguration = AndroidRuntimeConfiguration()
    val testAndroidDeviceCatalog = AndroidDeviceCatalog().setRuntimeConfiguration(testRunConfiguration)
    ftlDeviceCatalogTestHelper(testAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to handle empty AndroidRuntimeConfiguration") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.orientation.isEmpty())
      assertTrue(testFtlDeviceCatalog.locale.isEmpty())
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testEmptyOrientation() {
    val testRunConfiguration = AndroidRuntimeConfiguration().setOrientations(listOf(Orientation()))
    val testAndroidDeviceCatalog = AndroidDeviceCatalog().setRuntimeConfiguration(testRunConfiguration)
    ftlDeviceCatalogTestHelper(testAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to handle empty Orientation in AndroidRuntimeConfiguration") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.orientation.isEmpty())
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testEmptyLocale() {
    val testRunConfiguration = AndroidRuntimeConfiguration().setLocales(listOf(Locale()))
    val testAndroidDeviceCatalog = AndroidDeviceCatalog().setRuntimeConfiguration(testRunConfiguration)
    ftlDeviceCatalogTestHelper(testAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to handle empty Locale in AndroidRuntimeConfiguration") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.locale.isEmpty())
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testFtlDeviceCatalogObtainsAllInfo() {
    ftlDeviceCatalogTestHelper(fullAndroidDeviceCatalog,
                               "FtlDeviceCatalog fails to obtain all required information from a full AndroidDeviceCatalog") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(matchFtlDeviceCatalog(testFtlDeviceCatalog, fullAndroidDeviceCatalog))
      assertFalse(testFtlDeviceCatalog.isEmpty())
    }
  }

  @Test
  fun testSyncFtlDeviceCatalogFailed() {
    ftlDeviceCatalogTestHelper(null,
                               "FtlDeviceCatalog fails to handle null AndroidDeviceCatalog from CloudAuthenticator") {
      val testFtlDeviceCatalog = FtlDeviceCatalog().syncDeviceCatalog()
      assertTrue(testFtlDeviceCatalog.isEmpty())
    }
  }
}
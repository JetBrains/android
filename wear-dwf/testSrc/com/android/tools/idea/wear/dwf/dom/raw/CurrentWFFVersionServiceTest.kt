/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.wear.wff.WFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.android.tools.wear.wff.WFFVersion.WFFVersion3
import com.android.utils.concurrency.AsyncSupplier
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class CurrentWFFVersionServiceTest(val minSdkVersion: Int, val expectedFallbackVersion: WFFVersion) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "minSdkVersion={0} expectedFallbackVersion={1}")
    fun data(): List<Array<Any>> =
      listOf(
        arrayOf(30, WFFVersion1),
        arrayOf(33, WFFVersion1),
        arrayOf(34, WFFVersion2),
        arrayOf(36, WFFVersion2),
      )
  }

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModel(
      createAndroidProjectBuilderForDefaultTestProjectStructure().withMinSdk({ minSdkVersion })
    )

  private val mainModule
    get() =
      projectRule.module.getModuleSystem().getProductionAndroidModule()
        ?: error("expected main module to exist")

  private lateinit var service: CurrentWFFVersionService

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()

    service = CurrentWFFVersionService.getInstance()
  }

  @Test
  fun `getCurrentWFFVersion returns null if there is no manifest`() {
    val mockMergedManifestManager = mock<MergedManifestManager>()
    whenever(mockMergedManifestManager.mergedManifest)
      .thenReturn(
        object : AsyncSupplier<MergedManifestSnapshot> {
          override val now: MergedManifestSnapshot?
            get() = null

          override fun get(): ListenableFuture<MergedManifestSnapshot> {
            TODO()
          }
        }
      )
    projectRule.replaceService(MergedManifestManager::class.java, mockMergedManifestManager)

    assertThat(service.getCurrentWFFVersion(mainModule)).isNull()
  }

  @Test
  fun `getCurrentWFFVersion returns the version from the manifest`() {
    addManifestWithWFFVersion("3")
    val currentWFFVersion = service.getCurrentWFFVersion(mainModule)
    assertThat(currentWFFVersion).isEqualTo(CurrentWFFVersion(WFFVersion3, isFallback = false))
  }

  @Test
  fun `getCurrentWFFVersion returns a fallback version if the manifest version is invalid`() {
    addManifestWithWFFVersion("invalid")
    val currentWFFVersion = service.getCurrentWFFVersion(mainModule)
    assertThat(currentWFFVersion).isEqualTo(CurrentWFFVersion(expectedFallbackVersion, isFallback = true))
  }

  private fun addManifestWithWFFVersion(version: String) {
    projectRule.fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, manifestWithWFFVersion(version))
    // create the manifest snapshot
    MergedManifestManager.getMergedManifest(mainModule).get()
  }
}

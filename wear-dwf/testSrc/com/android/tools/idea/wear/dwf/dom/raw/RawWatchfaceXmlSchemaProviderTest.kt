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

import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FD_RES_RAW
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wear.dwf.analytics.DeclarativeWatchFaceUsageTracker
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion3
import com.android.utils.concurrency.AsyncSupplier
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.replaceService
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val RES_RAW_FOLDER = "${FD_RES}/${FD_RES_RAW}"

class RawWatchfaceXmlSchemaProviderTest {

  private val projectRule =
    AndroidProjectRule.withAndroidModel(
      createAndroidProjectBuilderForDefaultTestProjectStructure().withMinSdk({ 33 })
    )

  private val domRule = AndroidDomRule(RES_RAW_FOLDER) { projectRule.fixture }

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(domRule)

  private val mainModule
    get() =
      projectRule.module.getModuleSystem().getProductionAndroidModule()
        ?: error("expected main module to exist")

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/${RES_RAW_FOLDER}").toString()
  }

  @Test
  fun `test the provider is available for watch face files when the flag is on`() {
    val provider = RawWatchfaceXmlSchemaProvider()
    // add a manifest file for the `res/` folder to be considered a resource folder
    addManifestWithWFFVersion("1")
    val watchFaceFile =
      projectRule.fixture.addFileToProject(
        "${RES_RAW_FOLDER}/watch_face.xml",
        // language=xml
        """
        <WatchFace />
      """
          .trimIndent(),
      ) as XmlFile
    val rawResourceFile =
      projectRule.fixture.addFileToProject(
        "${RES_RAW_FOLDER}/resource.xml",
        // language=xml
        """
        <resource />
      """
          .trimIndent(),
      ) as XmlFile

    assertTrue(provider.isAvailable(watchFaceFile))
    assertFalse(provider.isAvailable(rawResourceFile))

    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    assertFalse(provider.isAvailable(watchFaceFile))
  }

  @Test
  fun `test tag autocompletion`() {
    addManifestWithWFFVersion("1")

    domRule.testCompletion(
      "watch_face_completion_metadata_tag.xml",
      "watch_face_completion_metadata_tag_after.xml",
    )
  }

  @Test
  fun `test tag autocompletion for feature with lower version than required`() {
    addManifestWithWFFVersion("1")

    // The tag should not be autocompleted as it's part of the version 2 features
    domRule.testCompletion(
      "watch_face_completion_flavor_tag.xml",
      "watch_face_completion_flavor_tag_after_version_1.xml",
    )
  }

  @Test
  fun `test tag autocompletion for feature with required version`() {
    addManifestWithWFFVersion("2")

    // The tag should be autocompleted as it's part of the version 2 features
    domRule.testCompletion(
      "watch_face_completion_flavor_tag.xml",
      "watch_face_completion_flavor_tag_after_version_2.xml",
    )
  }

  @Test
  fun `test attribute autocompletion`() {
    addManifestWithWFFVersion("1")

    assertEquals(
      listOf("CIRCLE", "NONE", "RECTANGLE"),
      domRule.getCompletionResults("watch_face_completion_attribute.xml"),
    )
  }

  @Test
  fun `test unrecognised tag is highlighted as an error`() {
    addManifestWithWFFVersion("1")

    domRule.testHighlighting("watch_face_highlight_unrecognised_tag.xml")
  }

  @Test
  fun `test unrecognised attributes is highlighted as an error`() {
    addManifestWithWFFVersion("1")

    domRule.testHighlighting("watch_face_highlight_unrecognised_attribute.xml")
  }

  @Test
  fun `test using feature with lower version is highlighted as an error`() {
    addManifestWithWFFVersion("1")

    domRule.testHighlighting("watch_face_highlight_feature_used_with_wrong_version.xml")
  }

  @Test
  fun `test using feature with the correct version highlighted without errors`() {
    addManifestWithWFFVersion("2")

    domRule.testHighlighting("watch_face_highlight_feature_used_with_correct_version.xml")
  }

  @Test
  fun `test the provider falls back to WFF version 1 when the manifest is empty`() {
    projectRule.fixture.addFileToProject(
      FN_ANDROID_MANIFEST_XML,
      // language=XML
      """
<?xml version="1.0" encoding="utf-8"?>
<manifest />
"""
        .trimIndent(),
    )
    // create the manifest snapshot
    MergedManifestManager.getMergedManifest(mainModule).get()

    domRule.testCompletion(
      "watch_face_completion_metadata_tag.xml",
      "watch_face_completion_metadata_tag_after.xml",
    )
  }

  @Test
  fun `test the provider returns null if there is no merged manifest`() {
    val mockMergedManifestManager = mock<MergedManifestManager>()
    whenever(mockMergedManifestManager.mergedManifest)
      .thenReturn(
        object : AsyncSupplier<MergedManifestSnapshot> {
          override val now: MergedManifestSnapshot?
            get() = null

          override fun get(): ListenableFuture<MergedManifestSnapshot> {
            return Futures.immediateFuture(null)
          }
        }
      )
    projectRule.replaceService(MergedManifestManager::class.java, mockMergedManifestManager)

    val watchFaceFile =
      projectRule.fixture.addFileToProject(
        "${RES_RAW_FOLDER}/watch_face.xml",
        // language=xml
        """
        <WatchFace />
      """
          .trimIndent(),
      ) as XmlFile

    val provider = RawWatchfaceXmlSchemaProvider()
    val schema = provider.getSchema("", projectRule.module, watchFaceFile)
    assertThat(schema).isNull()
  }

  @Test
  fun `test the provider falls back to WFF version 1 when the version is invalid with minSdk 33`() {
    addManifestWithWFFVersion("invalid")

    domRule.testCompletion(
      "watch_face_completion_metadata_tag.xml",
      "watch_face_completion_metadata_tag_after.xml",
    )
  }

  @Test
  fun `test the provider falls back to WFF version 2 when the version is invalid with minSdk 34`() {
    val facet = mainModule.androidFacet ?: error("expected AndroidFacet")
    AndroidModel.set(facet, TestAndroidModel(minSdkVersion = AndroidVersion.fromString("34")))
    addManifestWithWFFVersion("invalid")

    // The tag should be autocompleted as it's part of the version 2 features
    domRule.testCompletion(
      "watch_face_completion_flavor_tag.xml",
      "watch_face_completion_flavor_tag_after_version_2.xml",
    )
  }

  @Test
  fun `test the provider tracks usage of the XML schema`() {
    val mockTracker = mock<DeclarativeWatchFaceUsageTracker>()
    ApplicationManager.getApplication()
      .replaceService(
        DeclarativeWatchFaceUsageTracker::class.java,
        mockTracker,
        projectRule.testRootDisposable,
      )
    addManifestWithWFFVersion("3")

    domRule.testHighlighting("watch_face_completion_metadata_tag_after.xml")

    verify(mockTracker, atLeastOnce()).trackXmlSchemaUsed(WFFVersion3, isFallback = false)
  }

  @Test
  fun `test the provider tracks usage of the XML schema version fallbacks`() {
    val mockTracker = mock<DeclarativeWatchFaceUsageTracker>()
    ApplicationManager.getApplication()
      .replaceService(
        DeclarativeWatchFaceUsageTracker::class.java,
        mockTracker,
        projectRule.testRootDisposable,
      )

    // invalid to force use of a fallback version
    addManifestWithWFFVersion("invalid")

    domRule.testHighlighting("watch_face_completion_metadata_tag_after.xml")

    verify(mockTracker, atLeastOnce()).trackXmlSchemaUsed(WFFVersion1, isFallback = true)
  }

  private fun addManifestWithWFFVersion(version: String) {
    projectRule.fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, manifestWithWFFVersion(version))
    // create the manifest snapshot
    MergedManifestManager.getMergedManifest(mainModule).get()
  }
}

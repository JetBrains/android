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
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.intellij.psi.xml.XmlFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val RES_RAW_FOLDER = "${FD_RES}/${FD_RES_RAW}"

class RawWatchfaceXmlSchemaProviderTest {
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(true)
  private val domRule = AndroidDomRule(RES_RAW_FOLDER) { projectRule.fixture }

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(domRule)

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

    domRule.testHighlighting("watch_face_highlight_urecognised_attribute.xml")
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

  private fun addManifestWithWFFVersion(version: String) {
    projectRule.fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, manifestWithWFFVersion(version))
    // create the manifest snapshot
    MergedManifestManager.getMergedManifest(projectRule.module).get()
  }
}

private fun manifestWithWFFVersion(version: String) =
  // language=XML
  """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.android.tools.idea.wear.dwf.dom.raw">
    <uses-feature android:name="android.hardware.type.watch" />
    <application
        android:icon="@drawable/preview"
        android:label="@string/app_name"
        android:hasCode="false"
        >

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <property
            android:name="com.google.wear.watchface.format.version"
            android:value="$version" />
        <property
            android:name="com.google.wear.watchface.format.publisher"
            android:value="Test publisher" />

        <uses-library
            android:name="com.google.android.wearable"
            android:required="false" />
    </application>
</manifest>
        """
    .trimIndent()

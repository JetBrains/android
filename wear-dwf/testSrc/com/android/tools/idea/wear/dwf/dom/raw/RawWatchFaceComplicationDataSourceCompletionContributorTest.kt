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
import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val RES_RAW_FOLDER = "${FD_RES}/${FD_RES_RAW}"

class RawWatchFaceComplicationDataSourceCompletionContributorTest {
  private val projectRule = AndroidProjectRule.onDisk().initAndroid(true)
  private val domRule = AndroidDomRule(RES_RAW_FOLDER) { projectRule.fixture }

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(domRule)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/$RES_RAW_FOLDER").toString()
    // add a manifest file for the `res/` folder to be considered a resource folder
    fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, "")

    fixture.addFileToProject("res/drawable/another_image.png", "")
    fixture.addFileToProject("res/drawable/some_image.png", "")
    projectRule.waitForResourceRepositoryUpdates()
  }

  @Test
  fun `complication image resource completion is empty when the studio flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )

    assertEquals(
      emptyList(),
      domRule.getCompletionResults("watch_face_completion_complication_image_variants.xml"),
    )
  }

  @Test
  fun `complication image resource completion contains complication data sources`() {
    assertEquals(
      listOf(
        "COMPLICATION.MONOCHROMATIC_IMAGE",
        "COMPLICATION.PHOTO_IMAGE",
        "COMPLICATION.SMALL_IMAGE",
        "another_image",
        "some_image",
      ),
      domRule.getCompletionResults("watch_face_completion_complication_image_variants.xml"),
    )
  }

  @Test
  fun `regular image resource completion does not contain complication data sources`() {
    assertEquals(
      listOf("another_image", "some_image"),
      domRule.getCompletionResults("watch_face_completion_regular_image_variants.xml"),
    )
  }

  @Test
  fun `autocomplete surrounds the complication data source reference with brackets`() {
    domRule.testCompletion(
      "watch_face_completion_complication_image.xml",
      "watch_face_completion_complication_image_after.xml",
    )
    domRule.testCompletion(
      "watch_face_completion_complication_image_2.xml",
      "watch_face_completion_complication_image_after.xml",
    )
    domRule.testCompletion(
      "watch_face_completion_complication_image_3.xml",
      "watch_face_completion_complication_image_after.xml",
    )
  }

  @Test
  fun `autocomplete does not add brackets for drawable references`() {
    domRule.testCompletion(
      "watch_face_completion_complication_image_drawable.xml",
      "watch_face_completion_complication_image_drawable_after.xml",
    )
  }
}

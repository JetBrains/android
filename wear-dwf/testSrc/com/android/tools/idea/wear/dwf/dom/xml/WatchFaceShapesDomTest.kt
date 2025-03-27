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
package com.android.tools.idea.wear.dwf.dom.xml

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
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

private const val RES_XML_FOLDER = "res/xml"

class WatchFaceShapesDomTest {
  // onDisk is used to resolve resources in the highlights test
  private val projectRule = AndroidProjectRule.onDisk().initAndroid(true)
  private val domRule = AndroidDomRule(RES_XML_FOLDER) { projectRule.fixture }

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(domRule)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/${RES_XML_FOLDER}").toString()
    // add a manifest file for the `res/` folder to be considered a resource folder
    fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, "<manifest />")
  }

  @Test
  fun `the description is available for watch face shapes files when the flag is on`() {
    val description = WatchFaceShapesDescription()
    val watchFaceShapesFile =
      fixture.addFileToProject(
        "${RES_XML_FOLDER}/watch_face_shapes.xml",
        // language=xml
        """
          <WatchFaces />
        """
          .trimIndent(),
      ) as XmlFile

    assertTrue(description.isMyFile(watchFaceShapesFile, projectRule.module))

    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    assertFalse(description.isMyFile(watchFaceShapesFile, projectRule.module))
  }

  @Test
  fun `tag completion`() {
    domRule.testCompletion(
      "watch_face_shapes_completion_tag.xml",
      "watch_face_shapes_completion_tag_after.xml",
    )
  }

  @Test
  fun `attribute completion`() {
    domRule.testCompletion(
      "watch_face_shapes_completion_attr.xml",
      "watch_face_shapes_completion_attr_after.xml",
    )
  }

  @Test
  fun `attribute completion variants`() {
    assertEquals(
      listOf("file", "height", "shape", "width"),
      domRule.getCompletionResults("watch_face_shapes_completion_attr_variants.xml"),
    )
  }

  @Test
  fun `shape completion variants`() {
    assertEquals(
      listOf("CIRCLE", "RECTANGLE"),
      domRule.getCompletionResults("watch_face_shapes_completion_shape_variants.xml"),
    )
  }

  @Test
  fun highlighting() {
    // add resources that are referenced in the highlights file
    fixture.addFileToProject(
      "res/values/dimens.xml",
      // language=XML
      """
        <resources>
          <dimen name="width">450dp</dimen>
          <dimen name="height">450dp</dimen>
        </resources>
      """
        .trimIndent(),
    )

    // language=XML
    val watchFace =
      """
      <WatchFace />
    """
        .trimIndent()

    fixture.addFileToProject("res/raw/watch_face.xml", watchFace)
    fixture.addFileToProject("res/raw/watch_face_circle.xml", watchFace)
    projectRule.waitForResourceRepositoryUpdates()

    domRule.testHighlighting("watch_face_shapes_highlighting.xml")
  }
}

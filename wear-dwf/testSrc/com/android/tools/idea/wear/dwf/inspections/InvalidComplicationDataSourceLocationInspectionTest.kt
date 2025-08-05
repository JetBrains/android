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
package com.android.tools.idea.wear.dwf.inspections

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionFileType
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InvalidComplicationDataSourceLocationInspectionTest {

  @get:Rule
  val flagRule = FlagRule(StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT, true)
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture
    get() = projectRule.fixture

  private val inspection = InvalidComplicationDataSourceLocationInspection()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
    fixture.enableInspections(inspection)
  }

  @Test
  fun `complication data sources are valid when used inside a complication tag of the right type`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Complication type="RANGED_VALUE">
            <Parameter expression="[COMPLICATION.RANGED_VALUE_MAX]" />
          </Complication>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `complication data sources are invalid when used outside of a complication tag`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Parameter expression="<error descr="Complication data sources must be used within a <Complication> tag">[COMPLICATION.RANGED_VALUE_MAX]</error>" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `complication data sources are invalid when used in a complication tag without a type`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Complication>
            <Parameter expression="<error descr="This complication data source can only be used in complications with the following type(s): \"LONG_TEXT\", \"RANGED_VALUE\", \"SHORT_TEXT\", \"GOAL_PROGRESS\", \"WEIGHTED_ELEMENTS\"">[COMPLICATION.TEXT]</error>" />
            <Parameter expression="<error descr="This complication data source can only be used in complications with the following type(s): \"RANGED_VALUE\"">[COMPLICATION.RANGED_VALUE_MAX]</error>" />
          </Complication>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `complication data sources are invalid when used in a complication tag with an empty type`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Complication type="">
            <Parameter expression="<error descr="This complication data source can only be used in complications with the following type(s): \"RANGED_VALUE\"">[COMPLICATION.RANGED_VALUE_MAX]</error>" />
          </Complication>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `complication data sources are invalid when used in a complication tag of the wrong type`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Complication type="SHORT_TEXT">
            <Parameter expression="<error descr="This complication data source can only be used in complications with the following type(s): \"RANGED_VALUE\"">[COMPLICATION.RANGED_VALUE_MAX]</error>" />
          </Complication>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `errors are not reported when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Parameter expression="[COMPLICATION.RANGED_VALUE_MAX]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `errors are not reported when the WFF expression is not injected in a declarative watch face`() {
    fixture.configureByText(WFFExpressionFileType, "[COMPLICATION.RANGED_VALUE_MAX]")

    fixture.checkHighlighting(true, false, false)
  }
}

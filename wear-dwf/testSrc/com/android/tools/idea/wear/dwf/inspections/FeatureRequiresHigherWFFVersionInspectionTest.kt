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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionFileType
import com.android.tools.idea.wear.dwf.dom.raw.overrideCurrentWFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeatureRequiresHigherWFFVersionInspectionTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture
    get() = projectRule.fixture

  private val inspection = FeatureRequiresHigherWFFVersionInspection()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
    fixture.enableInspections(inspection)
  }

  @Test
  fun `function ids requiring a higher WFF version are reported with an error`() {
    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)

    // floor requires version 1, icuBestText version 2 which are both ok
    // extractColorFromColors requires version 4 which is higher than the current
    fixture.configureByText(
      WFFExpressionFileType,
      "floor(10) + icuBestText() * extractColorFromColors()",
    )

    val error = fixture.doHighlighting().single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("extractColorFromColors")
    assertThat(error.description).isEqualTo("This function requires WFF version 4")
  }

  @Test
  fun `function ids do not report errors if the current WFF version is null`() {
    overrideCurrentWFFVersion(null, projectRule.testRootDisposable)

    // extractColorFromColors requires version 4
    fixture.configureByText(WFFExpressionFileType, "extractColorFromColors()")

    val errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }

  @Test
  fun `data sources requiring a higher WFF version are reported with an error`() {
    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)

    // UTC_TIMESTAMP requires version 1, FIRST_DAY_OF_WEEK version 2 which are both ok
    // HOURS_SINCE_EPOCH requires version 3 which is higher than the current
    fixture.configureByText(
      WFFExpressionFileType,
      "[UTC_TIMESTAMP] + [FIRST_DAY_OF_WEEK] + [HOURS_SINCE_EPOCH]",
    )

    val error = fixture.doHighlighting().single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("HOURS_SINCE_EPOCH")
    assertThat(error.description).isEqualTo("This data source requires WFF version 3")
  }

  @Test
  fun `data sources are do not report errors if the current WFF version is null`() {
    overrideCurrentWFFVersion(null, projectRule.testRootDisposable)

    // HOURS_SINCE_EPOCH requires version 3
    fixture.configureByText(WFFExpressionFileType, "[HOURS_SINCE_EPOCH]")

    val errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }

  // Regression test for b/436552607
  @Test
  fun `complication data source is not reported as requiring version 2`() {
    // wrap in a watch face file to get the complication type
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
      <WatchFace>
        <Scene>
          <ComplicationSlot>
            <Complication type="SHORT_TEXT">
              <PartText>
                  <Text>
                      <BitmapFont>
                          <Template>%s
                              <Parameter expression="[COMPLICATION.TEXT]" />
                          </Template>
                      </BitmapFont>
                  </Text>
              </PartText>
            </Complication>
          </ComplicationSlot>
        </Scene>
      </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    overrideCurrentWFFVersion(WFFVersion1, projectRule.testRootDisposable)

    fixture.checkHighlighting()
  }

  // Regression test for b/436552607
  @Test
  fun `complication data source is reported as requiring version 2`() {
    // wrap in a watch face file to get the complication type
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
      <WatchFace>
        <Scene>
          <ComplicationSlot>
            <Complication type="WEIGHTED_ELEMENTS">
              <PartText>
                  <Text>
                      <BitmapFont>
                          <Template>%s
                              <Parameter expression="[COMPLICATION.TEXT]" />
                          </Template>
                      </BitmapFont>
                  </Text>
              </PartText>
            </Complication>
          </ComplicationSlot>
        </Scene>
      </WatchFace>
      """
          .trimIndent(),
      )

    overrideCurrentWFFVersion(WFFVersion1, projectRule.testRootDisposable)
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    var errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).hasSize(1)
    assertThat(errors[0].text).isEqualTo("COMPLICATION.TEXT")
    assertThat(errors[0].description).isEqualTo("This data source requires WFF version 2")

    // Once we use version 2, there should no longer be any errors
    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }
}

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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.wear.dwf.dom.raw.overrideCurrentWFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class WFFExpressionAnnotatorTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModel(
      createAndroidProjectBuilderForDefaultTestProjectStructure().withMinSdk({ 33 })
    )

  val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
  }

  @Test
  fun `function ids are annotated`() {
    fixture.configureByText(WFFExpressionFileType, "log10(10, 2, 3) * unknownFunction() * keyword")

    val highlightInfos = fixture.doHighlighting()
    assertThat(highlightInfos).hasSize(3)

    val infos = highlightInfos.filter { it.severity == HighlightSeverity.INFORMATION }
    assertThat(infos[0].text).isEqualTo("log10")
    assertThat(infos[0].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.FUNCTION_ID.key)
    assertThat(infos[1].text).isEqualTo("unknownFunction")
    assertThat(infos[1].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.FUNCTION_ID.key)

    val error = highlightInfos.single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("unknownFunction")
    assertThat(error.forcedTextAttributesKey)
      .isEqualTo(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
    assertThat(error.toolTip).isEqualTo("<html>Unknown function</html>")
  }

  @Test
  fun `function ids requiring a higher WFF version are annotated with an error`() {
    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)

    // floor requires version 1, icuBestText version 2 which are both ok
    // extractColorFromColors requires version 4 which is higher than the current
    fixture.configureByText(
      WFFExpressionFileType,
      "floor(10) + icuBestText() * extractColorFromColors()",
    )

    val error = fixture.doHighlighting().single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("extractColorFromColors")
    assertThat(error.forcedTextAttributesKey).isEqualTo(CodeInsightColors.ERRORS_ATTRIBUTES)
    assertThat(error.toolTip).isEqualTo("<html>This function requires WFF version 4</html>")
  }

  @Test
  fun `function ids are not annotated with errors if the current WFF version is null`() {
    overrideCurrentWFFVersion(null, projectRule.testRootDisposable)

    // extractColorFromColors requires version 4
    fixture.configureByText(WFFExpressionFileType, "extractColorFromColors()")

    val errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }

  @Test
  fun `data sources are annotated`() {
    fixture.configureByText(WFFExpressionFileType, "[WEEK_IN_MONTH] * [UNKNOWN_DATA_SOURCE]")

    val highlightInfos = fixture.doHighlighting()
    assertThat(highlightInfos).hasSize(3)

    val infos = highlightInfos.filter { it.severity == HighlightSeverity.INFORMATION }
    assertThat(infos[0].text).isEqualTo("WEEK_IN_MONTH")
    assertThat(infos[0].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.DATA_SOURCE.key)
    assertThat(infos[1].text).isEqualTo("UNKNOWN_DATA_SOURCE")
    assertThat(infos[1].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.DATA_SOURCE.key)

    val error = highlightInfos.single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("UNKNOWN_DATA_SOURCE")
    assertThat(error.forcedTextAttributesKey)
      .isEqualTo(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
    assertThat(error.toolTip).isEqualTo("<html>Unknown data source</html>")
  }

  @Test
  fun `data sources requiring a higher WFF version are annotated with an error`() {
    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)

    // UTC_TIMESTAMP requires version 1, FIRST_DAY_OF_WEEK version 2 which are both ok
    // HOURS_SINCE_EPOCH requires version 3 which is higher than the current
    fixture.configureByText(
      WFFExpressionFileType,
      "[UTC_TIMESTAMP] + [FIRST_DAY_OF_WEEK] + [HOURS_SINCE_EPOCH]",
    )

    val error = fixture.doHighlighting().single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("HOURS_SINCE_EPOCH")
    assertThat(error.forcedTextAttributesKey).isEqualTo(CodeInsightColors.ERRORS_ATTRIBUTES)
    assertThat(error.toolTip).isEqualTo("<html>This data source requires WFF version 3</html>")
  }

  @Test
  fun `data sources are not annotated with errors if the current WFF version is null`() {
    overrideCurrentWFFVersion(null, projectRule.testRootDisposable)

    // HOURS_SINCE_EPOCH requires version 3
    fixture.configureByText(WFFExpressionFileType, "[HOURS_SINCE_EPOCH]")

    val errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }

  @Test
  fun `configurations are annotated`() {
    // wrap in a watch face file for the configuration references to resolve
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="someConfig" />
          </UserConfigurations>
          <Parameter expression="[CONFIGURATION.someConfig] * [CONFIGURATION.unknownConfig]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    val highlightInfos = fixture.doHighlighting()

    val infos = highlightInfos.filter { it.severity == HighlightSeverity.INFORMATION }
    assertThat(infos).hasSize(2)
    assertThat(infos[0].text).isEqualTo("CONFIGURATION.someConfig")
    assertThat(infos[0].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.CONFIGURATION.key)
    assertThat(infos[1].text).isEqualTo("CONFIGURATION.unknownConfig")
    assertThat(infos[1].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.CONFIGURATION.key)

    val error = highlightInfos.single { it.severity == HighlightSeverity.ERROR }
    assertThat(error.text).isEqualTo("CONFIGURATION.unknownConfig")
    assertThat(error.forcedTextAttributesKey)
      .isEqualTo(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
    assertThat(error.toolTip).isEqualTo("<html>Unknown configuration</html>")
  }

  @Test
  fun `weather data sources are annotated`() {
    fixture.configureByText(
      WFFExpressionFileType,
      "[WEATHER.CONDITION] + [WEATHER.UNKNOWN] + [WEATHER.HOURS.0.CONDITION] + [WEATHER.DAYS.0.UNKNOWN]",
    )

    val highlightInfos = fixture.doHighlighting()
    assertThat(highlightInfos).hasSize(6)

    val infos = highlightInfos.filter { it.severity == HighlightSeverity.INFORMATION }
    assertThat(infos).hasSize(4)
    assertThat(infos[0].text).isEqualTo("WEATHER.CONDITION")
    assertThat(infos[0].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.DATA_SOURCE.key)
    assertThat(infos[1].text).isEqualTo("WEATHER.UNKNOWN")
    assertThat(infos[1].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.DATA_SOURCE.key)
    assertThat(infos[2].text).isEqualTo("WEATHER.HOURS.0.CONDITION")
    assertThat(infos[2].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.DATA_SOURCE.key)
    assertThat(infos[3].text).isEqualTo("WEATHER.DAYS.0.UNKNOWN")
    assertThat(infos[3].forcedTextAttributesKey)
      .isEqualTo(WFFExpressionTextAttributes.DATA_SOURCE.key)

    val errors = highlightInfos.filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).hasSize(2)
    assertThat(errors[0].text).isEqualTo("WEATHER.UNKNOWN")
    assertThat(errors[0].forcedTextAttributesKey)
      .isEqualTo(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
    assertThat(errors[0].toolTip).isEqualTo("<html>Unknown data source</html>")
    assertThat(errors[1].text).isEqualTo("WEATHER.DAYS.0.UNKNOWN")
    assertThat(errors[1].forcedTextAttributesKey)
      .isEqualTo(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
    assertThat(errors[1].toolTip).isEqualTo("<html>Unknown data source</html>")
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
    assertThat(errors[0].forcedTextAttributesKey).isEqualTo(CodeInsightColors.ERRORS_ATTRIBUTES)
    assertThat(errors[0].toolTip).isEqualTo("<html>This data source requires WFF version 2</html>")

    // Once we use version 2, there should no longer be any errors
    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }

  // Regression test for b/436562054
  @Test
  fun `known complication data source in wrong location is not reported as unknown`() {
    overrideCurrentWFFVersion(WFFVersion1, projectRule.testRootDisposable)

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
                              <Parameter expression="[COMPLICATION.SMALL_IMAGE]" />
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

    val errors = fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    assertThat(errors).isEmpty()
  }
}

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
  fun `configuration are annotated`() {
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
}

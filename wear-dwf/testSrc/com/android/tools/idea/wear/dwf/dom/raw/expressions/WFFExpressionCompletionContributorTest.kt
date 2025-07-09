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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.wear.dwf.dom.raw.overrideCurrentWFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.android.tools.wear.wff.WFFVersion.WFFVersion4
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class WFFExpressionCompletionContributorTest {
  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
  }

  @Test
  fun `autocompletes functions when a literal is expected`() {
    fixture.configureByText(WFFExpressionFileType, caret)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllOf("log", "log10", "clamp")
  }

  @Test
  fun `function variants depend on WFF version`() {
    fixture.configureByText(WFFExpressionFileType, caret)

    overrideCurrentWFFVersion(WFFVersion1, projectRule.testRootDisposable)
    var variants = fixture.completeBasic().map { it.lookupString }
    assertThat(variants).contains("log") // version 1 required
    assertThat(variants).doesNotContain("icuText") // version 2 required
    assertThat(variants).doesNotContain("extractColorFromColors") // version 4 required

    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)
    variants = fixture.completeBasic().map { it.lookupString }
    assertThat(variants).contains("log") // version 1 required
    assertThat(variants).contains("icuText") // version 2 required
    assertThat(variants).doesNotContain("extractColorFromColors") // version 4 required

    overrideCurrentWFFVersion(WFFVersion4, projectRule.testRootDisposable)
    variants = fixture.completeBasic().map { it.lookupString }
    assertThat(variants).contains("log") // version 1 required
    assertThat(variants).contains("icuText") // version 2 required
    assertThat(variants).contains("extractColorFromColors") // version 4 required

    overrideCurrentWFFVersion(null, projectRule.testRootDisposable)
    // when we can't determine the version, we should return all
    variants = fixture.completeBasic().map { it.lookupString }
    assertThat(variants).contains("log")
    assertThat(variants).contains("icuText")
    assertThat(variants).contains("extractColorFromColors")
  }

  @Test
  fun `does not autocomplete functions when after a bracket`() {
    fixture.configureByText(WFFExpressionFileType, "[$caret")

    assertThat(fixture.completeBasic().map { it.lookupString }).doesNotContain("log")

    fixture.configureByText(WFFExpressionFileType, "[CONFIGURATION.$caret]")

    assertThat(fixture.completeBasic().map { it.lookupString }).doesNotContain("log")
  }

  @Test
  fun `does not autocomplete functions when a literal is not expected`() {
    fixture.configureByText(WFFExpressionFileType, "log() $caret")

    assertThat(fixture.completeBasic().map { it.lookupString }).isEmpty()
  }

  @Test
  fun `autocompleted functions adds parenthesis if needed and moves the cursor`() {
    fixture.configureByText(WFFExpressionFileType, "textLen$caret")

    fixture.completeBasic()

    fixture.checkResult("textLength($caret)")
  }

  @Test
  fun `autocompleted functions does not add parenthesis if not needed and moves the cursor`() {
    fixture.configureByText(WFFExpressionFileType, "textLen$caret(\"some string\")")

    fixture.completeBasic()

    fixture.checkResult("textLength($caret\"some string\")")
  }

  @Test
  fun `does not autocomplete when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      fixture.testRootDisposable,
    )
    // wrap in a watch face file for the configuration references to resolve
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
          </UserConfigurations>
          <Parameter expression="$caret" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString }).isEmpty()
  }
}

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
import com.android.tools.idea.wear.dwf.WFFConstants.DataSources
import com.android.tools.idea.wear.dwf.dom.raw.overrideCurrentWFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.android.tools.wear.wff.WFFVersion.WFFVersion3
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
    configureExpression(caret)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllOf("log", "log10", "clamp")
  }

  @Test
  fun `function variants depend on WFF version`() {
    configureExpression(caret)

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
    configureExpression("[$caret")

    assertThat(fixture.completeBasic().map { it.lookupString }).doesNotContain("log")

    configureExpression("[CONFIGURATION.$caret")

    assertThat(fixture.completeBasic().map { it.lookupString }).doesNotContain("log")
  }

  @Test
  fun `does not autocomplete functions when a literal is not expected`() {
    configureExpression("log() $caret")

    assertThat(fixture.completeBasic().map { it.lookupString }).isEmpty()
  }

  @Test
  fun `autocompleted functions adds parenthesis if needed and moves the cursor`() {
    configureExpression("textLen$caret")

    fixture.completeBasic()

    checkExpressionAutocomplete("textLength($caret)")
  }

  @Test
  fun `autocompleted functions does not add parenthesis if not needed and moves the cursor`() {
    configureExpression("textLen$caret(\"some string\")")

    fixture.completeBasic()

    checkExpressionAutocomplete("textLength($caret\"some string\")")
  }

  @Test
  fun `autocompletes data sources when a literal is expected`() {
    configureExpression(caret)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllOf(
        "WEATHER.IS_AVAILABLE",
        "STEP_COUNT",
        "WEATHER.DAYS.<days>.CHANCE_OF_PRECIPITATION",
      )
  }

  @Test
  fun `data source variants depend on the WFF version`() {
    configureExpression(caret)

    overrideCurrentWFFVersion(WFFVersion1, projectRule.testRootDisposable)
    var variants = fixture.completeBasic().map { it.lookupString }
    assertThat(variants).contains("SECOND_MILLISECOND") // version 1 required
    assertThat(variants).doesNotContain("SECOND_UNITS_DIGIT") // version 2 required
    assertThat(variants).doesNotContain("TIMEZONE_OFFSET_MINUTES") // version 3 required

    overrideCurrentWFFVersion(WFFVersion2, projectRule.testRootDisposable)
    variants = fixture.completeBasic().map { it.lookupString }
    assertThat(variants).contains("SECOND_MILLISECOND") // version 1 required
    assertThat(variants).contains("SECOND_UNITS_DIGIT") // version 2 required
    assertThat(variants).doesNotContain("TIMEZONE_OFFSET_MINUTES") // version 3 required

    for (version in listOf(WFFVersion3, WFFVersion4, null)) {
      overrideCurrentWFFVersion(version, projectRule.testRootDisposable)
      variants = fixture.completeBasic().map { it.lookupString }
      assertThat(variants).contains("SECOND_MILLISECOND") // version 1 required
      assertThat(variants).contains("SECOND_UNITS_DIGIT") // version 2 required
      assertThat(variants).contains("TIMEZONE_OFFSET_MINUTES") // version 3 required
    }
  }

  @Test
  fun `all data source variants are returned when the WFF version can't be determined`() {
    configureExpression(caret)
    overrideCurrentWFFVersion(null, projectRule.testRootDisposable)

    val variants = fixture.completeBasic().map { it.lookupString }

    assertThat(variants).contains("SECOND_MILLISECOND") // version 1 required
    assertThat(variants).contains("SECOND_UNITS_DIGIT") // version 2 required
    assertThat(variants).contains("TIMEZONE_OFFSET_MINUTES") // version 3 required
  }

  @Test
  fun `does not autocomplete data sources when a literal is not expected`() {
    configureExpression("[STEP_COUNT] $caret")

    assertThat(fixture.completeBasic().map { it.lookupString }).isEmpty()
  }

  @Test
  fun `autocompleted data sources add brackets if needed`() {
    configureExpression("STEP_C$caret")

    fixture.completeBasic()

    checkExpressionAutocomplete("[STEP_COUNT]")
  }

  @Test
  fun `autocompleted data sources do not add brackets if not needed`() {
    configureExpression("[STEP_C$caret]")

    fixture.completeBasic()
    fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)

    checkExpressionAutocomplete("[STEP_COUNT]")
  }

  @Test
  fun `autocompletes weather data sources after the dot`() {
    configureExpression("[WEATHER.$caret")

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllOf(
        "WEATHER.IS_AVAILABLE",
        "WEATHER.DAYS.<days>.IS_AVAILABLE",
        "WEATHER.HOURS.<hours>.IS_AVAILABLE",
      )
  }

  @Test
  fun `autocompletes patterned weather data sources after the dot`() {
    configureExpression("[WEATHER.DAYS.$caret")

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllOf(
        "WEATHER.DAYS.<days>.IS_AVAILABLE",
        "WEATHER.DAYS.<days>.CHANCE_OF_PRECIPITATION",
      )
  }

  @Test
  fun `autocompletes patterned weather data sources after the specified days`() {
    configureExpression("[WEATHER.DAYS.3.$caret")

    val lookupStrings = fixture.completeBasic().map { it.lookupString }
    assertThat(lookupStrings)
      .containsAllOf("WEATHER.DAYS.3.IS_AVAILABLE", "WEATHER.DAYS.3.CONDITION_DAY_NAME")
    assertThat(lookupStrings).doesNotContain("WEATHER.DAYS.<days>.IS_AVAILABLE")
    assertThat(lookupStrings).doesNotContain("WEATHER.HOURS.<hours>.IS_AVAILABLE")
    assertThat(lookupStrings).doesNotContain("WEATHER.IS_AVAILABLE")
    assertThat(lookupStrings).doesNotContain("STEP_COUNT")
  }

  @Test
  fun `autocompletes patterned weather data sources after the specified hours`() {
    configureExpression("[WEATHER.HOURS.2$caret")

    val lookupStrings = fixture.completeBasic().map { it.lookupString }
    assertThat(lookupStrings)
      .containsAllOf("WEATHER.HOURS.2.IS_AVAILABLE", "WEATHER.HOURS.2.CONDITION_NAME")
    assertThat(lookupStrings).doesNotContain("WEATHER.HOURS.<hours>.IS_AVAILABLE")
    assertThat(lookupStrings).doesNotContain("WEATHER.DAYS.<days>.IS_AVAILABLE")
    assertThat(lookupStrings).doesNotContain("WEATHER.IS_AVAILABLE")
    assertThat(lookupStrings).doesNotContain("STEP_COUNT")
  }

  @Test
  fun `autocompleted patterned weather data sources add brackets, remove the cursor token, and move the cursor`() {
    configureExpression("WEATHER.DAYS.is$caret")

    fixture.completeBasic()

    checkExpressionAutocomplete("[WEATHER.DAYS.$caret.IS_AVAILABLE]")
  }

  @Test
  fun `autocompleted patterned weather data sources don't add brackets if not needed`() {
    configureExpression("[WEATHER.DAYS.is$caret]")

    fixture.completeBasic()
    fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)

    checkExpressionAutocomplete("[WEATHER.DAYS.$caret.IS_AVAILABLE]")
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

  @Test
  fun `does not autocomplete complication data sources when not under a complication tag`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
      <WatchFace>
        <Parameter expression="$caret" />
      </WatchFace>
    """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsNoneIn(DataSources.COMPLICATION_ALL.map { it.id })
  }

  @Test
  fun `does not autocomplete complication data sources when under a complication tag without a type`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
      <WatchFace>
        <Complication>
          <PartText>
              <Text>
                  <BitmapFont>
                      <Template>%s
                          <Parameter expression="$caret" />
                      </Template>
                  </BitmapFont>
              </Text>
          </PartText>
        </Complication>
      </WatchFace>
    """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsNoneIn(DataSources.COMPLICATION_ALL.map { it.id })
  }

  @Test
  fun `autocompletes complication data sources of the right type when under a complication tag with a type`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
      <WatchFace>
        <Complication type="RANGED_VALUE">
          <PartText>
              <Text>
                  <BitmapFont>
                      <Template>%s
                          <Parameter expression="$caret" />
                      </Template>
                  </BitmapFont>
              </Text>
          </PartText>
        </Complication>
      </WatchFace>
    """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllIn(DataSources.COMPLICATION_BY_TYPE["RANGED_VALUE"]?.map { it.id })
    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsNoneIn(DataSources.COMPLICATION_BY_TYPE["SMALL_IMAGE"]?.map { it.id })
  }

  private fun configureExpression(wffExpression: String) {
    // wrap in the watch face file for references to be created
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        basicWatchFaceFileWithWFFExpression(wffExpression),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)
  }

  private fun checkExpressionAutocomplete(expectedWffExpression: String) {
    fixture.checkResult(basicWatchFaceFileWithWFFExpression(expectedWffExpression))
  }

  private fun basicWatchFaceFileWithWFFExpression(wffExpression: String) =
    // language=XML
    """
      <WatchFace>
        <Parameter expression="$wffExpression" />
      </WatchFace>
    """
      .trimIndent()
}

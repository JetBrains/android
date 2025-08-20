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

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.wear.dwf.dom.raw.findInjectedExpressionLiteralAtCaret
import com.android.tools.idea.wear.dwf.dom.raw.overrideCurrentWFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion3
import com.android.tools.wear.wff.WFFVersion.WFFVersion4
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ReferenceTagReferenceTest {

  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val flagRule = FlagRule(StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT, true)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
    overrideCurrentWFFVersion(WFFVersion4, projectRule.testRootDisposable)
  }

  @Test
  fun `references are created for ids and data sources`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Scene>
            <PartText>
              <Reference name="partTextRef" />
            </PartText>
            <PartImage>
              <Reference name="partImageRef" />
            </PartImage>
            <Parameter expression="[REFERENCE.partTextRef] + someId + #ff0000 + [DATA_SOURCE] + [REFERENCE.partImageRef]" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[REFERENCE.partTextRef|]")
    val partTextRef = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(partTextRef).isNotNull()
    assertThat(partTextRef?.referenceTagReference).isNotNull()
    assertThat(partTextRef?.referenceTagReference?.resolve())
      .isEqualTo(
        fixture.findElementByText("<Reference name=\"partTextRef\" />", XmlTag::class.java)
      )

    fixture.moveCaret("[REFERENCE.partImageRef|]")
    val partImageRef = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(partImageRef).isNotNull()
    assertThat(partImageRef?.referenceTagReference).isNotNull()
    assertThat(partImageRef?.referenceTagReference?.resolve())
      .isEqualTo(
        fixture.findElementByText("<Reference name=\"partImageRef\" />", XmlTag::class.java)
      )

    fixture.moveCaret("some|Id")
    val someId = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(someId).isNotNull()
    assertThat(someId?.referenceTagReference).isNotNull()
    assertThat(someId?.referenceTagReference?.resolve()).isNull()

    // not an ID or data source
    fixture.moveCaret("#ff0|000")
    val hexColor = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(hexColor).isNotNull()
    assertThat(hexColor?.referenceTagReference).isNull()

    fixture.moveCaret("[DATA_SOURCE|]")
    val dataSource = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(dataSource).isNotNull()
    assertThat(dataSource?.referenceTagReference).isNotNull()
    assertThat(dataSource?.referenceTagReference?.resolve()).isNull()
  }

  @Test
  fun `references are not created when the flag is disabled`() {
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
          <Scene>
            <PartText>
              <Reference name="partTextRef" />
            </PartText>
            <Parameter expression="[REFERENCE.partTextRef]" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[REFERENCE.partTextRef|]")
    val partTextRef = fixture.findInjectedExpressionLiteralAtCaret()
    // this shouldn't be injected when the flag is disabled
    assertThat(partTextRef).isNull()
  }

  @Test
  fun `references are not created when the WFF version is lower than 4`() {
    overrideCurrentWFFVersion(WFFVersion3, projectRule.testRootDisposable)
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
          <WatchFace>
            <Scene>
              <PartText>
                <Reference name="partTextRef" />
              </PartText>
              <Parameter expression="[REFERENCE.partTextRef]" />
            </Scene>
          </WatchFace>
        """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[REFERENCE.partTextRef|]")
    val partTextRef = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(partTextRef).isNotNull()
    assertThat(partTextRef?.referenceTagReference).isNull()
  }

  @Test
  fun `references variants are available when starting with a bracket`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Scene>
            <PartText>
              <Reference name="partTextRef" />
            </PartText>
            <PartText>
              <!-- should not be listed in the variants -->
              <Reference name="" />
            </PartText>
            <PartText>
              <!-- should not be listed in the variants -->
              <Reference />
            </PartText>
            <PartImage>
              <Reference name="partImageRef" />
            </PartImage>
            <Parameter expression="[$caret" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().flatMap { it.allLookupStrings })
      .containsAllOf(
        "REFERENCE.partTextRef",
        "[REFERENCE.partTextRef]",
        "REFERENCE.partImageRef",
        "[REFERENCE.partImageRef]",
      )
  }

  @Test
  fun `references variants are available when starting without a bracket`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Scene>
            <PartText>
              <Reference name="partTextRef" />
            </PartText>
            <PartText>
              <!-- should not be listed in the variants -->
              <Reference name="" />
            </PartText>
            <PartText>
              <!-- should not be listed in the variants -->
              <Reference />
            </PartText>
            <PartImage>
              <Reference name="partImageRef" />
            </PartImage>
            <Parameter expression="$caret" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().flatMap { it.allLookupStrings })
      .containsAllOf(
        "REFERENCE.partTextRef",
        "[REFERENCE.partTextRef]",
        "REFERENCE.partImageRef",
        "[REFERENCE.partImageRef]",
      )
  }

  private val WFFExpressionLiteralExpr.referenceTagReference
    get() = references.filterIsInstance<ReferenceTagReference>().firstOrNull()
}

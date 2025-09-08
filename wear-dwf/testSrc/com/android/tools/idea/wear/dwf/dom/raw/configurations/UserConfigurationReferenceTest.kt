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
package com.android.tools.idea.wear.dwf.dom.raw.configurations

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionLiteralExpr
import com.android.tools.idea.wear.dwf.dom.raw.findInjectedExpressionLiteralAtCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class UserConfigurationReferenceTest {
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
  }

  @Test
  fun `references are created for xml attributes`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config" />
            <ColorConfiguration id="another_color_config" />
            <PhotosConfiguration id="photo_config" />
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.unknownColor]">
            <PartDraw tintColor="[CONFIGURATION.some_color_config]">
            <PartDraw tintColor="[CO">
            <Stroke color="#80ffffff" />
            <Stroke color="[CONFIGURATION.another_color_config]" />
            <Stroke notAColorAttribute="[CONFIGURATION.some_color_config]" />
            <Photos source="[CONFIGURATION.photo_config]" />
            <NotAPhotos source="[CONFIGURATION.photo_config]" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("backgroundColor=\"[CONFIGURATION.|unknownColor]\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition())
      .isInstanceOf(UserConfigurationReference::class.java)
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve()).isNull()

    fixture.moveCaret("tintColor=\"[CONFIGURATION.|some_color_config]\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition())
      .isInstanceOf(UserConfigurationReference::class.java)
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve())
      .isEqualTo(
        fixture.findElementByText(
          "<ColorConfiguration id=\"some_color_config\" />",
          XmlTag::class.java,
        )
      )

    fixture.moveCaret("tintColor=\"[CO|\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition())
      .isInstanceOf(UserConfigurationReference::class.java)
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve()).isNull()

    fixture.moveCaret("color=\"#80|ffffff\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNull()

    fixture.moveCaret("color=\"[|CONFIGURATION.another_color_config]\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition())
      .isInstanceOf(UserConfigurationReference::class.java)
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve())
      .isEqualTo(
        fixture.findElementByText(
          "<ColorConfiguration id=\"another_color_config\" />",
          XmlTag::class.java,
        )
      )

    fixture.moveCaret("notAColorAttribute=\"[CONFIGURATION.|some_color_config]\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNull()

    fixture.moveCaret("<Photos source=\"[|CONFIGURATION.photo_config]\" />")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition())
      .isInstanceOf(UserConfigurationReference::class.java)
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve())
      .isEqualTo(
        fixture.findElementByText("<PhotosConfiguration id=\"photo_config\" />", XmlTag::class.java)
      )

    fixture.moveCaret("<NotAPhotos source=\"[CONFIGURATION|.photo_config]\" />")
    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun `xml color attribute variants only contain color configurations`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
            <ColorConfiguration id="color_config_1" />
            <ColorConfiguration id="color_config_2" />
            <ColorConfiguration id="color_config_3" />
            <PhotosConfiguration id="photo_config_1" />
            <PhotosConfiguration id="photo_config_2" />
            <ListConfiguration id="list_configuration" />
          </UserConfigurations>
          <Scene backgroundColor="[$caret" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly(
        "CONFIGURATION.color_config_1",
        "CONFIGURATION.color_config_2",
        "CONFIGURATION.color_config_3",
      )
  }

  @Test
  fun `variant lookup strings contain configurations with and without brackets`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config_1" />
            <ColorConfiguration id="color_config_2" />
          </UserConfigurations>
          <Scene backgroundColor="[$caret" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().flatMap { it.allLookupStrings })
      .containsExactly(
        "CONFIGURATION.color_config_1",
        "[CONFIGURATION.color_config_1]",
        "CONFIGURATION.color_config_2",
        "[CONFIGURATION.color_config_2]",
      )
  }

  @Test
  fun `xml attributes can autocomplete without starting with an open bracket`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config_1" />
            <ColorConfiguration id="color_config_2" />
            <ColorConfiguration id="color_config_3" />
            <PhotosConfiguration id="photo_config_1" />
            <PhotosConfiguration id="photo_config_2" />
          </UserConfigurations>
          <Scene backgroundColor="color_$caret" />
          <Photos source="photo_" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly(
        "CONFIGURATION.color_config_1",
        "CONFIGURATION.color_config_2",
        "CONFIGURATION.color_config_3",
      )

    fixture.moveCaret("<Photos source=\"photo_|\" />")
    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly("CONFIGURATION.photo_config_1", "CONFIGURATION.photo_config_2")
  }

  @Test
  fun `xml color attribute variants contain color indices when there are more than one color`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config_with_one_color">
              <ColorOption colors="#ff0000" />
            </ColorConfiguration>
            <ColorConfiguration id="color_config_with_multiple_colors">
              <ColorOption colors="#ff0000 #00ff00 #0000ff" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor="[$caret" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)
    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly(
        "CONFIGURATION.color_config_with_one_color",
        "CONFIGURATION.color_config_with_multiple_colors.0",
        "CONFIGURATION.color_config_with_multiple_colors.1",
        "CONFIGURATION.color_config_with_multiple_colors.2",
      )
  }

  @Test
  fun `xml photo attribute variants only contain photo configurations`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
            <ColorConfiguration id="color_config_1" />
            <ColorConfiguration id="color_config_2" />
            <ColorConfiguration id="color_config_3" />
            <PhotosConfiguration id="photo_config_1" />
            <PhotosConfiguration id="photo_config_2" />
            <ListConfiguration id="list_configuration" />
          </UserConfigurations>
          <Scene>
             <Photos source="[$caret" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly("CONFIGURATION.photo_config_1", "CONFIGURATION.photo_config_2")
  }

  @Test
  fun `autocompleting the variant doesn't add extra brackets`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config" />
          </UserConfigurations>
          <Scene backgroundColor="[CON$caret]" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.completeBasic()
    fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)

    fixture.checkResult(
      // language=XML
      """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config" />
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.color_config]" />
        </WatchFace>
      """
        .trimIndent()
    )
  }

  @Test
  fun `references are created for expressions`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
            <ColorConfiguration id="color_config" />
            <PhotosConfiguration id="photo_config" />
            <ListConfiguration id="list_configuration" />
          </UserConfigurations>
          <Scene>
             <Parameter expression="[DATA_SOURCE] + [CONFIGURATION.boolean_configuration] + [CONFIGURATION.unknown]" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[DATA_|SOURCE]")
    val dataSource = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(dataSource).isNotNull()
    assertThat(dataSource?.userConfigurationReference).isNotNull()
    assertThat(dataSource?.userConfigurationReference?.resolve()).isNull()

    fixture.moveCaret("[CONFIGURATION.boolean|_configuration]")
    val configuration = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(configuration).isNotNull()
    assertThat(configuration?.userConfigurationReference).isNotNull()
    assertThat(configuration?.userConfigurationReference?.resolve())
      .isEqualTo(
        fixture.findElementByText(
          "<BooleanConfiguration id=\"boolean_configuration\" />",
          XmlTag::class.java,
        )
      )

    fixture.moveCaret("[CONFIGURATION.|unknown]")
    val unknownConfiguration = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(unknownConfiguration).isNotNull()
    assertThat(unknownConfiguration?.userConfigurationReference).isNotNull()
    assertThat(unknownConfiguration?.userConfigurationReference?.resolve()).isNull()
  }

  @Test
  fun `variants for references in expressions contain all configuration types`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
            <ColorConfiguration id="color_config" />
            <PhotosConfiguration id="photo_config" />
            <ListConfiguration id="list_configuration" />
          </UserConfigurations>
          <Scene>
             <Parameter expression="[CONFIGURATION.$caret" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly(
        "CONFIGURATION.boolean_configuration",
        "CONFIGURATION.color_config",
        "CONFIGURATION.photo_config",
        "CONFIGURATION.list_configuration",
      )
  }

  @Test
  fun `variants are available for color indices in expressions`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config">
              <ColorOption colors="#ff0000 #00ff00 #0000ff" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene>
             <Parameter expression="[CONFIGURATION.color_config.$caret" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly(
        "CONFIGURATION.color_config.0",
        "CONFIGURATION.color_config.1",
        "CONFIGURATION.color_config.2",
      )
  }

  @Test
  fun `autocomplete in expressions does not add extra brackets`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config" />
          </UserConfigurations>
          <Scene>
             <Parameter expression="[CONFIGURATION.color_$caret" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.completeBasic()

    fixture.checkResult(
      // language=XML
      """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config" />
          </UserConfigurations>
          <Scene>
             <Parameter expression="[CONFIGURATION.color_config]" />
          </Scene>
        </WatchFace>
      """
        .trimIndent()
    )
  }

  @Test
  fun `references are not created if the flag is disabled`() {
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
          <Scene backgroundColor="[CONFIGURATION.color_config]">
             <Parameter expression="[CONFIGURATION.boolean_config]" />
             <Photos source="[CONFIGURATION.photo_config]" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("expression=\"[CONFIGURATION.|boolean_config]\"")
    val expressionConfiguration = fixture.findInjectedExpressionLiteralAtCaret()
    // this shouldn't be injected when the flag is disabled
    assertThat(expressionConfiguration).isNull()

    fixture.moveCaret("backgroundColor=\"[CONFIGURATION|.color_config]")
    assertThat(fixture.getReferenceAtCaretPosition()).isNull()

    fixture.moveCaret("source=\"[CONFIGURATION.|photo_config]\"")
    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun `color configuration references can specify a color index`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config">
              <ColorOption colors="#ff0000 #00ff00 #0000ff"/>
            </ColorConfiguration>
          </UserConfigurations>
          <!-- this is valid -->
          <Stroke color="[CONFIGURATION.color_config.0]" />
          <!-- this is not valid -->
          <Stroke color="[CONFIGURATION.color_config.0.0]" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[CONFIGURATION.|color_config.0]")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve())
      .isEqualTo(
        fixture.findElementByText("<ColorConfiguration id=\"color_config\">", XmlTag::class.java)
      )

    fixture.moveCaret("[CONFIGURATION.|color_config.0.0]")
    assertThat(fixture.getReferenceAtCaretPosition()).isNotNull()
    assertThat(fixture.getReferenceAtCaretPosition()?.resolve()).isNull()
  }

  @Test
  fun `boolean configuration references cannot specify a color index`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_config" />
          </UserConfigurations>
          <Parameter expression="[CONFIGURATION.boolean_config] + [CONFIGURATION.boolean_config.0]" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[CONFIGURATION.|boolean_config]")
    val validBooleanConfig = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(validBooleanConfig).isNotNull()
    assertThat(validBooleanConfig?.userConfigurationReference?.resolve())
      .isEqualTo(
        fixture.findElementByText(
          "<BooleanConfiguration id=\"boolean_config\" />",
          XmlTag::class.java,
        )
      )

    fixture.moveCaret("[CONFIGURATION.|boolean_config.0]")
    val invalidBooleanConfig = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(invalidBooleanConfig).isNotNull()
    assertThat(invalidBooleanConfig?.userConfigurationReference?.resolve()).isNull()
  }

  @Test
  fun `list configuration references cannot specify a color index`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ListConfiguration id="list_config" />
          </UserConfigurations>
          <Parameter expression="[CONFIGURATION.list_config] + [CONFIGURATION.list_config.0]" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("[CONFIGURATION.|list_config]")
    val validListConfig = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(validListConfig).isNotNull()
    assertThat(validListConfig?.userConfigurationReference?.resolve())
      .isEqualTo(
        fixture.findElementByText("<ListConfiguration id=\"list_config\" />", XmlTag::class.java)
      )

    fixture.moveCaret("[CONFIGURATION.|list_config.0]")
    val invalidListConfig = fixture.findInjectedExpressionLiteralAtCaret()
    assertThat(invalidListConfig).isNotNull()
    assertThat(invalidListConfig?.userConfigurationReference?.resolve()).isNull()
  }

  @Test
  fun `autocompletes user configurations in expressions when a literal is expected`() {
    // wrap in a watch face file for the configuration references to resolve
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
            <ColorConfiguration id="color_config_1" />
            <ColorConfiguration id="color_config_2" />
            <PhotosConfiguration id="photo_config" />
            <ListConfiguration id="list_configuration" />
          </UserConfigurations>
          <Parameter expression="$caret" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsAllIn(
        arrayOf(
          "CONFIGURATION.boolean_configuration",
          "CONFIGURATION.color_config_1",
          "CONFIGURATION.color_config_2",
          "CONFIGURATION.photo_config",
          "CONFIGURATION.list_configuration",
        )
      )

    fixture.type("[CONFIGURATION.color_config_1] * list")
    assertThat(fixture.completeBasic().map { it.lookupString })
      .containsExactly("CONFIGURATION.list_configuration")
  }

  @Test
  fun `does not autocomplete user configurations in expressions when a literal is not expected`() {
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
          <Parameter expression="[CONFIGURATION.boolean_configuration] $caret" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    assertThat(fixture.completeBasic().map { it.lookupString }).isEmpty()
  }

  // Regression test for b/443685010
  @Test
  fun `user configurations are supported in quick edit fragments`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <BooleanConfiguration id="boolean_configuration" />
            <ListConfiguration id="list_configuration" />
          </UserConfigurations>
          <Scene>
             <Parameter expression="[CONFIGURATION.boolean_${caret}configuration]" />
          </Scene>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)
    val injectionTestFixture = InjectionTestFixture(fixture)
    val quickEditHandler =
      QuickEditAction()
        .invokeImpl(
          fixture.project,
          injectionTestFixture.topLevelEditor,
          injectionTestFixture.topLevelFile,
        )
    val fragmentFile = quickEditHandler.newFile
    fixture.openFileInEditor(fragmentFile.virtualFile)

    val reference =
      fixture
        .findElementByText(
          "[CONFIGURATION.boolean_configuration]",
          WFFExpressionLiteralExpr::class.java,
        )
        .userConfigurationReference

    assertThat(reference).isNotNull()
    val resolved = reference?.resolve()
    assertThat(resolved).isNotNull()
    assertThat(resolved).isInstanceOf(XmlTag::class.java)
    assertThat(fixture.completeBasic().flatMap { it.allLookupStrings })
      .containsAllOf("[CONFIGURATION.boolean_configuration]", "[CONFIGURATION.list_configuration]")
  }

  private val WFFExpressionLiteralExpr.userConfigurationReference
    get() =
      references.firstOrNull { it is UserConfigurationReference } as UserConfigurationReference?
}

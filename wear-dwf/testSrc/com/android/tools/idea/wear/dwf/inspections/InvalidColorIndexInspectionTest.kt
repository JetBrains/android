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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InvalidColorIndexInspectionTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture
    get() = projectRule.fixture

  private val xmlInspection = InvalidColorIndexXmlInspection()
  private val wffExpressionInspection = InvalidColorIndexWFFExpressionInspection()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
    fixture.enableInspections(xmlInspection)
    fixture.enableInspections(wffExpressionInspection)
  }

  @Test
  fun `the color index is optional if there is only one color in the palette`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.some_color_config]" />
          <Parameter expression="[CONFIGURATION.some_color_config]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `the color index is valid`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000 #00ff00 #0000ff" />
            </ColorConfiguration>
            <ColorConfiguration id="another_color_config">
              <ColorOption colors="#ff0000" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.some_color_config.0]" />
          <Parameter expression="[CONFIGURATION.some_color_config.1]" />
          <Parameter expression="[CONFIGURATION.some_color_config.2]" />
          <Parameter expression="[CONFIGURATION.another_color_config.0]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `reports missing color index`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000 #00ff00" />
            </ColorConfiguration>
            <ColorConfiguration id="another_color_config">
              <ColorOption colors="#ff0000 #00ff00  #0000ff" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor=<error descr="A color index in the range [0, 1] must be specified">"[CONFIGURATION.some_color_config]"</error> />
          <Parameter expression="<error descr="A color index in the range [0, 1] must be specified">[CONFIGURATION.some_color_config]</error>" />
          <Parameter expression="<error descr="A color index in the range [0, 2] must be specified">[CONFIGURATION.another_color_config]</error>" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `does not report missing color index for unresolved color configurations`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000 #00ff00" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.unknown_color_config]" />
          <!-- This is reported as an error by the annotator -->
          <Parameter expression="[<error descr="Unknown configuration">CONFIGURATION.unknown_color_config</error>]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `reports invalid color indices`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000 #00ff00 #0000ff" />
            </ColorConfiguration>
            <ColorConfiguration id="another_color_config">
              <ColorOption colors="#ff0000" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.some_color_config.<error descr="The color index must be an integer in the range [0, 2]">a</error>]" />
          <Scene backgroundColor="[CONFIGURATION.some_color_config.<error descr="The color index must be an integer in the range [0, 2]">10</error>]" />
          <Parameter expression="[CONFIGURATION.another_color_config.<error descr="The color index is optional and must be 0 if specified">1</error>]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `reports missing colors declarations in the color configurations`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="color_config_without_color_option" />
            <ColorConfiguration id="color_config_with_missing_colors">
              <ColorOption />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor=<error descr="The color configuration must declare at least one color in a ColorOption">"[CONFIGURATION.color_config_without_color_option]"</error> />
          <Scene backgroundColor=<error descr="The color configuration must declare at least one color in a ColorOption">"[CONFIGURATION.color_config_without_color_option.0]"</error> />
          <Scene backgroundColor=<error descr="The color configuration must declare at least one color in a ColorOption">"[CONFIGURATION.color_config_with_missing_colors]"</error> />
          <Scene backgroundColor=<error descr="The color configuration must declare at least one color in a ColorOption">"[CONFIGURATION.color_config_with_missing_colors.0]"</error> />
          <Parameter expression="<error descr="The color configuration must declare at least one color in a ColorOption">[CONFIGURATION.color_config_without_color_option]</error>" />
          <Parameter expression="<error descr="The color configuration must declare at least one color in a ColorOption">[CONFIGURATION.color_config_without_color_option.0]</error>" />
          <Parameter expression="<error descr="The color configuration must declare at least one color in a ColorOption">[CONFIGURATION.color_config_with_missing_colors]</error>" />
          <Parameter expression="<error descr="The color configuration must declare at least one color in a ColorOption">[CONFIGURATION.color_config_with_missing_colors.0]</error>" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `only reports invalid color indices in xml attributes if only the xml inspection is enabled`() {
    fixture.disableInspections(wffExpressionInspection)
    fixture.enableInspections(xmlInspection)

    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000 #00ff00 #0000ff" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor=<error descr="A color index in the range [0, 2] must be specified">"[CONFIGURATION.some_color_config]"</error> />
          <Parameter expression="[CONFIGURATION.some_color_config]" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `only reports invalid color indices in wff expressions if only the wff expression inspection is enabled`() {
    fixture.enableInspections(wffExpressionInspection)
    fixture.disableInspections(xmlInspection)

    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <UserConfigurations>
            <ColorConfiguration id="some_color_config">
              <ColorOption colors="#ff0000 #00ff00 #0000ff" />
            </ColorConfiguration>
          </UserConfigurations>
          <Scene backgroundColor="[CONFIGURATION.some_color_config]" />
          <Parameter expression="<error descr="A color index in the range [0, 2] must be specified">[CONFIGURATION.some_color_config]</error>" />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }
}

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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.dwf.dom.raw.configurations.BooleanConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.ColorConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.ListConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.PhotosConfiguration
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class RawWatchFaceUtilsTest {

  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `extracts user configurations from Declarative Watch Face file`() {
    val notAWatchFaceFile =
      projectRule.fixture.addFileToProject(
        "res/raw/not_a_watchface.xml",
        // language=XML
        """
      <NotAWatchFace>
        <UserConfigurations>
          <ColorConfiguration id="color_config_1" />
        </UserConfigurations>
      </NotAWatchFace>
    """
          .trimIndent(),
      ) as XmlFile

    val watchFaceFile =
      projectRule.fixture.addFileToProject(
        "res/raw/watchface.xml",
        // language=XML
        """
      <WatchFace>
        <UserConfigurations>
          <BooleanConfiguration id="boolean_configuration_1" />
          <BooleanConfiguration id="boolean_configuration_2" />
          <ColorConfiguration id="color_config_1" />
          <ColorConfiguration id="color_config_2">
            <ColorOption colors="#ff0000 #00ff00 #0000ff" />
          </ColorConfiguration>
          <ColorConfiguration id="color_config_3">
            <ColorOption colors="#ff0000" />
          </ColorConfiguration>
          <PhotosConfiguration id="photo_config" />
          <ListConfiguration id="list_configuration" />
        </UserConfigurations>

        <NotAConfigurationsTag>
          <ColorConfiguration id="not_a_color_config" />
        </NotAConfigurationsTag>
      </WatchFace>
    """
          .trimIndent(),
      ) as XmlFile

    projectRule.fixture.configureFromExistingVirtualFile(notAWatchFaceFile.virtualFile)
    assertThat(notAWatchFaceFile.extractUserConfigurations()).isEmpty()

    projectRule.fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)
    assertThat(watchFaceFile.extractUserConfigurations())
      .containsExactly(
        BooleanConfiguration("boolean_configuration_1", findXmlTag("boolean_configuration_1")),
        BooleanConfiguration("boolean_configuration_2", findXmlTag("boolean_configuration_2")),
        ColorConfiguration("color_config_1", findXmlTag("color_config_1"), IntRange.EMPTY),
        ColorConfiguration("color_config_2", findXmlTag("color_config_2"), 0..2),
        ColorConfiguration("color_config_3", findXmlTag("color_config_3"), 0..0),
        PhotosConfiguration("photo_config", findXmlTag("photo_config")),
        ListConfiguration("list_configuration", findXmlTag("list_configuration")),
      )
  }

  private fun findXmlTag(text: String) =
    projectRule.fixture.findElementByText(text, XmlTag::class.java)
}

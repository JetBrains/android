/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.application.options.CodeStyle
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.REARRANGE_ALWAYS
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidCodeStyleSettingsTest {
  @Rule
  @JvmField
  val myRule = AndroidProjectRule.inMemory()

  @Test
  fun initializedDefaultsInRealProjectInStudio() {
    // Note: this test is intentionally not an AndroidTestCase, because that applies the Android code style to all tests anyway.
    if (IdeInfo.getInstance().isAndroidStudio) {
      val newSettings = CodeStyleSettings()
      assertThat(AndroidXmlCodeStyleSettings.getInstance(newSettings).USE_CUSTOM_SETTINGS).isTrue()
      assertThat(newSettings.getCustomSettings(JavaCodeStyleSettings::class.java).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND)
        .isEqualTo(99)
      assertThat(newSettings.getCommonSettings(XMLLanguage.INSTANCE).FORCE_REARRANGE_MODE).isEqualTo(REARRANGE_ALWAYS)

      // CodeInsightTestFixtureImpl will instantiate a temporary code style for each test. This test only needs to verify the code style
      // defaults for the project, so we can drop the temporary settings early as we're not going to make any changes.
      CodeStyle.dropTemporarySettings(myRule.project)
      val projectSettings = CodeStyle.getSettings(myRule.project)
      assertThat(AndroidXmlCodeStyleSettings.getInstance(projectSettings).USE_CUSTOM_SETTINGS).isTrue()
      assertThat(JavaCodeStyleSettings.getInstance(myRule.project).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND).isEqualTo(99)
      assertThat(projectSettings.getCommonSettings(XMLLanguage.INSTANCE).FORCE_REARRANGE_MODE).isEqualTo(REARRANGE_ALWAYS)
    }
  }
}

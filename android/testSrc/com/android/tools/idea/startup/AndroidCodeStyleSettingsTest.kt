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

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.AppPropertiesComponentImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

private const val DEFAULT_RIGHT_MARGIN = 100
private const val CUSTOM_RIGHT_MARGIN = 90

@RunWith(JUnit4::class)
class AndroidCodeStyleSettingsTest {
  @Rule
  @JvmField
  val myRule = AndroidProjectRule.inMemory()

  private lateinit var mySchemes: CodeStyleSchemes
  private lateinit var myScheme: CodeStyleScheme
  private lateinit var myProperties: PropertiesComponent

  @Before
  fun mockSchemes() {
    mySchemes = Mockito.mock(CodeStyleSchemes::class.java)
  }

  @Before
  fun mockScheme() {
    myScheme = Mockito.mock(CodeStyleScheme::class.java)
    Mockito.`when`(myScheme.codeStyleSettings).thenReturn(CodeStyleSettings())
  }

  @Before
  fun initProperties() {
    myProperties = AppPropertiesComponentImpl()
  }

  @Test
  fun initializeDefaults() {
    Mockito.`when`(mySchemes.defaultScheme).thenReturn(myScheme)

    val defaultScheme = mySchemes.defaultScheme
    val defaultSettings = defaultScheme.codeStyleSettings

    myProperties.setValue(AndroidCodeStyleSettings.CONFIG_V1, false)
    AndroidCodeStyleSettings.initializeDefaults(mySchemes, myProperties)
    assertThat(defaultSettings.defaultRightMargin).isEqualTo(DEFAULT_RIGHT_MARGIN)

    defaultSettings.defaultRightMargin = CUSTOM_RIGHT_MARGIN
    assertThat(defaultSettings.defaultRightMargin).isEqualTo(CUSTOM_RIGHT_MARGIN)

    AndroidCodeStyleSettings.initializeDefaults(mySchemes, myProperties)
    assertThat(defaultScheme.codeStyleSettings.defaultRightMargin).isEqualTo(CUSTOM_RIGHT_MARGIN)

    myProperties.setValue(AndroidCodeStyleSettings.CONFIG_V1, false)
    AndroidCodeStyleSettings.initializeDefaults(mySchemes, myProperties)
    assertThat(defaultSettings.defaultRightMargin).isEqualTo(DEFAULT_RIGHT_MARGIN)
  }

  @Test
  fun setArrangementSettingsToVersion3() {
    // Arrange
    Mockito.`when`(mySchemes.currentScheme).thenReturn(myScheme)

    // Act
    AndroidCodeStyleSettings.setArrangementSettingsToVersion3(mySchemes, myProperties)

    // Assert
    val settings = getArrangementSettings(mySchemes)
    assertThat(settings).isEqualTo(AndroidXmlPredefinedCodeStyle.createVersion3Settings())

    // Act
    // An arbitrary change to the arrangement settings
    settings?.sections?.removeAt(6)

    AndroidCodeStyleSettings.setArrangementSettingsToVersion3(mySchemes, myProperties)

    // Assert
    assertThat(getArrangementSettings(mySchemes)).isEqualTo(settings)
  }
}

private fun getArrangementSettings(schemes: CodeStyleSchemes): ArrangementSettings? {
  return schemes.currentScheme.codeStyleSettings.getCommonSettings(XMLLanguage.INSTANCE).arrangementSettings
}
/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.support

import com.android.tools.property.panel.api.EnumValue
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.property.panel.api.HeaderEnumValue
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase

class FontEnumSupportTest : AndroidTestCase() {
  private var file: VirtualFile? = null

  override fun setUp() {
    super.setUp()
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/customfont.ttf")
    myFixture.copyFileToProject("fonts/my_circular_font_family_1.xml", "res/font/my_circular_font_family_1.xml")
    myFixture.copyFileToProject("fonts/my_circular_font_family_2.xml", "res/font/my_circular_font_family_2.xml")
    file = myFixture.copyFileToProject("fonts/roboto.xml", "res/font/roboto.xml")
  }

  override fun tearDown() {
    file = null
    super.tearDown()
  }

  private fun createEnumSupport(): FontEnumSupport {
    val configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file!!)
    val resourceResolver = configuration.resourceResolver
    return FontEnumSupport(myFacet, resourceResolver)
  }

  fun testFindPossibleValues() {
    val values = createEnumSupport().values
    checkHeader(values[0], "Project")
    checkEnumValue(values[1], "@font/customfont", "customfont")
    checkEnumValue(values[2], "@font/my_circular_font_family_1", "my_circular_font_family_1")
    checkEnumValue(values[3], "@font/my_circular_font_family_2", "my_circular_font_family_2")
    checkEnumValue(values[4], "@font/roboto", "roboto")
    checkHeader(values[5], "Android")
    checkEnumValue(values[6], "sans-serif", "sans-serif")
    checkEnumValue(values[7], "sans-serif-thin", "sans-serif-thin")
    checkEnumValue(values[8], "sans-serif-light", "sans-serif-light")
    checkEnumValue(values[9], "sans-serif-medium", "sans-serif-medium")
    checkEnumValue(values[10], "sans-serif-black", "sans-serif-black")
    checkEnumValue(values[11], "sans-serif-condensed", "sans-serif-condensed")
    checkEnumValue(values[12], "sans-serif-condensed-light", "sans-serif-condensed-light")
    checkEnumValue(values[13], "sans-serif-condensed-medium", "sans-serif-condensed-medium")
    checkEnumValue(values[14], "serif", "serif")
    checkEnumValue(values[15], "monospace", "monospace")
    checkEnumValue(values[16], "serif-monospace", "serif-monospace")
    checkEnumValue(values[17], "casual", "casual")
    checkEnumValue(values[18], "cursive", "cursive")
    checkEnumValue(values[19], "sans-serif-smallcaps", "sans-serif-smallcaps")
    checkSeparator(values[20])
    checkEnumValue(values[21], "", "More Fonts...", false)
    assertThat(values).hasSize(22)
  }

  private fun checkSeparator(enumValue: EnumValue) {
    assertThat(enumValue).isSameAs(EnumValue.SEPARATOR)
  }

  private fun checkHeader(enumValue: EnumValue, header: String) {
    assertThat(enumValue).isInstanceOf(HeaderEnumValue::class.java)
    assertThat((enumValue as HeaderEnumValue).header).isEqualTo(header)
  }

  private fun checkEnumValue(enumValue: EnumValue, value: String, display: String, indented: Boolean = true) {
    assertThat(enumValue.value).isEqualTo(value)
    assertThat(enumValue.display).isEqualTo(display)
    assertThat(enumValue.indented).isEqualTo(indented)
  }
}

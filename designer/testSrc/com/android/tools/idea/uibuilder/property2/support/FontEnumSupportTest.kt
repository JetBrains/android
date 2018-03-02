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
package com.android.tools.idea.uibuilder.property2.support

import com.android.tools.idea.common.property2.api.EnumValue
import com.android.tools.idea.configurations.ConfigurationManager
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
    val configuration = ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(file!!)
    val resourceResolver = configuration.resourceResolver ?: throw IllegalStateException("No ResourceResolver")
    return FontEnumSupport(myFacet, resourceResolver)
  }

  fun testFindPossibleValues() {
    val values = createEnumSupport().values
    checkEnumValue(values[0], "@font/customfont", "customfont", "Project")
    checkEnumValue(values[1], "@font/my_circular_font_family_1", "my_circular_font_family_1")
    checkEnumValue(values[2], "@font/my_circular_font_family_2", "my_circular_font_family_2")
    checkEnumValue(values[3], "@font/roboto", "roboto")
    checkEnumValue(values[4], "sans-serif", "sans-serif", "Android")
    checkEnumValue(values[5], "sans-serif-condensed", "sans-serif-condensed")
    checkEnumValue(values[6], "serif", "serif")
    checkEnumValue(values[7], "monospace", "monospace")
    checkEnumValue(values[8], "serif-monospace", "serif-monospace")
    checkEnumValue(values[9], "casual", "casual")
    checkEnumValue(values[10], "cursive", "cursive")
    checkEnumValue(values[11], "sans-serif-smallcaps", "sans-serif-smallcaps")
    checkEnumValue(values[12], "", "More Fonts...", "", false)
    assertThat(values).hasSize(13)
  }

  private fun checkEnumValue(enumValue: EnumValue, value: String, display: String, header: String = "", indented: Boolean = true) {
    assertThat(enumValue.value).isEqualTo(value)
    assertThat(enumValue.display).isEqualTo(display)
    assertThat(enumValue.header).isEqualTo(header)
    assertThat(enumValue.indented).isEqualTo(indented)
  }
}

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
package org.jetbrains.android.dom.converters

import com.android.SdkConstants
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.xml.DomManager
import org.jetbrains.android.dom.manifest.Property
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class PropertyValueConverterTest {

  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture
    get() = projectRule.fixture

  @Test
  fun `an integer converter is used for watch face format version property values`() {
    val file =
      projectRule.fixture.addFileToProject(
        SdkConstants.ANDROID_MANIFEST_XML,
        // language=XML
        """
         <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
             <property android:name="com.google.wear.watchface.format.version" android:value="" />
           </application>
         </manifest>
       """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val xmlTag = fixture.findElementByText("<property", XmlTag::class.java)
    val property = DomManager.getDomManager(projectRule.project).getDomElement(xmlTag) as? Property
    assertThat(property).isNotNull()
    val propertyValue = property?.getValue()
    assertThat(propertyValue?.converter).isInstanceOf(PropertyValueConverter::class.java)
    val propertyValueConverter = propertyValue?.converter as PropertyValueConverter
    assertThat(propertyValueConverter.getConverter(propertyValue))
      .isInstanceOf(IntegerConverter::class.java)
  }

  @Test
  fun `a resource reference converter is used for other cases`() {
    val file =
      projectRule.fixture.addFileToProject(
        SdkConstants.ANDROID_MANIFEST_XML,
        // language=XML
        """
         <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
             <property android:name="some.other.property" android:value="" />
             <property android:name="" android:value="" />
             <property android:value="" />
           </application>
         </manifest>
       """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val tagTexts =
      listOf(
        "<property android:name=\"some.other.property\"",
        "<property android:name=\"\"",
        "<property android:value=\"\" />",
      )

    for (tagText in tagTexts) {
      val xmlTag = fixture.findElementByText(tagText, XmlTag::class.java)
      val property =
        DomManager.getDomManager(projectRule.project).getDomElement(xmlTag) as? Property
      assertThat(property).isNotNull()
      val propertyValue = property?.getValue()
      assertThat(propertyValue?.converter).isInstanceOf(PropertyValueConverter::class.java)
      val propertyValueConverter = propertyValue?.converter as PropertyValueConverter
      assertThat(propertyValueConverter.getConverter(propertyValue))
        .isInstanceOf(ResourceReferenceConverter::class.java)
    }
  }
}

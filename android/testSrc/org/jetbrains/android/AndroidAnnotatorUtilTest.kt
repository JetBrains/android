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
package org.jetbrains.android

import com.android.tools.idea.res.colorToString
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTagValue
import org.junit.Test
import org.mockito.Mockito
import java.awt.Color

class AndroidAnnotatorUtilTest {

  @Test
  fun createColorTaskForXmlTag() {
    val tag = Mockito.mock(XmlTag::class.java)
    val tagValue = Mockito.mock(XmlTagValue::class.java)
    Mockito.`when`(tag.value).thenReturn(tagValue)
    val task = AndroidAnnotatorUtil.ColorRenderer.createSetColorTask(tag)

    val color = Color.BLUE
    task.invoke(color)

    Mockito.verify(tagValue).text = colorToString(color)
  }

  @Test
  fun createColorTaskForXmlAttributeValue() {
    val attributeValue = Mockito.mock(XmlAttributeValue::class.java)
    val xmlAttribute = Mockito.mock(XmlAttribute::class.java)
    Mockito.`when`(attributeValue.parent).thenReturn(xmlAttribute)
    val task = AndroidAnnotatorUtil.ColorRenderer.createSetColorTask(attributeValue)

    val color = Color.BLUE
    task.invoke(color)

    Mockito.verify(xmlAttribute).value = colorToString(color)
  }
}
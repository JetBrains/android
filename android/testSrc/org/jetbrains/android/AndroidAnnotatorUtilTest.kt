/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ide.common.resources.ResourceResolver
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.XmlElementFactory
import org.jetbrains.kotlin.psi.KtPsiFactory

class AndroidAnnotatorUtilTest: AndroidTestCase() {

  fun testPickupColorResourceInJavaFile() {
    val resolver = ResourceResolver.create(emptyMap(), null)
    val element = PsiElementFactory.getInstance(project)
      .createExpressionFromText("R.color.color1", null)

    val renderer = AndroidAnnotatorUtil.ColorRenderer(element, null, resolver, null, false, null)
    val task = renderer.createSetColorAttributeTask()

    task.consume("@color/color2")
    assertEquals("R.color.color2", renderer.element.text)

    task.consume("@android:color/color3")
    assertEquals("android.R.color.color3", renderer.element.text)
  }

  fun testColorRendererInKotlinFile() {
    val resolver = ResourceResolver.create(emptyMap(), null)
    val parentElement = KtPsiFactory(project, false).createExpression("R.color.color1") as PsiElement
    val element = parentElement.lastChild

    val renderer = AndroidAnnotatorUtil.ColorRenderer(element, null, resolver, null, false, null)
    val task = renderer.createSetColorAttributeTask()

    task.consume("@color/color2")
    assertEquals("color2", renderer.element.text)
    assertEquals("R.color.color2", renderer.element.parent.text)

    task.consume("@android:color/color3")
    assertEquals("color3", renderer.element.text)
    assertEquals("android.R.color.color3", renderer.element.parent.text)
  }

  fun testSetColorToXmlTag() {
    val xmlTag = XmlElementFactory.getInstance(project).createTagFromText("<color name=\"xxx\">#000000</color>")
    val task = AndroidAnnotatorUtil.createSetXmlAttributeTask(xmlTag)
    task.consume("#FFFFFF")
    assertEquals("<color name=\"xxx\">#FFFFFF</color>", xmlTag.text)

    task.consume("@color/color1")
    assertEquals("<color name=\"xxx\">@color/color1</color>", xmlTag.text)

    task.consume("@android:color/color2")
    assertEquals("<color name=\"xxx\">@android:color/color2</color>", xmlTag.text)
  }

  fun testSetColorToXmlAttribute() {
    val xmlAttribute = XmlElementFactory.getInstance(project).createXmlAttribute("android:background", "#000000")

    run {
      val task = AndroidAnnotatorUtil.createSetXmlAttributeTask(xmlAttribute.valueElement!!)
      task.consume("#FFFFFF")
      assertEquals("#FFFFFF", xmlAttribute.valueElement!!.value)
    }

    // The xml attribute value element is replaced when new value is set, thus we need to recreate a new task for different test cases.

    run {
      val task = AndroidAnnotatorUtil.createSetXmlAttributeTask(xmlAttribute.valueElement!!)
      task.consume("@color/color1")
      assertEquals("@color/color1", xmlAttribute.valueElement!!.value)
    }

    run {
      val task = AndroidAnnotatorUtil.createSetXmlAttributeTask(xmlAttribute.valueElement!!)
      task.consume("@android:color/color2")
      assertEquals("@android:color/color2", xmlAttribute.valueElement!!.value)
    }
  }
}

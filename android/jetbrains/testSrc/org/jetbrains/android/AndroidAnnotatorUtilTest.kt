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

import com.android.SdkConstants
import com.android.ide.common.resources.ProtoXmlPullParser
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.resources.TEST_DATA_DIR
import com.android.test.testutils.TestUtils
import com.android.tools.idea.util.toVirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.XmlElementFactory
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert

class AndroidAnnotatorUtilTest : AndroidTestCase() {

  fun testPickupColorResourceInJavaFile() {
    val element = createJavaElement("R.color.color1")

    val task = AndroidAnnotatorUtil.SetAttributeConsumer(element, ResourceType.COLOR)

    task.testJavaConsumer("@color/color2" to "R.color.color2",
                          "@android:color/color3" to "android.R.color.color3")
  }

  fun testSetColorInKotlinFile() {
    val element = createKotlinElement("R.color.color1")

    val task = AndroidAnnotatorUtil.SetAttributeConsumer(element, ResourceType.COLOR)

    task.testKotlinConsumer("@color/color2" to "R.color.color2",
                            "@android:color/color3" to "android.R.color.color3")

    // Verify that the current element in the consumer is the PsiElement that corresponds to the name only
    assertEquals("color3", task.element.text)
  }

  fun testSetColorToXmlTag() {
    val xmlTag = XmlElementFactory.getInstance(project).createTagFromText("<color name=\"xxx\">#000000</color>")
    val task = AndroidAnnotatorUtil.SetAttributeConsumer(xmlTag, ResourceType.COLOR)
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
      val task = AndroidAnnotatorUtil.SetAttributeConsumer(xmlAttribute.valueElement!!, ResourceType.COLOR)
      task.consume("#FFFFFF")
      assertEquals("#FFFFFF", xmlAttribute.valueElement!!.value)
    }

    // The xml attribute value element is replaced when new value is set, thus we need to recreate a new task for different test cases.

    run {
      val task = AndroidAnnotatorUtil.SetAttributeConsumer(xmlAttribute.valueElement!!, ResourceType.COLOR)
      task.consume("@color/color1")
      assertEquals("@color/color1", xmlAttribute.valueElement!!.value)
    }

    run {
      val task = AndroidAnnotatorUtil.SetAttributeConsumer(xmlAttribute.valueElement!!, ResourceType.COLOR)
      task.consume("@android:color/color2")
      assertEquals("@android:color/color2", xmlAttribute.valueElement!!.value)
    }
  }

  fun testPickupDrawableResourceInJavaFile() {
    val element = createJavaElement("R.drawable.drawable1")

    val task = AndroidAnnotatorUtil.SetAttributeConsumer(element, ResourceType.DRAWABLE)

    task.testJavaConsumer("@drawable/drawable2" to "R.drawable.drawable2",
                          "@android:drawable/drawable3" to "android.R.drawable.drawable3")
  }

  fun testSetDrawableInKotlinFile() {
    val element = createKotlinElement("R.drawable.drawable1")

    val task = AndroidAnnotatorUtil.SetAttributeConsumer(element, ResourceType.DRAWABLE)

    task.testKotlinConsumer("@drawable/drawable2" to "R.drawable.drawable2",
                            "@android:drawable/drawable3" to "android.R.drawable.drawable3")

    // Verify that the current element in the consumer is the PsiElement that corresponds to the name only
    assertEquals("drawable3", task.element.text)
  }

  fun testCreateXmlPullParser() {
    val resApkPath = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "/design_aar/" + SdkConstants.FN_RESOURCE_STATIC_LIBRARY)
    val resourcePath = "$resApkPath!/res/layout/design_bottom_navigation_item.xml"
    val pathString = PathString("apk", resourcePath)

    val virtualFile = pathString.toVirtualFile()!!
    val parser = AndroidAnnotatorUtil.createXmlPullParser(virtualFile)
    Assert.assertTrue(parser is ProtoXmlPullParser)
  }

  private fun createJavaElement(text: String) = PsiElementFactory.getInstance(project).createExpressionFromText(text, null)

  private fun createKotlinElement(text: String) = (KtPsiFactory(project, false).createExpression(text) as PsiElement).lastChild

  /**
   * Test the [AndroidAnnotatorUtil.SetAttributeConsumer] for multiple changes when the [PsiElement] in the consumer is in a Java file.
   */
  private fun AndroidAnnotatorUtil.SetAttributeConsumer.testJavaConsumer(vararg resourceAttributeAndExpected: Pair<String, String>) {
    for ((resource, expectedResult) in resourceAttributeAndExpected) {
      consume(resource)
      assertEquals(expectedResult, element.text)
    }
  }

  /**
   * Test the [AndroidAnnotatorUtil.SetAttributeConsumer] for multiple changes when the [PsiElement] in the consumer is in a Kotlin file.
   */
  private fun AndroidAnnotatorUtil.SetAttributeConsumer.testKotlinConsumer(vararg resourceAttributeAndExpected: Pair<String, String>) {
    for ((resource, expectedResult) in resourceAttributeAndExpected) {
      consume(resource)
      // For Kotlin, test against the parent, which is the full resource reference: namespace.R.resource_type.resource_name
      assertEquals(expectedResult, element.parent.text)
    }
  }
}

/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.dom.resources.Resources
import org.jetbrains.android.util.AndroidUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class IdeResourcesUtilCoroutinesTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture }

  @Test
  fun testCreateStringResourceValueAsync(): Unit = runBlocking {
    fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, """<?xml version="1.0" encoding="utf-8"?><manifest package="com.example"/>""")
    val resFile = fixture.addFileToProject("res/values/dummy.xml", "<resources></resources>")
    val resDir = resFile.virtualFile.parent.parent

    val success =
      createValueResourceAsync(
        project = project,
        resDir = resDir,
        resourceName = "test_string",
        resourceType = ResourceType.STRING,
        fileName = "strings.xml",
        dirNames = listOf("values"),
        value = "Hello World",
      )
    assertThat(success).isTrue()

    val stringsFile = resDir.findChild("values")?.findChild("strings.xml")
    assertThat(stringsFile).isNotNull()

    val psiFile = runReadAction { PsiManager.getInstance(project).findFile(stringsFile!!) } as XmlFile
    val resources = runReadAction { AndroidUtils.loadDomElementWithReadPermission(project, psiFile, Resources::class.java) }
    assertThat(resources).isNotNull()

    runReadAction {
      val strings = resources!!.strings
      assertThat(strings.map { it.name.value }).contains("test_string")
      assertThat(strings.first { it.name.value == "test_string" }.stringValue).isEqualTo("Hello World")
    }
  }

  @Test
  fun testCreateColorResourceValueAsync(): Unit = runBlocking {
    fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, """<?xml version="1.0" encoding="utf-8"?><manifest package="com.example"/>""")
    val resFile = fixture.addFileToProject("res/values/dummy.xml", "<resources></resources>")
    val resDir = resFile.virtualFile.parent.parent

    val success =
      createValueResourceAsync(
        project = project,
        resDir = resDir,
        resourceName = "test_color",
        resourceType = ResourceType.COLOR,
        fileName = "colors.xml",
        dirNames = listOf("values"),
        value = "#FF0000",
      )
    assertThat(success).isTrue()

    val colorsFile = resDir.findChild("values")?.findChild("colors.xml")
    assertThat(colorsFile).isNotNull()

    val psiFile = runReadAction { PsiManager.getInstance(project).findFile(colorsFile!!) } as XmlFile
    val resources = runReadAction { AndroidUtils.loadDomElementWithReadPermission(project, psiFile, Resources::class.java) }
    assertThat(resources).isNotNull()

    runReadAction {
      val colors = resources!!.colors
      assertThat(colors.map { it.name.value }).contains("test_color")
      assertThat(colors.first { it.name.value == "test_color" }.stringValue).isEqualTo("#FF0000")
    }
  }
}

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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

@RunsInEdt
class InspectorPropertyItemTest {
  private var componentStack: ComponentStack? = null
  private var model: InspectorModel? = null
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun setUp() {
    val project = projectRule.project
    model = model(project, DemoExample.setUpDemo(projectRule.fixture))
    componentStack = ComponentStack(project)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, Mockito.mock(FileEditorManager::class.java))
  }

  @After
  fun tearDown() {
    componentStack!!.restore()
    componentStack = null
    model = null
  }

  @Test
  fun testBrowseBackgroundInLayout() {
    val descriptor = browseProperty(ATTR_BACKGROUND, Type.DRAWABLE, null)
    Truth.assertThat(descriptor.file.name).isEqualTo("demo.xml")
    Truth.assertThat(CheckUtil.findLineAtOffset(descriptor.file, descriptor.offset))
      .isEqualTo("framework:background=\"@drawable/battery\"")
  }

  @Test
  fun testBrowseTextSizeFromTextAppearance() {
    val textAppearance = ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance.Material.Body1")
    val descriptor = browseProperty(ATTR_TEXT_SIZE, Type.INT32, textAppearance)
    Truth.assertThat(descriptor.file.name).isEqualTo("styles_material.xml")
    Truth.assertThat(CheckUtil.findLineAtOffset(descriptor.file, descriptor.offset))
      .isEqualTo("<item name=\"textSize\">@dimen/text_size_body_1_material</item>")
  }

  private fun browseProperty(attrName: String,
                             type: Type,
                             source: ResourceReference?): OpenFileDescriptor {
    val node = model!!["title"]!!
    val property = InspectorPropertyItem(
      ANDROID_URI, attrName, attrName, type, null, PropertySection.DECLARED, source ?: node.layout, node, model!!.resourceLookup)
    val fileManager = FileEditorManager.getInstance(projectRule.project)
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.`when`(fileManager.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))

    property.helpSupport.browse()
    Mockito.verify(fileManager).openEditor(file.capture(), ArgumentMatchers.eq(true))
    return file.value
  }
}

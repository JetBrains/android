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
import com.android.tools.idea.layoutinspector.util.InspectorBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

@RunsInEdt
class InspectorPropertyItemTest {
  private var componentStack: ComponentStack? = null

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    InspectorBuilder.setUpDemo(projectRule)
    componentStack = ComponentStack(projectRule.project)
    componentStack!!.registerComponentImplementation(FileEditorManager::class.java, Mockito.mock(FileEditorManager::class.java))
  }

  @After
  fun tearDown() {
    InspectorBuilder.tearDownDemo()
    componentStack!!.restoreComponents()
    componentStack = null
  }

  @Test
  fun testBrowseBackgroundInLayout() {
    val model = createModel()
    val descriptor = browseProperty(ATTR_BACKGROUND, Type.DRAWABLE, null, model)
    Truth.assertThat(descriptor.file.name).isEqualTo("demo.xml")
    Truth.assertThat(findLineAtOffset(descriptor.file, descriptor.offset))
      .isEqualTo("framework:background=\"@drawable/battery\"")
  }

  @Test
  fun testBrowseTextSizeFromTextAppearance() {
    val model = createModel()
    val textAppearance = ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance.Material.Body1")
    val descriptor = browseProperty(ATTR_TEXT_SIZE, Type.INT32, textAppearance, model)
    Truth.assertThat(descriptor.file.name).isEqualTo("styles_material.xml")
    Truth.assertThat(findLineAtOffset(descriptor.file, descriptor.offset))
      .isEqualTo("<item name=\"textSize\">@dimen/text_size_body_1_material</item>")
  }

  private fun browseProperty(attrName: String,
                             type: Type,
                             source: ResourceReference?,
                             model: InspectorPropertiesModel): OpenFileDescriptor {
    val textViewId = "title"
    val node = InspectorBuilder.findViewNode(model.layoutInspector!!, textViewId)!!
    val property = InspectorPropertyItem(ANDROID_URI, attrName, attrName, type, null, true, source ?: node.layout, node, model)
    val fileManager = FileEditorManager.getInstance(projectRule.project)
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.`when`(fileManager.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))

    property.helpSupport.browse()
    Mockito.verify(fileManager).openEditor(file.capture(), ArgumentMatchers.eq(true))
    return file.value
  }

  private fun createModel(): InspectorPropertiesModel {
    val model = InspectorPropertiesModel()
    model.layoutInspector = InspectorBuilder.createLayoutInspectorForDemo(projectRule)
    return model
  }

  private fun findLineAtOffset(file: VirtualFile, offset: Int): String {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return lineText.trim()
  }
}

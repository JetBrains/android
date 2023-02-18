/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_TEXT
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.LambdaParameterItem
import com.android.tools.idea.layoutinspector.ui.ResolutionElementEditor
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.impl.ui.IconWithFocusBorder
import com.android.tools.property.panel.impl.ui.PropertyLink
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.android.tools.property.panel.impl.ui.PropertyTextFieldWithLeftButton
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ResolutionStackEditorProviderTest {
  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val cleaner = MockitoCleanerRule()

  private val enumSupportProvider = object : EnumSupportProvider<InspectorPropertyItem> {
    override fun invoke(property: InspectorPropertyItem): EnumSupport? = null
  }

  private val controlTypeProvider = object : ControlTypeProvider<InspectorPropertyItem> {
    override fun invoke(property: InspectorPropertyItem): ControlType =
      when (property.type) {
        PropertyType.DRAWABLE,
        PropertyType.COLOR -> ControlType.COLOR_EDITOR
        PropertyType.LAMBDA -> ControlType.LINK_EDITOR
        else -> ControlType.TEXT_EDITOR
      }
  }

  @Test
  fun createTextEditor() {
    val property = InspectorPropertyItem(
      ANDROID_URI, ATTR_TEXT, PropertyType.STRING, "Hello", PropertySection.DECLARED, null, 2, mock())
    val propertiesModel = InspectorPropertiesModel()
    val provider = ResolutionStackEditorProvider(propertiesModel, enumSupportProvider, controlTypeProvider)
    val (_, editor) = provider.createEditor(property)
    assertThat(editor).isInstanceOf(PropertyTextField::class.java)
  }

  @Test
  fun createColorEditor() {
    val property = InspectorPropertyItem(
      ANDROID_URI, ATTR_BACKGROUND, PropertyType.COLOR, "#220088", PropertySection.DECLARED, null, 2, mock())
    val propertiesModel = InspectorPropertiesModel()
    val provider = ResolutionStackEditorProvider(propertiesModel, enumSupportProvider, controlTypeProvider)
    val (_, editor) = provider.createEditor(property)
    assertThat(editor).isInstanceOf(PropertyTextFieldWithLeftButton::class.java)
    assertThat(editor.getComponent(0)).isInstanceOf(IconWithFocusBorder::class.java)
  }

  @Test
  fun createLambdaEditor() {
    val property = LambdaParameterItem("onText", PropertySection.DEFAULT, -2, -1, 0, "com.example", "Text.kt", "f1$1", "", 34, 34, mock())
    val propertiesModel = InspectorPropertiesModel()
    val provider = ResolutionStackEditorProvider(propertiesModel, enumSupportProvider, controlTypeProvider)
    val (_, editor) = provider.createEditor(property)
    assertThat(editor).isInstanceOf(PropertyLink::class.java)
  }

  @Test
  fun createResolutionStackEditor() {
    val children = listOf(InspectorPropertyItem(
      "", "", PropertyType.COLOR, "#880088", PropertySection.DECLARED, null, 2, mock()))
    val property = InspectorGroupPropertyItem(
      ANDROID_URI, ATTR_BACKGROUND, PropertyType.COLOR, "#121212", null, PropertySection.DECLARED, null, 1, mock(), children)
    val propertiesModel = InspectorPropertiesModel()
    val provider = ResolutionStackEditorProvider(propertiesModel, enumSupportProvider, controlTypeProvider)
    val (_, editor) = provider.createEditor(property)
    assertThat(editor).isInstanceOf(ResolutionElementEditor::class.java)
  }
}

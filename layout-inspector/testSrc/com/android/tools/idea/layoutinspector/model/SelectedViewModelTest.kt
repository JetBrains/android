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
package com.android.tools.idea.layoutinspector.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.FQCN_BUTTON
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.SdkConstants.TEXT_VIEW
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.google.common.truth.Truth.assertThat
import com.intellij.util.text.nullize
import icons.StudioIcons
import org.junit.Test
import com.android.tools.idea.layoutinspector.properties.PropertyType

class SelectedViewModelTest {

  @Test
  fun testButtonWithId() {
    val name = nameOf(FQCN_BUTTON)
    val id = idOf("button1")
    val model = SelectedViewModel(name, id)
    assertThat(model.id).isEqualTo("@id/button1")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.BUTTON)
    assertThat(model.description).isEqualTo(BUTTON)
  }

  @Test
  fun testTextViewWithoutId() {
    val name = nameOf(FQCN_TEXT_VIEW)
    val id = idOf(null)
    val model = SelectedViewModel(name, id)
    assertThat(model.id).isEqualTo("<unnamed>")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(model.description).isEqualTo(TEXT_VIEW)
  }

  @Test
  fun testDecorView() {
    val name = nameOf("com.android.internal.policy.DecorView")
    val id = idOf(null)
    val model = SelectedViewModel(name, id)
    assertThat(model.id).isEqualTo("<unnamed>")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW)
    assertThat(model.description).isEqualTo("DecorView")
  }

  @Test
  fun testCoreText() {
    val name = nameOf("CoreText")
    val id = idOf("")
    val model = SelectedViewModel(name, id)
    assertThat(model.id).isEqualTo("")
    assertThat(model.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(model.description).isEqualTo("CoreText")
  }

  private fun nameOf(name: String): InspectorPropertyItem {
    val lookup: ViewNodeAndResourceLookup = mock()
    return InspectorPropertyItem(ANDROID_URI, ATTR_NAME, PropertyType.STRING, name, PropertySection.VIEW, null, 1L, lookup)
  }

  private fun idOf(id: String?): InspectorPropertyItem {
    val lookup: ViewNodeAndResourceLookup = mock()
    val value = id.nullize()?.let { "@id/$id" } ?: id
    return InspectorPropertyItem(ANDROID_URI, ATTR_ID, PropertyType.STRING, value, PropertySection.VIEW, null, 1L, lookup)
  }
}

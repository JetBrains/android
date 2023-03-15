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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_PADDING_BOTTOM
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.android.tools.dom.attrs.AttributeDefinition
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@RunsInEdt
class ControlTypeProviderImplTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testFlagEditorForFlagProperties() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, "definition", null, listOf(AttributeFormat.FLAGS))
    val property = util.makeFlagsProperty(ANDROID_URI, definition)
    val enumSupportProvider = createEnumSupportProvider()
    val enumSupport = mock(EnumSupport::class.java)
    whenever(enumSupportProvider.invoke(property)).thenReturn(enumSupport)
    val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)
    assertThat(controlTypeProvider(property)).isEqualTo(ControlType.FLAG_EDITOR)
  }

  @Test
  fun testComboBoxForEnumSupport() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NlPropertyType.DIMENSION)
    val enumSupportProvider = createEnumSupportProvider()
    val enumSupport = mock(EnumSupport::class.java)
    whenever(enumSupportProvider.invoke(property)).thenReturn(enumSupport)
    val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)
    assertThat(controlTypeProvider(property)).isEqualTo(ControlType.COMBO_BOX)
  }

  @Test
  fun testBooleanForBooleanTypes() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_CLICKABLE, NlPropertyType.THREE_STATE_BOOLEAN)
    val enumSupportProvider = createEnumSupportProvider()
    val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)
    assertThat(controlTypeProvider(property)).isEqualTo(ControlType.THREE_STATE_BOOLEAN)
  }

  @Test
  fun testTextEditorForEverythingElse() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_PADDING_BOTTOM, NlPropertyType.DIMENSION)
    val enumSupportProvider = createEnumSupportProvider()
    val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)
    assertThat(controlTypeProvider(property)).isEqualTo(ControlType.TEXT_EDITOR)
  }

  // Method here to isolate the unchecked cast to a single place
  private fun createEnumSupportProvider(): EnumSupportProvider<NlPropertyItem> {
    @Suppress("UNCHECKED_CAST")
    return mock(EnumSupportProvider::class.java) as EnumSupportProvider<NlPropertyItem>
  }
}

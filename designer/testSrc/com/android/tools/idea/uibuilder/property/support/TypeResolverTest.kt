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

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.dom.attrs.AttributeDefinition
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import org.junit.Test

class TypeResolverTest {

  @Test
  fun testBySpecialType() {
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_ABOVE, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_TO_END_OF, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_END_TO_END_OF, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_ALIGN_TOP, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_TOP_TO_TOP_OF, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_CHECKED_BUTTON, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_CHECKED_CHIP, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_ACCESSIBILITY_TRAVERSAL_AFTER, null)).isEqualTo(NlPropertyType.ID)
  }

  @Test
  fun testFromAttributeDefinition() {
    assertThat(resolve(ATTR_TEXT_ALL_CAPS, AttributeFormat.BOOLEAN)).isEqualTo(NlPropertyType.THREE_STATE_BOOLEAN)
    assertThat(resolve(ATTR_TEXT_COLOR, AttributeFormat.COLOR)).isEqualTo(NlPropertyType.COLOR_STATE_LIST)
    assertThat(resolve(ATTR_ELEVATION, AttributeFormat.DIMENSION)).isEqualTo(NlPropertyType.DIMENSION)
    assertThat(resolve(ATTR_MAXIMUM, AttributeFormat.FLOAT)).isEqualTo(NlPropertyType.FLOAT)
    assertThat(resolve(ATTR_TEXT_ALL_CAPS, AttributeFormat.FRACTION)).isEqualTo(NlPropertyType.FRACTION)
    assertThat(resolve(ATTR_TEXT, AttributeFormat.STRING)).isEqualTo(NlPropertyType.STRING)
    assertThat(resolve(ATTR_FONT_FAMILY, AttributeFormat.STRING)).isEqualTo(NlPropertyType.FONT)
    assertThat(resolve(ATTR_VISIBILITY, AttributeFormat.ENUM)).isEqualTo(NlPropertyType.ENUM)
    assertThat(resolve(ATTR_INPUT_TYPE, AttributeFormat.FLAGS)).isEqualTo(NlPropertyType.FLAGS)
    assertThat(resolve(ATTR_LAYOUT_WIDTH, AttributeFormat.ENUM, AttributeFormat.DIMENSION)).isEqualTo(NlPropertyType.DIMENSION)
  }

  @Test
  fun testFromNameLookup() {
    assertThat(TypeResolver.resolveType(ATTR_STYLE, null)).isEqualTo(NlPropertyType.STYLE)
    assertThat(TypeResolver.resolveType(ATTR_CLASS, null)).isEqualTo(NlPropertyType.FRAGMENT)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT, null)).isEqualTo(NlPropertyType.LAYOUT)
    assertThat(TypeResolver.resolveType(ATTR_SHOW_IN, null)).isEqualTo(NlPropertyType.LAYOUT)
    assertThat(TypeResolver.resolveType(ATTR_ELEVATION, null)).isEqualTo(NlPropertyType.DIMENSION)
    assertThat(TypeResolver.resolveType(ATTR_STATE_LIST_ANIMATOR, null)).isEqualTo(NlPropertyType.ANIMATOR)
  }

  @Test
  fun testFromNameFallback() {
    assertThat(TypeResolver.resolveType(ATTR_BACKGROUND, null)).isEqualTo(NlPropertyType.DRAWABLE)
    assertThat(TypeResolver.resolveType(ATTR_BACKGROUND_TINT, null)).isEqualTo(NlPropertyType.COLOR_STATE_LIST)
    assertThat(TypeResolver.resolveType(ATTR_TEXT_APPEARANCE, null)).isEqualTo(NlPropertyType.TEXT_APPEARANCE)
    assertThat(TypeResolver.resolveType(ATTR_SWITCH_TEXT_APPEARANCE, null)).isEqualTo(NlPropertyType.TEXT_APPEARANCE)
    assertThat(TypeResolver.resolveType(ATTR_SCROLLBAR_STYLE, null)).isEqualTo(NlPropertyType.STYLE)
    assertThat(TypeResolver.resolveType("XYZ", null)).isEqualTo(NlPropertyType.STRING)
  }

  private fun resolve(name: String, vararg formats: AttributeFormat): NlPropertyType {
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, name, null, setOf(*formats))
    return TypeResolver.resolveType(name, definition)
  }
}

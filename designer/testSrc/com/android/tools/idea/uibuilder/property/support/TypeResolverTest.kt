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

import com.android.SdkConstants.ATTR_ACCESSIBILITY_TRAVERSAL_AFTER
import com.android.SdkConstants.ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_BACKGROUND_TINT
import com.android.SdkConstants.ATTR_CHECKED_BUTTON
import com.android.SdkConstants.ATTR_CHECKED_CHIP
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_ELEVATION
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_LAYOUT_ABOVE
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_MAXIMUM
import com.android.SdkConstants.ATTR_SCROLLBAR_STYLE
import com.android.SdkConstants.ATTR_SHOW_IN
import com.android.SdkConstants.ATTR_STATE_LIST_ANIMATOR
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_SWITCH_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_ALL_CAPS
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.PreferenceAttributes.ATTR_DEFAULT_VALUE
import com.android.SdkConstants.PreferenceClasses
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiClass
import com.android.tools.dom.attrs.AttributeDefinition
import org.junit.Test


class TypeResolverTest {

  @Test
  fun testBySpecialType() {
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_ABOVE, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_TO_END_OF, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_END_TO_END_OF, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_ALIGN_TOP, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT_TOP_TO_TOP_OF, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_CHECKED_BUTTON, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_CHECKED_CHIP, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE, null, null)).isEqualTo(NlPropertyType.ID)
    assertThat(TypeResolver.resolveType(ATTR_ACCESSIBILITY_TRAVERSAL_AFTER, null, null)).isEqualTo(NlPropertyType.ID)
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
    assertThat(TypeResolver.resolveType(ATTR_STYLE, null, null)).isEqualTo(NlPropertyType.STYLE)
    assertThat(TypeResolver.resolveType(ATTR_CLASS, null, null)).isEqualTo(NlPropertyType.FRAGMENT)
    assertThat(TypeResolver.resolveType(ATTR_LAYOUT, null, null)).isEqualTo(NlPropertyType.LAYOUT)
    assertThat(TypeResolver.resolveType(ATTR_SHOW_IN, null, null)).isEqualTo(NlPropertyType.LAYOUT)
    assertThat(TypeResolver.resolveType(ATTR_ELEVATION, null, null)).isEqualTo(NlPropertyType.DIMENSION)
    assertThat(TypeResolver.resolveType(ATTR_STATE_LIST_ANIMATOR, null, null)).isEqualTo(NlPropertyType.ANIMATOR)
  }

  @Test
  fun testFromNameFallback() {
    assertThat(TypeResolver.resolveType(ATTR_BACKGROUND, null, null)).isEqualTo(NlPropertyType.DRAWABLE)
    assertThat(TypeResolver.resolveType(ATTR_BACKGROUND_TINT, null, null)).isEqualTo(NlPropertyType.COLOR_STATE_LIST)
    assertThat(TypeResolver.resolveType(ATTR_TEXT_APPEARANCE, null, null)).isEqualTo(NlPropertyType.TEXT_APPEARANCE)
    assertThat(TypeResolver.resolveType(ATTR_SWITCH_TEXT_APPEARANCE, null, null)).isEqualTo(NlPropertyType.TEXT_APPEARANCE)
    assertThat(TypeResolver.resolveType(ATTR_SCROLLBAR_STYLE, null, null)).isEqualTo(NlPropertyType.STYLE)
    assertThat(TypeResolver.resolveType("XYZ", null, null)).isEqualTo(NlPropertyType.STRING)
  }

  @Test
  fun testPreferenceDefaultValue() {
    val editTextPreference = createPsiClass(
      PreferenceClasses.CLASS_EDIT_TEXT_PREFERENCE,
      PreferenceClasses.CLASS_DIALOG_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, editTextPreference)).isEqualTo(NlPropertyType.STRING)

    val listPreference = createPsiClass(
      PreferenceClasses.CLASS_LIST_PREFERENCE,
      PreferenceClasses.CLASS_DIALOG_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, listPreference)).isEqualTo(NlPropertyType.STRING)

    val multiSelectListPreference = createPsiClass(
      PreferenceClasses.CLASS_MULTI_SELECT_LIST_PREFERENCE,
      PreferenceClasses.CLASS_DIALOG_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, multiSelectListPreference)).isEqualTo(NlPropertyType.STRING_ARRAY)

    val multiCheckListPreference = createPsiClass(
      PreferenceClasses.CLASS_MULTI_CHECK_PREFERENCE,
      PreferenceClasses.CLASS_DIALOG_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, multiCheckListPreference)).isEqualTo(NlPropertyType.STRING)

    val ringtonePreference = createPsiClass(
      PreferenceClasses.CLASS_RINGTONE_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, ringtonePreference)).isEqualTo(NlPropertyType.STRING)

    val seekBarPreference = createPsiClass(
      PreferenceClasses.CLASS_SEEK_BAR_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, seekBarPreference)).isEqualTo(NlPropertyType.INTEGER)

    val checkBoxPreference = createPsiClass(
      PreferenceClasses.CLASS_CHECK_BOX_PREFERENCE,
      PreferenceClasses.CLASS_TWO_STATE_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, checkBoxPreference)).isEqualTo(NlPropertyType.THREE_STATE_BOOLEAN)

    val switchPreference = createPsiClass(
      PreferenceClasses.CLASS_SWITCH_PREFERENCE,
      PreferenceClasses.CLASS_TWO_STATE_PREFERENCE,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, switchPreference)).isEqualTo(NlPropertyType.THREE_STATE_BOOLEAN)

    val dialogPreference = createPsiClass(
      PreferenceClasses.CLASS_PREFERENCE_CATEGORY,
      PreferenceClasses.CLASS_PREFERENCE_GROUP,
      PreferenceClasses.CLASS_PREFERENCE
    )
    assertThat(TypeResolver.resolveType(ATTR_DEFAULT_VALUE, null, dialogPreference)).isEqualTo(NlPropertyType.UNKNOWN)
  }

  private fun createPsiClass(vararg names: String): PsiClass? {
    var psiClass: PsiClass? = null
    names.reverse()
    for (name in names) {
      psiClass = mock<PsiClass?>().apply {
        whenever(this?.superClass).thenReturn(psiClass)
        whenever(this?.qualifiedName).thenReturn(name)
      }
    }
    return psiClass
  }

  private fun resolve(name: String, vararg formats: AttributeFormat): NlPropertyType {
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, name, null, setOf(*formats))
    return TypeResolver.resolveType(name, definition, null)
  }
}

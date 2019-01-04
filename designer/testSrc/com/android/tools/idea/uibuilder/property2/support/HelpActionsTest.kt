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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.CLASS_VIEWGROUP
import com.android.SdkConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.FQCN_IMAGE_VIEW
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class HelpActionsTest {

  @Test
  fun testToHelpUrl() {
    assertThat(toHelpUrl(FQCN_IMAGE_VIEW, ATTR_SRC)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/widget/ImageView.html#attr_android:src")

    assertThat(toHelpUrl(FQCN_TEXT_VIEW, ATTR_FONT_FAMILY)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/widget/TextView.html#attr_android:fontFamily")

    assertThat(toHelpUrl(CLASS_VIEWGROUP, ATTR_LAYOUT_HEIGHT)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/view/ViewGroup.LayoutParams.html#attr_android:layout_height")

    assertThat(toHelpUrl(CLASS_VIEWGROUP, ATTR_LAYOUT_MARGIN_BOTTOM)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/view/ViewGroup.MarginLayoutParams.html#attr_android:layout_marginBottom")

    assertThat(toHelpUrl(CONSTRAINT_LAYOUT.oldName(), ATTR_LAYOUT_TO_END_OF)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/support/constraint/ConstraintLayout.LayoutParams.html")

    assertThat(toHelpUrl(CONSTRAINT_LAYOUT.newName(), ATTR_LAYOUT_TO_END_OF)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}androidx/constraintlayout/widget/ConstraintLayout.LayoutParams.html")

    assertThat(toHelpUrl("com.company.MyView", "my_attribute")).isNull()
  }

  private fun toHelpUrl(componentName: String, propertyName: String): String? {
    val property = mock(NelePropertyItem::class.java)
    `when`(property.name).thenReturn(propertyName)
    return HelpActions.toHelpUrl(componentName, property)
  }
}
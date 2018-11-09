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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.tools.idea.uibuilder.api.XmlType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabLayoutHandlerTest {

  @Test
  fun testXmlWithOldName() {
    val handler = TabLayoutHandler()
    assertThat(handler.getXml(SdkConstants.TAB_LAYOUT.oldName(), XmlType.COMPONENT_CREATION)).isEqualTo("""
      <android.support.design.widget.TabLayout
          android:layout_width="match_parent"
          android:layout_height="wrap_content">

          <android.support.design.widget.TabItem
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Monday" />

          <android.support.design.widget.TabItem
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Tuesday" />

          <android.support.design.widget.TabItem
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Wednesday" />
      </android.support.design.widget.TabLayout>

      """.trimIndent())
  }

  @Test
  fun testXmlWithNewName() {
    val handler = TabLayoutHandler()
    assertThat(handler.getXml(SdkConstants.TAB_LAYOUT.newName(), XmlType.COMPONENT_CREATION)).isEqualTo("""
      <com.google.android.material.tabs.TabLayout
          android:layout_width="match_parent"
          android:layout_height="wrap_content">

          <com.google.android.material.tabs.TabItem
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Monday" />

          <com.google.android.material.tabs.TabItem
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Tuesday" />

          <com.google.android.material.tabs.TabItem
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Wednesday" />
      </com.google.android.material.tabs.TabLayout>

      """.trimIndent())
  }
}

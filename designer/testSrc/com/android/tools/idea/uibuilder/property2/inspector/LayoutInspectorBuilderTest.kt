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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LayoutInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testLinearLayout() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    val builder = LayoutInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, InspectorSection.LAYOUT.title)
    util.checkEditor(1, ANDROID_URI, ATTR_LAYOUT_WIDTH)
    util.checkEditor(2, ANDROID_URI, ATTR_LAYOUT_HEIGHT)
    util.checkEditor(3, ANDROID_URI, ATTR_LAYOUT_WEIGHT)
    util.checkEditor(4, ANDROID_URI, ATTR_VISIBILITY)
    util.checkEditor(5, TOOLS_URI, ATTR_VISIBILITY)
    assertThat(util.inspector.lines).hasSize(6)
  }
}

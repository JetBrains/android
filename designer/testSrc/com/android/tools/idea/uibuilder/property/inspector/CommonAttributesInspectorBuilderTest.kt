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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ADJUST_VIEW_BOUNDS
import com.android.SdkConstants.ATTR_ALPHA
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_CROP_TO_PADDING
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_INDETERMINATE
import com.android.SdkConstants.ATTR_INDETERMINATE_DRAWABLE
import com.android.SdkConstants.ATTR_INDETERMINATE_TINT
import com.android.SdkConstants.ATTR_LINE_SPACING_EXTRA
import com.android.SdkConstants.ATTR_MAXIMUM
import com.android.SdkConstants.ATTR_PROGRESS
import com.android.SdkConstants.ATTR_PROGRESS_DRAWABLE
import com.android.SdkConstants.ATTR_PROGRESS_TINT
import com.android.SdkConstants.ATTR_SCALE_TYPE
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_TEXT_STYLE
import com.android.SdkConstants.ATTR_TYPEFACE
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.PROGRESS_BAR
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class CommonAttributesInspectorBuilderTest {

  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    addManifest(projectRule.fixture)
  }

  @Test
  fun testTextView() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = CommonAttributesInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT)
    util.checkEditor(2, TOOLS_URI, ATTR_TEXT)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_TEXT_APPEARANCE)
    util.checkEditor(5, ANDROID_URI, ATTR_FONT_FAMILY)
    util.checkEditor(6, ANDROID_URI, ATTR_TYPEFACE)
    util.checkEditor(7, ANDROID_URI, ATTR_TEXT_SIZE)
    util.checkEditor(8, ANDROID_URI, ATTR_LINE_SPACING_EXTRA)
    util.checkEditor(9, ANDROID_URI, ATTR_TEXT_COLOR)
    util.checkEditor(10, ANDROID_URI, ATTR_TEXT_STYLE)
    util.checkEditor(11, ANDROID_URI, ATTR_TEXT_ALIGNMENT)
    util.checkEditor(12, ANDROID_URI, ATTR_ALPHA)
    assertThat(util.inspector.lines).hasSize(13)
  }

  @Test
  fun testProgressBar() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = CommonAttributesInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_PROGRESS_DRAWABLE)
    util.checkEditor(3, ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE)
    util.checkEditor(4, ANDROID_URI, ATTR_PROGRESS_TINT)
    util.checkEditor(5, ANDROID_URI, ATTR_INDETERMINATE_TINT)
    util.checkEditor(6, ANDROID_URI, ATTR_MAXIMUM)
    util.checkEditor(7, ANDROID_URI, ATTR_PROGRESS)
    util.checkEditor(8, ANDROID_URI, ATTR_INDETERMINATE)
    util.checkEditor(9, ANDROID_URI, ATTR_ALPHA)
    assertThat(util.inspector.lines).hasSize(10)
  }

  @Test
  fun testImageView() {
    val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
    val builder = CommonAttributesInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, "Common Attributes")
    util.checkEditor(1, ANDROID_URI, ATTR_SRC)
    util.checkEditor(2, TOOLS_URI, ATTR_SRC)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_BACKGROUND)
    util.checkEditor(5, ANDROID_URI, ATTR_SCALE_TYPE)
    util.checkEditor(6, ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS)
    util.checkEditor(7, ANDROID_URI, ATTR_CROP_TO_PADDING)
    util.checkEditor(8, ANDROID_URI, ATTR_ALPHA)
    assertThat(util.inspector.lines).hasSize(9)
  }
}

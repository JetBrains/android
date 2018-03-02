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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

class ViewInspectorBuilderTest: AndroidTestCase() {

  fun testAllButtonProperties() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, BUTTON)
    val builder = ViewInspectorBuilder(project, util.editorProvider)
    addButtonProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(8)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND)
    assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND_TINT)
    assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_STATE_LIST_ANIMATOR)
    assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_ELEVATION)
    assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_VISIBILITY)
    assertThat(util.inspector.lines[7].editorModel?.property?.name).isEqualTo(ATTR_ON_CLICK)
  }

  fun testButtonWithSomeMissingProperties() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, BUTTON)
    val builder = ViewInspectorBuilder(project, util.editorProvider)
    addButtonProperties(util)
    util.removeProperty(ANDROID_URI, ATTR_BACKGROUND_TINT)
    util.removeProperty(ANDROID_URI, ATTR_VISIBILITY)
    util.removeProperty(ANDROID_URI, ATTR_ON_CLICK)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(5)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND)
    assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_STATE_LIST_ANIMATOR)
    assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_ELEVATION)
  }

  private fun addButtonProperties(util: InspectorTestUtil) {
    util.addProperty("", ATTR_STYLE, NelePropertyType.STYLE)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND_TINT, NelePropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_STATE_LIST_ANIMATOR, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_ELEVATION, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_ON_CLICK, NelePropertyType.STRING)
  }
}

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

class LayoutInspectorBuilderTest: AndroidTestCase() {

  fun testLinearLayout() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW, LINEAR_LAYOUT)
    val builder = LayoutInspectorBuilder(project, util.editorProvider)
    addLayoutProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(3)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].title).isEqualTo("layout")
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_LAYOUT_WEIGHT)
  }

  private fun addLayoutProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_WEIGHT, NelePropertyType.DIMENSION)
  }
}

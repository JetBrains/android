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
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

class FavoritesInspectorBuilderTest: AndroidTestCase() {

  fun testNotApplicableWhenNoFavorites() {
    val propertiesComponent = PropertiesComponentMock()
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.editorProvider, propertiesComponent)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(0)
  }

  fun testApplicableWith2Properties() {
    val propertiesComponent = PropertiesComponentMock()
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.editorProvider, propertiesComponent)
    propertiesComponent.setValue(STARRED_PROP, "gravity;paddingBottom;nonExistingProperty")
    util.addProperty(ANDROID_URI, ATTR_GRAVITY, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_PADDING_BOTTOM, NelePropertyType.INTEGER)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(4)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].title).isEqualTo("Favorite Attributes")
    assertThat(util.inspector.lines[1].expandable).isTrue()
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_GRAVITY)
    assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_PADDING_BOTTOM)
  }
}

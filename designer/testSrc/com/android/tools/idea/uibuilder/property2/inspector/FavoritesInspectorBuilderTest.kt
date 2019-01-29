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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class FavoritesInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testNotApplicableWhenNoFavorites() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val propertiesComponent = PropertiesComponentMock()
    val builder = FavoritesInspectorBuilder(util.editorProvider, propertiesComponent)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(0)
  }

  @Test
  fun testApplicableWith2Properties() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val propertiesComponent = PropertiesComponentMock()
    val builder = FavoritesInspectorBuilder(util.editorProvider, propertiesComponent)
    propertiesComponent.setValue(STARRED_PROP, "gravity;paddingBottom;nonExistingProperty")
    util.addProperty(ANDROID_URI, ATTR_GRAVITY, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_PADDING_BOTTOM, NelePropertyType.INTEGER)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(3)
    assertThat(util.inspector.lines[0].title).isEqualTo("Favorite Attributes")
    assertThat(util.inspector.lines[0].expandable).isTrue()
    assertThat(util.inspector.lines[1].editorModel?.property?.name).isEqualTo(ATTR_GRAVITY)
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_PADDING_BOTTOM)
  }
}

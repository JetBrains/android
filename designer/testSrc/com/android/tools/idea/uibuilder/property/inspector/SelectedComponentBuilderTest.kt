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

import com.android.SdkConstants
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.property.panel.impl.model.util.FakeLineType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel

@RunsInEdt
class SelectedComponentBuilderTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Test
  fun testSelectedComponent() {
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    util.addProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT, NlPropertyType.STRING)
    val builder = SelectedComponentBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(1)
    assertThat(util.inspector.lines[0].type).isEqualTo(FakeLineType.PANEL)
  }

  @Test
  fun testNoProperties() {
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    val builder = SelectedComponentBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).isEmpty()
  }

  @Test
  fun testUpdateIdValue() {
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    util.addProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID, NlPropertyType.ID)
    util.select { it.tagName == SdkConstants.TEXT_VIEW }
    val builder = SelectedComponentBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val component = util.inspector.lines[0].component!!
    FakeUi(component, createFakeWindow = true)
    val idComponent = component.components[1] as JLabel
    assertThat(idComponent.text).isEqualTo("@+id/textview")

    val idProperty = util.makeProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID, NlPropertyType.ID)
    idProperty.value = ""
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    util.model.firePropertyValueChangeIfNeeded()
    assertThat(idComponent.text).isEqualTo("<unnamed>")

    idProperty.value = "@+id/text123"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    util.model.firePropertyValueChangeIfNeeded()
    assertThat(idComponent.text).isEqualTo("@+id/text123")
  }
}

/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.ui.ExpandableLabel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class SelectedViewBuilderTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun testBuilder() {
    val model =
      model(disposableRule.disposable) {
        view(ROOT) { view(VIEW1, viewId = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "view1")) }
      }
    val layoutInspector: LayoutInspector = mock()
    whenever(layoutInspector.inspectorModel).thenReturn(model)
    val propertiesModel = InspectorPropertiesModel(disposableRule.disposable)
    propertiesModel.layoutInspector = layoutInspector
    val panel = FakeInspectorPanel()
    val builder = SelectedViewBuilder(propertiesModel)

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    builder.attachToInspector(panel, PropertiesTable.emptyTable())
    assertThat(panel.lines).hasSize(1)
    val component = panel.lines[0].component!!
    val left = component.components[0] as ExpandableLabel
    val right = component.components[1] as ExpandableLabel
    assertThat(left.actualText).isEqualTo("View")
    assertThat(left.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW)
    assertThat(right.actualText).isEqualTo("@id/view1")

    panel.lines.clear()
    model.setSelection(null, SelectionOrigin.INTERNAL)
    builder.attachToInspector(panel, PropertiesTable.emptyTable())
    assertThat(panel.lines).isEmpty()
  }
}

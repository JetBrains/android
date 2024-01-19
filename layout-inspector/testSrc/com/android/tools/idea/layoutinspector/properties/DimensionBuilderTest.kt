/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.resources.Density
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.model.util.FakeLineType
import com.google.common.collect.HashBasedTable
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class DimensionBuilderTest {
  @get:Rule val disposableRule = DisposableRule()

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @Before
  fun before() {
    val application = ApplicationManager.getApplication()
    application.registerServiceInstance(
      PropertiesComponent::class.java,
      PropertiesComponentMock(),
      disposableRule.disposable,
    )
  }

  @Test
  fun testDimensionModel() {
    val model =
      model(disposableRule.disposable) { view(ROOT, 10, 20, 30, 40, qualifiedName = "rootType") }
    val properties =
      PropertiesTable.create(HashBasedTable.create<String, String, InspectorPropertyItem>())
    addInternalProperties(properties, model[1L]!!, "root", model)
    val inspector = FakeInspectorPanel()
    DimensionBuilder.attachToInspector(inspector, properties)
    assertThat(inspector.lines).hasSize(1)
    val lineModel = inspector.lines.single()
    assertThat(lineModel.isSearchable).isTrue()
    assertThat(lineModel.type).isEqualTo(FakeLineType.TABLE)
    val tableModel = lineModel.tableModel!!
    assertThat(tableModel.items).hasSize(4)
    assertThat(tableModel.items[0].name).isEqualTo("x")
    assertThat(tableModel.items[1].name).isEqualTo("y")
    assertThat(tableModel.items[2].name).isEqualTo("width")
    assertThat(tableModel.items[3].name).isEqualTo("height")

    // In Pixels
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
    assertThat(tableModel.items[0].value).isEqualTo("10px")
    assertThat(tableModel.items[1].value).isEqualTo("20px")
    assertThat(tableModel.items[2].value).isEqualTo("30px")
    assertThat(tableModel.items[3].value).isEqualTo("40px")

    // In dp
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    model.resourceLookup.dpi = Density.XHIGH.dpiValue
    assertThat(tableModel.items[0].value).isEqualTo("5dp")
    assertThat(tableModel.items[1].value).isEqualTo("10dp")
    assertThat(tableModel.items[2].value).isEqualTo("15dp")
    assertThat(tableModel.items[3].value).isEqualTo("20dp")
  }
}

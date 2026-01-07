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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.resources.Density.MEDIUM
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ParameterItemTest {
  private val lookup: ViewNodeAndResourceLookup = mock()
  private val resourceLookup: ResourceLookup = mock()

  @get:Rule val rule = ApplicationRule()

  @Before
  fun before() {
    whenever(lookup.resourceLookup).thenReturn(resourceLookup)
    whenever(resourceLookup.dpi).thenReturn(MEDIUM.dpiValue)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  @Test
  fun testUpdateSimpleValue() {
    val p1 = createParameterItem("count", "2", 0, PropertyType.INT32)
    val p2 = createParameterItem("count", "3", 0, PropertyType.INT32)
    val childElementChanges = p1.updateValue(p2)
    assertThat(p1.value).isEqualTo("3")
    assertThat(childElementChanges).isFalse()
  }

  @Test
  fun testUpdateGroupValueWithSameElements() {
    val p1 =
      createParameterGroupItem("count", "List[2]", 0) { outer ->
        outer.add(createParameterItem("count", "2", 0, PropertyType.INT32))
        outer.add(
          createParameterGroupItem("array", "Array[2]", 1) { inner ->
            inner.add(createParameterItem("text", "value", 0, PropertyType.STRING))
            inner.add(createParameterItem("size", "34", 1, PropertyType.DIMENSION))
          }
        )
      }
    val oldArrayValue = p1.children[1]
    val p2 =
      createParameterGroupItem("count", "List[2]", 0) { outer ->
        outer.add(createParameterItem("count", "15", 0, PropertyType.INT32))
        outer.add(
          createParameterGroupItem("array", "Array[2]", 1) { inner ->
            inner.add(createParameterItem("text", "newValue", 0, PropertyType.STRING))
            inner.add(createParameterItem("size", "50", 1, PropertyType.DIMENSION))
          }
        )
      }
    val childElementChanges = p1.updateValue(p2)
    assertThat(p1.value).isEqualTo("List[2]")
    assertThat(p1.children[0].value).isEqualTo("15")
    assertThat(p1.children[1]).isSameAs(oldArrayValue)
    assertThat(p1.children[1].value).isEqualTo("Array[2]")
    assertThat(p1.children[1].children[0].value).isEqualTo("newValue")
    assertThat(p1.children[1].children[1].value).isEqualTo("50px")
    assertThat(childElementChanges).isFalse()
  }

  @Test
  fun testUpdateGroupValueWithExtraElements() {
    val p1 =
      createParameterGroupItem("count", "List[2]", 0) { outer ->
        outer.add(createParameterItem("count", "2", 0, PropertyType.INT32))
        outer.add(
          createParameterGroupItem("array", "Array[2]", 1) { inner ->
            inner.add(createParameterItem("text", "value", 0, PropertyType.STRING))
            inner.add(createParameterItem("size", "34", 1, PropertyType.DIMENSION))
          }
        )
      }
    val oldArrayValue = p1.children[1]
    val p2 =
      createParameterGroupItem("count", "List[2]", 0) { outer ->
        outer.add(createParameterItem("count", "15", 0, PropertyType.INT32))
        outer.add(
          createParameterGroupItem("array", "Array[3]", 1) { inner ->
            inner.add(createParameterItem("text", "newValue", 0, PropertyType.STRING))
            inner.add(createParameterItem("size", "50", 1, PropertyType.DIMENSION))
            inner.add(createParameterItem("direction", "north", 1, PropertyType.STRING))
          }
        )
      }
    val childElementChanges = p1.updateValue(p2)
    assertThat(p1.value).isEqualTo("List[2]")
    assertThat(p1.children[0].value).isEqualTo("15")
    assertThat(p1.children[1]).isSameAs(oldArrayValue)
    assertThat(p1.children[1].value).isEqualTo("Array[3]")
    assertThat(p1.children[1].children[0].value).isEqualTo("newValue")
    assertThat(p1.children[1].children[1].value).isEqualTo("50px")
    assertThat(p1.children[1].children[2].value).isEqualTo("north")
    assertThat(childElementChanges).isTrue()
  }

  private fun createParameterItem(
    name: String,
    value: String?,
    index: Int,
    type: PropertyType = PropertyType.STRING,
    section: PropertySection = PropertySection.PARAMETERS,
    viewId: Long = COMPOSE1,
    rootId: Long = ROOT,
  ): ParameterItem {
    return ParameterItem(name, type, value, section, viewId, lookup, rootId, index)
  }

  private fun createParameterGroupItem(
    name: String,
    value: String?,
    index: Int,
    reference: ParameterReference? = null,
    type: PropertyType = PropertyType.ITERABLE,
    section: PropertySection = PropertySection.PARAMETERS,
    viewId: Long = COMPOSE1,
    rootId: Long = ROOT,
    block: (MutableList<ParameterItem>) -> Unit = {},
  ): ParameterGroupItem {
    val children = mutableListOf<ParameterItem>()
    block(children)
    return ParameterGroupItem(
      name,
      type,
      value,
      section,
      viewId,
      lookup,
      rootId,
      index,
      reference,
      children,
    )
  }

  private val ParameterItem.children: List<ParameterItem>
    get() = (this as ParameterGroupItem).children
}

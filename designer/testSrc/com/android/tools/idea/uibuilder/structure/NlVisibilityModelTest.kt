/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure

import com.android.SdkConstants
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import org.junit.Test
import org.mockito.Mockito

class NlVisibilityModelTest : LayoutTestCase() {

  private val listOfVisibility: List<String?> = listOf(null, "invisible", "gone")

  private fun generateModel(android: Visibility, tools: Visibility): NlVisibilityModel {
    val component =
      component(SdkConstants.LINEAR_LAYOUT).withBounds(0, 0, 1000, 1000).id("@id/linear")

    if (android != Visibility.NONE) {
      component.withAttribute(SdkConstants.ANDROID_URI, "visibility", Visibility.convert(android)!!)
    }
    if (tools != Visibility.NONE) {
      component.withAttribute(SdkConstants.TOOLS_URI, "visibility", Visibility.convert(tools)!!)
    }
    val model = model("visibility.xml", component).build()

    val nlComponent = model.getRoot()
    return NlVisibilityModel(nlComponent)
  }

  @Test
  fun testConstructor() {
    val model = generateModel(Visibility.NONE, Visibility.NONE)
    assertEquals(Visibility.NONE, model.androidVisibility)
    assertEquals(Visibility.NONE, model.toolsVisibility)
  }

  @Test
  fun testConstructorAndroid() {
    val model = generateModel(Visibility.VISIBLE, Visibility.NONE)

    assertEquals(Visibility.VISIBLE, model.androidVisibility)
    assertEquals(Visibility.VISIBLE, model.getCurrentVisibility())
    assertEquals(Visibility.NONE, model.toolsVisibility)
  }

  @Test
  fun testConstructorTools() {
    val model = generateModel(Visibility.NONE, Visibility.GONE)

    assertEquals(Visibility.NONE, model.androidVisibility)
    assertEquals(Visibility.GONE, model.toolsVisibility)
    assertEquals(Visibility.GONE, model.getCurrentVisibility())
  }

  @Test
  fun testConstructorBoth() {
    val model = generateModel(Visibility.INVISIBLE, Visibility.VISIBLE)

    assertEquals(Visibility.INVISIBLE, model.androidVisibility)
    assertEquals(Visibility.VISIBLE, model.toolsVisibility)
    assertEquals(Visibility.VISIBLE, model.getCurrentVisibility())
  }

  @Test
  fun testContains() {
    val model = generateModel(Visibility.INVISIBLE, Visibility.VISIBLE)

    assertTrue(model.contains(Visibility.INVISIBLE, SdkConstants.ANDROID_URI))
    assertTrue(model.contains(Visibility.VISIBLE, SdkConstants.TOOLS_URI))
    assertFalse(model.contains(Visibility.NONE, SdkConstants.ANDROID_URI))
    assertFalse(model.contains(Visibility.INVISIBLE, SdkConstants.TOOLS_URI))
  }

  @Test
  fun testWriteAttribute() {
    val model = generateModel(Visibility.NONE, Visibility.NONE)
    val uri = SdkConstants.ANDROID_URI

    model.writeToComponent(Visibility.VISIBLE, uri)
    assertEquals(Visibility.VISIBLE, model.androidVisibility)
  }

  @Test
  fun testRemoveAttribute() {
    val model = generateModel(Visibility.VISIBLE, Visibility.VISIBLE)
    val uri = SdkConstants.TOOLS_URI

    model.writeToComponent(Visibility.NONE, uri)
    assertEquals(Visibility.NONE, model.toolsVisibility)
  }

  @Test
  fun testVisibleComponentFromGoneParent() {
    val parentVisibility = "gone"
    listOfVisibility.forEach { assertAgainstParent(it, parentVisibility, Visibility.GONE) }
  }

  @Test
  fun testVisibleComponentFromInvisibleParent() {
    val parentVisibility = "invisible"
    assertAgainstParent(null, parentVisibility, Visibility.INVISIBLE)
    assertAgainstParent("invisible", parentVisibility, Visibility.INVISIBLE)
    assertAgainstParent("gone", parentVisibility, Visibility.GONE)
  }

  @Test
  fun testInvisibleComponentFromVisibleParent() {
    val parentVisibility = null
    assertAgainstParent(null, parentVisibility, Visibility.NONE)
    assertAgainstParent("invisible", parentVisibility, Visibility.INVISIBLE)
    assertAgainstParent("gone", parentVisibility, Visibility.GONE)
  }

  @Test
  fun testVisibilityFromGoneGrandParent() {
    val grandparentVisibility = "gone"

    listOfVisibility.forEach { currVisibility ->
      listOfVisibility.forEach { parentVisibility ->
        assertAgainstGrandParent(
          currVisibility,
          parentVisibility,
          grandparentVisibility,
          Visibility.GONE,
        )
      }
    }
  }

  private fun assertAgainstParent(
    currentVisibility: String?,
    parentVisibility: String?,
    expected: Visibility,
  ) {
    val component = mockComponent(currentVisibility)
    val parent = mockComponent(parentVisibility)

    whenever(component.parent).thenReturn(parent)

    assertEquals(expected, getVisibilityFromParents(component))
  }

  private fun assertAgainstGrandParent(
    currentVisibility: String?,
    parentVisibility: String?,
    grandparentVisibility: String?,
    expected: Visibility,
  ) {
    val component = mockComponent(currentVisibility)
    val parent = mockComponent(parentVisibility)
    val grandParent = mockComponent(grandparentVisibility)

    whenever(component.parent).thenReturn(parent)
    whenever(parent.parent).thenReturn(grandParent)

    assertEquals(expected, getVisibilityFromParents(component))
  }

  private fun mockComponent(visibility: String? = null): NlComponent {
    val component: NlComponent = Mockito.mock(NlComponent::class.java)
    whenever(component.getAttribute(SdkConstants.ANDROID_URI, "visibility")).thenReturn(visibility)
    return component
  }
}

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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentModificationDelegate
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class NlVisibilityModelTest {

  companion object {
    fun generateModel(android: Visibility, tools: Visibility): NlVisibilityModel {
      val component: NlComponent = Mockito.mock(NlComponent::class.java)
      Mockito.`when`(component.getAttribute(SdkConstants.ANDROID_URI, "visibility"))
        .thenReturn(Visibility.convert(android))
      Mockito.`when`(component.getAttribute(SdkConstants.TOOLS_URI, "visibility"))
        .thenReturn(Visibility.convert(tools))
      val delegate = Mockito.mock(NlComponentModificationDelegate::class.java)
      Mockito.`when`(component.componentModificationDelegate).thenReturn(delegate)
      return NlVisibilityModel(component)
    }
  }
  @Test
  fun testConstructor() {
    val component: NlComponent = Mockito.mock(NlComponent::class.java)
    val model = NlVisibilityModel(component)
    assertEquals(Visibility.NONE, model.androidVisibility)
    assertEquals(Visibility.NONE, model.toolsVisibility)
  }

  @Test
  fun testConstructorAndroid() {
    val model = generateModel(Visibility.VISIBLE, Visibility.NONE)

    assertEquals(Visibility.VISIBLE, model.androidVisibility)
    assertEquals(Visibility.VISIBLE, model.getCurrentVisibility().first)
    assertEquals(Visibility.NONE, model.toolsVisibility)
  }

  @Test
  fun testConstructorTools() {
    val model = generateModel(Visibility.NONE, Visibility.GONE)

    assertEquals(Visibility.NONE, model.androidVisibility)
    assertEquals(Visibility.GONE, model.toolsVisibility)
    assertEquals(Visibility.GONE, model.getCurrentVisibility().first)
  }

  @Test
  fun testConstructorBoth() {
    val model = generateModel(Visibility.INVISIBLE, Visibility.VISIBLE)

    assertEquals(Visibility.INVISIBLE, model.androidVisibility)
    assertEquals(Visibility.VISIBLE, model.toolsVisibility)
    assertEquals(Visibility.VISIBLE, model.getCurrentVisibility().first)
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
}

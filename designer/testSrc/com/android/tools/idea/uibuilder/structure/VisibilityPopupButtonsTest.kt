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
import com.android.tools.idea.common.model.NlComponentModificationDelegate
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import org.junit.Test
import org.mockito.Mockito

class VisibilityPopupButtonsTest : LayoutTestCase() {

  @Test
  fun testDefaultSelected() {
    Visibility.values().forEach { assertDefaultSelected(it) }
  }

  private fun assertDefaultSelected(visibility: Visibility) {
    var callbackTriggered = false
    val buttons =
      VisibilityPopupButtons(SdkConstants.ANDROID_URI) { _: Visibility, _: String ->
        callbackTriggered = true
      }
    buttons.update(generateModel(visibility, Visibility.VISIBLE))

    val clicked = ArrayList<NlVisibilityButton>()
    buttons.buttons.forEach {
      if (it.isClicked) {
        clicked.add(it)
      }
    }
    assertEquals(1, clicked.size)
    assertEquals(visibility, clicked[0].visibility)
    assertFalse(callbackTriggered)
  }

  @Test
  fun testClick() {
    assertOneItemClicked(
      0,
      Visibility.values()[0],
      false,
      generateModel(Visibility.GONE, Visibility.GONE),
    )
    for (i in 1 until Visibility.values().size) {
      assertOneItemClicked(i, Visibility.values()[i])
    }

    assertOneItemClicked(
      0,
      Visibility.values()[0],
      true,
      generateModel(Visibility.GONE, Visibility.GONE),
    )
    for (i in 1 until Visibility.values().size) {
      assertOneItemClicked(i, Visibility.values()[i], true)
    }
  }

  private fun assertOneItemClicked(
    clickedButtonIndex: Int,
    expectedVisibility: Visibility,
    isToolsAttr: Boolean = false,
    startModel: NlVisibilityModel = generateModel(Visibility.NONE, Visibility.NONE),
  ) {

    var callbackTriggered = false
    val popupContent = VisibilityPopupContent { clickedVisibility: Visibility, uri: String ->
      callbackTriggered = true
      assertEquals(expectedVisibility, clickedVisibility)
      if (isToolsAttr) {
        assertEquals(uri, SdkConstants.TOOLS_URI)
      } else {
        assertEquals(uri, SdkConstants.ANDROID_URI)
      }
    }
    popupContent.update(startModel)

    // simulate clicked.
    val buttons = if (isToolsAttr) popupContent.toolsButtons!! else popupContent.androidButtons!!
    val buttonToBeClicked = buttons.buttons[clickedButtonIndex]
    buttonToBeClicked.mouseListeners.forEach { it.mouseClicked(null) }

    buttons.buttons.forEach {
      if (it == buttonToBeClicked) {
        assertTrue("Button clicked for $clickedButtonIndex", it.isClicked)
      } else {
        assertFalse("Button not selected for $clickedButtonIndex", it.isClicked)
      }
    }
    assertTrue(callbackTriggered)
  }

  @Test
  fun testToggleDisabled() {
    var callbackTriggered = false
    val model = generateModel(Visibility.GONE, Visibility.VISIBLE)
    val buttons =
      VisibilityPopupButtons(SdkConstants.ANDROID_URI) { _, _ -> callbackTriggered = true }
    buttons.update(model)
    val noneButton = buttons.buttons[0]
    val visibleButton = buttons.buttons[1]
    val invisibleButton = buttons.buttons[2]
    val goneButton = buttons.buttons[3]

    // Click gone again.
    assertTrue(goneButton.isClicked)
    goneButton.mouseListeners.forEach { it.mouseClicked(null) }

    assertFalse(visibleButton.isClicked)
    assertFalse(noneButton.isClicked)
    assertFalse(invisibleButton.isClicked)
    assertTrue(goneButton.isClicked)
    assertFalse(callbackTriggered)
  }

  private fun generateModel(android: Visibility, tools: Visibility): NlVisibilityModel {
    val component: NlComponent = Mockito.mock(NlComponent::class.java)
    whenever(component.getAttribute(SdkConstants.ANDROID_URI, "visibility"))
      .thenReturn(Visibility.convert(android))
    whenever(component.getAttribute(SdkConstants.TOOLS_URI, "visibility"))
      .thenReturn(Visibility.convert(tools))
    val delegate = Mockito.mock(NlComponentModificationDelegate::class.java)
    whenever(component.componentModificationDelegate).thenReturn(delegate)
    return NlVisibilityModel(component)
  }
}

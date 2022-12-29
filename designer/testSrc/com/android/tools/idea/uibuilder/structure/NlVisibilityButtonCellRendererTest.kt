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

import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NlVisibilityButtonCellRendererTest {

  @Test
  fun testRender() {
    val renderer = NlVisibilityButtonCellRenderer()
    val list = NlVisibilityJBList()
    val presentation = ButtonPresentation(Visibility.NONE, false)

    val button = renderer.getListCellRendererComponent(
      list,
      presentation,
      0,
      false,
      false)
    assertNotNull(button)
    assertTrue(button is NlVisibilityButton)
    assertEquals(presentation.visibility, (button as NlVisibilityButton).visibility)
    list.currHovered = 1
    list.dispose()
    assertEquals(-1, list.currHovered)
  }
}
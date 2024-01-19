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
package com.android.tools.idea.uibuilder.scene.target

import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.idea.common.model.NlAttributesHolder
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeBaseTargetTest {

  @Test
  fun testResizingCursors() {
    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.W_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.LEFT).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.E_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.RIGHT).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.N_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.TOP).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.S_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.BOTTOM).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.NW_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.LEFT_TOP).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.SW_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.LEFT_BOTTOM).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.NE_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.RIGHT_TOP).getMouseCursor(0),
    )

    assertEquals(
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.SE_RESIZE),
      TestResizeBaseTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM).getMouseCursor(0),
    )
  }
}

private class TestResizeBaseTarget(type: Type) : ResizeBaseTarget(type) {
  override fun updateAttributes(attributes: NlAttributesHolder, x: Int, y: Int) = Unit
}

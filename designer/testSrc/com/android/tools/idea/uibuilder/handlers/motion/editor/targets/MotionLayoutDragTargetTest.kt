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
package com.android.tools.idea.uibuilder.handlers.motion.editor.targets

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification
import com.android.tools.idea.uibuilder.scene.SceneTest

class MotionLayoutDragTargetTest : SceneTest() {

  fun testUpdateAttribute() {
    val button = myScreen.get("@id/button").sceneComponent!!

    val dropHandler = MotionLayoutDropHandler(button)
    val modification = ComponentModification(button.getNlComponent(), "move")

    dropHandler.updateAttributes(modification, button, 14, 10)
    modification.apply()
    // 14 unit has margin 9dp (14 - 5) from right side of text view. Which would be snapped to 8dp
    assertEquals("8dp", modification.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_START))

    // regression test for b/131758111
    dropHandler.updateAttributes(modification, button, 5, 10)
    modification.apply()
    // 5 unit is the exactly right side of text view. The margin should be removed.
    assertNull(modification.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_START))

    dropHandler.updateAttributes(modification, button, 19, 10)
    modification.apply()
    // 19 unit has margin 14dp (14 - 5) from right side of text view. Which would be snapped to 12dp
    assertEquals("12dp", modification.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_START))

    dropHandler.updateAttributes(modification, button, 13, 10)
    modification.apply()
    // 13 unit has margin 8dp (13 - 5) from right side of text view. Which is same as snapped value.
    assertEquals("8dp", modification.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_START))
  }

  override fun createModel(): ModelBuilder {
    return model(
      "model.xml",
      component(AndroidXConstants.MOTION_LAYOUT.defaultName())
        .id("@+id/root")
        .withBounds(0, 0, 1000, 1000)
        .children(
          component(SdkConstants.BUTTON)
            .id("@+id/textView")
            .withBounds(0, 0, 10, 10)
            .width("5dp")
            .height("5dp"),
          component(SdkConstants.TEXT_VIEW)
            .id("@id/button")
            .withBounds(10, 10, 10, 10)
            .width("5dp")
            .height("5dp")
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
              "@id/textView",
            )
            .withAttribute(
              SdkConstants.ANDROID_URI,
              SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
              "@id/textView",
            ),
        ),
    )
  }
}

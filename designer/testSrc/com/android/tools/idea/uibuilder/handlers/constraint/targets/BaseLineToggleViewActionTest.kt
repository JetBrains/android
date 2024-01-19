/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.targets

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.target.AnchorTarget
import com.android.tools.idea.uibuilder.scene.SceneTest

class BaseLineToggleViewActionTest : SceneTest() {

  fun testToggleBaselineAnchor() {
    val textView = myScene.getSceneComponent("textView")!!
    val topAnchor =
      textView.targets
        .filterIsInstance<ConstraintAnchorTarget>()
        .filter { it.type == AnchorTarget.Type.TOP }[0]
    val bottomAnchor =
      textView.targets
        .filterIsInstance<ConstraintAnchorTarget>()
        .filter { it.type == AnchorTarget.Type.BOTTOM }[0]
    val baselineAnchor =
      textView.targets
        .filterIsInstance<ConstraintAnchorTarget>()
        .filter { it.type == AnchorTarget.Type.BASELINE }[0]
    myInteraction.select(textView, true)

    assertTrue(topAnchor.isEnabled)
    assertTrue(bottomAnchor.isEnabled)
    assertFalse(baselineAnchor.isEnabled)

    myInteraction.performViewAction("textView") { it is BaseLineToggleViewAction }

    assertFalse(topAnchor.isEnabled)
    assertFalse(bottomAnchor.isEnabled)
    assertTrue(baselineAnchor.isEnabled)
  }

  override fun createModel(): ModelBuilder {
    return model(
      "constraint.xml",
      component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
        .withBounds(0, 0, 1000, 1000)
        .id("@id/constraint")
        .matchParentWidth()
        .matchParentHeight()
        .children(
          component(SdkConstants.TEXT_VIEW)
            .withBounds(0, 0, 200, 200)
            .id("@id/textView")
            .width("100dp")
            .height("100dp")
        ),
    )
  }
}

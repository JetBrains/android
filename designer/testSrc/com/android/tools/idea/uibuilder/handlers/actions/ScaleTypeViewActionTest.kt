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
package com.android.tools.idea.uibuilder.handlers.actions

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.model.getViewHandler
import com.android.tools.idea.uibuilder.scene.SceneTest

class ScaleTypeViewActionTest : SceneTest() {

  fun testChangeScaleType() {
    val imageView = myModel.find("imageView")!!
    for (type in ScaleType.values()) {
      val action = ScaleTypeViewAction(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCALE_TYPE, type)
      val editor = ViewEditorImpl(myModel, myScene)

      action.perform(editor, imageView.getViewHandler {}!!, imageView, mutableListOf(imageView), 0)

      assertEquals(type.attributeValue, imageView.getAndroidAttribute(SdkConstants.ATTR_SCALE_TYPE))
    }
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
          component(SdkConstants.IMAGE_VIEW)
            .withBounds(0, 0, 200, 200)
            .id("@id/imageView")
            .width("100dp")
            .height("100dp")
        ),
    )
  }
}

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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.model.viewInfo

class IsPartiallyHiddenTest: LayoutTestCase() {

  fun testTextHiddenIndex() {
    // Text hidden because image is defined after text
    val model =
      model("is_hidden.xml",
            component(SdkConstants.CONSTRAINT_LAYOUT.newName())
              .withBounds(0, 0, 200, 200)
              .withMockView()
              .children(
                component(SdkConstants.TEXT_VIEW)
                  .withMockView(),
                component(SdkConstants.IMAGE_VIEW)
                  .withMockView()
              )
    ).build()
    assertTrue(isTextHidden(0, 1, model))
  }

  fun testTextShownIndex() {
    // Text shown because image is defined before text
    val model =
      model("is_hidden.xml",
            component(SdkConstants.CONSTRAINT_LAYOUT.newName())
              .withBounds(0, 0, 200, 200)
              .withMockView()
              .children(
                component(SdkConstants.IMAGE_VIEW)
                  .withMockView(),
                component(SdkConstants.TEXT_VIEW)
                  .withMockView()
              )
      ).build()
    assertFalse(isTextHidden(1, 0, model))
  }

  fun testTextHiddenElevation() {
    // Text hidden because image has higher elevation than text, even tho text view is defined later.
    val model =
      model("is_hidden.xml",
            component(SdkConstants.CONSTRAINT_LAYOUT.newName())
              .withBounds(0, 0, 200, 200)
              .withMockView()
              .children(
                component(SdkConstants.IMAGE_VIEW)
                  .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION, "20dp")
                  .withMockView(),
                component(SdkConstants.TEXT_VIEW)
                  .withMockView()
              )
      ).build()
    assertTrue(isTextHidden(1, 0, model))
  }

  fun testTextShownElevation() {
    // Text shown because text has higher elevation
    val model =
      model("is_hidden.xml",
            component(SdkConstants.CONSTRAINT_LAYOUT.newName())
              .withBounds(0, 0, 200, 200)
              .withMockView()
              .children(
                component(SdkConstants.TEXT_VIEW)
                  .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION, "25dp")
                  .withMockView(),
                component(SdkConstants.IMAGE_VIEW)
                  .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION, "20dp")
                  .withMockView()
              )
      ).build()
    assertFalse(isTextHidden(0, 1, model))
  }

  private fun isTextHidden(textIndex: Int, imageIndex: Int, model: NlModel): Boolean {
    val text = model.getRoot().children[textIndex].viewInfo!!
    val image = model.getRoot().children[imageIndex].viewInfo!!

    return isPartiallyHidden(text, textIndex, image, imageIndex, model)
  }
}

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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.scene.SceneTest
import org.mockito.Mockito

class WidgetConstraintModelTest: SceneTest() {

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(SdkConstants.CONSTRAINT_LAYOUT.newName())
                   .withBounds(0, 0, 1000, 1000)
                   .id("@id/constraint")
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(0, 0, 200, 200)
                       .id("@id/textView")
                       .width("100dp")
                       .height("100dp"),
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(200, 0, 200, 200)
                       .id("@id/textView2")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, "linear")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, "0.632")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, "0.411"),
                     component(SdkConstants.LINEAR_LAYOUT)
                       .withBounds(200, 200, 800, 800)
                       .id("@id/linear")
                       .width("400dp")
                       .height("400dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "100dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "100dp"),
                     component(SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE.newName())
                       .id("@id/guideline")
                       .withBounds(0, 200, 1000, 1)
                       .wrapContentWidth()
                       .wrapContentHeight()
                       .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION, SdkConstants.VALUE_HORIZONTAL)
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, "200dp")
                   )
    )
  }

  fun testDeleteAttribute() {
    val widgetModel = WidgetConstraintModel {}
    val textView2 = myModel.find("textView2")!!
    widgetModel.component = textView2

    // Test deleting vertical constraints
    assertNotNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
    assertNotNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))
    assertNotNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS))

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF)
    assertNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF)
    assertNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))

    // Deleting both Top and Bottom will delete vertical bias as well
    assertNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS))


    // Test deleting horizontal constraints
    assertNotNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF))
    assertNotNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF))
    assertNotNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS))

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF)
    assertNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF))

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF)
    assertNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF))

    // Deleting both Start and End will delete vertical bias as well
    assertNull(textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS))
  }

  fun testConstraintVerification() {
    val widgetModel = WidgetConstraintModel {}

    // Test a Widget which is fully constrained
    widgetModel.component = myModel.find("textView2")
    assertFalse(widgetModel.isMissingHorizontalConstrained)
    assertFalse(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)

    // Test a Widget which isn't constrained.
    val linear = myModel.find("linear")!!
    widgetModel.component = linear

    assertTrue(widgetModel.isMissingHorizontalConstrained)
    assertTrue(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)

    NlWriteCommandActionUtil.run(linear, "Set Params") {
      linear.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_PARENT)
      linear.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF, SdkConstants.ATTR_PARENT)
    }

    assertFalse(widgetModel.isMissingHorizontalConstrained)
    assertFalse(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)

    NlWriteCommandActionUtil.run(linear, "Set Constraints") {
      linear.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_PARENT)
    }
    assertTrue(widgetModel.isOverConstrained)

    // Test Constraint Guideline doesn't need to constrained vertically and horizontally.
    val guideline = myModel.find("guideline")!!
    widgetModel.component = guideline
    assertFalse(widgetModel.isMissingHorizontalConstrained)
    assertFalse(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)
  }

  fun testTriggerCallbackWhenSettingSurface() {
    // The callback in practise is used to update ui components.
    val callback = Mockito.mock(Runnable::class.java)

    val widgetModel = WidgetConstraintModel(callback)
    widgetModel.surface = myScene.designSurface

    Mockito.verify(callback, Mockito.times(1)).run()
  }
}

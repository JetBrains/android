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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.ChangeType
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestUtil
import java.awt.event.ActionEvent
import java.util.Locale
import org.mockito.Mockito

class WidgetConstraintModelTest : SceneTest() {
  private var defaultLocale: Locale? = null

  override fun setUp() {
    super.setUp()
    defaultLocale = Locale.getDefault()
    // Set the default Locale to Arabic which catches bugs where a number is formatted with arabic
    // numbers instead of cardinal numbers.
    Locale.setDefault(Locale("ar"))
  }

  override fun tearDown() {
    defaultLocale?.let { Locale.setDefault(it) }
    super.tearDown()
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
            .height("100dp"),
          component(SdkConstants.TEXT_VIEW)
            .withBounds(200, 0, 200, 200)
            .id("@id/textView2")
            .width("100dp")
            .height("100dp")
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
              "parent",
            )
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
              "linear",
            )
            .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, "0.632")
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
              "parent",
            )
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
              "parent",
            )
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
              "0.411",
            ),
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(200, 200, 800, 800)
            .id("@id/linear")
            .width("400dp")
            .height("400dp")
            .withAttribute(
              SdkConstants.TOOLS_URI,
              SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
              "100dp",
            )
            .withAttribute(
              SdkConstants.TOOLS_URI,
              SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
              "100dp",
            ),
          component(AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE.newName())
            .id("@id/guideline")
            .withBounds(0, 200, 1000, 1)
            .wrapContentWidth()
            .wrapContentHeight()
            .withAttribute(
              SdkConstants.ANDROID_URI,
              SdkConstants.ATTR_ORIENTATION,
              SdkConstants.VALUE_HORIZONTAL,
            )
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN,
              "200dp",
            ),
        ),
    )
  }

  fun testDeleteAttribute() {
    val widgetModel = WidgetConstraintModel {}
    val textView2 = myModel.treeReader.find("textView2")!!
    widgetModel.component = textView2

    // Test deleting vertical constraints
    assertNotNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF)
    )
    assertNotNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF)
    )
    assertNotNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS)
    )

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF)
    assertNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF)
    )

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF)
    assertNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF)
    )

    // Deleting both Top and Bottom will delete vertical bias as well
    assertNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS)
    )

    // Test deleting horizontal constraints
    assertNotNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF)
    )
    assertNotNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF)
    )
    assertNotNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS)
    )

    widgetModel.removeAttributes(
      SdkConstants.SHERPA_URI,
      SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
    )
    assertNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF)
    )

    widgetModel.removeAttributes(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF)
    assertNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF)
    )

    // Deleting both Start and End will delete vertical bias as well
    assertNull(
      textView2.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS)
    )
  }

  fun testConstraintVerification() {
    val widgetModel = WidgetConstraintModel {}

    // Test a Widget which is fully constrained
    widgetModel.component = myModel.treeReader.find("textView2")
    assertFalse(widgetModel.isMissingHorizontalConstrained)
    assertFalse(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)

    // Test a Widget which isn't constrained.
    val linear = myModel.treeReader.find("linear")!!
    widgetModel.component = linear

    assertTrue(widgetModel.isMissingHorizontalConstrained)
    assertTrue(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)

    NlWriteCommandActionUtil.run(linear, "Set Params") {
      linear.setAttribute(
        SdkConstants.SHERPA_URI,
        SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
        SdkConstants.ATTR_PARENT,
      )
      linear.setAttribute(
        SdkConstants.SHERPA_URI,
        SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
        SdkConstants.ATTR_PARENT,
      )
    }

    assertFalse(widgetModel.isMissingHorizontalConstrained)
    assertFalse(widgetModel.isMissingVerticalConstrained)
    assertFalse(widgetModel.isOverConstrained)

    NlWriteCommandActionUtil.run(linear, "Set Constraints") {
      linear.setAttribute(
        SdkConstants.SHERPA_URI,
        SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
        SdkConstants.ATTR_PARENT,
      )
    }
    assertTrue(widgetModel.isOverConstrained)

    // Test Constraint Guideline doesn't need to constrained vertically and horizontally.
    val guideline = myModel.treeReader.find("guideline")!!
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

  fun testTriggerUpdateAfterModelChanges() {
    ignoreRendering()
    var count = 0
    val updateUICallback = Runnable { count++ }
    val widgetModel = WidgetConstraintModel(updateUICallback)
    val textView2 = myModel.treeReader.find("textView2")!!
    widgetModel.component = textView2
    count = 0 // reset the count which will be incremented after setting the component to textView2

    myModel.notifyModified(ChangeType.EDIT)
    assertThat(count).isAtLeast(1)
  }

  fun testTriggerUpdateAfterLayoutlibUpdate() {
    ignoreRendering()
    var count = 0
    val updateUICallback = Runnable { count++ }
    val widgetModel = WidgetConstraintModel(updateUICallback)
    val textView2 = myModel.treeReader.find("textView2")!!
    widgetModel.component = textView2
    count = 0 // reset the count which will be incremented after setting the component to textView2

    myModel.notifyListenersModelDerivedDataChanged()
    assertThat(count).isAtLeast(1)
  }

  fun testSetLeftMarginMinApi16() {
    val widgetModel = WidgetConstraintModel {}
    val component = myModel.treeReader.find("textView2")!!
    widgetModel.component = component
    widgetModel.setMargin(WidgetConstraintModel.CONNECTION_LEFT, "16dp")
    widgetModel.timer.stop()
    widgetModel.timer.actionListeners.forEach { it.actionPerformed(ActionEvent(component, 0, "")) }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
      )
      .isEqualTo("16dp")
    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START)
      )
      .isEqualTo("16dp")
  }

  fun testSetLeftMarginMinApi16TargetApi1() {
    val widgetModel = WidgetConstraintModel {}
    val component = myModel.treeReader.find("textView2")!!
    widgetModel.component = component
    widgetModel.setMargin(WidgetConstraintModel.CONNECTION_LEFT, "16dp")
    widgetModel.timer.stop()
    widgetModel.timer.actionListeners.forEach { it.actionPerformed(ActionEvent(component, 0, "")) }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
      )
      .isEqualTo("16dp")
  }

  fun testSetLeftMarginMinApi17() {
    val widgetModel = WidgetConstraintModel {}
    val component = myModel.treeReader.find("textView2")!!
    widgetModel.component = component
    widgetModel.setMargin(WidgetConstraintModel.CONNECTION_LEFT, "16dp")
    widgetModel.timer.stop()
    widgetModel.timer.actionListeners.forEach { it.actionPerformed(ActionEvent(component, 0, "")) }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
      )
      .isNull()
    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START)
      )
      .isEqualTo("16dp")
  }

  fun testSetVerticalMargin() {
    val widgetModel = WidgetConstraintModel {}
    val component = myModel.treeReader.find("textView2")!!
    widgetModel.component = component
    widgetModel.setMargin(WidgetConstraintModel.CONNECTION_TOP, "8dp")
    widgetModel.setMargin(WidgetConstraintModel.CONNECTION_BOTTOM, "16dp")
    widgetModel.timer.stop()
    widgetModel.timer.actionListeners.forEach { it.actionPerformed(ActionEvent(component, 0, "")) }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP)
      )
      .isEqualTo("8dp")
    assertThat(
        component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM)
      )
      .isEqualTo("16dp")
  }

  // To speed up the tests ignore all render requests
  private fun ignoreRendering() {
    val manager = myModel.surface.getSceneManager(myModel) as? SyncLayoutlibSceneManager ?: return
    manager.ignoreRenderRequests = true
  }
}

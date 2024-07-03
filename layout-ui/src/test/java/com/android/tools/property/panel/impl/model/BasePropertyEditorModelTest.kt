/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.testing.IconTester
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.awt.Color

class BasePropertyEditorModelTest {

  private fun createModel(): BasePropertyEditorModel {
    val property = FakePropertyItem(ANDROID_URI, ATTR_COLOR, "#00FF00")
    return object : BasePropertyEditorModel(property) {}
  }

  private fun createModelWithListener(): Pair<BasePropertyEditorModel, ValueChangedListener> {
    val model = createModel()
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return model to listener
  }

  @Test
  fun testValueFromProperty() {
    val model = createModel()
    assertThat(model.value).isEqualTo("#00FF00")
  }

  @Test
  fun testSetValueIsPropagatedToPropertyAndValueListener() {
    val (model, listener) = createModelWithListener()
    model.value = "#FFFF00"
    verify(listener, never()).valueChanged()
    assertThat(model.property.value).isEqualTo("#FFFF00")
  }

  @Test
  fun testSetVisibleIsPropagatedToValueListener() {
    val (model, listener) = createModelWithListener()
    model.visible = false
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusRequestIsPropagatedToValueListener() {
    val (model, listener) = createModelWithListener()
    model.requestFocus()
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusGainIsRecodedButNotPropagatedToListener() {
    val (model, listener) = createModelWithListener()
    model.focusGained()
    assertThat(model.hasFocus).isTrue()
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testFocusLossIsRecodedButNotPropagatedToListener() {
    // setup
    val (model, listener) = createModelWithListener()
    model.focusGained()

    // test
    model.focusLost()
    assertThat(model.hasFocus).isFalse()
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val model = createModel()
    val listener = RecursiveValueChangedListener(model)
    model.addListener(listener)
    model.visible = true
    assertThat(listener.called).isTrue()
  }

  @Ignore // AS Koala 2024.1.2 Canary 7 Merge: https://youtrack.jetbrains.com/issue/IDEA-355833
  @Test
  fun testDisplayedIcon() {
    IconLoader.activate()
    val model = createModel()
    assertThat(model.displayedIcon(StudioIcons.Common.ERROR)).isSameAs(StudioIcons.Common.ERROR)

    model.isUsedInRendererWithSelection = true
    assertThat(IconTester.hasOnlyWhiteColors(model.displayedIcon(StudioIcons.Common.ERROR)!!))
      .isEqualTo(ExperimentalUI.isNewUI())
  }

  @Test
  fun testDisplayedColorIcon() {
    val icon = ColorIcon(16, Color.GREEN)
    IconLoader.activate()
    val model = createModel()
    assertThat(IconTester.singleColorOrNull(model.displayedIcon(icon)!!)).isEqualTo(Color.GREEN.rgb)

    // A ColorIcon should not be converted to white in a selected table row
    model.isUsedInRendererWithSelection = true
    assertThat(IconTester.singleColorOrNull(model.displayedIcon(icon)!!)).isEqualTo(Color.GREEN.rgb)
  }

  @Test
  fun testDisplayedForeground() {
    val model = createModel()
    assertThat(model.displayedForeground(JBColor.BLUE)).isEqualTo(JBColor.BLUE)

    model.isUsedInRendererWithSelection = true
    assertThat(model.displayedForeground(JBColor.BLUE))
      .isEqualTo(UIUtil.getTableForeground(true, true))
  }

  @Test
  fun testDisplayedBackground() {
    val model = createModel()
    assertThat(model.displayedBackground(JBColor.RED)).isEqualTo(JBColor.RED)

    model.isUsedInRendererWithSelection = true
    assertThat(model.displayedBackground(JBColor.RED))
      .isEqualTo(UIUtil.getTableBackground(true, true))
  }

  private class RecursiveValueChangedListener(private val model: BasePropertyEditorModel) :
    ValueChangedListener {
    var called = false

    override fun valueChanged() {
      model.addListener(RecursiveValueChangedListener(model))
      called = true
    }
  }
}

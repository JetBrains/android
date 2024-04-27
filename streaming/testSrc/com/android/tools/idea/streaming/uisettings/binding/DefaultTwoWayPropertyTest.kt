/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.binding

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.junit.Test

class DefaultTwoWayPropertyTest {
  private var uiChangeCount = 0
  private var uiChangeLastValue = ""

  @get:Rule
  val disposableRule = DisposableRule()

  @Test
  fun testSetValueFromUi() {
    val property = createProperty()
    val ui = property.createAndAddListener()
    assertThat(ui.controllerChangeCount).isEqualTo(1)
    assertThat(property.value).isEqualTo("initial-value")
    property.setFromUi("UI-1")
    assertThat(uiChangeCount).isEqualTo(1)
    assertThat(uiChangeLastValue).isEqualTo("UI-1")
    assertThat(property.value).isEqualTo("UI-1")
    property.setFromUi("UI-2")
    assertThat(uiChangeCount).isEqualTo(2)
    assertThat(uiChangeLastValue).isEqualTo("UI-2")
    assertThat(property.value).isEqualTo("UI-2")
    assertThat(ui.controllerChangeCount).isEqualTo(1)
  }

  @Test
  fun testSetValueFromController() {
    val property = createProperty()
    val ui = property.createAndAddListener()
    assertThat(ui.controllerChangeCount).isEqualTo(1)
    assertThat(ui.controllerChangeLastValue).isEqualTo("initial-value")
    assertThat(property.value).isEqualTo("initial-value")
    property.setFromController("CTRL-1")
    assertThat(ui.controllerChangeCount).isEqualTo(2)
    assertThat(ui.controllerChangeLastValue).isEqualTo("CTRL-1")
    assertThat(uiChangeCount).isEqualTo(0)
    assertThat(property.value).isEqualTo("CTRL-1")
    property.setFromController("CTRL-2")
    assertThat(ui.controllerChangeCount).isEqualTo(3)
    assertThat(ui.controllerChangeLastValue).isEqualTo("CTRL-2")
    assertThat(uiChangeCount).isEqualTo(0)
    assertThat(property.value).isEqualTo("CTRL-2")
  }

  private fun createProperty() = DefaultTwoWayProperty(initialValue = "initial-value").apply {
    uiChangeListener = ChangeListener { newValue ->
      uiChangeCount++
      uiChangeLastValue = newValue
    }
  }

  private fun TwoWayProperty<String>.createAndAddListener(): Ui {
    val ui = Ui()
    Disposer.register(disposableRule.disposable, ui)
    addControllerListener(ui) { newValue ->
      ui.controllerChangeCount++
      ui.controllerChangeLastValue = newValue
    }
    return ui
  }

  private class Ui : Disposable {
    var controllerChangeCount = 0
    var controllerChangeLastValue = ""
    override fun dispose() {}
  }
}

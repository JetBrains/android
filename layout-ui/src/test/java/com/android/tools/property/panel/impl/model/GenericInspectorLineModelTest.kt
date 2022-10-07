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

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.*

class GenericInspectorLineModelTest {

  private fun createModel(hidden: Boolean = false): Pair<GenericInspectorLineModel, ValueChangedListener> {
    val model = GenericInspectorLineModel()
    model.hidden = hidden
    val listener = mock(ValueChangedListener::class.java)
    model.addValueChangedListener(listener)
    return model to listener
  }

  @Test
  fun testVisibleOff() {
    val (model, listener) = createModel()
    model.visible = false
    assertThat(model.visible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testVisibleOn() {
    val (model, listener) = createModel()
    model.visible = true
    assertThat(model.visible).isTrue()
    verify(listener).valueChanged()
  }

  @Test
  fun testHiddenOverridesVisible() {
    val (model, listener) = createModel(true)

    model.visible = false
    assertThat(model.visible).isFalse()
    verifyNoMoreInteractions(listener)

    model.visible = true
    assertThat(model.visible).isFalse()
    verifyNoMoreInteractions(listener)
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val (model, _) = createModel()
    val listener = RecursiveValueChangedListener(model)
    model.addValueChangedListener(listener)
    model.hidden = true
    assertThat(listener.called).isTrue()
  }

  private class RecursiveValueChangedListener(private val model: GenericInspectorLineModel) : ValueChangedListener {
    var called = false

    override fun valueChanged() {
      model.addValueChangedListener(RecursiveValueChangedListener(model))
      called = true
    }
  }
}

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
package com.android.tools.idea.observable.ui

import com.android.tools.idea.observable.CountListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class SpinnerLongValuePropertyTest {

  @Test
  fun testSliderValueProperty() {
    val model = SpinnerNumberModel(1_000_000L, Long.MIN_VALUE, Long.MAX_VALUE, 1L)
    val spinner = JSpinner(model)
    val spinnerValue = SpinnerLongValueProperty(spinner)

    val listener = CountListener()
    spinnerValue.addListener(listener)

    assertThat(spinnerValue.get()).isEqualTo(1_000_000L)
    assertThat(listener.count).isEqualTo(0)

    spinner.value = 300L
    assertThat(spinnerValue.get()).isEqualTo(300L)
    assertThat(listener.count).isEqualTo(1)

    spinner.value = -500L
    assertThat(spinnerValue.get()).isEqualTo(-500L)
    assertThat(listener.count).isEqualTo(2)
  }
}
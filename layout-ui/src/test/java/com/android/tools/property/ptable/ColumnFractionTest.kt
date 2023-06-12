/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.property.ptable

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColumnFractionTest {

  @Test
  fun testDefaultFraction() {
    assertThat(ColumnFraction().value).isEqualTo(0.4f)
    assertThat(ColumnFraction().resizeSupported).isFalse()
    try {
      ColumnFraction().value = 0.6f
      error("unexpected")
    }
    catch (ex: Exception) {
      assertThat(ex.message).isEqualTo("Not supported")
    }
  }

  @Test
  fun testChangedNotifications() {
    val fraction = ColumnFraction(initialValue = 0.5f, resizeSupported = true)
    var notifications = 0
    fraction.listeners.add(ValueChangedListener { notifications++ })
    assertThat(fraction.value).isEqualTo(0.5f)
    assertThat(notifications).isEqualTo(0)

    fraction.value = 0.5f
    assertThat(notifications).isEqualTo(0)

    fraction.value = 0.4f
    assertThat(notifications).isEqualTo(1)
    fraction.value = 0.4f
    assertThat(notifications).isEqualTo(1)

    fraction.value = 0.6f
    assertThat(notifications).isEqualTo(2)
  }

  @Test
  fun testLimits() {
    val fraction = ColumnFraction(initialValue = 0.5f, resizeSupported = true)
    fraction.value = 0.8f
    assertThat(fraction.value).isEqualTo(0.8f)
    fraction.value = 56.4f
    assertThat(fraction.value).isEqualTo(0.99f)
    fraction.value = 0.0f
    assertThat(fraction.value).isEqualTo(0.01f)
  }
}
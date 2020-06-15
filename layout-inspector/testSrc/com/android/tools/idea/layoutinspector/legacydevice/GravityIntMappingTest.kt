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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.SdkConstants.GRAVITY_VALUE_CENTER
import com.android.SdkConstants.GRAVITY_VALUE_CENTER_VERTICAL
import com.android.SdkConstants.GRAVITY_VALUE_FILL
import com.android.SdkConstants.GRAVITY_VALUE_FILL_HORIZONTAL
import com.android.SdkConstants.GRAVITY_VALUE_FILL_VERTICAL
import com.android.SdkConstants.GRAVITY_VALUE_LEFT
import com.android.SdkConstants.GRAVITY_VALUE_START
import com.android.SdkConstants.GRAVITY_VALUE_TOP
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GravityIntMappingTest {
  private val mapping by lazy(LazyThreadSafetyMode.NONE) { GravityIntMapping() }

  @Test
  fun testLeftOverridesCenter() {
    assertThat(mapping.fromIntValue(GRAVITY_CENTER or GRAVITY_LEFT))
      .containsExactly(GRAVITY_VALUE_CENTER_VERTICAL, GRAVITY_VALUE_LEFT)
  }

  @Test
  fun testTopBottomBecomesVerticalFill() {
    assertThat(mapping.fromIntValue(GRAVITY_TOP or GRAVITY_BOTTOM or GRAVITY_START))
      .containsExactly(GRAVITY_VALUE_FILL_VERTICAL, GRAVITY_VALUE_START)
  }

  @Test
  fun testLeftRightBecomesHorizontalFill() {
    assertThat(mapping.fromIntValue(GRAVITY_TOP or GRAVITY_LEFT or GRAVITY_RIGHT))
      .containsExactly(GRAVITY_VALUE_TOP, GRAVITY_VALUE_FILL_HORIZONTAL)
  }

  @Test
  fun testStartEndBecomesHorizontalFill() {
    assertThat(mapping.fromIntValue(GRAVITY_TOP or GRAVITY_START or GRAVITY_END))
      .containsExactly(GRAVITY_VALUE_TOP, GRAVITY_VALUE_FILL_HORIZONTAL)
  }

  @Test
  fun testTopBottomLeftRightSimplyBecomesFill() {
    assertThat(mapping.fromIntValue(GRAVITY_TOP or GRAVITY_BOTTOM or GRAVITY_LEFT or GRAVITY_RIGHT))
      .containsExactly(GRAVITY_VALUE_FILL)
  }

  @Test
  fun testVerticalAndHorizontalCenterSimplyBecomesCenter() {
    assertThat(mapping.fromIntValue(GRAVITY_CENTER_VERTICAL or GRAVITY_CENTER_HORIZONTAL))
      .containsExactly(GRAVITY_VALUE_CENTER)
  }
}

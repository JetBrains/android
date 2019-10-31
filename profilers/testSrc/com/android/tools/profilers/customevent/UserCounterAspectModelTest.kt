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
package com.android.tools.profilers.customevent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserCounterAspectModelTest {

  private val userCounterAspectModel = UserCounterAspectModel()

  @Test
  fun testAddSameEvents() {
    assertThat(userCounterAspectModel.size).isEqualTo(0)
    assertThat(userCounterAspectModel.add("Event1")).isTrue()
    assertThat(userCounterAspectModel.size).isEqualTo(1)
    assertThat(userCounterAspectModel.add("Event1")).isFalse()
    assertThat(userCounterAspectModel.size).isEqualTo(1)
    assertThat(userCounterAspectModel.eventNames).containsExactly("Event1")
  }

  @Test
  fun testAddDifferentEvents() {
    assertThat(userCounterAspectModel.size).isEqualTo(0)
    assertThat(userCounterAspectModel.add("Event1")).isTrue()
    assertThat(userCounterAspectModel.size).isEqualTo(1)
    assertThat(userCounterAspectModel.add("Event2")).isTrue()
    assertThat(userCounterAspectModel.size).isEqualTo(2)
    assertThat(userCounterAspectModel.eventNames).containsExactly("Event1", "Event2")
  }
}
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
package com.android.tools.idea.uibuilder.property2.ui

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`

class TransformsPanelTest {

  @Test
  fun testValueOf() {
    checkValueOf(null, 0.0)
    checkValueOf("", 0.0)
    checkValueOf("0", 0.0)
    checkValueOf("1", 1.0)
    checkValueOf(".5", 0.5)
  }

  private fun checkValueOf(value: String?, expected: Double?) {
    val property: NelePropertyItem = mock()
    `when`(property.value).thenReturn(value)

    assertThat(TransformsPanel.valueOf(property)).isEqualTo(expected)
  }
}

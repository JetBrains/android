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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.resources.ResourceType
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypeFiltersModelTest {

  private lateinit var model: TypeFiltersModel

  @Before
  fun setUp() {
    model = TypeFiltersModel()
  }

  @Test
  fun valueChangedCallback() {
    var counter = 0
    model.valueChangedCallback = { counter += 1 }
    val drawableFilterOption = model.getSupportedFilters(ResourceType.DRAWABLE)[0]
    assertFalse(model.isEnabled(ResourceType.DRAWABLE, drawableFilterOption))
    assertTrue(model.getActiveFilters(ResourceType.DRAWABLE).isEmpty())
    assertEquals(0, counter)

    model.setEnabled(ResourceType.DRAWABLE, drawableFilterOption, true)
    assertTrue(model.isEnabled(ResourceType.DRAWABLE, drawableFilterOption))
    assertEquals(1, model.getActiveFilters(ResourceType.DRAWABLE).size)
    assertEquals(1, counter)

    model.clearAll(ResourceType.DRAWABLE)
    assertEquals(0, model.getActiveFilters(ResourceType.DRAWABLE).size)
    assertEquals(2, counter)
  }
}
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
package com.android.tools.adtui.model

import com.android.tools.adtui.model.filter.Filter
import com.android.tools.adtui.model.filter.FilterHandler
import com.android.tools.adtui.model.filter.FilterModel
import com.android.tools.adtui.model.filter.FilterResult
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.Before

class FilterModelTest {
  private lateinit var myFilter: Filter
  private lateinit var myModel: FilterModel
  private var myFilterHandled = false

  @Before
  fun setUp() {
    myFilter = Filter.EMPTY_FILTER
    myModel = FilterModel()
    myFilterHandled = false
    myModel.setFilterHandler(object: FilterHandler() {
      override fun applyFilter(filter: Filter): FilterResult {
        myFilter = filter
        myFilterHandled = true
        return FilterResult(0, false)
      }
    })
  }

  @Test
  fun changesText() {
    myModel.setFilter(Filter("testText"))
    assertThat(myFilterHandled).isTrue()
    assertThat(myFilter.matches("testText")).isTrue()
    assertThat(myFilter.matches("testtext")).isTrue()
    assertThat(myFilter.matches("test")).isFalse()
  }

  @Test
  fun changesMatchCase() {
    myModel.setFilter(Filter("testText", true, false))
    assertThat(myFilterHandled).isTrue()
    assertThat(myFilter.isMatchCase).isTrue()
    assertThat(myFilter.matches("testText")).isTrue()
    assertThat(myFilter.matches("testtext")).isFalse()
    assertThat(myFilter.matches("test")).isFalse()
  }

  @Test
  fun changesRegex() {
    myModel.setFilter(Filter("test[A-Z]ext", false, true))
    assertThat(myFilterHandled).isTrue()
    assertThat(myFilter.isRegex).isTrue()
    assertThat(myFilter.matches("testText")).isTrue()
    assertThat(myFilter.matches("testAext")).isTrue()
    assertThat(myFilter.matches("testaext")).isTrue()
    assertThat(myFilter.matches("test.ext")).isFalse()
  }

  @Test
  fun changesMatchCaseAndRegex() {
    myModel.setFilter(Filter("test[A-Z]ext", true, true))
    assertThat(myFilterHandled).isTrue()
    assertThat(myFilter.isMatchCase).isTrue()
    assertThat(myFilter.isRegex).isTrue()
    assertThat(myFilter.matches("testText")).isTrue()
    assertThat(myFilter.matches("testAext")).isTrue()
    assertThat(myFilter.matches("testaext")).isFalse()
    assertThat(myFilter.matches("test.ext")).isFalse()
  }
}
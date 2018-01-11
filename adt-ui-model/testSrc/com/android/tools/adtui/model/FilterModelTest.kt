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

import org.junit.Test
import java.util.regex.Pattern
import com.google.common.truth.Truth.assertThat
import org.junit.Before

class FilterModelTest {
  private lateinit var myPattern:Pattern
  private lateinit var myModel:FilterModel
  private var myHasBeenGetCalled = false

  @Before
  fun setUp() {
    myPattern = Pattern.compile("")
    myModel = FilterModel()
    myHasBeenGetCalled = false
    myModel.addOnFilterChange { p ->
      run {
        myHasBeenGetCalled = true
        myPattern = p
      }
    }
  }

  @Test
  fun changesText() {
    myModel.setFilterString("testText");
    assertThat(myHasBeenGetCalled).isTrue()
    assertThat(myPattern.matcher("testText").matches()).isTrue()
    assertThat(myPattern.matcher("testtext").matches()).isTrue()
    assertThat(myPattern.matcher("test").matches()).isFalse()
  }

  @Test
  fun changesMatchCase() {
    myModel.setFilterString("testText");
    myModel.isMatchCase = true;
    assertThat(myHasBeenGetCalled).isTrue()
    assertThat(myModel.isMatchCase).isTrue()
    assertThat(myPattern.matcher("testText").matches()).isTrue()
    assertThat(myPattern.matcher("testtext").matches()).isFalse()
    assertThat(myPattern.matcher("test").matches()).isFalse()
  }

  @Test
  fun changesRegex() {
    myModel.setFilterString("test[A-Z]ext");
    myModel.isRegex = true;
    assertThat(myHasBeenGetCalled).isTrue()
    assertThat(myModel.isRegex).isTrue()
    assertThat(myPattern.matcher("testText").matches()).isTrue()
    assertThat(myPattern.matcher("testAext").matches()).isTrue()
    assertThat(myPattern.matcher("testaext").matches()).isTrue()
    assertThat(myPattern.matcher("test.ext").matches()).isFalse()
  }

  @Test
  fun changesMatchCaseAndRegex() {
    myModel.setFilterString("test[A-Z]ext");
    myModel.isMatchCase = true;
    myModel.isRegex = true;
    assertThat(myHasBeenGetCalled).isTrue()
    assertThat(myModel.isMatchCase).isTrue()
    assertThat(myModel.isRegex).isTrue()
    assertThat(myPattern.matcher("testText").matches()).isTrue()
    assertThat(myPattern.matcher("testAext").matches()).isTrue()
    assertThat(myPattern.matcher("testaext").matches()).isFalse()
    assertThat(myPattern.matcher("test.ext").matches()).isFalse()
  }
}
/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

public class PsModelNameComparatorTest {
  private PsModelNameComparator<PsModel> myComparator;

  @Before
  public void before() {
    myComparator = new PsModelNameComparator<>();
  }

  @Test
  public void compare() throws Exception {
    PsModel a = new TestModel("a");
    PsModel b = new TestModel("b");
    assertThat(myComparator.compare(a, b)).isLessThan(0);
    assertThat(myComparator.compare(b, a)).isGreaterThan(0);
  }

  @Test
  public void compare_equals() throws Exception {
    PsModel a = new TestModel("a");
    PsModel b = new TestModel("a");
    assertEquals(0, myComparator.compare(a, a));
    assertEquals(0, myComparator.compare(a, b));
  }
}
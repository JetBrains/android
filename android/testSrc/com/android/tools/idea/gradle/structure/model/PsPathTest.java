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

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PsPathTest {

  @Test
  public void compareTo() {
    PsPath a = new TestPath("a", "d");
    PsPath b = new TestPath("b", "c");

    assertThat(a.compareTo(b)).isLessThan(0);
    assertThat(b.compareTo(a)).isGreaterThan(0);
  }

  @Test
  public void compareTo_equalParameters() {
    PsPath a = new TestPath("a", "b");
    PsPath b = new TestPath("a", "b");

    assertEquals(0, a.compareTo(b));
  }

  @Test
  public void emptyPath_compareToAndEquals() {
    PsPath a = new TestPath("");

    assertEquals(0, a.compareTo(TestPath.EMPTY_PATH));
    assertNotEquals(TestPath.EMPTY_PATH, a);
  }
}
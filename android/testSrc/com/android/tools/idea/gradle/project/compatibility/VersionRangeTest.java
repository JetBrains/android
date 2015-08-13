/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.compatibility;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link VersionRange}.
 */
public class VersionRangeTest {
  @Test
  public void testRangeWithPlusNotation() {
    VersionRange range = VersionRange.parse("23+");
    assertTrue(range.contains("23.0.0"));
    assertTrue(range.contains("23.0.1"));
    assertTrue(range.contains("23.1.1"));
    assertTrue(range.contains("24"));
    assertFalse(range.contains("22"));
    assertFalse(range.contains("Hello"));
  }

  @Test
  public void testRangeWithRangeNotationBothInclusive() {
    VersionRange range = VersionRange.parse("[1, 10]");
    for (int i = 1; i <= 10; i++) {
      String version = String.valueOf(i);
      assertTrue(version, range.contains(version));
    }
    assertTrue(range.contains("1.1"));
    assertTrue(range.contains("2.0.5"));
    assertFalse(range.contains("0"));
    assertFalse(range.contains("11"));
  }

  @Test
  public void testRangeWithRangeNotationBothExclusive() {
    VersionRange range = VersionRange.parse("(1, 10)");
    for (int i = 2; i <= 9; i++) {
      String version = String.valueOf(i);
      assertTrue(version, range.contains(version));
    }
    assertTrue(range.contains("1.1"));
    assertTrue(range.contains("2.0.5"));
    assertFalse(range.contains("0"));
    assertFalse(range.contains("1"));
    assertFalse(range.contains("10"));
  }

  @Test
  public void testRangeWithRangeNotationInclusiveAndExclusive() {
    VersionRange range = VersionRange.parse("[1, 10)");
    for (int i = 1; i <= 9; i++) {
      String version = String.valueOf(i);
      assertTrue(version, range.contains(version));
    }
    assertTrue(range.contains("1.1"));
    assertTrue(range.contains("2.0.5"));
    assertFalse(range.contains("0"));
    assertFalse(range.contains("10"));
  }

  @Test
  public void testRangeWithRangeNotationExclusiveAndInclusive() {
    VersionRange range = VersionRange.parse("(1, 10]");
    for (int i = 2; i <= 10; i++) {
      String version = String.valueOf(i);
      assertTrue(version, range.contains(version));
    }
    assertTrue(range.contains("1.1"));
    assertTrue(range.contains("2.0.5"));
    assertFalse(range.contains("0"));
    assertFalse(range.contains("11"));
  }

  @Test
  public void testRangeWithRangeNotationWithMinValueInclusiveOnly() {
    VersionRange range = VersionRange.parse("[1, +)");
    for (int i = 2; i <= 100; i++) {
      String version = String.valueOf(i);
      assertTrue(version, range.contains(version));
    }
    assertTrue(range.contains(String.valueOf(Integer.MAX_VALUE)));
    assertFalse(range.contains("0"));
  }

  @Test
  public void testRangeWithSingleNonNumericalValue() {
    VersionRange range = VersionRange.parse("M");
    assertTrue(range.contains("M"));
    assertFalse(range.contains("L"));
  }
}
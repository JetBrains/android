/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import org.junit.Test;

import static com.android.tools.idea.gradle.project.sync.compatibility.version.VersionRangeSubject.versionRange;
import static com.google.common.truth.Truth.assertAbout;

/**
 * Tests for {@link VersionRange}.
 */
public class VersionRangeTest {
  @Test
  public void withPlusNotation() {
    VersionRange range = VersionRange.parse("23+");
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("23")
                                           .isMinVersionInclusive(true)
                                           .hasMaxVersion(null)
                                           .isMaxVersionInclusive(false)
                                           .contains("23.0.0", "23.0.1", "23.1.1", "24")
                                           .doesNotContain("22", "hello")
                                           .hasDescription("23 (or newer)");
    // @formatter:on
  }

  @Test
  public void withRangeNotationBothInclusive() {
    VersionRange range = VersionRange.parse("[1, 10]");
    for (int i = 1; i <= 10; i++) {
      assertAbout(versionRange()).that(range).contains(String.valueOf(i));
    }
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("1")
                                           .isMinVersionInclusive(true)
                                           .hasMaxVersion("10")
                                           .isMaxVersionInclusive(true)
                                           .contains("1.1", "2.0.5")
                                           .doesNotContain("0", "11")
                                           .hasDescription("versions 1 (inclusive) to 10 (inclusive)");
    // @formatter:on
  }

  @Test
  public void withRangeNotationBothExclusive() {
    VersionRange range = VersionRange.parse("(1, 10)");
    for (int i = 2; i <= 9; i++) {
      assertAbout(versionRange()).that(range).contains(String.valueOf(i));
    }
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("1")
                                           .isMinVersionInclusive(false)
                                           .hasMaxVersion("10")
                                           .isMaxVersionInclusive(false)
                                           .contains("1.1", "2.0.5")
                                           .doesNotContain("1", "10")
                                           .hasDescription("versions 1 (exclusive) to 10 (exclusive)");
    // @formatter:on
  }

  @Test
  public void withRangeNotationInclusiveAndExclusive() {
    VersionRange range = VersionRange.parse("[1, 10)");
    for (int i = 1; i <= 9; i++) {
      assertAbout(versionRange()).that(range).contains(String.valueOf(i));
    }
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("1")
                                           .isMinVersionInclusive(true)
                                           .hasMaxVersion("10")
                                           .isMaxVersionInclusive(false)
                                           .contains("1.1", "2.0.5")
                                           .doesNotContain("0", "10")
                                           .hasDescription("versions 1 (inclusive) to 10 (exclusive)");
    // @formatter:on
  }

  @Test
  public void withRangeNotationExclusiveAndInclusive() {
    VersionRange range = VersionRange.parse("(1, 10]");
    for (int i = 2; i <= 10; i++) {
      assertAbout(versionRange()).that(range).contains(String.valueOf(i));
    }
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("1")
                                           .isMinVersionInclusive(false)
                                           .hasMaxVersion("10")
                                           .isMaxVersionInclusive(true)
                                           .contains("1.1", "2.0.5")
                                           .doesNotContain("0", "11")
                                           .hasDescription("versions 1 (exclusive) to 10 (inclusive)");
    // @formatter:on
  }

  @Test
  public void withRangeNotationWithMinValueInclusiveOnly() {
    VersionRange range = VersionRange.parse("[1, +)");
    for (int i = 2; i <= 100; i++) {
      assertAbout(versionRange()).that(range).contains(String.valueOf(i));
    }
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("1")
                                           .isMinVersionInclusive(true)
                                           .hasMaxVersion(null)
                                           .isMaxVersionInclusive(false)
                                           .contains(String.valueOf(Integer.MAX_VALUE))
                                           .doesNotContain("0")
                                           .hasDescription("1 (or newer)");
    // @formatter:on
  }

  @Test
  public void withSingleNonNumericalValue() {
    VersionRange range = VersionRange.parse("M");
    // @formatter:off
    assertAbout(versionRange()).that(range).hasMinVersion("M")
                                           .isMinVersionInclusive(false)
                                           .hasMaxVersion(null)
                                           .isMaxVersionInclusive(false)
                                           .contains("M")
                                           .doesNotContain("L")
                                           .hasDescription("M (or newer)");
    // @formatter:on
  }
}
/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.bazel;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BazelVersion}. */
@RunWith(JUnit4.class)
public class BazelVersionTest {

  @Test
  public void testParseOldVersionFormat() {
    BazelVersion version = BazelVersion.parseVersion("release 0.4.1");
    assertThat(version).isNotNull();
    assertThat(version.getMajor()).isEqualTo(0);
    assertThat(version.getMinor()).isEqualTo(4);
    assertThat(version.getBugfix()).isEqualTo(1);
  }

  @Test
  public void testParseVersionFormatDistributionPackage() {
    BazelVersion version = BazelVersion.parseVersion("release 0.4.3- (@non-git)");
    assertThat(version).isNotNull();
    assertThat(version.getMajor()).isEqualTo(0);
    assertThat(version.getMinor()).isEqualTo(4);
    assertThat(version.getBugfix()).isEqualTo(3);
  }

  @Test
  public void testParseVersionFormatManualBuild() {
    BazelVersion version = BazelVersion.parseVersion("release 0.4.3- (@c9139896");
    assertThat(version).isNotNull();
    assertThat(version.getMajor()).isEqualTo(0);
    assertThat(version.getMinor()).isEqualTo(4);
    assertThat(version.getBugfix()).isEqualTo(3);
  }

  @Test
  public void testParseDevelopmentVersion() {
    BazelVersion version = BazelVersion.parseVersion("development version");
    assertThat(version).isEqualTo(BazelVersion.DEVELOPMENT);
    assertThat(version.isAtLeast(9, 9, 9)).isTrue();
  }

  @Test
  public void testIsAtLeast() {
    BazelVersion version = BazelVersion.parseVersion("release 0.4.1");
    assertThat(version).isNotNull();
    assertThat(version.isAtLeast(0, 3, 2)).isTrue();
    assertThat(version.isAtLeast(0, 4, 0)).isTrue();
    assertThat(version.isAtLeast(0, 4, 1)).isTrue();
    assertThat(version.isAtLeast(0, 4, 2)).isFalse();
    assertThat(version.isAtLeast(0, 5, 0)).isFalse();
  }
}

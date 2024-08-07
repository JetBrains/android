/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestSourceGlobMatcherTest {

  @Test
  public void testTrailingAsterisk() {
    TestSourceGlobMatcher globMatcher = new TestSourceGlobMatcher(ImmutableSet.of("javatests*"));

    assertThat(globMatcher.matches(Path.of("javatests/MyTest.java"))).isTrue();
    assertThat(globMatcher.matches(Path.of("javatests/nested/MyNestedTest.java"))).isTrue();
    assertThat(globMatcher.matches(Path.of("javatests_ext/MyExtendedTest.java"))).isTrue();

    assertThat(globMatcher.matches(Path.of("javatersts/MyTerstTest.java"))).isFalse();
  }

  @Test
  public void testTrailingFileSeparator() {
    TestSourceGlobMatcher globMatcher = new TestSourceGlobMatcher(ImmutableSet.of("javatests/"));

    assertThat(globMatcher.matches(Path.of("javatests/MyTest.java"))).isTrue();
    assertThat(globMatcher.matches(Path.of("javatests/nested/MyNestedTest.java"))).isTrue();
    assertThat(globMatcher.matches(Path.of("javatests_ext/MyExtendedTest.java"))).isTrue();

    assertThat(globMatcher.matches(Path.of("javatersts/MyTerstTest.java"))).isFalse();
  }

  @Test
  public void testTrailingFileSeparatorAndAsterisk() {
    TestSourceGlobMatcher globMatcher = new TestSourceGlobMatcher(ImmutableSet.of("javatests/*"));

    assertThat(globMatcher.matches(Path.of("javatests/MyTest.java"))).isTrue();
    assertThat(globMatcher.matches(Path.of("javatests/nested/MyNestedTest.java"))).isTrue();
    assertThat(globMatcher.matches(Path.of("javatests_ext/MyExtendedTest.java"))).isTrue();

    assertThat(globMatcher.matches(Path.of("javatersts/MyTerstTest.java"))).isFalse();
  }
}

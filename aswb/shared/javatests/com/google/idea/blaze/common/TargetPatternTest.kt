/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common

import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TargetPatternTest {
  @get:Rule
  val expect = Expect.create()

  @Test
  fun simpleMatch() {
    expect.that(TargetPattern.parse("//some/path").includes(Label.of("//some/path"))).isTrue()
    expect.that(TargetPattern.parse("//some/path").includes(Label.of("//some/path1"))).isFalse()
    expect.that(TargetPattern.parse("//some/path").includes(Label.of("//some1/path"))).isFalse()
  }

  @Test
  fun subpathWildcard() {
    expect.that(TargetPattern.parse("//some/path/...").includes(Label.of("//some/path"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...").includes(Label.of("//some/path:target"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...").includes(Label.of("//some/path/subpackage"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...").includes(Label.of("//some/path/subpackage:target"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...").includes(Label.of("//some/path1"))).isFalse()
    expect.that(TargetPattern.parse("//some/path/...").includes(Label.of("//some1/path"))).isFalse()
    // Wildcard paths imply wildcard targets.
    expect.that(TargetPattern.parse("//some/path/...:all").includes(Label.of("//some/path"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...:all-target").includes(Label.of("//some/path:target"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...:*").includes(Label.of("//some/path/subpackage"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...:all").includes(Label.of("//some/path/subpackage:target"))).isTrue()
    expect.that(TargetPattern.parse("//some/path/...:all-target").includes(Label.of("//some/path1"))).isFalse()
    expect.that(TargetPattern.parse("//some/path/...:*").includes(Label.of("//some1/path"))).isFalse()
    // Although, our target pattern parsing does not validate or enforce it.
    expect.that(TargetPattern.parse("//some/path/...:target-names-not-allowed-here").includes(Label.of("//some1/path"))).isFalse()
  }

  @Test
  fun wildcardTargetName() {
    expect.that(TargetPattern.parse("//some/path:*").includes(Label.of("//some/path"))).isTrue()
    expect.that(TargetPattern.parse("//some/path:all").includes(Label.of("//some/path"))).isTrue()
    expect.that(TargetPattern.parse("//some/path:all-targets").includes(Label.of("//some/path"))).isTrue()
    expect.that(TargetPattern.parse("//some/path:all-something-else").includes(Label.of("//some/path"))).isFalse()
  }
}

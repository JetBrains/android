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
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.INCLUDED
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.EXCLUDED
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.NOT_IN_SCOPE
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
    expect.that(TargetPattern.parse("//some/path").inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path").inScope(Label.of("//some/path1"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("//some/path").inScope(Label.of("//some1/path"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("-//some/path").inScope(Label.of("//some/path"))).isEqualTo(EXCLUDED)
    expect.that(TargetPattern.parse("-//some/path").inScope(Label.of("//some/path1"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("-//some/path").inScope(Label.of("//some1/path"))).isEqualTo(NOT_IN_SCOPE)
  }

  @Test
  fun subpathWildcard() {
    expect.that(TargetPattern.parse("//some/path/...").inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...").inScope(Label.of("//some/path:target"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...").inScope(Label.of("//some/path/subpackage"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...").inScope(Label.of("//some/path/subpackage:target"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...").inScope(Label.of("//some/path1"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("//some/path/...").inScope(Label.of("//some1/path"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("-//some/path/...").inScope(Label.of("//some1/path"))).isEqualTo(NOT_IN_SCOPE)
    // Wildcard paths imply wildcard targets.
    expect.that(TargetPattern.parse("//some/path/...:all").inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("-//some/path/...:all").inScope(Label.of("//some/path"))).isEqualTo(EXCLUDED)
    expect.that(TargetPattern.parse("//some/path/...:all-target").inScope(Label.of("//some/path:target"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...:*").inScope(Label.of("//some/path/subpackage"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...:all").inScope(Label.of("//some/path/subpackage:target"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path/...:all-target").inScope(Label.of("//some/path1"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("//some/path/...:*").inScope(Label.of("//some1/path"))).isEqualTo(NOT_IN_SCOPE)
    // Although, our target pattern parsing does not validate or enforce it.
    expect.that(TargetPattern.parse("//some/path/...:target-names-not-allowed-here").inScope(Label.of("//some1/path"))).isEqualTo(NOT_IN_SCOPE)
  }

  @Test
  fun wildcardTargetName() {
    expect.that(TargetPattern.parse("//some/path:*").inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("-//some/path:*").inScope(Label.of("//some/path"))).isEqualTo(EXCLUDED)
    expect.that(TargetPattern.parse("//some/path:all").inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("//some/path:all-targets").inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(TargetPattern.parse("-//some/path:all-targets").inScope(Label.of("//some/path"))).isEqualTo(EXCLUDED)
    expect.that(TargetPattern.parse("//some/path:all-something-else").inScope(Label.of("//some/path"))).isEqualTo(NOT_IN_SCOPE)
    expect.that(TargetPattern.parse("-//some/path:all-something-else").inScope(Label.of("//some/path"))).isEqualTo(NOT_IN_SCOPE)
  }
}

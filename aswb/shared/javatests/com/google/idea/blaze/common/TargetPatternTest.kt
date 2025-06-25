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
    expect.that(TargetPattern.parse("//some/path/...:target-names-not-allowed-here").inScope(Label.of("//some1/path"))).isEqualTo(
      NOT_IN_SCOPE)
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

  @Test
  fun matchesToString() {
    expect.that(TargetPattern.parse("//some/path").toString()).isEqualTo("//some/path:path")
    expect.that(TargetPattern.parse("-//some/path").toString()).isEqualTo("-//some/path:path")
    expect.that(TargetPattern.parse("//some/path/...").toString()).isEqualTo("//some/path/...")
    expect.that(TargetPattern.parse("-//some/path/...").toString()).isEqualTo("-//some/path/...")
    expect.that(TargetPattern.parse("//some/path:target").toString()).isEqualTo("//some/path:target")
    // Note, repo names are normalized even though it might not be correct to do when in the context of of a dependency repo.
    expect.that(TargetPattern.parse("@repo//some/path:target").toString()).isEqualTo("@@repo//some/path:target")
    // Note, for now we do not distinguish different wildcard kinds since we only deal with rules anyway.
    expect.that(TargetPattern.parse("//some/path:all").toString()).isEqualTo("//some/path:*")
    expect.that(TargetPattern.parse("//some/path:all-targets").toString()).isEqualTo("//some/path:*")
    expect.that(TargetPattern.parse("//some/path:*").toString()).isEqualTo("//some/path:*")
  }

  @Test
  fun simpleScope() {
    val scope = TargetPatternCollection.create(listOf(TargetPattern.parse("//..."), TargetPattern.parse("-//some")))
    expect.that(scope.inScope(Label.of("//some/path"))).isEqualTo(INCLUDED)
    expect.that(scope.inScope(Label.of("//some"))).isEqualTo(EXCLUDED)
    expect.that(scope.inScope(Label.of("@@repo//some"))).isEqualTo(NOT_IN_SCOPE)
  }

  @Test
  fun scopeOverrides() {
    val scope = TargetPatternCollection.create(listOf(
      TargetPattern.parse("//some/..."),
      TargetPattern.parse("//some/deeper/path"),
      TargetPattern.parse("-//some/deeper/..."),
      TargetPattern.parse("//some/deeper/other/path"),
    ))
    expect.that(scope.inScope(Label.of("//some/deeper/path"))).isEqualTo(EXCLUDED)
    expect.that(scope.inScope(Label.of("//some/deeper/other/path"))).isEqualTo(INCLUDED)
    expect.that(scope.inScope(Label.of("//other/path"))).isEqualTo(NOT_IN_SCOPE)
  }

  @Test
  fun scopePackageLevelOverrides() {
    val scope = TargetPatternCollection.create(listOf(
      TargetPattern.parse("//some/..."),
      TargetPattern.parse("-//some/deeper:all"),
      TargetPattern.parse("//some/deeper:but-this-one"),
    ))
    expect.that(scope.inScope(Label.of("//some/deeper/more"))).isEqualTo(INCLUDED)
    expect.that(scope.inScope(Label.of("//some/deeper:target"))).isEqualTo(EXCLUDED)
    expect.that(scope.inScope(Label.of("//some/deeper:but-this-one"))).isEqualTo(INCLUDED)
    expect.that(scope.inScope(Label.of("//other/path"))).isEqualTo(NOT_IN_SCOPE)
  }
}

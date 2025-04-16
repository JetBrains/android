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
package com.google.idea.blaze.base.qsync.action

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.TargetsToBuild
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TargetDisambiguatorTest {
  @Test
  fun basic() {
    val targets = setOf(Label.of("//a:a"), Label.of("//b:b"))
    val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(
      setOf(TargetsToBuild.targetGroup (targets)),
      TargetDisambiguationAnchors.NONE
    )
    assertThat(disambiguator.ambiguousTargetSets).isEmpty()
    assertThat(disambiguator.unambiguousTargets).isEqualTo(targets)
  }

  @Test
  fun unambiguousOnly() {
    val targets1 = setOf(Label.of("//a:a"), Label.of("//b:b"))
    val targets2 = setOf(Label.of("//b:b"), Label.of("//c:c"))
    val srcFileTarget = setOf(Label.of("//s:s"))
    val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(
      setOf(
        TargetsToBuild.targetGroup(targets1),
        TargetsToBuild.targetGroup(targets2),
        TargetsToBuild.forSourceFile(srcFileTarget, Path.of("some/file.kt"))
      ),
      TargetDisambiguationAnchors.NONE
    )
    assertThat(disambiguator.ambiguousTargetSets).isEmpty()
    assertThat(disambiguator.unambiguousTargets).isEqualTo(targets1 + targets2 + srcFileTarget)
  }

  @Test
  fun oneAmbiguous() {
    val srcFileTarget = setOf(Label.of("//s:s1"), Label.of("//s:s2"))
    val sourceFileTargetGroup = TargetsToBuild.forSourceFile(srcFileTarget, Path.of("some/file.kt"))
    val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(
      setOf(
        sourceFileTargetGroup
      ),
      TargetDisambiguationAnchors.NONE
    )
    assertThat(disambiguator.ambiguousTargetSets).containsExactly(sourceFileTargetGroup)
    assertThat(disambiguator.unambiguousTargets).isEmpty()
  }

  @Test
  fun oneAmbiguousDisambiguatedThroughOtherTargets() {
    val targets = setOf(Label.of("//a:a"), Label.of("//s:s2"))
    val srcFileTarget = setOf(Label.of("//s:s1"), Label.of("//s:s2"))
    val sourceFileTargetGroup = TargetsToBuild.forSourceFile(srcFileTarget, Path.of("some/file.kt"))
    val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(
      setOf(
        TargetsToBuild.targetGroup(targets),
        sourceFileTargetGroup
      ),
      TargetDisambiguationAnchors.NONE
    )
    assertThat(disambiguator.ambiguousTargetSets).isEmpty()
    assertThat(disambiguator.unambiguousTargets).isEqualTo(targets)
  }

  @Test
  fun someAmbiguousDisambiguatedThroughOtherTargets() {
    val targets = setOf(Label.of("//a:a"), Label.of("//s:s2"))
    val srcFileTarget1 = setOf(Label.of("//s:s1"), Label.of("//s:s2"))
    val srcFileTarget2 = setOf(Label.of("//t:s1"), Label.of("//t:s2"))
    val sourceFileTargetGroup1 = TargetsToBuild.forSourceFile(srcFileTarget1, Path.of("some/file1.kt"))
    val sourceFileTargetGroup2 = TargetsToBuild.forSourceFile(srcFileTarget2, Path.of("some/file2.kt"))
    val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(
      setOf(
        TargetsToBuild.targetGroup(targets),
        sourceFileTargetGroup1,
        sourceFileTargetGroup2,
      ),
      TargetDisambiguationAnchors.NONE
    )
    assertThat(disambiguator.ambiguousTargetSets).containsExactly(sourceFileTargetGroup2)
    assertThat(disambiguator.unambiguousTargets).isEqualTo(targets)
  }

  @Test
  fun ambiguousDisambiguatedThroughAnchorsAndOtherTargets() {
    val targets = setOf(Label.of("//a:a"), Label.of("//s:s2"))
    val srcFileTarget1 = setOf(Label.of("//s:s1"), Label.of("//s:s2"))
    val srcFileTarget2 = setOf(Label.of("//t:s1"), Label.of("//t:s2"))
    val sourceFileTargetGroup1 = TargetsToBuild.forSourceFile(srcFileTarget1, Path.of("some/file1.kt"))
    val sourceFileTargetGroup2 = TargetsToBuild.forSourceFile(srcFileTarget2, Path.of("some/file2.kt"))
    val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(
      setOf(
        TargetsToBuild.targetGroup(targets),
        sourceFileTargetGroup1,
        sourceFileTargetGroup2,
      ),
      TargetDisambiguationAnchors.Targets(setOf(Label.of("//t:s1")))
    )
    assertThat(disambiguator.ambiguousTargetSets).isEmpty()
    assertThat(disambiguator.unambiguousTargets).isEqualTo(targets + Label.of("//t:s1"))
  }
}
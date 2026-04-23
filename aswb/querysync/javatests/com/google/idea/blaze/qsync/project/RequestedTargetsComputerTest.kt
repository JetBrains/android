/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Label
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RequestedTargetsComputerTest {

  /**
   * A simple implementation of [DependencyGraphProvider] for testing purposes. It uses a map to look up dependencies and a set to identify
   * external targets.
   */
  class MapDependencyGraphProvider(
    private val depsMap: Map<Label, Collection<Label>>,
    private val externalTargets: Set<Label> = emptySet(),
  ) : DependencyGraphProvider {
    override fun getTarget(label: Label): Target {
      val isExternal = label in externalTargets || !depsMap.containsKey(label)
      val deps = depsMap[label] ?: emptyList()
      return TestTarget(label, deps, isExternal, this)
    }
  }

  /** A simple implementation of [Target] for testing purposes. */
  class TestTarget(override val label: Label, val deps: Collection<Label>, val isExternal: Boolean, val provider: DependencyGraphProvider) :
    Target {
    override val dependencies: Collection<Target>
      get() = deps.map { provider.getTarget(it) }

    override val status: TargetStatus
      get() = if (isExternal) TargetStatus.EXTERNAL_TO_PROJECT_SCOPE else TargetStatus.IN_PROJECT_SCOPE

    override fun equals(other: Any?): Boolean = (other as? Target)?.label == label

    override fun hashCode(): Int = label.hashCode()
  }

  @Test
  fun testEmptyGraph() {
    val provider = MapDependencyGraphProvider(emptyMap())
    val required = computeTargetsRequiredFor(emptyList(), provider)
    assertThat(required).isEmpty()
  }

  @Test
  fun testSimpleGraph() {
    val labelA = Label.of("//A:A")
    val labelB = Label.of("//B:B")
    val provider = MapDependencyGraphProvider(mapOf(labelA to listOf(labelB), labelB to emptyList()))
    val required = computeTargetsRequiredFor(listOf(labelA), provider)
    assertThat(required).isEmpty()
  }

  @Test
  fun testWithExternalDep() {
    val labelA = Label.of("//A:A")
    val labelExt = Label.of("@@+intellij+intellij//:intellij-sdk")
    val provider = MapDependencyGraphProvider(depsMap = mapOf(labelA to listOf(labelExt)), externalTargets = setOf(labelExt))
    val required = computeTargetsRequiredFor(listOf(labelA), provider)
    assertThat(required.map { it.label }).containsExactly(labelExt)
  }

  @Test
  fun testTransientDep() {
    val labelA = Label.of("//A:A")
    val labelB = Label.of("//B:B")
    val labelExt = Label.of("@@+intellij+intellij//:intellij-sdk")
    val provider =
      MapDependencyGraphProvider(depsMap = mapOf(labelA to listOf(labelB), labelB to listOf(labelExt)), externalTargets = setOf(labelExt))
    val required = computeTargetsRequiredFor(listOf(labelA), provider)
    assertThat(required.map { it.label }).containsExactly(labelExt)
  }

  @Test
  fun testProtoDep() {
    val labelA = Label.of("//A:A")
    val labelProto = Label.of("//proto:proto_java_proto")
    val provider = MapDependencyGraphProvider(depsMap = mapOf(labelA to listOf(labelProto)), externalTargets = setOf(labelProto))
    val required = computeTargetsRequiredFor(listOf(labelA), provider)
    assertThat(required.map { it.label }).containsExactly(labelProto)
  }
}

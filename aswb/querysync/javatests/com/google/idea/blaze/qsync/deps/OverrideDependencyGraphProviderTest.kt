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
package com.google.idea.blaze.qsync.deps

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts
import com.google.idea.blaze.qsync.java.artifacts.AspectProto.OutputArtifact
import com.google.idea.blaze.qsync.project.DependencyGraphProvider
import com.google.idea.blaze.qsync.project.Target
import com.google.idea.blaze.qsync.project.TargetStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class OverrideDependencyGraphProviderTest {

  private val targetLabel = Label.of("//foo:target")
  private val depLabel = Label.of("//foo:dep")

  @Test
  fun getTarget_presentInOutputInfo_prefersOutputInfoDepsAndStatus() {
    // dep is external
    val depArtifacts = JavaArtifacts.newBuilder().setTarget(depLabel.toString()).setIsExternalDependency(true).build()

    // target depends on dep
    val targetArtifacts =
      JavaArtifacts.newBuilder()
        .setTarget(targetLabel.toString())
        .setIsExternalDependency(false)
        .addDepJavaInfoFiles(OutputArtifact.newBuilder().setFile("foo/dep.java-info.txt").build())
        .build()

    val outputInfo = OutputInfo.builder().setArtifactInfo(depArtifacts, targetArtifacts).build()

    val fallbackTarget = FakeTarget(targetLabel, TargetStatus.IN_PROJECT_SCOPE, emptyList())
    val fallbackProvider = FakeDependencyGraphProvider(mapOf(targetLabel to fallbackTarget))

    val provider = JavaOutputInfoDependencyGraphProvider(outputInfo, fallbackProvider)

    val target = provider.getTarget(targetLabel)
    assertThat(target.label).isEqualTo(targetLabel)
    assertThat(target.status).isEqualTo(TargetStatus.IN_PROJECT_SCOPE)

    // Dependencies should be resolved from OutputInfo
    assertThat(target.dependencies.map { it.label }).containsExactly(depLabel)

    // The dependency target itself should have its status determined from OutputInfo
    val depTarget = target.dependencies.first()
    assertThat(depTarget.status).isEqualTo(TargetStatus.EXTERNAL_TO_PROJECT_SCOPE)
  }

  @Test
  fun getTarget_missingFromOutputInfo_fallsBackToDelegate() {
    val outputInfo = OutputInfo.EMPTY

    val fallbackDep = FakeTarget(depLabel, TargetStatus.IN_PROJECT_SCOPE, emptyList())
    val fallbackTarget = FakeTarget(targetLabel, TargetStatus.EXTERNAL_TO_PROJECT_SCOPE, listOf(fallbackDep))
    val fallbackProvider = FakeDependencyGraphProvider(mapOf(targetLabel to fallbackTarget, depLabel to fallbackDep))

    val provider = JavaOutputInfoDependencyGraphProvider(outputInfo, fallbackProvider)

    val target = provider.getTarget(targetLabel)
    assertThat(target.label).isEqualTo(targetLabel)
    assertThat(target.status).isEqualTo(TargetStatus.EXTERNAL_TO_PROJECT_SCOPE)
    assertThat(target.dependencies.map { it.label }).containsExactly(depLabel)
  }

  @Test
  fun getTarget_statusIsExternal_whenJavaArtifactSaysSo() {
    val targetArtifacts = JavaArtifacts.newBuilder().setTarget(targetLabel.toString()).setIsExternalDependency(true).build()

    val outputInfo = OutputInfo.builder().setArtifactInfo(targetArtifacts).build()

    val fallbackTarget = FakeTarget(targetLabel, TargetStatus.IN_PROJECT_SCOPE, emptyList())
    val fallbackProvider = FakeDependencyGraphProvider(mapOf(targetLabel to fallbackTarget))

    val provider = JavaOutputInfoDependencyGraphProvider(outputInfo, fallbackProvider)

    val target = provider.getTarget(targetLabel)
    assertThat(target.status).isEqualTo(TargetStatus.EXTERNAL_TO_PROJECT_SCOPE)
  }

  private class FakeDependencyGraphProvider(private val targets: Map<Label, Target>) : DependencyGraphProvider {
    override fun getTarget(label: Label): Target {
      return targets[label] ?: FakeTarget(label, TargetStatus.UNKNOWN, emptyList())
    }
  }

  private class FakeTarget(override val label: Label, override val status: TargetStatus, override val dependencies: Collection<Target>) :
    Target {
    override fun equals(other: Any?): Boolean = (other as? Target)?.label == label

    override fun hashCode(): Int = label.hashCode()
  }
}

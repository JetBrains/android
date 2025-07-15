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
package com.google.idea.blaze.qsync.deps

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.Multimap
import com.google.devtools.build.lib.view.proto.Deps
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcCompilationInfo
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/** Build output artifacts and metadata. */
interface OutputInfo {
  val javaArtifactInfo: Map<Label, JavaArtifacts>
  val compileJdeps: Map<Label, Deps.Dependencies>
  val ccCompilationInfo: List<CcCompilationInfo>

  val jars: List<OutputArtifact>
  val aars: List<OutputArtifact>
  val generatedSources: List<OutputArtifact>
  val allJavaArtifacts: Collection<OutputArtifact>

  val isEmpty: Boolean
  val exitCode: Int
  val targetsWithErrors: Set<Label>

  val buildContext: DependencyBuildContext

  @VisibleForTesting
  data class Data(
    override val javaArtifactInfo: Map<Label, JavaArtifacts>,
    override val compileJdeps: Map<Label, Deps.Dependencies>,
    override val ccCompilationInfo: List<CcCompilationInfo>,
    private val artifacts: Map<OutputGroup, List<OutputArtifact>>,
    override val exitCode: Int,
    override val buildContext: DependencyBuildContext,
    override val targetsWithErrors: Set<Label>,
  ) : OutputInfo {
    override val allJavaArtifacts: Collection<OutputArtifact>
      get() = OutputGroup.entries.filter { it.usedBySymbolResolution }.flatMap { artifacts[it].orEmpty() }
    override val jars: List<OutputArtifact>
      get() = artifacts[OutputGroup.JARS].orEmpty()
    override val aars: List<OutputArtifact>
      get() = artifacts[OutputGroup.AARS].orEmpty()
    override val generatedSources: List<OutputArtifact>
      get() = artifacts[OutputGroup.GENSRCS].orEmpty()
    override val isEmpty: Boolean
      get() = artifacts.isEmpty() && ccCompilationInfo.isEmpty()
  }

  companion object {
    @JvmField
    val EMPTY: OutputInfo =
      Data(
        javaArtifactInfo = emptyMap(),
        compileJdeps = emptyMap(),
        ccCompilationInfo = emptyList(),
        artifacts = emptyMap(),
        exitCode = 0,
        buildContext = DependencyBuildContext.NONE,
        targetsWithErrors = emptySet(),
      )

    @JvmStatic
    fun create(
      allArtifacts: Multimap<OutputGroup, OutputArtifact>,
      javaArtifacts: Map<Path, JavaArtifacts>,
      compileJdeps: Map<Path, Deps.Dependencies>,
      ccInfo: List<CcCompilationInfo>,
      targetWithErrors: Set<Label>,
      exitCode: Int,
      buildContext: DependencyBuildContext,
    ): OutputInfo {
      val jdepsArtifactPathToTarget: Map<Path, Label> =
        javaArtifacts.values
          .flatMap { javaInfo ->
            javaInfo.compileJdepsList.map { jdepsOutputArtifact -> Path.of(jdepsOutputArtifact.getFile()) to Label.of(javaInfo.target) }
          }
          .toMap()

      return Data(
        javaArtifactInfo = javaArtifacts.values.associateBy { Label.of(it.target) },
        compileJdeps = compileJdeps.entries
          .associate { (jdepsArtifactPathToTarget[it.key] ?: error("Unknown compileJdeps artifact path: ${it.key}")) to it.value },
        ccCompilationInfo = ccInfo,
        artifacts = allArtifacts.asMap().mapValues { it.value.toList() },
        exitCode = exitCode,
        buildContext = buildContext,
        targetsWithErrors = targetWithErrors
      )
    }

    @TestOnly
    @JvmStatic
    fun builder(): TestOutputInfoBuilder = TestOutputInfoBuilder()
  }
}

@TestOnly
class TestOutputInfoBuilder() {
  private var outputGroups = ImmutableListMultimap.Builder<OutputGroup, OutputArtifact>().build()
  private var javaArtifacts = mapOf<Path, JavaArtifacts>()
  private var targetsWithErrors = setOf<Label>()

  fun setOutputGroups(outputGroups: ImmutableListMultimap<OutputGroup, OutputArtifact>): TestOutputInfoBuilder {
    this.outputGroups = outputGroups
    return this
  }

  fun setArtifactInfo(vararg artifactInfos: JavaArtifacts): TestOutputInfoBuilder {
    this.javaArtifacts = artifactInfos
      .associate {
        Label.of(it.target).let { label ->
          label.getBuildPackagePath().resolve(label.name + ".java-info.txt") to it
        }
      }
    return this
  }

  fun setTargetsWithErrors(vararg targets: Label): TestOutputInfoBuilder {
    this.targetsWithErrors = targets.toSet()
    return this
  }

  fun build(): OutputInfo {
    return OutputInfo.create(
      allArtifacts = outputGroups,
      javaArtifacts = javaArtifacts,
      compileJdeps = mapOf(),
      ccInfo = emptyList(),
      targetWithErrors = targetsWithErrors,
      exitCode = 0,
      buildContext = DependencyBuildContext.NONE,
    )
  }
}
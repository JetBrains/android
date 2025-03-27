/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult.bepparser

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.common.artifact.OutputArtifact
import org.jetbrains.annotations.TestOnly

/** A data class representing blaze's build event protocol (BEP) output for a build.  */
interface ParsedBepOutput {
  /**
   * The exit code of the Bazel build.
   */
  fun buildResult(): Int

  /**
   * The total number of bytes in the build event protocol output.
   */
  fun bepBytesConsumed(): Long

  /**
   * An obscure ID that can be used to identify the build in the external environment.
   *
   *
   * *DO NOT* attempt to interpret or compare.
   */
  fun idForLogging(): String

  /**
   * A list of the artifacts outputted by the given target to the given output group.
   *
   *
   * Note that the same artifact may be outputted by multiple targets and into multiple output groups.
   */
  fun getOutputGroupTargetArtifacts(
    outputGroup: String,
    label: String
  ): List<OutputArtifact>

  /**
   * A de-duplicated list of the artifacts outputted to the given output group.
   *
   *
   * Note that the same artifact may be outputted by multiple targets and into multiple output groups. Such artifacts are included in the
   * resulting list only once.
   */
  fun getOutputGroupArtifacts(outputGroup: String): List<OutputArtifact>

  /**
   * Label of any targets that were not build because of build errors.
   */
  fun targetsWithErrors(): Set<String>

  @TestOnly
  fun getAllOutputArtifactsForTesting(): List<OutputArtifact>

  class Legacy internal constructor(
    val buildId: String?,
    /**
     * A map from file set ID to file set, with the same ordering as the BEP stream.
     */
    @field:VisibleForTesting
    @JvmField
    val fileSets: ImmutableMap<String, FileSet>,
    private val syncStartTimeMillis: Long,
    /**
     * Returns the build result.
     */
    val buildResult: Int,
    val bepBytesConsumed: Long,
    /**
     * Returns the set of build targets that had an error.
     */
    val targetsWithErrors: ImmutableSet<String>
  ) {
    /**
     * Returns all output artifacts of the build.
     */
    @TestOnly
    fun getAllOutputArtifactsForTesting(): Set<OutputArtifact> {
      return fileSets
        .values
        .flatMap { it.parsedOutputs }
        .toSet()
    }

    /**
     * Returns a map from artifact key to [BepArtifactData] for all artifacts reported during
     * the build.
     */
    fun getFullArtifactData(): ImmutableMap<String, BepArtifactData> {
      return ImmutableMap.copyOf(
        fileSets
          .values
          .flatMap { it.toPerArtifactData() }
          .groupBy { it.artifact.bazelOutRelativePath }
          .mapValues { BepArtifactData.combine(it.value) }
      )
    }

    class FileSet internal constructor(
      @VisibleForTesting
      val parsedOutputs: List<OutputArtifact>,
      outputGroups: Set<String>,
      targets: Set<String>
    ) {
      @VisibleForTesting
      val outputGroups: Set<String>

      @VisibleForTesting
      val targets: Set<String>

      init {
        this.outputGroups = ImmutableSet.copyOf<String>(outputGroups)
        this.targets = ImmutableSet.copyOf<String>(targets)
      }

      fun toPerArtifactData(): Sequence<BepArtifactData> {
        return parsedOutputs.asSequence()
          .map { BepArtifactData(it, outputGroups, targets) }
      }
    }

    companion object {
      @VisibleForTesting
      val EMPTY: Legacy = Legacy(
        "build-id",
        ImmutableMap.of<String?, FileSet?>(),
        0,
        0,
        0,
        ImmutableSet.of<String?>()
      )
    }
  }
}

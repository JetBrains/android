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

}

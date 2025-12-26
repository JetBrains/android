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
package com.google.idea.blaze.qsync.java

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import kotlin.jvm.optionals.getOrNull

/**
 * Adds Kotlin compiler flags from toolchain targets to the project proto.
 *
 * This operation identifies Kotlin toolchain targets and extracts their compiler flags,
 * adding them to the workspace module in the project proto.
 *
 * NOTE: As a workaround, we currently take the flags from the first available Kotlin toolchain
 * and apply them to the single IDE workspace module. This is because Query Sync currently
 * merges all project targets into a single IDE module, but we need toolchain-level
 * configuration to correctly set up the Kotlin facet.
 */
class AddProjectKotlinCompilerFlags : ProjectProtoUpdateOperation {

  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>,
    externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder,
  ) {
    val toolchains = artifactState.targets()
      .filter { it.javaInfo().getOrNull()?.isKotlinToolchain ?: false }

    if (toolchains.isEmpty()) return

    if (toolchains.size > 1) {
      context.output(PrintOutput.error(
        "Multiple Kotlin toolchains found: ${toolchains.joinToString { it.label().toString() }}. Using flags from the first one."
      ))
    }

    val javaInfo = toolchains.first().javaInfo().get()
    update.module(Label.of("@aswb_workspace_module//")) {
      addKotlinCompilerFlags(javaInfo.kotlinCompilerFlags())
    }
  }
}

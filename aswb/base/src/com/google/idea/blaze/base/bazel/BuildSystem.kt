/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel

import com.google.errorprone.annotations.MustBeClosed
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider
import com.google.idea.blaze.base.command.info.BlazeInfo
import com.google.idea.blaze.base.model.BlazeVersionData
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.BazelQueryRunner
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.settings.BuildBinaryType
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.blaze.base.sync.SyncScope
import com.google.idea.blaze.exception.BuildException
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import java.io.InputStream
import java.util.Optional

/**
 * Encapsulates interactions with a Bazel based build system.
 *
 *
 * The main purpose of this class is to provide instances of [BuildInvoker] to encapsulate
 * a method of executing Bazel commands.
 */
interface BuildSystem {
  /**
   * Strategy to use for builds that are part of a project sync.
   */
  enum class SyncStrategy {
    /**
     * Never parallelize sync builds.
     */
    SERIAL,

    /**
     * Parallelize sync builds if it's deemed likely that doing so will be faster.
     */
    DECIDE_AUTOMATICALLY,

    /**
     * Always parallelize sync builds.
     */
    PARALLEL,
  }

  /**
   * Encapsulates a means of executing build commands, often as a Bazel compatible binary.
   */
  interface BuildInvoker {
    enum class Capability {
      /**
       * Capability to invoke blaze/bazel via CLI
       */
      SUPPORT_CLI,

      /**
       * Can return a process handler
       */
      RETURN_PROCESS_HANDLER,

      /**
       * Capability to run parallel builds
       */
      BUILD_PARALLEL_SHARDS,

      /**
       * Capability to run blaze/bazel query command with --query_file flag
       */
      SUPPORT_QUERY_FILE,

      /**
       * Capability to run blaze/bazel build/test command with --target_pattern_file flag
       */
      SUPPORT_TARGET_PATTERN_FILE,

      /**
       * Capability to debug Android local test
       */
      ATTACH_JAVA_DEBUGGER
    }

    val capabilities: Set<Capability>

    /**
     * Runs a blaze command, parses the build results into a [BlazeBuildOutputs] object.
     */
    @Throws(BuildException::class)
    fun invoke(
      blazeCommandBuilder: BlazeCommand.Builder,
      blazeContext: BlazeContext,
    ): BuildEventStreamProvider

    /**
     * Runs a blaze command and returns a process handler, which can be used by the IDE to control its execution.
     */
    @Throws(BuildException::class)
    fun invokeAsProcessHandler(
      blazeCommandBuilder: BlazeCommand.Builder,
      blazeContext: BlazeContext,
    ): ProcessHandler

    /**
     * Runs a blaze query command.
     *
     * @return [InputStream] from the stdout of the blaze invocation and null if the query fails
     */
    @MustBeClosed
    @Throws(BuildException::class)
    fun invokeQuery(
      blazeCommandBuilder: BlazeCommand.Builder,
      blazeContext: BlazeContext,
    ): InputStream

    /**
     * Runs a blaze info command.
     *
     * @return [InputStream] from the stdout of the blaze invocation and null if blaze info fails
     */
    @MustBeClosed
    @Throws(BuildException::class)
    fun invokeInfo(
      blazeCommandBuilder: BlazeCommand.Builder,
      blazeContext: BlazeContext,
    ): InputStream

    /**
     * Returns the type of this build interface. Used for logging purposes.
     */
    val type: BuildBinaryType

    val binaryPath: String

    @Throws(SyncScope.SyncFailedException::class)
    fun getBlazeInfo(blazeContext: BlazeContext): BlazeInfo

    /**
     * Returns the BuildSystem object.
     */
    val buildSystem: BuildSystem
  }

  /**
   * Returns the type of the build system.
   */
  val name: BuildSystemName

  /**
   * Possible digests of empty .jar files in BEP.
   *
   * This is used for optimization purposes only.
   */
  val emptyJarDigests: Set<String>

  /**
   * Get a Blaze invoker with desired capabilities.
   */
  fun getBuildInvoker(
    project: Project,
    requirements: Set<BuildInvoker.Capability>,
  ): Optional<BuildInvoker>

  /**
   * Get a Blaze invoker. Returns the parallel invoker if the sync strategy is PARALLEL and the system supports it (for legacy sync);
   * otherwise returns the standard invoker.
   */
  fun getBuildInvoker(project: Project): BuildInvoker {
    if (Blaze.getProjectType(project) != BlazeImportSettings.ProjectType.QUERY_SYNC
        && getSyncStrategy(project) == SyncStrategy.PARALLEL
    ) {
      return getBuildInvoker(
        project,
        requirements = setOf(BuildInvoker.Capability.BUILD_PARALLEL_SHARDS)
      ).orElseThrow()
    }
    return getBuildInvoker(
      project,
      requirements = emptySet()
    ).orElseThrow()
  }

  /**
   * Return the strategy for remote syncs to be used with this build system.
   */
  fun getSyncStrategy(project: Project): SyncStrategy

  /**
   * Populates the passed builder with version data.
   */
  fun populateBlazeVersionData(
    workspaceRoot: WorkspaceRoot,
    blazeInfo: BlazeInfo,
    builder: BlazeVersionData.Builder,
  )

  /**
   * Get bazel only version. Returns empty if it's not bazel project.
   */
  fun getBazelVersionString(blazeInfo: BlazeInfo): Optional<String>

  /**
   * Returns invocation link for the given invocation ID.
   */
  fun getInvocationLink(invocationId: String): Optional<String>

  fun createQueryRunner(project: Project): BazelQueryRunner
}

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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.BazelQueryRunner;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates interactions with a Bazel based build system.
 *
 * <p>The main purpose of this class is to provide instances of {@link BuildInvoker} to encapsulate
 * a method of executing Bazel commands.
 */
public interface BuildSystem {

  /**
   * Strategy to use for builds that are part of a project sync.
   */
  enum SyncStrategy {
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

    enum Capability {
      /**
       * Capability to build Android Instrumentation Test APK
       */
      BUILD_AIT,
      /**
       * Capability to invoke blaze/bazel via CLI
       */
      SUPPORT_CLI,
      /**
       * Capability to run parallel builds
       */
      BUILD_PARALLEL_SHARDS,
      /**
       * Capability to run blaze/bazel query command via remote invocation
       */
      RUN_REMOTE_QUERIES,
      /**
       * Capability to debug Android local test
       */
      DEBUG_LOCAL_TEST
    }

    default ImmutableSet<Capability> getCapabilities() {
      throw new UnsupportedOperationException("This invoker does not support capabilities.");
    }

    /**
     * Runs a blaze command, parses the build results into a {@link BlazeBuildOutputs} object.
     */
    BuildEventStreamProvider invoke(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) throws BuildException;

    /**
     * Runs a blaze query command.
     *
     * @return {@link InputStream} from the stdout of the blaze invocation and null if the query fails
     */
    @MustBeClosed
    InputStream invokeQuery(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) throws BuildException;

    /**
     * Runs a blaze info command.
     *
     * @return {@link InputStream} from the stdout of the blaze invocation and null if blaze info fails
     */
    @MustBeClosed
    InputStream invokeInfo(BlazeCommand.Builder blazeCommandBuilder, BlazeContext blazeContext) throws BuildException;

    /**
     * Returns the type of this build interface. Used for logging purposes.
     */
    BuildBinaryType getType();

    /**
     * The path to the build binary on disk.
     *
     * <p>TODO(mathewi) This should really be fully encapsulated inside the runner returned from
     * {@link #getCommandRunner()} since it's not applicable to all implementations.
     */
    String getBinaryPath();

    BlazeInfo getBlazeInfo(BlazeContext blazeContext) throws SyncFailedException;

    /**
     * Returns a {@link BlazeCommandRunner} to be used to invoke the build.
     */
    BlazeCommandRunner getCommandRunner();

    /**
     * Returns the BuildSystem object.
     */
    BuildSystem getBuildSystem();
  }

  /**
   * Returns the type of the build system.
   */
  BuildSystemName getName();

  /**
   * Get a Blaze invoker with desired capabilities.
   */
  Optional<BuildInvoker> getBuildInvoker(Project project, Set<BuildInvoker.Capability> requirements);

  /**
   * Get a Blaze invoker.
   */
  default BuildInvoker getBuildInvoker(Project project) {
    return getBuildInvoker(project, ImmutableSet.of()).orElseThrow();
  }

  /**
   * Get a Blaze invoker specific to executor type and run config.
   */
  default BuildInvoker getBuildInvoker(
    Project project, ExecutorType executorType, Kind targetKind) {
    throw new UnsupportedOperationException(
      String.format(
        "The getBuildInvoker method specific to executor type and target kind is not"
        + " implemented in %s",
        this.getClass().getSimpleName()));
  }

  /**
   * Get a Blaze invoker specific to the blaze command.
   */
  default BuildInvoker getBuildInvoker(
    Project project, BlazeCommandName command) {
    throw new UnsupportedOperationException(
      String.format(
        "The getBuildInvoker method specific to a blaze command is not implemented in %s",
        this.getClass().getSimpleName()));
  }

  /**
   * Return the strategy for remote syncs to be used with this build system.
   */
  SyncStrategy getSyncStrategy(Project project);

  /**
   * Populates the passed builder with version data.
   */
  void populateBlazeVersionData(
    WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo, BlazeVersionData.Builder builder);

  /**
   * Get bazel only version. Returns empty if it's not bazel project.
   */
  Optional<String> getBazelVersionString(BlazeInfo blazeInfo);

  /**
   * Returns the parallel invoker if the sync strategy is PARALLEL and the system supports it;
   * otherwise returns the standard invoker.
   */
  default BuildInvoker getDefaultInvoker(Project project) {
    if (Blaze.getProjectType(project) != ProjectType.QUERY_SYNC
        && getSyncStrategy(project) == SyncStrategy.PARALLEL) {
      return getBuildInvoker(project, ImmutableSet.of(BuildInvoker.Capability.BUILD_PARALLEL_SHARDS)).orElseThrow();
    }
    return getBuildInvoker(project);
  }

  /**
   * Returns invocation link for the given invocation ID.
   */
  default Optional<String> getInvocationLink(String invocationId) {
    return Optional.empty();
  }

  BazelQueryRunner createQueryRunner(Project project);
}

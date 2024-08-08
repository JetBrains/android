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
package com.google.idea.blaze.base.qsync;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.utils.Lists;

/** Finds candidate build packages for adding to the project view. */
public class CandidatePackageFinder {

  private static final int BAZEL_QUERY_EXIT_CODE_COMMAND_FAILURE = 7;

  public static CandidatePackageFinder create(QuerySyncProject project, Project ideProject) {
    BlazeContext context = BlazeContext.create();
    BuildInvoker invoker = project.getBuildSystem().getBuildInvoker(ideProject, context);
    Path workspacePath = WorkspaceRoot.fromProject(ideProject).path();
    return new CandidatePackageFinder(ideProject, invoker, workspacePath, context);
  }

  private final Project ideProject;

  private final BuildInvoker buildInvoker;
  private final Path workspaceRoot;
  private final BlazeContext context;

  @VisibleForTesting
  CandidatePackageFinder(
      Project ideProject, BuildInvoker buildInvoker, Path workspaceRoot, BlazeContext context) {
    this.ideProject = ideProject;
    this.buildInvoker = buildInvoker;
    this.workspaceRoot = workspaceRoot;
    this.context = context;
  }

  /**
   * A candidate package to be added to the project view. Include the package path and the total
   * number of packages included with it.
   */
  public static class CandidatePackage {
    public final Path path;
    public final int packageCount;

    CandidatePackage(Path path, int packageCount) {
      this.path = path;
      this.packageCount = packageCount;
    }
  }

  public ImmutableList<CandidatePackage> getCandidatePackages(
      Path forPath, Runnable cancellationCheck) throws BuildException {
    if (!Files.isDirectory(workspaceRoot.resolve(forPath))) {
      forPath = forPath.getParent();
    }
    List<CandidatePackage> candidates = Lists.newArrayList();
    ImmutableList<String> packages;
    // we query the given path and it's parents until we find one of:
    // - a path with at least 2 packages in it
    // - at least two paths with any packages in
    // this is to offer the user some choice, and ensure we don't add paths with no packages in
    // (which would cause the query to fail later on).
    do {
      cancellationCheck.run();
      packages = runQuery(forPath);
      if (!packages.isEmpty()) {
        candidates.add(new CandidatePackage(forPath, packages.size()));
      }
      forPath = forPath.getParent();
    } while (candidates.size() < 2 && packages.size() < 2);
    return ImmutableList.copyOf(candidates);
  }

  private ImmutableList<String> runQuery(Path path) throws BuildException {
    BlazeCommand.Builder command =
        BlazeCommand.builder(buildInvoker, BlazeCommandName.QUERY)
            .addBlazeFlags("--output", "package")
            .addBlazeFlags("//" + path + "/...");
    try (BuildResultHelper helper = buildInvoker.createBuildResultHelper()) {
      try (InputStream queryOut =
          buildInvoker.getCommandRunner().runQuery(ideProject, command, helper, context)) {
        return ImmutableList.copyOf(CharStreams.readLines(new InputStreamReader(queryOut, UTF_8)));
      } catch (BazelExitCodeException exitCodeException) {
        if (exitCodeException.getExitCode() == BAZEL_QUERY_EXIT_CODE_COMMAND_FAILURE) {
          // This covers the case that there were no matching packages which is WAI here.
          return ImmutableList.of();
        }
        throw exitCodeException;
      }
    } catch (IOException ioe) {
      throw new BuildException(ioe);
    }
  }
}

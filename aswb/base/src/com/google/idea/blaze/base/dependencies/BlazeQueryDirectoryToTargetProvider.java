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
package com.google.idea.blaze.base.dependencies;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.query.BlazeQueryLabelKindParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import javax.annotation.Nullable;

/** Runs a blaze query to derive a set of targets from the project's {@link ImportRoots}. */
public class BlazeQueryDirectoryToTargetProvider implements DirectoryToTargetProvider {
  private static final Logger logger =
      Logger.getInstance(BlazeQueryDirectoryToTargetProvider.class);

  @Nullable
  @Override
  public List<TargetInfo> doExpandDirectoryTargets(
      Project project,
      ImportRoots directories,
      WorkspacePathResolver pathResolver,
      BlazeContext context) {
    return runQuery(project, getQueryString(directories), context);
  }

  private static String getQueryString(ImportRoots directories) {
    StringBuilder targets = new StringBuilder();
    targets.append(
        directories.rootDirectories().stream()
            .map(w -> TargetExpression.allFromPackageRecursive(w).toString())
            .collect(joining(" + ")));
    for (WorkspacePath excluded : directories.excludeDirectories()) {
      targets.append(" - " + TargetExpression.allFromPackageRecursive(excluded).toString());
    }

    // exclude 'manual' targets, which shouldn't be built when expanding wildcard target patterns
    if (SystemInfo.isWindows) {
      // TODO(b/201974254): Windows support for Bazel sync (see
      // https://github.com/bazelbuild/intellij/issues/113).
      return String.format("attr('tags', '^((?!manual).)*$', %s)", targets);
    }
    return String.format("attr(\"tags\", \"^((?!manual).)*$\", %s)", targets);
  }

  /**
   * Runs a Blaze query synchronously, returning an output list of {@link TargetInfo}, or null if
   * the query failed.
   */
  @Nullable
  private static ImmutableList<TargetInfo> runQuery(
      Project project, String query, BlazeContext context) {
    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BlazeCommand.Builder command =
        BlazeCommand.builder(
                buildSystem.getDefaultInvoker(project, context), BlazeCommandName.QUERY)
            .addBlazeFlags("--output=label_kind")
            .addBlazeFlags("--keep_going")
            .addBlazeFlags(query);
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    BlazeQueryLabelKindParser outputProcessor = new BlazeQueryLabelKindParser(t -> true);
    try (BuildResultHelper helper = invoker.createBuildResultHelper();
        InputStream queryResultStream =
            invoker.getCommandRunner().runQuery(project, command, helper, context)) {
      new BufferedReader(new InputStreamReader(queryResultStream, UTF_8))
          .lines()
          .forEach(outputProcessor::processLine);
      return outputProcessor.getTargets();
    } catch (IOException | BuildException e) {
      logger.error(e.getMessage(), e);
      return null;
    }
  }
}

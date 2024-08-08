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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Futures;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.query.BlazeQueryLabelKindParser;
import com.google.idea.blaze.base.query.BlazeQueryOutputBaseProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Given a source file, runs a blaze query invocation to find the direct rdeps of that file.
 *
 * <p>This is expected to be slow, so should be asynchronous and/or cancellable.
 */
public class BlazeQuerySourceToTargetProvider implements SourceToTargetProvider {

  /**
   * Currently disabled for performance reasons. SourceToTargetProvider is called often, in the
   * background, and we don't want to monopolize the local blaze server.
   */
  private static final BoolExperiment enabled =
      new BoolExperiment("use.blaze.query.for.background.rdeps", false);

  /**
   * Blaze source-to-target heuristics for macros. Blaze's same_pkg_direct_rdeps method has a
   * limitation that prevents it from detecting targets in expanded macros. This fix will use a
   * modified recursive rdeps query to resolve it. Refer b/206027020#comment24.
   */
  private static final BoolExperiment isSourceToTargetHeuristicsForMacrosEnabled =
      new BoolExperiment("use.blaze.query.source.to.target.heuristics.for.macros", true);

  /** Prefix to identify targets connected to Kotlin macros */
  public static final String KOTLIN_MACRO_PREFIX = "kt_";
  /** Suffix to identify Kotlin source files */
  public static final String KOTLIN_FILE_SUFFIX = ".kt";

  /** Exception thrown while querying targets for a source file */
  public static class BlazeQuerySourceToTargetException extends Exception {
    public BlazeQuerySourceToTargetException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Override
  public Future<List<TargetInfo>> getTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath) {
    if (!enabled.getValue()) {
      return Futures.immediateFuture(null);
    }
    Label label = getSourceLabel(project, workspaceRelativePath);
    if (label == null) {
      return Futures.immediateFuture(null);
    }
    return PooledThreadExecutor.INSTANCE.submit(
        () ->
            Scope.root(
                context -> {
                  context.push(new IdeaLogScope());
                  try {
                    ImmutableList<TargetInfo> targetInfos =
                        runDirectRdepsQuery(
                            project, ImmutableList.of(label), context, ContextType.Other);
                    if (shouldRunRecursiveRdepsQuery(
                        ImmutableList.of(label), ContextType.Other, targetInfos)) {
                      return runRecursiveRdepsQuery(
                          project, ImmutableList.of(label), context, ContextType.Other);
                    }
                    return targetInfos;
                  } catch (BlazeQuerySourceToTargetException ex) {
                    return null;
                  }
                }));
  }

  /** Synchronously runs a blaze query to find the direct rdeps of the given source files. */
  @Nullable
  public static ImmutableList<TargetInfo> getTargetsBuildingSourceFiles(
      Project project, Collection<WorkspacePath> sources, BlazeContext context, ContextType type) {
    ImmutableList<Label> labels =
        sources.stream()
            .map(s -> getSourceLabel(project, s.relativePath()))
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    try {
      ImmutableList<TargetInfo> targetInfos = runDirectRdepsQuery(project, labels, context, type);
      if (shouldRunRecursiveRdepsQuery(labels, type, targetInfos)) {
        return runRecursiveRdepsQuery(project, labels, context, type);
      }
      return targetInfos;
    } catch (BlazeQuerySourceToTargetException ex) {
      return null;
    }
  }

  private static boolean shouldRunRecursiveRdepsQuery(
      Collection<Label> sources, ContextType type, ImmutableList<TargetInfo> targetInfos) {
    // This method is used by both full and partial sync to find targets for source file, but this
    // fix is only required for the partial sync as full sync is able to find targets when syncing
    // the BUILD file along with the source file. Ref. b/206027020#comment24. So, to ensure that
    // only partial sync uses this flow, we verify that there is a single source file
    return isSourceToTargetHeuristicsForMacrosEnabled.getValue()
        && sources.size() == 1
        // Ensure that fix is only applied on Kotlin files
        && sources.stream().anyMatch(s -> s.toString().endsWith(KOTLIN_FILE_SUFFIX))
        && type == ContextType.Sync
        // Verify that there are no targets other than Kotlin macros
        && targetInfos.stream()
            .map(TargetInfo::getKind)
            .map(kind -> Objects.toString(kind, /* nullDefault= */ ""))
            .allMatch(kind -> kind.startsWith(KOTLIN_MACRO_PREFIX));
  }

  @Nullable
  private static ImmutableList<TargetInfo> runDirectRdepsQuery(
      Project project, Collection<Label> sources, BlazeContext context, ContextType type)
      throws BlazeQuerySourceToTargetException {
    if (sources.isEmpty()) {
      return ImmutableList.of();
    }
    // quote labels to handle punctuation in file names
    String expr = "\"" + Joiner.on("\"+\"").join(sources) + "\"";
    String directRdepsQuery = String.format("same_pkg_direct_rdeps(%s)", expr);
    return getTargetInfoList(project, context, type, directRdepsQuery);
  }

  @Nullable
  private static ImmutableList<TargetInfo> runRecursiveRdepsQuery(
      Project project, Collection<Label> sources, BlazeContext context, ContextType type)
      throws BlazeQuerySourceToTargetException {
    String expr = "\"" + Joiner.on("\"+\"").join(sources) + "\"";
    String packageName = getPackageName(project, context, type, expr);
    String rdepsQuery =
        String.format("kind(\".*_test\", rdeps(%s:all, %s, 2))", packageName, sources.toArray()[0]);
    return getTargetInfoList(project, context, type, rdepsQuery);
  }

  @Nullable
  private static ImmutableList<TargetInfo> getTargetInfoList(
      Project project, BlazeContext context, ContextType type, String rdepsQuery)
      throws BlazeQuerySourceToTargetException {
    BlazeCommand.Builder command =
        getBlazeCommandBuilder(
            project, type, rdepsQuery, ImmutableList.of("--output=label_kind"), context);
    try (InputStream queryResultStream = runQuery(project, command, context)) {
      BlazeQueryLabelKindParser blazeQueryLabelKindParser =
          new BlazeQueryLabelKindParser(t -> true);
      if (queryResultStream == null) {
        return null;
      }
      new BufferedReader(new InputStreamReader(queryResultStream, UTF_8))
          .lines()
          .forEach(blazeQueryLabelKindParser::processLine);
      return blazeQueryLabelKindParser.getTargets();
    } catch (IOException e) {
      throw new BlazeQuerySourceToTargetException("Failed to get target info list", e);
    }
  }

  @Nullable
  private static String getPackageName(
      Project project, BlazeContext context, ContextType type, String expr)
      throws BlazeQuerySourceToTargetException {
    BlazeCommand.Builder commandBuilder =
        getBlazeCommandBuilder(project, type, expr, ImmutableList.of("--output=package"), context);

    try (InputStream queryResultStream = runQuery(project, commandBuilder, context)) {
      return queryResultStream == null
          ? null
          : CharStreams.toString(new InputStreamReader(queryResultStream, UTF_8)).trim();
    } catch (IOException e) {
      context.output(
          PrintOutput.log(
              String.format("Failed to execute blaze query: %s", e.getCause().getMessage())));
      throw new BlazeQuerySourceToTargetException(e.getCause().getMessage(), e);
    }
  }

  @Nullable
  @MustBeClosed
  private static InputStream runQuery(
      Project project, BlazeCommand.Builder blazeCommand, BlazeContext context)
      throws BlazeQuerySourceToTargetException {
    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getDefaultInvoker(project, context);
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      return invoker.getCommandRunner().runQuery(project, blazeCommand, buildResultHelper, context);
    } catch (BuildException e) {
      context.output(
          PrintOutput.log(String.format("Failed to execute blaze query: %s", e.getMessage())));
      throw new BlazeQuerySourceToTargetException(e.getMessage(), e);
    }
  }

  private static BlazeCommand.Builder getBlazeCommandBuilder(
      Project project,
      ContextType type,
      String query,
      List<String> additionalBlazeFlags,
      BlazeContext context) {
    // never use a custom output base for queries during sync
    String outputBaseFlag =
        type == ContextType.Sync
            ? null
            : BlazeQueryOutputBaseProvider.getInstance(project).getOutputBaseFlag();
    BuildInvoker buildInvoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getDefaultInvoker(project, context);
    return BlazeCommand.builder(buildInvoker, BlazeCommandName.QUERY)
        .addBlazeFlags(additionalBlazeFlags)
        .addBlazeFlags("--keep_going")
        .addBlazeFlags(query)
        .addBlazeStartupFlags(
            outputBaseFlag == null ? ImmutableList.of() : ImmutableList.of(outputBaseFlag));
  }

  /**
   * Derives the blaze target label corresponding to a source file, or null if it can't be
   * determined.
   */
  @Nullable
  private static Label getSourceLabel(Project project, String workspaceRelativePath) {
    WorkspacePathResolver resolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (resolver == null) {
      return null;
    }
    File file = resolver.resolveToFile(workspaceRelativePath);
    return WorkspaceHelper.getBuildLabel(project, file);
  }
}

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
package com.google.idea.blaze.base.qsync;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.build.BlazeBuildListener;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** An object that knows how to build dependencies for given targets */
public class BazelRenderJarBuilder implements RenderJarBuilder {

  protected final Project project;
  protected final BuildSystem buildSystem;
  protected final WorkspaceRoot workspaceRoot;

  public BazelRenderJarBuilder(
      Project project, BuildSystem buildSystem, WorkspaceRoot workspaceRoot) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.workspaceRoot = workspaceRoot;
  }

  @Override
  public RenderJarInfo buildRenderJar(BlazeContext context, Set<Label> buildTargets)
      throws IOException, BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      List<String> additionalBlazeFlags =
          BlazeFlags.blazeFlags(
              project,
              projectViewSet,
              BlazeCommandName.BUILD,
              context,
              BlazeInvocationContext.OTHER_CONTEXT);

      String aspectLocation = prepareAspect(context);
      BlazeCommand.Builder builder =
          BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
              .addBlazeFlags(buildTargets.stream().map(Label::toString).collect(toImmutableList()))
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(additionalBlazeFlags)
              .addBlazeFlags(
                  String.format(
                      "--aspects=%1$s%%collect_compose_dependencies,%1$s%%package_compose_dependencies",
                      aspectLocation))
              .addBlazeFlags("--output_groups=render_jars");

      BlazeBuildOutputs outputs =
          invoker.getCommandRunner().run(project, builder, buildResultHelper, context);
      BazelExitCodeException.throwIfFailed(builder, outputs.buildResult);
      // Building render jar also involves building the dependencies of the file,
      // (as discussed in b/309154453#comment5). So we also invoke the full QuerySync to build the
      // dependencies and notify the sync listeners.
      runFullSyncAndNotifyListeners(outputs);
      return createRenderJarInfo(outputs);
    }
  }

  protected CharSource getAspect() throws IOException {
    return MoreFiles.asCharSource(getBundledAspectPath(), UTF_8);
  }

  private void runFullSyncAndNotifyListeners(BlazeBuildOutputs buildOutputs) {
    // TODO(b/336622303): Send an event instead of null to stats
    ListenableFuture<Boolean> syncFuture = QuerySyncManager.getInstance(project)
        .fullSync(QuerySyncActionStatsScope.create(getClass(), null), TaskOrigin.USER_ACTION);
    // Notify the build listeners after file caches are done refreshing.
    // This is required for the Project System to render the preview (check BuildCallbackPublisher)
    // TODO(b/336620315): Migrate this to new preview design
    Futures.addCallback(
        syncFuture,
        new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(@Nullable Boolean unused) {
            BlazeBuildListener.EP_NAME
                .extensions()
                .forEach(ep -> ep.buildCompleted(project, buildOutputs.buildResult));
          }

          @Override
          public void onFailure(Throwable throwable) {
            // No additional steps for failures. The file caches notify users and
            // print logs as required.
            BlazeBuildListener.EP_NAME
                .extensions()
                .forEach(ep -> ep.buildCompleted(project, buildOutputs.buildResult));
          }
        },
        MoreExecutors.directExecutor());
  }

  protected Path getBundledAspectPath() {
    PluginDescriptor plugin = checkNotNull(PluginManager.getPluginByClass(getClass()));
    return Paths.get(plugin.getPluginPath().toString(), "aspect", "build_compose_dependencies.bzl");
  }

  protected Label getGeneratedAspectLabel() {
    return Label.of("//.aswb:build_compose_dependencies.bzl");
  }

  /**
   * Prepares for use, and returns the location of the {@code build_compose_dependencies.bzl}
   * aspect.
   *
   * <p>The return value is a string in the format expected by bazel for an aspect file, omitting
   * the name of the aspect within that file. For example, {@code //package:aspect.bzl}.
   */
  // TODO(b/336628891): This will be combined with build_dependencies.bzl aspect
  protected String prepareAspect(BlazeContext context) throws IOException, BuildException {
    Label generatedAspectLabel = getGeneratedAspectLabel();
    Path generatedAspect =
        workspaceRoot
            .path()
            .resolve(generatedAspectLabel.getPackage())
            .resolve(generatedAspectLabel.getName());
    if (!Files.exists(generatedAspect.getParent())) {
      Files.createDirectories(generatedAspect.getParent());
    }
    Files.writeString(generatedAspect, getAspect().read());
    // bazel asks BUILD file exists with the .bzl file. It's ok that BUILD file contains nothing.
    Path buildPath = generatedAspect.resolveSibling("BUILD");
    if (!Files.exists(buildPath)) {
      Files.createFile(buildPath);
    }
    return generatedAspectLabel.toString();
  }

  private RenderJarInfo createRenderJarInfo(BlazeBuildOutputs blazeBuildOutputs) {
    ImmutableList<OutputArtifact> renderJars =
      blazeBuildOutputs.getOutputGroupArtifacts("render_jars");
    // TODO(b/283283123): Update the aspect to only return the render jar of the required target.
    // TODO(b/283280194): To setup fqcn -> target and target -> render jar mappings that would
    // increase the count of render jars but help with the performance by reducing the size of the
    // render jar loaded by the class loader.
    // TODO(b/336633197): Investigate performance impact of large number of render jars
    return RenderJarInfo.create(renderJars, blazeBuildOutputs.buildResult.exitCode);
  }
}


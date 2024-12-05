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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStats;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.proto.ProtoStringInterner;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.OutputGroup;
import com.google.idea.blaze.qsync.deps.OutputInfo;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcCompilationInfo;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.StringExperiment;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import com.jgoodies.common.base.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** An object that knows how to build dependencies for given targets */
public class BazelDependencyBuilder implements DependencyBuilder {
  private static final Logger logger = Logger.getInstance(BazelDependencyBuilder.class);

  public static final BoolExperiment buildGeneratedSrcJars =
      new BoolExperiment("qsync.build.generated.src.jars", false);

  public static final StringExperiment aspectLocation =
    new StringExperiment("qsync.build.aspect.location");

  /**
   * Logs message if the number of artifact info files fetched is greater than
   * FILE_NUMBER_LOG_THRESHOLD
   */
  private static final int FILE_NUMBER_LOG_THRESHOLD = 1;

  /**
   * Logs message if the size of all artifact info files fetched is greater than
   * FETCH_SIZE_LOG_THRESHOLD
   */
  private static final long FETCH_SIZE_LOG_THRESHOLD = (1 << 20); // 1 mB

  protected final Project project;
  protected final BuildSystem buildSystem;
  protected final ProjectDefinition projectDefinition;
  protected final WorkspaceRoot workspaceRoot;
  protected final Optional<BlazeVcsHandler> vcsHandler;
  protected final ImmutableSet<String> handledRuleKinds;
  protected final BuildArtifactCache buildArtifactCache;

  public BazelDependencyBuilder(
    Project project,
    BuildSystem buildSystem,
    ProjectDefinition projectDefinition,
    WorkspaceRoot workspaceRoot,
    Optional<BlazeVcsHandler> vcsHandler,
    BuildArtifactCache buildArtifactCache,
    ImmutableSet<String> handledRuleKinds) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.projectDefinition = projectDefinition;
    this.workspaceRoot = workspaceRoot;
    this.vcsHandler = vcsHandler;
    this.handledRuleKinds = handledRuleKinds;
    this.buildArtifactCache = buildArtifactCache;
  }

  private static final ImmutableMultimap<QuerySyncLanguage, OutputGroup> OUTPUT_GROUPS_BY_LANGUAGE =
      ImmutableMultimap.<QuerySyncLanguage, OutputGroup>builder()
          .putAll(
              QuerySyncLanguage.JAVA,
              OutputGroup.JARS,
              OutputGroup.AARS,
              OutputGroup.GENSRCS,
              OutputGroup.ARTIFACT_INFO_FILE)
          .putAll(QuerySyncLanguage.CC, OutputGroup.CC_HEADERS, OutputGroup.CC_INFO_FILE)
          .build();

  @Override
  public OutputInfo build(
      BlazeContext context, Set<Label> buildTargets, Set<QuerySyncLanguage> languages)
      throws IOException, BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    Optional<BuildDepsStats.Builder> buildDepsStatsBuilder =
        BuildDepsStatsScope.fromContext(context);
    buildDepsStatsBuilder.ifPresent(stats -> stats.setBlazeBinaryType(invoker.getType()));
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      String includes =
          projectDefinition.projectIncludes().stream()
              .map(path -> "//" + path)
              .collect(joining(","));
      String excludes =
          projectDefinition.projectExcludes().stream()
              .map(path -> "//" + path)
              .collect(joining(","));
      String aspectLocation = prepareAspect(context);
      Set<String> ruleKindsToBuild =
          Sets.difference(BlazeQueryParser.ALWAYS_BUILD_RULE_KINDS, handledRuleKinds);
      String alwaysBuildParam = Joiner.on(",").join(ruleKindsToBuild);

      ImmutableSet<OutputGroup> outputGroups =
          languages.stream()
              .map(OUTPUT_GROUPS_BY_LANGUAGE::get)
              .flatMap(Collection::stream)
              .collect(ImmutableSet.toImmutableSet());

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      // TODO This is not SYNC_CONTEXT, but also not OTHER_CONTEXT, we need to decide what kind
      // of flags need to be passed here.
      List<String> additionalBlazeFlags =
          BlazeFlags.blazeFlags(
              project,
              projectViewSet,
              BlazeCommandName.BUILD,
              context,
              BlazeInvocationContext.OTHER_CONTEXT);

      BlazeCommand.Builder builder =
          BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
              .addBlazeFlags(buildTargets.stream().map(Label::toString).collect(toImmutableList()))
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(additionalBlazeFlags)
              .addBlazeFlags(
                  String.format(
                      "--aspects=%1$s%%collect_dependencies,%1$s%%package_dependencies",
                      aspectLocation))
              .addBlazeFlags(String.format("--aspects_parameters=include=%s", includes))
              .addBlazeFlags(String.format("--aspects_parameters=exclude=%s", excludes))
              .addBlazeFlags(
                  String.format("--aspects_parameters=always_build_rules=%s", alwaysBuildParam))
              .addBlazeFlags("--aspects_parameters=generate_aidl_classes=True")
              .addBlazeFlags(
                  String.format(
                      "--aspects_parameters=use_generated_srcjars=%s",
                      buildGeneratedSrcJars.getValue() ? "True" : "False"))
              .addBlazeFlags("--noexperimental_run_validations")
              .addBlazeFlags("--keep_going");
      outputGroups.stream()
          .map(g -> "--output_groups=" + g.outputGroupName())
          .forEach(builder::addBlazeFlags);
      buildDepsStatsBuilder.ifPresent(
          stats -> stats.setBuildFlags(builder.build().toArgumentList()));
      Instant buildTime = Instant.now();
      BlazeBuildOutputs outputs =
          invoker.getCommandRunner().run(project, builder, buildResultHelper, context);
      buildDepsStatsBuilder.ifPresent(
          stats -> {
            stats.setBuildIds(outputs.getBuildIds());
            stats.setBepByteConsumed(outputs.bepBytesConsumed);
          });

      BazelExitCodeException.throwIfFailed(
          builder,
          outputs.buildResult,
          ThrowOption.ALLOW_PARTIAL_SUCCESS,
          ThrowOption.ALLOW_BUILD_FAILURE);

      return createOutputInfo(outputs, outputGroups, buildTime, context);
    }
  }

  /**
   * Provides information about files that must be create in the workspace root for the aspect to operate.
   *
   * @return A map of (workspace-relative path) to (contents to write there).
   */
  protected ImmutableMap<Path, ByteSource> getAspectFiles() {
    return ImmutableMap.of(
      Path.of(".aswb/BUILD"), ByteSource.empty(),
      Path.of(".aswb/build_dependencies.bzl"), MoreFiles.asByteSource(getBundledAspectPath("build_dependencies.bzl")),
      Path.of(".aswb/build_dependencies_deps.bzl"), MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_deps.bzl"))
      );
  }

  /**
   * Returns the label of the build_dependencies aspect. This must refer to a file populated from {@link #getAspectFiles()}.
   */
  protected Label getGeneratedAspectLabel() {
    return Label.of("//.aswb:build_dependencies.bzl");
  }

  protected Path getBundledAspectPath(String dir, String filename) {
    String aspectPath = System.getProperty(String.format("qsync.aspect.%s.file", filename));
    if (aspectPath != null) {
      return Path.of(aspectPath);
    }
    PluginDescriptor plugin = checkNotNull(PluginManager.getPluginByClass(getClass()));
    Path rootAspectDirectory;
    if (Strings.isNotEmpty(aspectLocation.getValue())) {
      Path workspaceAbsolutePath = workspaceRoot.absolutePathFor("");
      // NOTE: aspectLocation allows both relative and absolute paths.
      rootAspectDirectory = workspaceAbsolutePath.resolve(aspectLocation.getValue());
      logger.info("Using build aspect from: " + rootAspectDirectory);
    }
    else{
      rootAspectDirectory = plugin.getPluginPath();
    }
    return rootAspectDirectory.resolve(dir).resolve(filename);
  }

  protected Path getBundledAspectPath(String filename) {
    return getBundledAspectPath("aspect", filename);
  }

  /**
   * Prepares for use, and returns the location of the {@code build_dependencies.bzl} aspect.
   *
   * <p>The return value is a string in the format expected by bazel for an aspect file, omitting
   * the name of the aspect within that file. For example, {@code //package:aspect.bzl}.
   */
  protected String prepareAspect(BlazeContext context) throws IOException, BuildException {
    for (Map.Entry<Path, ByteSource> e : getAspectFiles().entrySet()) {
      Path absolutePath = workspaceRoot.path().resolve(e.getKey());
      Files.createDirectories(absolutePath.getParent());
      Files.copy(e.getValue().openStream(), absolutePath, StandardCopyOption.REPLACE_EXISTING);
    }
    return getGeneratedAspectLabel().toString();
  }

  private OutputInfo createOutputInfo(
      BlazeBuildOutputs blazeBuildOutputs,
      Set<OutputGroup> outputGroups,
      Instant buildTime,
      BlazeContext context)
      throws BuildException {
    ImmutableListMultimap<OutputGroup, OutputArtifact> allArtifacts =
        GroupedOutputArtifacts.create(blazeBuildOutputs, outputGroups);

    ImmutableList<OutputArtifact> artifactInfoFiles =
        allArtifacts.get(OutputGroup.ARTIFACT_INFO_FILE);
    ImmutableList<OutputArtifact> ccArtifactInfoFiles = allArtifacts.get(OutputGroup.CC_INFO_FILE);
    long startTime = System.currentTimeMillis();
    int totalFilesToFetch = artifactInfoFiles.size() + ccArtifactInfoFiles.size();
    long totalBytesToFetch =
        artifactInfoFiles.stream().mapToLong(OutputArtifact::getLength).sum()
            + ccArtifactInfoFiles.stream().mapToLong(OutputArtifact::getLength).sum();

    boolean shouldLog =
        totalFilesToFetch > FILE_NUMBER_LOG_THRESHOLD
            || totalBytesToFetch > FETCH_SIZE_LOG_THRESHOLD;
    if (shouldLog) {
      context.output(
        PrintOutput.log(
          String.format(
            "Fetching and parsing %d artifact info files (%s)",
            totalFilesToFetch, StringUtilRt.formatFileSize(totalBytesToFetch))));
    }

    ImmutableSet.Builder<JavaArtifacts> artifactInfoFilesBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<CcCompilationInfo> ccInfoBuilder = ImmutableSet.builder();

    List<JavaArtifacts> artifactInfos =
      readAndTransformInfoFiles(context, artifactInfoFiles, this::readArtifactInfoFile);
    List<CcCompilationInfo> ccInfos =
      readAndTransformInfoFiles(context, ccArtifactInfoFiles, this::readCcInfoFile);

    artifactInfoFilesBuilder.addAll(artifactInfos);
    ccInfoBuilder.addAll(ccInfos);

    long elapsed = System.currentTimeMillis() - startTime;
    if (shouldLog) {
      context.output(
        PrintOutput.log(String.format("Fetched and parsed artifact info files in %d ms", elapsed)));
    }
    Optional<VcsState> vcsState = Optional.empty();
    if (vcsHandler.isPresent()) {
      try {
        vcsState = vcsHandler.get().vcsStateForWorkspaceStatus(blazeBuildOutputs.workspaceStatus);
      } catch (BuildException e) {
        context.handleExceptionAsWarning("Failed to get VCS state", e);
      }
    }
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            // getOnlyElement should be safe since we never shard querysync builds:
            getOnlyElement(blazeBuildOutputs.getBuildIds()), buildTime, vcsState);

    return OutputInfo.create(
        allArtifacts,
        artifactInfoFilesBuilder.build(),
        ccInfoBuilder.build(),
        blazeBuildOutputs.getTargetsWithErrors().stream()
            .map(Object::toString)
            .map(Label::of)
            .collect(toImmutableSet()),
        blazeBuildOutputs.buildResult.exitCode,
        buildContext);
  }

  @FunctionalInterface
  private interface CheckedTransform<T, R> {
    R apply(T t) throws BuildException;
  }


  private <T> ImmutableList<T> readAndTransformInfoFiles(
    Context<?> context,
    ImmutableList<OutputArtifact> artifactInfoFiles,
    CheckedTransform<ByteSource, T> transform) throws BuildException {

    ListenableFuture<?> listenableFuture = buildArtifactCache.addAll(artifactInfoFiles, context);
    Stopwatch sw = Stopwatch.createStarted();
    // TODO: solodkyy - For now separate fetching and parsing. We have had some thread safety issues and it allows us reporting fetching
    //  time correctly which happens to be much shorter than the time it takes to parse the info files.
    try {
      final var unused = listenableFuture.get();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuildException(e);
    }
    catch (ExecutionException e) {
      throw new BuildException(e);
    }
    context.output(PrintOutput.output("Fetched %d info files in %sms", artifactInfoFiles.size(), sw.elapsed().toMillis()));
    ImmutableList<ListenableFuture<CachedArtifact>> artifactFutures =
      artifactInfoFiles.stream()
        .map(OutputArtifact::getDigest)
        .map(digest -> {
          final var result = buildArtifactCache.get(digest);
          if (result.isEmpty()) {
            context.output(PrintOutput.error("Failed to get artifact future for: " + digest));
            context.setHasError();
          }
          return result;
        })
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableList());

    ImmutableList<ListenableFuture<T>> futures =
      artifactFutures.stream()
        .map(it ->
               Futures.transformAsync(it, a -> immediateFuture(transform.apply(a.byteSource())), FetchExecutor.EXECUTOR))
        .collect(toImmutableList());
    try {
      return ImmutableList.copyOf(Futures.allAsList(futures).get());
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuildException(e);
    }
    catch (ExecutionException e) {
      throw new BuildException(e);
    }
  }

  private JavaArtifacts readArtifactInfoFile(ByteSource file) throws BuildException {
    return ProtoStringInterner.intern(
        readArtifactInfoProtoFile(JavaArtifacts.newBuilder(), file).build());
  }

  private CcCompilationInfo readCcInfoFile(ByteSource file) throws BuildException {
    return ProtoStringInterner.intern(
        readArtifactInfoProtoFile(CcCompilationInfo.newBuilder(), file).build());
  }

  protected <B extends Message.Builder> B readArtifactInfoProtoFile(B builder, ByteSource file)
    throws BuildException {
    try (InputStream inputStream = file.openStream()) {
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder;
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }
}

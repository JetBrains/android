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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.intellij.openapi.util.text.StringUtil.sanitizeJavaIdentifier;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.view.proto.Deps;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStats;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.VersionChecker;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.proto.ProtoStringInterner;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.NewArtifactTracker;
import com.google.idea.blaze.qsync.deps.OutputGroup;
import com.google.idea.blaze.qsync.deps.OutputInfo;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcCompilationInfo;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.jetbrains.annotations.VisibleForTesting;

/** An object that knows how to build dependencies for given targets */
public class BazelDependencyBuilder implements DependencyBuilder, BazelDependencyBuilderPublicForTests {

  @VisibleForTesting
  public static final BoolExperiment buildGeneratedSrcJars =
      new BoolExperiment("qsync.build.generated.src.jars", false);

  // Note, this is currently incompatible with the build API.
  public static final BoolExperiment buildUseTargetPatternFile =
      new BoolExperiment("qsync.build.use.target.pattern.file", true);
  public static final BoolExperiment buildEnforceProjectConfigs =
    new BoolExperiment("qsync.build.enforce.project.configs", false);

  public static final String INVOCATION_FILES_DIR = ".aswb";

  public static final Label RULES_ANDROID_RULES_BZL1 = Label.of("@@rules_android~//android:rules.bzl");
  public static final Label RULES_ANDROID_RULES_BZL2 = Label.of("@@rules_android+//android:rules.bzl");

  // The following .bzl file defines the iml_module rule used by Android Studio
  public static final Label STUDIO_IML_MODULE_RULE =  Label.of("//tools/base/bazel:bazel.bzl");

  public record BuildDependencyParameters(
      ImmutableList<String> include,
      ImmutableList<String> exclude,
      ImmutableList<String> alwaysBuildRules,
      boolean generateIdlClasses,
      boolean useGeneratedSrcJars) {}

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
  protected final SnapshotHolder snapshotHolder;
  protected final WorkspaceRoot workspaceRoot;
  protected final Optional<BlazeVcsHandler> vcsHandler;
  protected final ImmutableSet<String> handledRuleKinds;
  protected final BuildArtifactCache buildArtifactCache;
  private final AspectFiles aspectFiles;

  /**
   * @param argsAndFlags arguments and flags to be passed to `bazel build` command to build
   *     dependencies and metadata required by query sync.
   * @param requestedOutputGroups lists output groups that are requested by {@code argsAndFlags}.
   */
  public record BuildDependenciesBazelInvocationInfo(
      ImmutableList<String> argsAndFlags,
      ImmutableSet<OutputGroup> requestedOutputGroups,
      ImmutableMap<Path, ByteSource> invocationWorkspaceFiles) {}

  public BazelDependencyBuilder(
    Project project,
    BuildSystem buildSystem,
    ProjectDefinition projectDefinition,
    SnapshotHolder snapshotHolder,
    WorkspaceRoot workspaceRoot,
    Optional<BlazeVcsHandler> vcsHandler,
    BuildArtifactCache buildArtifactCache,
    ImmutableSet<String> handledRuleKinds) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.projectDefinition = projectDefinition;
    this.snapshotHolder = snapshotHolder;
    this.workspaceRoot = workspaceRoot;
    this.vcsHandler = vcsHandler;
    this.handledRuleKinds = handledRuleKinds;
    this.buildArtifactCache = buildArtifactCache;
    this.aspectFiles = new AspectFiles(workspaceRoot);
  }

  @Override
  public OutputInfo build(BlazeContext context, Set<Label> buildTargets, Collection<OutputGroup> outputGroups)
    throws IOException, BuildException {
    try (final var ignoredLock =
        ApplicationManager.getApplication()
            .getService(BuildDependenciesLockService.class)
            .lockWorkspace(workspaceRoot.path().toString())) {
      if (VersionChecker.versionMismatch()) {
        throw new BuildException(
            "The IDE has been upgraded in the background. Bazel build aspect files maybe"
                + " incompatible. Please restart the IDE.");
      }
      BuildInvoker invoker = buildSystem.getBuildInvoker(project);
      final var buildDependenciesBazelInvocationInfo = getInvocationInfo(context, buildTargets, invoker.getCapabilities(), outputGroups);
      prepareInvocationFiles(
          context, buildDependenciesBazelInvocationInfo.invocationWorkspaceFiles());


      Optional<BuildDepsStats.Builder> buildDepsStatsBuilder =
          BuildDepsStatsScope.fromContext(context);
      buildDepsStatsBuilder.ifPresent(stats -> stats.setBlazeBinaryType(invoker.getType()));
      BlazeCommand.Builder builder =
          BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
              .addBlazeFlags(buildDependenciesBazelInvocationInfo.argsAndFlags());

      buildDepsStatsBuilder.ifPresent(
          stats -> stats.setBuildFlags(builder.build().toArgumentList()));
      Instant buildTime = Instant.now();
      try (BuildEventStreamProvider streamProvider = invoker.invoke(builder, context)) {
        BlazeBuildOutputs outputs =
            BlazeBuildOutputs.fromParsedBepOutput(
                BuildResultParser.getBuildOutput(streamProvider, Interners.STRING));
        BazelExitCodeException.throwIfFailed(
            builder,
            outputs.buildResult(),
            ThrowOption.ALLOW_PARTIAL_SUCCESS,
            ThrowOption.ALLOW_BUILD_FAILURE);

        return createOutputInfo(
            outputs,
            buildDependenciesBazelInvocationInfo.requestedOutputGroups(),
            buildTime,
            context);
      }
    }
  }

  @VisibleForTesting
  @Override
  public BuildDependenciesBazelInvocationInfo getInvocationInfo(
    BlazeContext context,
    Set<Label> buildTargets,
    Set<BuildSystem.BuildInvoker.Capability> buildInvokerCapabilities,
    Collection<OutputGroup> outputGroups
  ) {
    ImmutableList<String> includes =
        projectDefinition.projectIncludes().stream()
            .map(path -> "//" + path)
            .collect(toImmutableList());
    ImmutableList<String> excludes =
        projectDefinition.projectExcludes().stream()
            .map(path -> "//" + path)
            .collect(toImmutableList());
    ImmutableList<String> alwaysBuildRules =
        ImmutableList.copyOf(
            Sets.difference(BlazeQueryParser.ALWAYS_BUILD_RULE_KINDS, handledRuleKinds));
    final var parameters =
        new BuildDependencyParameters(
            includes,
            excludes,
            alwaysBuildRules,
            true,
            buildGeneratedSrcJars.getValue());

    InvocationFiles invocationFiles = getInvocationFiles(buildTargets, buildInvokerCapabilities, parameters);

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

    final var querySyncFlags = ImmutableList.<String>builder();
    if (invocationFiles.targetPatternFileWorkspaceRelativeFile().isPresent()) {
      querySyncFlags.add(
          "--target_pattern_file="
              + invocationFiles.targetPatternFileWorkspaceRelativeFile().get());
    } else {
      querySyncFlags.addAll(buildTargets.stream().map(Label::toString).collect(toImmutableList()));
    }
    querySyncFlags.addAll(additionalBlazeFlags);
    querySyncFlags.add(
        String.format(
            "--aspects=%1$s%%collect_dependencies,%1$s%%package_dependencies",
            invocationFiles.aspectFileLabel()));
    querySyncFlags.add("--noexperimental_run_validations");
    if (!buildEnforceProjectConfigs.getValue()) {
      querySyncFlags.add("--noenforce_project_configs");
    }
    querySyncFlags.add("--keep_going");
    querySyncFlags.addAll(
      outputGroups.stream()
            .map(g -> "--output_groups=" + g.getOutputGroupName())
            .collect(toImmutableList()));

    return new BuildDependenciesBazelInvocationInfo(querySyncFlags.build(), ImmutableSet.copyOf(outputGroups), invocationFiles.files());
  }

  public record InvocationFiles(
      ImmutableMap<Path, ByteSource> files,
      String aspectFileLabel,
      Optional<String> targetPatternFileWorkspaceRelativeFile) {}

  protected Map<String, ByteSource> getBuildDependenciesAspectDepsFiles() {
    ImmutableMap.Builder<String, ByteSource> files = ImmutableMap.builder();
    files.put(
      "build_dependencies_deps.bzl",
      MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_deps.bzl")));

    // Aspects for Android support
    if (snapshotHolder.getCurrent().map(it -> it.queryData().querySummary().getAllBuildIncludedFiles().contains(RULES_ANDROID_RULES_BZL1) ||
                                              it.queryData().querySummary().getAllBuildIncludedFiles().contains(RULES_ANDROID_RULES_BZL2))
      .orElse(false)) {
      files.put(
        "build_dependencies_android_deps.bzl",
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_android_rules_android_deps.bzl")));
    }
    else if (BlazeProjectDataManager.getInstance(project).getBlazeProjectData().getBlazeVersionData().bazelIsAtLeastVersion(7, 1, 0)) {
      files.put(
        "build_dependencies_android_deps.bzl",
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_android_deps.bzl")));
    }
    else {
      files.put(
        "build_dependencies_android_deps.bzl",
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_android_legacy_deps.bzl")));
    }

    // Aspects for Java and ImlModule support
    if (snapshotHolder.getCurrent().map(it -> it.queryData().querySummary().getAllBuildIncludedFiles()
      .contains(STUDIO_IML_MODULE_RULE)).orElse(false)) {
      files.put(
        "build_dependencies_java_deps.bzl",
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_iml_module_java_deps.bzl")));
      files.put(
        "build_dependencies_java_deps_wrapped.bzl",
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_java_deps.bzl")));
    } else {
      files.put(
        "build_dependencies_java_deps.bzl",
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_java_deps.bzl")));
    }

    files.put(
      "build_dependencies_cc_deps.bzl",
      MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_cc_deps.bzl")));
    files.put(
      "build_dependencies_java_proto_deps.bzl",
      MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_java_proto_deps.bzl")));
    files.put(
      "build_dependencies_kotlin_deps.bzl",
      MoreFiles.asByteSource(getBundledAspectPath("build_dependencies_kotlin_deps.bzl")));
    return files.build();
  }

  /**
   * Provides information about files that must be create in the workspace root for the aspect to
   * operate.
   *
   * @return A map of (workspace-relative path) to (contents to write there).
   */
  @VisibleForTesting
  public InvocationFiles getInvocationFiles(
      Set<Label> buildTargets,
      Set<BuildSystem.BuildInvoker.Capability> buildInvokerCapabilities,
      BuildDependencyParameters parameters
  ) {
    String aspectFileName = String.format("qs-%s.bzl", getProjectHash());
    ImmutableMap.Builder<Path, ByteSource> files = ImmutableMap.builder();
    files.put(Path.of(INVOCATION_FILES_DIR + "/BUILD"), ByteSource.empty());
    files.put(
        Path.of(INVOCATION_FILES_DIR + "/build_dependencies.bzl"),
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies.bzl")));
    getBuildDependenciesAspectDepsFiles().forEach((file, content) -> {
      files.put(Path.of(INVOCATION_FILES_DIR + "/" + file), content);
    });
    files.put(
        Path.of(INVOCATION_FILES_DIR + "/" + aspectFileName),
        getByteSourceFromString(getBuildDependenciesParametersFileContent(parameters)));
    Optional<String> targetPatternFileWorkspaceRelativeFile;
    if (buildUseTargetPatternFile.getValue() && buildInvokerCapabilities.contains(BuildInvoker.Capability.SUPPORT_TARGET_PATTERN_FILE)) {
      String patternsFileName = String.format("targets-%s.txt", getProjectHash());
      files.put(
          Path.of(INVOCATION_FILES_DIR + "/" + patternsFileName),
          getByteSourceFromString(
              buildTargets.stream().map(Label::toString).collect(Collectors.joining("\n"))));
      targetPatternFileWorkspaceRelativeFile =
          Optional.of(INVOCATION_FILES_DIR + "/" + patternsFileName);
    } else {
      targetPatternFileWorkspaceRelativeFile = Optional.empty();
    }
    return new InvocationFiles(
        files.build(),
        Label.of(String.format("//" + INVOCATION_FILES_DIR + ":" + aspectFileName)).toString(),
        targetPatternFileWorkspaceRelativeFile);
  }

  private ByteSource getByteSourceFromString(String content) {
    return new ByteSource() {
      @Override
      public InputStream openStream() {
        return new ByteArrayInputStream(content.getBytes(UTF_8));
      }
    };
  }

  private String getBuildDependenciesParametersFileContent(BuildDependencyParameters parameters) {
    final var result = new StringBuilder();
    result.append(
        "load(':build_dependencies.bzl', _collect_dependencies = 'collect_dependencies',"
            + " _package_dependencies = 'package_dependencies')\n");
    result.append("_config = struct(\n");
    appendStringList(result, "include", parameters.include);
    appendStringList(result, "exclude", parameters.exclude);
    appendStringList(result, "always_build_rules", parameters.alwaysBuildRules);
    appendBoolean(result, "generate_aidl_classes", parameters.generateIdlClasses);
    appendBoolean(result, "use_generated_srcjars", parameters.useGeneratedSrcJars);
    result.append(")\n");
    result.append("\n");
    result.append("collect_dependencies = _collect_dependencies(_config)\n");
    result.append("package_dependencies = _package_dependencies(_config)\n");
    return result.toString();
  }

  private void appendBoolean(StringBuilder result, String name, boolean value) {
    result.append("  ").append(name).append(" = ").append(value ? "True" : "False").append(",\n");
  }

  private void appendStringList(StringBuilder result, String name, ImmutableList<String> items) {
    result.append("  ").append(name).append(" = [\n");
    for (String item : items) {
      result.append("    \"").append(item).append("\",\n");
    }
    result.append("  ],\n");
  }

  @VisibleForTesting
  public String getProjectHash() {
    return sanitizeJavaIdentifier(project.getName() + project.getLocationHash());
  }

  @VisibleForTesting
  @Override
  public Path getBundledAspectPath(String filename) {
    return aspectFiles.getBundledAspectPath(filename);
  }

  /**
   * Prepares for use, and returns the location of the {@code build_dependencies.bzl} aspect.
   *
   * <p>The return value is a string in the format expected by bazel for an aspect file, omitting
   * the name of the aspect within that file. For example, {@code //package:aspect.bzl}.
   */
  @VisibleForTesting
  @Override
  public void prepareInvocationFiles(
    BlazeContext context, ImmutableMap<Path, ByteSource> invocationFiles)
      throws IOException, BuildException {
    for (Map.Entry<Path, ByteSource> e : invocationFiles.entrySet()) {
      aspectFiles.copyInvocationFile(e.getKey(), e.getValue());
    }
  }

  private OutputInfo createOutputInfo(
      BlazeBuildOutputs blazeBuildOutputs,
      Set<OutputGroup> outputGroups,
      Instant buildTime,
      BlazeContext context)
      throws BuildException {
    ImmutableListMultimap<OutputGroup, OutputArtifact> allArtifacts =
        GroupedOutputArtifacts.create(blazeBuildOutputs, outputGroups);

    ImmutableList<OutputArtifact> artifactInfoFiles = allArtifacts.get(OutputGroup.ARTIFACT_INFO_FILE);
    ImmutableList<OutputArtifact> compileJdepsFiles = allArtifacts.get(OutputGroup.JDEPS);
    ImmutableList<OutputArtifact> ccArtifactInfoFiles = allArtifacts.get(OutputGroup.CC_INFO_FILE);
    long startTime = System.currentTimeMillis();
    int totalFilesToFetch = artifactInfoFiles.size() + compileJdepsFiles.size() + ccArtifactInfoFiles.size();
    long totalBytesToFetch =
      artifactInfoFiles.stream().mapToLong(OutputArtifact::getLength).sum()
      + compileJdepsFiles.stream().mapToLong(OutputArtifact::getLength).sum()
      + ccArtifactInfoFiles.stream().mapToLong(OutputArtifact::getLength).sum();

    boolean shouldLog =
        totalFilesToFetch > FILE_NUMBER_LOG_THRESHOLD
            || totalBytesToFetch > FETCH_SIZE_LOG_THRESHOLD;
    if (shouldLog) {
      context.output(
          PrintOutput.log(
              String.format(Locale.ROOT,
                            "Fetching and parsing %d artifact info files (%s)",
                            totalFilesToFetch, StringUtilRt.formatFileSize(totalBytesToFetch))));
    }

    ImmutableList.Builder<CcCompilationInfo> ccInfoBuilder = ImmutableList.builder();

    Map<Path, JavaArtifacts> artifactInfos =
        readAndTransformInfoFiles(context, artifactInfoFiles, this::readArtifactInfoFile);
    Map<Path, CcCompilationInfo> ccInfos =
        readAndTransformInfoFiles(context, ccArtifactInfoFiles, this::readCcInfoFile);
    Map<Path, Deps.Dependencies> jdeps =
      NewArtifactTracker.enableJdepsDependencyGraph.isEnabled() ?
      readAndTransformInfoFiles(context, compileJdepsFiles, this::readJdepsFile)
                                                                : Collections.emptyMap();

    ccInfoBuilder.addAll(ccInfos.values());

    long elapsed = System.currentTimeMillis() - startTime;
    if (shouldLog) {
      context.output(
          PrintOutput.log(
              String.format(Locale.ROOT, "Fetched and parsed artifact info files in %d ms", elapsed)));
    }
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            // getOnlyElement should be safe since we never shard querysync builds:
            blazeBuildOutputs.buildId(), buildTime);

    return OutputInfo.create(
      allArtifacts,
      artifactInfos,
      jdeps,
      ccInfoBuilder.build(),
      blazeBuildOutputs.targetsWithErrors().stream()
        .map(Object::toString)
        .map(Label::of)
        .collect(toImmutableSet()),
      blazeBuildOutputs.buildResult().exitCode,
      buildContext);
  }

  @FunctionalInterface
  private interface CheckedTransform<T, R> {
    R apply(T t) throws BuildException;
  }

  private <T> ImmutableMap<Path, T> readAndTransformInfoFiles(
      Context<?> context,
      ImmutableList<OutputArtifact> artifactInfoFiles,
      CheckedTransform<ByteSource, T> transform)
      throws BuildException {

    ListenableFuture<?> listenableFuture = buildArtifactCache.addAll(artifactInfoFiles, context);
    Stopwatch sw = Stopwatch.createStarted();
    // TODO: solodkyy - For now separate fetching and parsing. We have had some thread safety issues
    // and it allows us reporting fetching
    //  time correctly which happens to be much shorter than the time it takes to parse the info
    // files.
    try {
      final var unused = listenableFuture.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuildException(e);
    } catch (ExecutionException e) {
      throw new BuildException(e);
    }
    context.output(
        PrintOutput.output(
            "Fetched %d info files in %sms", artifactInfoFiles.size(), sw.elapsed().toMillis()));
    ImmutableList<ListenableFuture<AbstractMap.SimpleEntry<Path, CachedArtifact>>> artifactFutures =
        artifactInfoFiles.stream()
            .map(
                it -> {
                  final var result = buildArtifactCache.get(it.getDigest());
                  if (result.isEmpty()) {
                    context.output(
                        PrintOutput.error("Failed to get artifact future for: " + it.getDigest()));
                    context.setHasError();
                  }
                  return result.map(
                    future -> Futures.transform(future, a -> new AbstractMap.SimpleEntry<>(it.getArtifactPath(), a), directExecutor()));
                })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableList());

    ImmutableList<ListenableFuture<AbstractMap.SimpleEntry<Path, T>>> futures =
        artifactFutures.stream()
            .map(
                it ->
                    Futures.transformAsync(
                        it,
                        a -> immediateFuture(new AbstractMap.SimpleEntry<>(a.getKey(), transform.apply(a.getValue().byteSource()))),
                        FetchExecutor.EXECUTOR))
            .collect(toImmutableList());
    try {
      return ImmutableMap.copyOf(Futures.allAsList(futures).get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuildException(e);
    } catch (ExecutionException e) {
      throw new BuildException(e);
    }
  }

  private JavaArtifacts readArtifactInfoFile(ByteSource file) throws BuildException {
    return ProtoStringInterner.intern(
        readArtifactInfoProtoFile(JavaArtifacts.newBuilder(), file).build());
  }

  private Deps.Dependencies readJdepsFile(ByteSource file) throws BuildException {
    Deps.Dependencies dependencies;
    try (InputStream inputStream = file.openStream()) {
      dependencies = Deps.Dependencies.parseFrom(inputStream);
    } catch (IOException e) {
      throw new BuildException(e);
    }
    return ProtoStringInterner.intern(dependencies);
  }

  private CcCompilationInfo readCcInfoFile(ByteSource file) throws BuildException {
    return ProtoStringInterner.intern(
        readArtifactInfoProtoFile(CcCompilationInfo.newBuilder(), file).build());
  }

  @VisibleForTesting
  public static <B extends Message.Builder> B readArtifactInfoProtoFile(B builder, ByteSource file)
      throws BuildException {
    try (InputStream inputStream = file.openStream()) {
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder;
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  @Service(Service.Level.APP)
  public static final class BuildDependenciesLockService {
    private final ConcurrentMap<String, ReentrantLock> workspaceLocks = new ConcurrentHashMap<>();

    public interface WorkspaceLock extends AutoCloseable {
      @Override
      void close();
    }

    public WorkspaceLock lockWorkspace(String workspace) {
      final var lock = workspaceLocks.computeIfAbsent(workspace, it -> new ReentrantLock());
      lock.lock();
      return lock::unlock;
    }
  }
}

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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.intellij.openapi.util.text.StringUtil.sanitizeJavaIdentifier;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
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
import com.google.idea.blaze.base.util.VersionChecker;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.proto.ProtoStringInterner;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import com.jgoodies.common.base.Strings;
import java.io.ByteArrayInputStream;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.jetbrains.annotations.VisibleForTesting;

/** An object that knows how to build dependencies for given targets */
public class BazelDependencyBuilder implements DependencyBuilder {
  private static final Logger logger = Logger.getInstance(BazelDependencyBuilder.class);

  public static final BoolExperiment buildGeneratedSrcJars =
      new BoolExperiment("qsync.build.generated.src.jars", false);

  // Note, this is currently incompatible with the build API.
  public static final BoolExperiment buildUseTargetPatternFile =
      new BoolExperiment("qsync.build.use.target.pattern.file", false);

  public static final BoolExperiment multiInfoFile =
      new BoolExperiment("qsync.multi.info.file.mode", true);

  public static final StringExperiment aspectLocation =
      new StringExperiment("qsync.build.aspect.location");
  public static final String INVOCATION_FILES_DIR = ".aswb";

  public record BuildDependencyParameters(
      ImmutableList<String> include,
      ImmutableList<String> exclude,
      ImmutableList<String> alwaysBuildRules,
      boolean generateIdlClasses,
      boolean useGeneratedSrcJars,
      boolean experimentMultiInfoFile) {}

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
    try (final var ignoredLock =
        ApplicationManager.getApplication()
            .getService(BuildDependenciesLockService.class)
            .lockWorkspace(workspaceRoot.path().toString())) {
      if (VersionChecker.versionMismatch()) {
        throw new BuildException(
            "The IDE has been upgraded in the background. Bazel build aspect files maybe"
                + " incompatible. Please restart the IDE.");
      }
      final var buildDependenciesBazelInvocationInfo =
          getInvocationInfo(context, buildTargets, languages);
      prepareInvocationFiles(
          context, buildDependenciesBazelInvocationInfo.invocationWorkspaceFiles());

      BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
      try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
        Optional<BuildDepsStats.Builder> buildDepsStatsBuilder =
            BuildDepsStatsScope.fromContext(context);
        buildDepsStatsBuilder.ifPresent(stats -> stats.setBlazeBinaryType(invoker.getType()));
        BlazeCommand.Builder builder =
            BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
                .addBlazeFlags(buildDependenciesBazelInvocationInfo.argsAndFlags())
                .addBlazeFlags(buildResultHelper.getBuildFlags());
        buildDepsStatsBuilder.ifPresent(
            stats -> stats.setBuildFlags(builder.build().toArgumentList()));
        Instant buildTime = Instant.now();
        BlazeBuildOutputs outputs =
            invoker.getCommandRunner().run(project, builder, buildResultHelper, context);

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
  public BuildDependenciesBazelInvocationInfo getInvocationInfo(
      BlazeContext context, Set<Label> buildTargets, Set<QuerySyncLanguage> languages) {
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
            buildGeneratedSrcJars.getValue(),
            multiInfoFile.getValue());

    InvocationFiles invocationFiles = getInvocationFiles(buildTargets, parameters);

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
    querySyncFlags.add("--keep_going");
    querySyncFlags.addAll(
        outputGroups.stream()
            .map(g -> "--output_groups=" + g.outputGroupName())
            .collect(toImmutableList()));
    return new BuildDependenciesBazelInvocationInfo(
        querySyncFlags.build(), outputGroups, invocationFiles.files());
  }

  public record InvocationFiles(
      ImmutableMap<Path, ByteSource> files,
      String aspectFileLabel,
      Optional<String> targetPatternFileWorkspaceRelativeFile) {}

  /**
   * Provides information about files that must be create in the workspace root for the aspect to
   * operate.
   *
   * @return A map of (workspace-relative path) to (contents to write there).
   */
  @VisibleForTesting
  public InvocationFiles getInvocationFiles(
      Set<Label> buildTargets, BuildDependencyParameters parameters) {
    String aspectFileName = String.format("qs-%s.bzl", getProjectHash());
    ImmutableMap.Builder<Path, ByteSource> files = ImmutableMap.builder();
    files.put(Path.of(INVOCATION_FILES_DIR + "/BUILD"), ByteSource.empty());
    files.put(
        Path.of(INVOCATION_FILES_DIR + "/build_dependencies.bzl"),
        MoreFiles.asByteSource(getBundledAspectPath("build_dependencies.bzl")));
    files.put(
        Path.of(INVOCATION_FILES_DIR + "/build_dependencies_deps.bzl"),
        MoreFiles.asByteSource(getBundledAspectDepsFilePath()));
    files.put(
        Path.of(INVOCATION_FILES_DIR + "/" + aspectFileName),
        getByteSourceFromString(getBuildDependenciesParametersFileContent(parameters)));
    Optional<String> targetPatternFileWorkspaceRelativeFile;
    if (buildUseTargetPatternFile.getValue()) {
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

  protected Path getBundledAspectDepsFilePath() {
    return getBundledAspectPath("build_dependencies_deps.bzl");
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
    appendBoolean(result, "experiment_multi_info_file", parameters.experimentMultiInfoFile);
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
    } else {
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
  @VisibleForTesting
  public void prepareInvocationFiles(
      BlazeContext context, ImmutableMap<Path, ByteSource> invocationFiles)
      throws IOException, BuildException {
    for (Map.Entry<Path, ByteSource> e : invocationFiles.entrySet()) {
      Path absolutePath = workspaceRoot.path().resolve(e.getKey());
      Files.createDirectories(absolutePath.getParent());
      Files.copy(e.getValue().openStream(), absolutePath, StandardCopyOption.REPLACE_EXISTING);
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
          PrintOutput.log(
              String.format("Fetched and parsed artifact info files in %d ms", elapsed)));
    }
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            // getOnlyElement should be safe since we never shard querysync builds:
            blazeBuildOutputs.buildId(), buildTime);

    return OutputInfo.create(
        Multimaps.filterKeys(
            allArtifacts,
            it -> it != OutputGroup.ARTIFACT_INFO_FILE && it != OutputGroup.CC_INFO_FILE),
        artifactInfoFilesBuilder.build(),
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

  private <T> ImmutableList<T> readAndTransformInfoFiles(
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
    ImmutableList<ListenableFuture<CachedArtifact>> artifactFutures =
        artifactInfoFiles.stream()
            .map(OutputArtifact::getDigest)
            .map(
                digest -> {
                  final var result = buildArtifactCache.get(digest);
                  if (result.isEmpty()) {
                    context.output(
                        PrintOutput.error("Failed to get artifact future for: " + digest));
                    context.setHasError();
                  }
                  return result;
                })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableList());

    ImmutableList<ListenableFuture<T>> futures =
        artifactFutures.stream()
            .map(
                it ->
                    Futures.transformAsync(
                        it,
                        a -> immediateFuture(transform.apply(a.byteSource())),
                        FetchExecutor.EXECUTOR))
            .collect(toImmutableList());
    try {
      return ImmutableList.copyOf(Futures.allAsList(futures).get());
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

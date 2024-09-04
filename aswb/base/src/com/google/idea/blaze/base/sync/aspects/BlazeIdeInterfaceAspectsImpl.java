/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.filecache.ArtifactsDiff;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.lang.AdditionalLanguagesHelper;
import com.google.idea.blaze.base.model.AspectSyncProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.SyncFlagsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Result;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.BlazeSyncBuildResult;
import com.google.idea.blaze.base.sync.BuildPhaseSyncTask;
import com.google.idea.blaze.base.sync.SyncProjectState;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedBuildProgressTracker;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.NavigatableAdapter;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Implementation of BlazeIdeInterface based on aspects. */
public class BlazeIdeInterfaceAspectsImpl implements BlazeIdeInterface {
  private static final Logger logger = Logger.getInstance(BlazeIdeInterfaceAspectsImpl.class);

  private static final BoolExperiment noFakeStampExperiment =
      new BoolExperiment("blaze.sync.nofake.stamp.data", true);

  @Override
  @Nullable
  public ProjectTargetData updateTargetData(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      SyncProjectState projectState,
      BlazeSyncBuildResult buildResult,
      boolean mergeWithOldState,
      @Nullable AspectSyncProjectData oldProjectData) {
    TargetMapAndInterfaceState state =
        updateTargetMap(
            project,
            context,
            workspaceRoot,
            projectState,
            buildResult,
            mergeWithOldState,
            oldProjectData);
    if (state == null) {
      return null;
    }
    context.output(PrintOutput.log("Target map size: " + state.targetMap.targets().size()));

    RemoteOutputArtifacts oldRemoteOutputs = RemoteOutputArtifacts.fromProjectData(oldProjectData);
    // combine outputs map, then filter to remove out-of-date / unnecessary items
    RemoteOutputArtifacts newRemoteOutputs =
        oldRemoteOutputs
            .appendNewOutputs(getTrackedOutputs(buildResult.getBuildResult()))
            .removeUntrackedOutputs(state.targetMap, projectState.getLanguageSettings())
            .appendNewOutputs(
                getTrackedOutputs(
                    buildResult.getBuildResult(),
                    group -> group.contains("intellij-resolve-java-direct-deps")));

    return new ProjectTargetData(state.targetMap, state.state, newRemoteOutputs);
  }

  /** Returns the {@link OutputArtifact}s we want to track between syncs. */
  private static ImmutableSet<OutputArtifact> getTrackedOutputs(BlazeBuildOutputs buildOutput) {
    // don't track intellij-info.txt outputs -- they're already tracked in
    // BlazeIdeInterfaceState
    return getTrackedOutputs(buildOutput, group -> true);
  }

  private static ImmutableSet<OutputArtifact> getTrackedOutputs(
      BlazeBuildOutputs buildOutput, Predicate<String> outputGroupFilter) {
    Predicate<String> pathFilter = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE.negate();
    return buildOutput.getOutputGroupArtifacts(outputGroupFilter).stream()
        .filter(a -> pathFilter.test(a.getRelativePath()))
        .collect(toImmutableSet());
  }

  @Nullable
  private static TargetMapAndInterfaceState updateTargetMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      SyncProjectState projectState,
      BlazeSyncBuildResult buildResult,
      boolean mergeWithOldState,
      @Nullable AspectSyncProjectData oldProjectData) {
    // If there was a partial error, make a best-effort attempt to sync. Retain
    // any old state that we have in an attempt not to lose too much code.
    if (buildResult.getBuildResult().buildResult.status == BuildResult.Status.BUILD_ERROR
        || buildResult.getBuildResult().buildResult.status == Status.FATAL_ERROR) {
      mergeWithOldState = true;
    }

    TargetMap oldTargetMap = oldProjectData != null ? oldProjectData.getTargetMap() : null;
    BlazeIdeInterfaceState prevState =
        oldProjectData != null ? oldProjectData.getTargetData().ideInterfaceState : null;

    Predicate<String> ideInfoPredicate = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE;
    Collection<OutputArtifact> files =
        buildResult
            .getBuildResult()
            .getOutputGroupArtifacts(group -> group.startsWith(OutputGroup.INFO.prefix))
            .stream()
            .filter(f -> ideInfoPredicate.test(f.getRelativePath()))
            .distinct()
            .collect(toImmutableList());

    ArtifactsDiff diff;
    try {
      diff =
          ArtifactsDiff.diffArtifacts(prevState != null ? prevState.ideInfoFileState : null, files);
    } catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    } catch (ExecutionException e) {
      IssueOutput.error("Failed to diff aspect output files: " + e).submit(context);
      return null;
    }

    // if we're merging with the old state, no files are removed
    int targetCount = files.size() + (mergeWithOldState ? diff.getRemovedOutputs().size() : 0);
    int removedCount = mergeWithOldState ? 0 : diff.getRemovedOutputs().size();

    context.output(
        PrintOutput.log(
            String.format(
                "Total rules: %d, new/changed: %d, removed: %d",
                targetCount, diff.getUpdatedOutputs().size(), removedCount)));

    ListenableFuture<?> downloadArtifactsFuture =
        RemoteArtifactPrefetcher.getInstance()
            .downloadArtifacts(
                /* projectName= */ project.getName(),
                /* outputArtifacts= */ RemoteOutputArtifact.getRemoteArtifacts(
                    diff.getUpdatedOutputs()));
    ListenableFuture<?> loadFilesInJvmFuture =
        RemoteArtifactPrefetcher.getInstance()
            .loadFilesInJvm(
                /* outputArtifacts= */ RemoteOutputArtifact.getRemoteArtifacts(
                    diff.getUpdatedOutputs()));

    if (!FutureUtil.waitForFuture(
            context, Futures.allAsList(downloadArtifactsFuture, loadFilesInJvmFuture))
        .timed("PrefetchRemoteAspectOutput", EventType.Prefetching)
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return null;
    }

    ListenableFuture<?> fetchLocalFilesFuture =
        PrefetchService.getInstance()
            .prefetchFiles(
                /* files= */ LocalFileArtifact.getLocalFiles(diff.getUpdatedOutputs()),
                /* refetchCachedFiles= */ true,
                /* fetchFileTypes= */ false);
    if (!FutureUtil.waitForFuture(context, fetchLocalFilesFuture)
        .timed("FetchAspectOutput", EventType.Prefetching)
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return null;
    }

    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystemName(project))
            .add(projectState.getProjectViewSet())
            .build();

    BlazeConfigurationHandler configHandler =
        new BlazeConfigurationHandler(buildResult.getBlazeInfo());
    TargetMapAndInterfaceState state =
        updateState(
            project,
            context,
            prevState,
            diff,
            configHandler,
            projectState.getBlazeVersionData(),
            projectState.getLanguageSettings(),
            importRoots,
            mergeWithOldState,
            oldTargetMap);
    if (state == null) {
      return null;
    }
    // prefetch ide-resolve genfiles
    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("GenfilesPrefetchBuildArtifacts", EventType.Other));
          ImmutableList<OutputArtifact> resolveOutputs =
              buildResult
                  .getBuildResult()
                  .getOutputGroupArtifacts(group -> group.startsWith(OutputGroup.RESOLVE.prefix));
          prefetchGenfiles(context, resolveOutputs);
        });
    return state;
  }

  @Nullable
  private static TargetMapAndInterfaceState updateState(
      Project project,
      BlazeContext parentContext,
      @Nullable BlazeIdeInterfaceState prevState,
      ArtifactsDiff fileState,
      BlazeConfigurationHandler configHandler,
      BlazeVersionData versionData,
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      boolean mergeWithOldState,
      @Nullable TargetMap oldTargetMap) {
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(versionData);
    Result<TargetMapAndInterfaceState> result =
        Scope.push(
            parentContext,
            context -> {
              context.push(new TimingScope("UpdateTargetMap", EventType.Other));
              context.output(new StatusOutput("Updating target map..."));

              // ideally, we'd flush through a per-build sync time parsed from BEP. For now, though
              // just set an approximate, batched sync time.
              Instant syncTime = Instant.now();
              Map<String, ArtifactState> nextFileState = new HashMap<>(fileState.getNewState());

              // If we're not removing we have to merge the old state
              // into the new one or we'll miss file removes next time
              if (mergeWithOldState && prevState != null) {
                prevState.ideInfoFileState.forEach(nextFileState::putIfAbsent);
              }

              BlazeIdeInterfaceState.Builder state = BlazeIdeInterfaceState.builder();
              state.ideInfoFileState = ImmutableMap.copyOf(nextFileState);

              Map<TargetKey, TargetIdeInfo> targetMap = Maps.newHashMap();
              if (prevState != null && oldTargetMap != null) {
                targetMap.putAll(oldTargetMap.map());
                state.ideInfoToTargetKey.putAll(prevState.ideInfoFileToTargetKey);
              }

              // Update removed unless we're merging with the old state
              if (!mergeWithOldState) {
                for (ArtifactState removed : fileState.getRemovedOutputs()) {
                  TargetKey key = state.ideInfoToTargetKey.remove(removed.getKey());
                  if (key != null) {
                    targetMap.remove(key);
                  }
                }
              }

              AtomicLong totalSizeLoaded = new AtomicLong(0);
              Set<LanguageClass> ignoredLanguages = Sets.newConcurrentHashSet();

              ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();

              // Read protos from any new files
              List<ListenableFuture<TargetFilePair>> futures = Lists.newArrayList();
              for (OutputArtifactWithoutDigest file : fileState.getUpdatedOutputs()) {
                futures.add(
                    executor.submit(
                        () -> {
                          totalSizeLoaded.addAndGet(file.getLength());
                          IntellijIdeInfo.TargetIdeInfo message =
                              aspectStrategy.readAspectFile(file);
                          TargetIdeInfo target =
                              protoToTarget(
                                  languageSettings,
                                  importRoots,
                                  message,
                                  ignoredLanguages,
                                  syncTime);
                          return new TargetFilePair(file, target);
                        }));
              }

              Set<TargetKey> newTargets = new HashSet<>();
              Set<String> configurations = new LinkedHashSet<>();
              configurations.add(configHandler.defaultConfigurationPathComponent);

              // Update state with result from proto files
              int duplicateTargetLabels = 0;
              try {
                for (TargetFilePair targetFilePair : Futures.allAsList(futures).get()) {
                  if (targetFilePair.target != null) {
                    OutputArtifactWithoutDigest file = targetFilePair.file;
                    String config = file.getConfigurationMnemonic();
                    configurations.add(config);
                    TargetKey key = targetFilePair.target.getKey();
                    if (targetMap.putIfAbsent(key, targetFilePair.target) == null) {
                      state.ideInfoToTargetKey.forcePut(file.getRelativePath(), key);
                    } else {
                      if (!newTargets.add(key)) {
                        duplicateTargetLabels++;
                      }
                      // prioritize the default configuration over build order
                      if (Objects.equals(config, configHandler.defaultConfigurationPathComponent)) {
                        targetMap.put(key, targetFilePair.target);
                        state.ideInfoToTargetKey.forcePut(file.getRelativePath(), key);
                      }
                    }
                  }
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.error(null);
              } catch (ExecutionException e) {
                return Result.error(e);
              }

              context.output(
                  PrintOutput.log(
                      String.format(
                          "Loaded %d aspect files, total size %dkB",
                          fileState.getUpdatedOutputs().size(), totalSizeLoaded.get() / 1024)));
              if (duplicateTargetLabels > 0) {
                context.output(
                    new PerformanceWarning(
                        String.format(
                            "There were %d duplicate rules, built with the following "
                                + "configurations: %s.\nYour IDE sync is slowed down by ~%d%%.",
                            duplicateTargetLabels,
                            configurations,
                            (100 * duplicateTargetLabels / targetMap.size()))));
              }

              // remove previously synced targets which are now unsupported
              for (TargetKey key : ImmutableSet.copyOf(state.ideInfoToTargetKey.values())) {
                TargetIdeInfo target = targetMap.get(key);
                if (target != null
                    && shouldIgnoreTarget(
                        languageSettings, importRoots, target, ignoredLanguages)) {
                  state.ideInfoToTargetKey.inverse().remove(key);
                  targetMap.remove(key);
                }
              }

              // update sync time for unchanged targets
              for (String artifactKey : fileState.getNewState().keySet()) {
                TargetKey targetKey = state.ideInfoToTargetKey.get(artifactKey);
                TargetIdeInfo target = targetKey != null ? targetMap.get(targetKey) : null;
                if (target != null) {
                  targetMap.put(targetKey, target.updateSyncTime(syncTime));
                }
              }

              ignoredLanguages.retainAll(
                  LanguageSupport.availableAdditionalLanguages(
                      languageSettings.getWorkspaceType()));
              warnIgnoredLanguages(project, context, ignoredLanguages);

              return Result.of(
                  new TargetMapAndInterfaceState(
                      new TargetMap(ImmutableMap.copyOf(targetMap)), state.build()));
            });

    if (result.error != null) {
      logger.error(result.error);
      return null;
    }
    return result.result;
  }

  private static boolean shouldIgnoreTarget(
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      TargetIdeInfo target,
      Set<LanguageClass> ignoredLanguages) {
    Kind kind = target.getKind();
    if (kind.getLanguageClasses().stream().anyMatch(languageSettings::isLanguageActive)) {
      return false;
    }
    if (importRoots.importAsSource(target.getKey().getLabel())) {
      ignoredLanguages.addAll(kind.getLanguageClasses());
    }
    return true;
  }

  @Nullable
  private static TargetIdeInfo protoToTarget(
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      IntellijIdeInfo.TargetIdeInfo message,
      Set<LanguageClass> ignoredLanguages,
      Instant syncTime) {
    Kind kind = Kind.fromProto(message);
    if (kind == null) {
      return null;
    }
    if (kind.getLanguageClasses().stream().anyMatch(languageSettings::isLanguageActive)) {
      return TargetIdeInfo.fromProto(message, syncTime);
    }
    TargetKey key = message.hasKey() ? TargetKey.fromProto(message.getKey()) : null;
    if (key != null && importRoots.importAsSource(key.getLabel())) {
      ignoredLanguages.addAll(kind.getLanguageClasses());
    }
    return null;
  }

  private static void warnIgnoredLanguages(
      Project project, BlazeContext context, Set<LanguageClass> ignoredLangs) {
    if (ignoredLangs.isEmpty()) {
      return;
    }
    List<LanguageClass> sorted = new ArrayList<>(ignoredLangs);
    sorted.sort(Ordering.usingToString());

    String msg =
        "Some project targets were ignored because the corresponding language support "
            + "isn't enabled. Click here to enable support for: "
            + Joiner.on(", ").join(sorted);
    IssueOutput.warn(msg)
        .navigatable(
            new NavigatableAdapter() {
              @Override
              public void navigate(boolean requestFocus) {
                AdditionalLanguagesHelper.enableLanguageSupport(project, sorted);
              }
            })
        .submit(context);
  }

  /** A filename filter for blaze output artifacts to prefetch. */
  private static Predicate<String> getGenfilePrefetchFilter() {
    ImmutableSet<String> extensions = PrefetchFileSource.getAllPrefetchFileExtensions();
    return fileName -> extensions.contains(FileUtil.getExtension(fileName));
  }

  /** Prefetch a list of blaze output artifacts, blocking until complete. */
  private static void prefetchGenfiles(
      BlazeContext context, ImmutableList<OutputArtifact> artifacts) {
    Predicate<String> filter = getGenfilePrefetchFilter();
    // TODO: handle prefetching for arbitrary OutputArtifacts
    ImmutableList<File> files =
        artifacts.stream()
            .filter(a -> filter.test(a.getRelativePath()))
            .filter(o -> o instanceof LocalFileArtifact)
            .map(o -> ((LocalFileArtifact) o).getFile())
            .collect(toImmutableList());
    if (files.isEmpty()) {
      return;
    }
    ListenableFuture<?> prefetchFuture =
        PrefetchService.getInstance().prefetchFiles(files, false, false);
    FutureUtil.waitForFuture(context, prefetchFuture)
        .timed("PrefetchGenfiles", EventType.Prefetching)
        .withProgressMessage("Prefetching genfiles...")
        .run();
  }

  private static class TargetMapAndInterfaceState {
    private final TargetMap targetMap;

    private final BlazeIdeInterfaceState state;

    TargetMapAndInterfaceState(TargetMap targetMap, BlazeIdeInterfaceState state) {
      this.targetMap = targetMap;
      this.state = state;
    }
  }

  private static class TargetFilePair {
    private final OutputArtifactWithoutDigest file;
    private final TargetIdeInfo target;

    TargetFilePair(OutputArtifactWithoutDigest file, TargetIdeInfo target) {
      this.file = file;
      this.target = target;
    }
  }

  @Override
  public BlazeBuildOutputs build(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeVersionData blazeVersion,
      BuildInvoker invoker,
      ProjectViewSet projectViewSet,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ImmutableSet<OutputGroup> outputGroups,
      BlazeInvocationContext blazeInvocationContext,
      boolean invokeParallel) {
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(blazeVersion);

    final Ref<BlazeBuildOutputs> combinedResult = new Ref<>();

    // The build is a sync iff INFO output group is present
    boolean isSync = outputGroups.contains(OutputGroup.INFO);

    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building targets for shard %s of %s...", count, shardedTargets.shardCount());

    final ShardedBuildProgressTracker progressTracker =
        new ShardedBuildProgressTracker(shardedTargets.shardCount());

    // Sync only flags (sync_only) override build_flags, so log them to warn the users
    List<String> syncOnlyFlags =
        BlazeFlags.expandBuildFlags(projectViewSet.listItems(SyncFlagsSection.KEY));
    if (!syncOnlyFlags.isEmpty()) {
      String message =
          String.format(
              "Sync flags (`%s`) specified in the project view file will override the build"
                  + " flags set in blazerc configurations or general build flags in the"
                  + " project view file.",
              String.join(" ", syncOnlyFlags));
      // Print to both summary and print outputs (i.e. main and subtask window of blaze console)
      context.output(SummaryOutput.output(Prefix.INFO, message).dedupe());
      context.output(PrintOutput.log(message));
    }
    // Fetching blaze flags here using parent context, to avoid duplicate fetch for every shard.
    List<String> additionalBlazeFlags =
        BlazeFlags.blazeFlags(
            project, projectViewSet, BlazeCommandName.BUILD, context, blazeInvocationContext);
    Function<List<? extends TargetExpression>, BuildResult> invocation =
        targets ->
            Scope.push(
                context,
                (childContext) -> {
                  Task task =
                      createTask(
                          project,
                          context,
                          "Build shard " + StringUtil.first(UUID.randomUUID().toString(), 8, true),
                          isSync);
                  // we use context (rather than childContext) here since the shard state relates to
                  // the parent task (which encapsulates all the build shards).

                  setupToolWindow(project, childContext, workspaceRoot, task);
                  progressTracker.onBuildStarted(context);

                  try {
                    BlazeBuildOutputs result =
                        runBuildForTargets(
                            project,
                            childContext,
                            invoker,
                            projectViewSet,
                            workspaceLanguageSettings.getActiveLanguages(),
                            targets,
                            aspectStrategy,
                            outputGroups,
                            additionalBlazeFlags,
                            invokeParallel);
                    if (result.buildResult.outOfMemory()) {
                      logger.warn(
                          String.format(
                              "Build shard failed with OOM error build-id=%s",
                              result.getBuildIds().stream().findFirst().orElse(null)));
                    }
                    printShardFinishedSummary(context, task.getName(), result, invoker);
                    synchronized (combinedResult) {
                      combinedResult.set(
                          combinedResult.isNull()
                              ? result
                              : combinedResult.get().updateOutputs(result));
                    }
                    return result.buildResult;
                  } catch (BuildException e) {
                    context.handleException("Failed to build targets", e);
                    return BuildResult.FATAL_ERROR;
                  } finally {
                    progressTracker.onBuildCompleted(context);
                  }
                });
    BuildResult buildResult =
        shardedTargets.runShardedCommand(
            project, context, progressMessage, invocation, invoker, invokeParallel);
    if (combinedResult.isNull()
        || (!BuildPhaseSyncTask.continueSyncOnOom.getValue()
            && buildResult.status == Status.FATAL_ERROR)) {
      return BlazeBuildOutputs.noOutputs(buildResult);
    }
    return combinedResult.get();
  }

  /* Prints summary only for failed shards */
  private void printShardFinishedSummary(
      BlazeContext context, String taskName, BlazeBuildOutputs result, BuildInvoker invoker) {
    if (result.buildResult.status == Status.SUCCESS) {
      return;
    }
    StringBuilder outputText = new StringBuilder();
    outputText.append(
        String.format(
            "%s finished with %s errors. ",
            taskName, result.buildResult.status == Status.BUILD_ERROR ? "build" : "fatal"));
    String invocationId =
        Iterables.getOnlyElement(
            result.getBuildIds(),
            null); // buildIds has exactly one invocationId because this is called only when a shard
    // is built and not when buildResults are combined
    invoker
        .getBuildSystem()
        .getInvocationLink(invocationId)
        .ifPresent(link -> outputText.append(String.format("See build results at %s", link)));
    context.output(SummaryOutput.error(Prefix.TIMESTAMP, outputText.toString()));
  }

  private static Task createTask(
      Project project, BlazeContext parentContext, String taskName, boolean isSync) {
    Task.Type taskType = isSync ? Task.Type.SYNC : Task.Type.MAKE;
    ToolWindowScope parentToolWindowScope = parentContext.getScope(ToolWindowScope.class);
    Task parentTask = parentToolWindowScope != null ? parentToolWindowScope.getTask() : null;
    return new Task(project, taskName, taskType, parentTask);
  }

  private static void setupToolWindow(
      Project project, BlazeContext childContext, WorkspaceRoot workspaceRoot, Task task) {
    ContextType contextType =
        task.getType().equals(Task.Type.SYNC) ? ContextType.Sync : ContextType.Other;
    childContext.push(
        new ToolWindowScope.Builder(project, task)
            .setIssueParsers(
                BlazeIssueParser.defaultIssueParsers(project, workspaceRoot, contextType))
            .build());
  }

  /** Runs a blaze build for the given output groups. */
  private static BlazeBuildOutputs runBuildForTargets(
      Project project,
      BlazeContext context,
      BuildInvoker invoker,
      ProjectViewSet viewSet,
      ImmutableSet<LanguageClass> activeLanguages,
      List<? extends TargetExpression> targets,
      AspectStrategy aspectStrategy,
      ImmutableSet<OutputGroup> outputGroups,
      List<String> additionalBlazeFlags,
      boolean invokeParallel)
      throws BuildException {

    boolean onlyDirectDeps =
        viewSet.getScalarValue(AutomaticallyDeriveTargetsSection.KEY).orElse(false);

    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {

      BlazeCommand.Builder builder = BlazeCommand.builder(invoker, BlazeCommandName.BUILD);
      builder
          .setInvokeParallel(invokeParallel)
          .addTargets(targets)
          .addBlazeFlags(BlazeFlags.KEEP_GOING)
          .addBlazeFlags(BlazeFlags.DISABLE_VALIDATIONS) // b/145245918: don't run lint during sync
          .addBlazeFlags(buildResultHelper.getBuildFlags())
          .addBlazeFlags(additionalBlazeFlags);

      // b/236031309: Sync builds that use rabbit-cli rely on build-changelist.txt being populated
      // with the correct build request id. We force Blaze to emit the correct build-changelist.
      if (noFakeStampExperiment.getValue() && invoker.getType() == BuildBinaryType.RABBIT) {
        builder.addBlazeFlags("--nofake_stamp_data");
      }

      aspectStrategy.addAspectAndOutputGroups(
          builder, outputGroups, activeLanguages, onlyDirectDeps);

      return invoker.getCommandRunner().run(project, builder, buildResultHelper, context);
    }
  }
}

/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult.bepparser;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public final class BepParser {
  private static final BoolExperiment parallelBepPoolingEnabled =
    new BoolExperiment("bep.parsing.pooling.enabled", true);
  // Maximum number of concurrent BEP parsing operations to allow.
  // For large projects, BEP parsing of a single shard can consume several hundred Mb of memory
  private static final IntExperiment maxThreads =
    new IntExperiment("bep.parsing.concurrency.limit", 5);

  private BepParser() {}

  /** Parses BEP events into {@link ParsedBepOutput} */
  public static ParsedBepOutput parseBepArtifacts(BuildEventStreamProvider stream)
    throws BuildEventStreamProvider.BuildEventStreamException {
    return parseBepArtifacts(stream, null);
  }

  /**
   * A record of the top level file sets output by the given {@link #outputGroup}, {@link #target} and {@link #config}.
   */
  private record OutputGroupTargetConfigFileSets(String outputGroup, String target, String config, ImmutableList<String> fileSetNames){}

  /**
   * A data structure allowing to associate file set names with output group, targets and configs and allowing to retrieve them efficiently
   * at each level of the hierarchy.
   */
  private static class OutputGroupTargetConfigFileSetMap {
    private final Map<String, Map<String, Map<String, ImmutableList<String>>>> data = new LinkedHashMap<>();

    private Map<String, Map<String, ImmutableList<String>>> getOutputGroup(String outputGroup) {
      return data.computeIfAbsent(outputGroup, it -> new LinkedHashMap<>());
    }

    private Map<String, ImmutableList<String>> getOutputGroupTarget(String outputGroup, String target) {
      return getOutputGroup(outputGroup).computeIfAbsent(target, it -> new LinkedHashMap<>());
    }

    private ImmutableList<String> getOutputGroupTargetConfig(String outputGroup, String target, String config) {
      final var result = getOutputGroupTarget(outputGroup, target).get(config);
      return result != null ? result : ImmutableList.of();
    }

    public void setOutputGroupTargetConfig(String outputGroup, String target, String config, ImmutableList<String> fileSetNames) {
      final var previous = getOutputGroupTarget(outputGroup, target).put(config, fileSetNames);
      if (previous != null){
        throw new IllegalStateException(outputGroup + ":" + target + ":" + config + " already present");
      }
    }

    public Stream<OutputGroupTargetConfigFileSets> fileSetStream() {
      return data.entrySet().stream().flatMap(
        outputGroup ->
          outputGroup.getValue().entrySet().stream().flatMap(
            target ->
              target.getValue().entrySet().stream().map(
                config ->
                  new OutputGroupTargetConfigFileSets(outputGroup.getKey(), target.getKey(),
                                                      config.getKey(), config.getValue()))));
    }

    public Stream<OutputGroupTargetConfigFileSets> outputGroupFileSetStream(String outputGroup) {
      final var outputGroupData = data.get(outputGroup);
      if (outputGroupData == null) {
        return Stream.empty();
      }
      return outputGroupData.entrySet().stream().flatMap(
        target ->
          target.getValue().entrySet().stream().map(
            config ->
              new OutputGroupTargetConfigFileSets(outputGroup, target.getKey(),
                                                  config.getKey(), config.getValue())));
    }

    public Stream<OutputGroupTargetConfigFileSets> outputGroupTargetFileSetStream(String outputGroup, String target) {
      final var outputGroupData = data.get(outputGroup);
      if (outputGroupData == null) {
        return Stream.empty();
      }
      final var outputGroupTargetData = outputGroupData.get(target);
      if (outputGroupTargetData == null) {
        return Stream.empty();
      }
      return outputGroupTargetData.entrySet().stream().map(
            config ->
              new OutputGroupTargetConfigFileSets(outputGroup, target,
                                                  config.getKey(), config.getValue()));
    }
  }

  /**
   * Parses BEP events into {@link ParsedBepOutput}. String references in {@link BuildEventStreamProtos.NamedSetOfFiles}
   * are interned to conserve memory.
   *
   * <p>BEP protos often contain many duplicate strings both within a single stream and across
   * shards running in parallel, so a {@link Interner} is used to share references.
   */
  public static ParsedBepOutput parseBepArtifacts(
      BuildEventStreamProvider stream, @Nullable Interner<String> nullableInterner)
    throws BuildEventStreamProvider.BuildEventStreamException {
    final var semaphore = ApplicationManager.getApplication().getService(BepParserSemaphore.class);
    semaphore.start();
    try {
      final Interner<String> interner = nullableInterner == null ? Interners.newStrongInterner() : nullableInterner;

      BuildEventStreamProtos.BuildEvent event;
      Map<String, String> configIdToMnemonic = new HashMap<>();
      Map<String, BuildEventStreamProtos.NamedSetOfFiles> fileSets = new LinkedHashMap<>();
      final var data = new OutputGroupTargetConfigFileSetMap();
      ImmutableSet.Builder<String> targetsWithErrors = ImmutableSet.builder();
      String localExecRoot = null;
      String buildId = null;
      long startTimeMillis = 0L;
      int buildResult = 0;
      boolean emptyBuildEventStream = true;

      while ((event = stream.getNext()) != null) {
        emptyBuildEventStream = false;
        switch (event.getId().getIdCase()) {
          case WORKSPACE:
            localExecRoot = event.getWorkspaceInfo().getLocalExecRoot();
            continue;
          case CONFIGURATION:
            configIdToMnemonic.put(
              event.getId().getConfiguration().getId(), event.getConfiguration().getMnemonic());
            continue;
          case NAMED_SET:
            BuildEventStreamProtos.NamedSetOfFiles namedSet = internNamedSet(event.getNamedSetOfFiles(), interner);
            fileSets.put(interner.intern(event.getId().getNamedSet().getId()), namedSet);
            continue;
          case ACTION_COMPLETED:
            Preconditions.checkState(event.hasAction());
            if (!event.getAction().getSuccess()) {
              targetsWithErrors.add(event.getId().getActionCompleted().getLabel());
            }
            break;
          case TARGET_COMPLETED:
            String label = event.getId().getTargetCompleted().getLabel();
            String configId = event.getId().getTargetCompleted().getConfiguration().getId();

            for (BuildEventStreamProtos.OutputGroup o : event.getCompleted().getOutputGroupList()) {
              final var fileSetNames = getFileSets(o, interner);
              data.setOutputGroupTargetConfig(interner.intern(o.getName()), interner.intern(label), interner.intern(configId), fileSetNames);
            }
            continue;
          case STARTED:
            buildId = Strings.emptyToNull(event.getStarted().getUuid());
            startTimeMillis = event.getStarted().getStartTimeMillis();
            continue;
          case BUILD_FINISHED:
            buildResult = event.getFinished().getExitCode().getCode();
            continue;
          default: // continue
        }
      }
      // If stream is empty, it means that service failed to retrieve any blaze build event from build
      // event stream. This should not happen if a build start correctly.
      if (emptyBuildEventStream) {
        throw new BuildEventStreamProvider.BuildEventStreamException("No build events found");
      }
      ImmutableMap<String, ParsedBepOutput.FileSet> filesMap =
        fillInTransitiveFileSetData(
          fileSets, data, configIdToMnemonic, startTimeMillis);
      return new ParsedBepOutput(
        buildId,
        localExecRoot,
        filesMap,
        data.fileSetStream()
          .collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(OutputGroupTargetConfigFileSets::target,
                                                               it -> it.fileSetNames().stream())),
        startTimeMillis,
        buildResult,
        stream.getBytesConsumed(),
        targetsWithErrors.build());
    }
    finally {
      semaphore.end();
    }
  }

  private static ImmutableList<String> getFileSets(BuildEventStreamProtos.OutputGroup group, Interner<String> interner) {
    return group.getFileSetsList().stream()
        .map(namedSetOfFilesId -> interner.intern(namedSetOfFilesId.getId()))
        .collect(toImmutableList());
  }

  /**
   * Only top-level targets have configuration mnemonic, producing target, and output group data
   * explicitly provided in BEP. This method fills in that data for the transitive closure.
   */
  private static ImmutableMap<String, ParsedBepOutput.FileSet> fillInTransitiveFileSetData(
    Map<String, BuildEventStreamProtos.NamedSetOfFiles> namedFileSets,
      OutputGroupTargetConfigFileSetMap data,
      Map<String, String> configIdToMnemonic,
      long startTimeMillis) {
    Map<String, FileSetBuilder> fileSets =
      ImmutableMap.copyOf(
        Maps.transformValues(namedFileSets, it -> new FileSetBuilder().setNamedSet(it)));
    Set<String> topLevelFileSets = new HashSet<>();
    data.fileSetStream().forEach(entry -> {
      entry.fileSetNames().forEach(fileSetName -> {
        final var fileSet = checkNotNull(fileSets.get(fileSetName));
        fileSet.setConfigId(entry.config());
        fileSet.addOutputGroup(entry.outputGroup());
        fileSet.addTarget(entry.target());
        topLevelFileSets.add(fileSetName);
      });
    });
    Queue<String> toVisit = Queues.newArrayDeque(topLevelFileSets);
    Set<String> visited = new HashSet<>(topLevelFileSets);
    while (!toVisit.isEmpty()) {
      String setId = toVisit.remove();
      FileSetBuilder fileSet = fileSets.get(setId);
      if (fileSet.namedSet == null) {
        continue;
      }
      fileSet.namedSet.getFileSetsList().stream()
          .map(BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId::getId)
          .filter(s -> !visited.contains(s))
          .forEach(
              child -> {
                fileSets.get(child).updateFromParent(fileSet);
                toVisit.add(child);
                visited.add(child);
              });
    }
    return fileSets.entrySet().stream()
        .filter(e -> e.getValue().isValid(configIdToMnemonic))
        .collect(
            toImmutableMap(
              Map.Entry::getKey, e -> e.getValue().build(configIdToMnemonic, startTimeMillis)));
  }

  private static ImmutableList<OutputArtifact> parseFiles(
    BuildEventStreamProtos.NamedSetOfFiles namedSet, String config, long startTimeMillis) {
    return namedSet.getFilesList().stream()
        .map(f -> OutputArtifactParser.parseArtifact(f, config, startTimeMillis))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  /** Returns a copy of a {@link BuildEventStreamProtos.NamedSetOfFiles} with interned string references. */
  private static BuildEventStreamProtos.NamedSetOfFiles internNamedSet(
    BuildEventStreamProtos.NamedSetOfFiles namedSet, Interner<String> interner) {
    return namedSet.toBuilder()
        .clearFiles()
        .addAllFiles(
            namedSet.getFilesList().stream()
                .map(
                    file -> {
                      BuildEventStreamProtos.File.Builder builder =
                          file.toBuilder()
                              .setUri(interner.intern(file.getUri()))
                              .setName(interner.intern(file.getName()))
                              .clearPathPrefix()
                              .addAllPathPrefix(
                                  file.getPathPrefixList().stream()
                                      .map(interner::intern)
                                      .collect(toImmutableList()));
                      return builder.build();
                    })
                .collect(toImmutableList()))
        .build();
  }

  @Service(Service.Level.APP)
  public static final class BepParserSemaphore {

    public Semaphore parallelParsingSemaphore = parallelBepPoolingEnabled.getValue() ? new Semaphore(maxThreads.getValue()) : null;

    public void start() throws BuildEventStreamProvider.BuildEventStreamException {
      if (parallelParsingSemaphore != null) {
        try {
          parallelParsingSemaphore.acquire();
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new BuildEventStreamProvider.BuildEventStreamException("Failed to acquire a parser semphore permit", e);
        }
      }
    }

    public void end() {
      if (parallelParsingSemaphore != null) {
        parallelParsingSemaphore.release();
      }
    }
  }

  private static class FileSetBuilder {
    @Nullable BuildEventStreamProtos.NamedSetOfFiles namedSet;
    @Nullable String configId;
    final Set<String> outputGroups = new HashSet<>();
    final Set<String> targets = new HashSet<>();

    @CanIgnoreReturnValue
    FileSetBuilder updateFromParent(FileSetBuilder parent) {
      configId = parent.configId;
      outputGroups.addAll(parent.outputGroups);
      targets.addAll(parent.targets);
      return this;
    }

    @CanIgnoreReturnValue
    FileSetBuilder setNamedSet(BuildEventStreamProtos.NamedSetOfFiles namedSet) {
      this.namedSet = namedSet;
      return this;
    }

    @CanIgnoreReturnValue
    FileSetBuilder setConfigId(String configId) {
      this.configId = configId;
      return this;
    }

    @CanIgnoreReturnValue
    FileSetBuilder addOutputGroup(String outputGroup) {
      this.outputGroups.add(outputGroup);
      return this;
    }

    @CanIgnoreReturnValue
    FileSetBuilder addTarget(String target) {
      this.targets.add(target);
      return this;
    }

    boolean isValid(Map<String, String> configIdToMnemonic) {
      return namedSet != null && configId != null && configIdToMnemonic.get(configId) != null;
    }

    ParsedBepOutput.FileSet build(Map<String, String> configIdToMnemonic, long startTimeMillis) {
      return new ParsedBepOutput.FileSet(parseFiles(namedSet, configIdToMnemonic.get(configId), startTimeMillis), outputGroups, targets);
    }
  }
}

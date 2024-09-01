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
package com.google.idea.blaze.base.command.buildresult;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
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
import com.google.common.collect.SetMultimap;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.File;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.OutputGroup;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.WorkspaceStatus.Item;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A data class representing blaze's build event protocol (BEP) output for a build. */
public final class ParsedBepOutput {

  @VisibleForTesting
  public static final ParsedBepOutput EMPTY =
      new ParsedBepOutput(
          "build-id",
          null,
          Optional.empty(),
          ImmutableMap.of(),
          ImmutableSetMultimap.of(),
          0,
          BuildResult.SUCCESS,
          0,
          ImmutableSet.of());

  private static final String WORKSPACE_ITEM_KEY_SOURCE_URI = "SOURCE_URI";

  /** Parses BEP events into {@link ParsedBepOutput} */
  public static ParsedBepOutput parseBepArtifacts(InputStream bepStream)
      throws BuildEventStreamException {
    return parseBepArtifacts(BuildEventStreamProvider.fromInputStream(bepStream));
  }

  /** Parses BEP events into {@link ParsedBepOutput} */
  public static ParsedBepOutput parseBepArtifacts(BuildEventStreamProvider stream)
      throws BuildEventStreamException {
    return parseBepArtifacts(stream, null);
  }

  /**
   * Parses BEP events into {@link ParsedBepOutput}. String references in {@link NamedSetOfFiles}
   * are interned to conserve memory.
   *
   * <p>BEP protos often contain many duplicate strings both within a single stream and across
   * shards running in parallel, so a {@link Interner} is used to share references.
   */
  public static ParsedBepOutput parseBepArtifacts(
      BuildEventStreamProvider stream, @Nullable Interner<String> interner)
      throws BuildEventStreamException {

    if (interner == null) {
      interner = Interners.newStrongInterner();
    }

    BuildEvent event;
    Map<String, String> configIdToMnemonic = new HashMap<>();
    Set<String> topLevelFileSets = new HashSet<>();
    Map<String, FileSet.Builder> fileSets = new LinkedHashMap<>();
    ImmutableSetMultimap.Builder<String, String> targetToFileSets = ImmutableSetMultimap.builder();
    ImmutableSet.Builder<Label> targetsWithErrors = ImmutableSet.builder();
    String localExecRoot = null;
    String buildId = null;
    Optional<String> sourceUri = Optional.empty();
    long startTimeMillis = 0L;
    BuildResult buildResult = BuildResult.SUCCESS;
    boolean emptyBuildEventStream = true;

    while ((event = stream.getNext()) != null) {
      emptyBuildEventStream = false;
      switch (event.getId().getIdCase()) {
        case WORKSPACE:
          localExecRoot = event.getWorkspaceInfo().getLocalExecRoot();
          continue;
        case WORKSPACE_STATUS:
          ImmutableMap<String, Item> itemMap =
              Maps.uniqueIndex(event.getWorkspaceStatus().getItemList(), Item::getKey);
          // TODO(mathewi) This shouldn't really be here since it is dependant on VCS specific
          //   integration with Bazel. We should refactor this code to allow the BlazeVcsHandler to
          //   be involved here instead.
          if (itemMap.containsKey(WORKSPACE_ITEM_KEY_SOURCE_URI)) {
            sourceUri = Optional.of(itemMap.get(WORKSPACE_ITEM_KEY_SOURCE_URI).getValue());
          }
          continue;
        case CONFIGURATION:
          configIdToMnemonic.put(
              event.getId().getConfiguration().getId(), event.getConfiguration().getMnemonic());
          continue;
        case NAMED_SET:
          NamedSetOfFiles namedSet = internNamedSet(event.getNamedSetOfFiles(), interner);
          fileSets.compute(
              event.getId().getNamedSet().getId(),
              (k, v) ->
                  v != null ? v.setNamedSet(namedSet) : FileSet.builder().setNamedSet(namedSet));
          continue;
        case ACTION_COMPLETED:
          Preconditions.checkState(event.hasAction());
          if (!event.getAction().getSuccess()) {
            targetsWithErrors.add(Label.create(event.getId().getActionCompleted().getLabel()));
          }
          break;
        case TARGET_COMPLETED:
          String label = event.getId().getTargetCompleted().getLabel();
          String configId = event.getId().getTargetCompleted().getConfiguration().getId();

          event
              .getCompleted()
              .getOutputGroupList()
              .forEach(
                  o -> {
                    List<String> sets = getFileSets(o);
                    targetToFileSets.putAll(label, sets);
                    topLevelFileSets.addAll(sets);
                    for (String id : sets) {
                      fileSets.compute(
                          id,
                          (k, v) -> {
                            FileSet.Builder builder = (v != null) ? v : FileSet.builder();
                            return builder
                                .setConfigId(configId)
                                .addOutputGroups(ImmutableSet.of(o.getName()))
                                .addTargets(ImmutableSet.of(label));
                          });
                    }
                  });
          continue;
        case STARTED:
          buildId = Strings.emptyToNull(event.getStarted().getUuid());
          startTimeMillis = event.getStarted().getStartTimeMillis();
          continue;
        case BUILD_FINISHED:
          buildResult = BuildResult.fromExitCode(event.getFinished().getExitCode().getCode());
          continue;
        default: // continue
      }
    }
    // If stream is empty, it means that service failed to retrieve any blaze build event from build
    // event stream. This should not happen if a build start correctly.
    if (emptyBuildEventStream) {
      throw new BuildEventStreamException("No build events found");
    }
    ImmutableMap<String, FileSet> filesMap =
        fillInTransitiveFileSetData(
            fileSets, topLevelFileSets, configIdToMnemonic, startTimeMillis);
    return new ParsedBepOutput(
        buildId,
        localExecRoot,
        sourceUri,
        filesMap,
        targetToFileSets.build(),
        startTimeMillis,
        buildResult,
        stream.getBytesConsumed(),
        targetsWithErrors.build());
  }

  private static List<String> getFileSets(OutputGroup group) {
    return group.getFileSetsList().stream()
        .map(NamedSetOfFilesId::getId)
        .collect(Collectors.toList());
  }

  /**
   * Only top-level targets have configuration mnemonic, producing target, and output group data
   * explicitly provided in BEP. This method fills in that data for the transitive closure.
   */
  private static ImmutableMap<String, FileSet> fillInTransitiveFileSetData(
      Map<String, FileSet.Builder> fileSets,
      Set<String> topLevelFileSets,
      Map<String, String> configIdToMnemonic,
      long startTimeMillis) {
    Queue<String> toVisit = Queues.newArrayDeque(topLevelFileSets);
    Set<String> visited = new HashSet<>(topLevelFileSets);
    while (!toVisit.isEmpty()) {
      String setId = toVisit.remove();
      FileSet.Builder fileSet = fileSets.get(setId);
      if (fileSet.namedSet == null) {
        continue;
      }
      fileSet.namedSet.getFileSetsList().stream()
          .map(NamedSetOfFilesId::getId)
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
                Entry::getKey, e -> e.getValue().build(configIdToMnemonic, startTimeMillis)));
  }

  @Nullable public final String buildId;

  /** A path to the local execroot */
  @Nullable private final String localExecRoot;

  final Optional<String> sourceUri;

  /** A map from file set ID to file set, with the same ordering as the BEP stream. */
  private final ImmutableMap<String, FileSet> fileSets;

  /** The set of named file sets directly produced by each target. */
  private final SetMultimap<String, String> targetFileSets;

  final long syncStartTimeMillis;

  private final BuildResult buildResult;
  private final long bepBytesConsumed;
  private final ImmutableSet<Label> targetsWithErrors;

  private ParsedBepOutput(
      @Nullable String buildId,
      @Nullable String localExecRoot,
      Optional<String> sourceUri,
      ImmutableMap<String, FileSet> fileSets,
      ImmutableSetMultimap<String, String> targetFileSets,
      long syncStartTimeMillis,
      BuildResult buildResult,
      long bepBytesConsumed,
      ImmutableSet<Label> targetsWithErrors) {
    this.buildId = buildId;
    this.localExecRoot = localExecRoot;
    this.sourceUri = sourceUri;
    this.fileSets = fileSets;
    this.targetFileSets = targetFileSets;
    this.syncStartTimeMillis = syncStartTimeMillis;
    this.buildResult = buildResult;
    this.bepBytesConsumed = bepBytesConsumed;
    this.targetsWithErrors = targetsWithErrors;
  }

  /** Returns the local execroot. */
  @Nullable
  public String getLocalExecRoot() {
    return localExecRoot;
  }

  /**
   * Returns URI of the source that the build consumed, if available. The format will be VCS
   * specific. If present, the value will be can be used with {@link
   * com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler#vcsStateForSourceUri(String)}.
   */
  public Optional<String> getSourceUri() {
    return sourceUri;
  }

  /** Returns the build result. */
  public BuildResult getBuildResult() {
    return buildResult;
  }

  public long getBepBytesConsumed() {
    return bepBytesConsumed;
  }

  /** Returns all output artifacts of the build. */
  public ImmutableSet<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .map(s -> s.parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getBazelOutRelativePath()))
        .collect(toImmutableSet());
  }

  /** Returns the set of artifacts directly produced by the given target. */
  public ImmutableSet<OutputArtifact> getDirectArtifactsForTarget(
      Label label, Predicate<String> pathFilter) {
    return targetFileSets.get(label.toString()).stream()
        .map(s -> fileSets.get(s).parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getBazelOutRelativePath()))
        .collect(toImmutableSet());
  }

  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(
      String outputGroup, Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .filter(f -> f.outputGroups.contains(outputGroup))
        .map(f -> f.parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getBazelOutRelativePath()))
        .distinct()
        .collect(toImmutableList());
  }

  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
    return getOutputGroupArtifacts(outputGroup, s -> true);
  }

  /**
   * Returns a map from artifact key to {@link BepArtifactData} for all artifacts reported during
   * the build.
   */
  public ImmutableMap<String, BepArtifactData> getFullArtifactData() {
    return fileSets.values().stream()
        .flatMap(FileSet::toPerArtifactData)
        .collect(
            toImmutableMap(d -> d.artifact.getBazelOutRelativePath(), d -> d, BepArtifactData::update));
  }

  /** Returns the set of build targets that had an error. */
  public ImmutableSet<Label> getTargetsWithErrors() {
    return targetsWithErrors;
  }

  private static ImmutableList<OutputArtifact> parseFiles(
      NamedSetOfFiles namedSet, String config, long startTimeMillis) {
    return namedSet.getFilesList().stream()
        .map(f -> OutputArtifactParser.parseArtifact(f, config, startTimeMillis))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  private static class FileSet {
    private final ImmutableList<OutputArtifact> parsedOutputs;
    private final ImmutableSet<String> outputGroups;
    private final ImmutableSet<String> targets;

    FileSet(
        NamedSetOfFiles namedSet,
        String configuration,
        long startTimeMillis,
        Set<String> outputGroups,
        Set<String> targets) {
      this.parsedOutputs = parseFiles(namedSet, configuration, startTimeMillis);
      this.outputGroups = ImmutableSet.copyOf(outputGroups);
      this.targets = ImmutableSet.copyOf(targets);
    }

    static Builder builder() {
      return new Builder();
    }

    private Stream<BepArtifactData> toPerArtifactData() {
      return parsedOutputs.stream().map(a -> new BepArtifactData(a, outputGroups, targets));
    }

    private static class Builder {
      @Nullable NamedSetOfFiles namedSet;
      @Nullable String configId;
      final Set<String> outputGroups = new HashSet<>();
      final Set<String> targets = new HashSet<>();

      @CanIgnoreReturnValue
      Builder updateFromParent(Builder parent) {
        configId = parent.configId;
        outputGroups.addAll(parent.outputGroups);
        targets.addAll(parent.targets);
        return this;
      }

      @CanIgnoreReturnValue
      Builder setNamedSet(NamedSetOfFiles namedSet) {
        this.namedSet = namedSet;
        return this;
      }

      @CanIgnoreReturnValue
      Builder setConfigId(String configId) {
        this.configId = configId;
        return this;
      }

      @CanIgnoreReturnValue
      Builder addOutputGroups(Set<String> outputGroups) {
        this.outputGroups.addAll(outputGroups);
        return this;
      }

      @CanIgnoreReturnValue
      Builder addTargets(Set<String> targets) {
        this.targets.addAll(targets);
        return this;
      }

      boolean isValid(Map<String, String> configIdToMnemonic) {
        return namedSet != null && configId != null && configIdToMnemonic.get(configId) != null;
      }

      FileSet build(Map<String, String> configIdToMnemonic, long startTimeMillis) {
        return new FileSet(
            namedSet, configIdToMnemonic.get(configId), startTimeMillis, outputGroups, targets);
      }
    }
  }

  /** Returns a copy of a {@link NamedSetOfFiles} with interned string references. */
  private static NamedSetOfFiles internNamedSet(
      NamedSetOfFiles namedSet, Interner<String> interner) {
    return namedSet.toBuilder()
        .clearFiles()
        .addAllFiles(
            namedSet.getFilesList().stream()
                .map(
                    file -> {
                      File.Builder builder =
                          file.toBuilder()
                              .setUri(interner.intern(file.getUri()))
                              .setName(interner.intern(file.getName()))
                              .clearPathPrefix()
                              .addAllPathPrefix(
                                  file.getPathPrefixList().stream()
                                      .map(interner::intern)
                                      .collect(Collectors.toUnmodifiableList()));
                      return builder.build();
                    })
                .collect(Collectors.toUnmodifiableList()))
        .build();
  }
}

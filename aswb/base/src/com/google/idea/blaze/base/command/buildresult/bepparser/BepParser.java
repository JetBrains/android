package com.google.idea.blaze.base.command.buildresult.bepparser;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
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
   * Parses BEP events into {@link ParsedBepOutput}. String references in {@link BuildEventStreamProtos.NamedSetOfFiles}
   * are interned to conserve memory.
   *
   * <p>BEP protos often contain many duplicate strings both within a single stream and across
   * shards running in parallel, so a {@link Interner} is used to share references.
   */
  public static ParsedBepOutput parseBepArtifacts(
      BuildEventStreamProvider stream, @Nullable Interner<String> interner)
    throws BuildEventStreamProvider.BuildEventStreamException {
    final var semaphore = ApplicationManager.getApplication().getService(BepParserSemaphore.class);
    semaphore.start();
    try {
      if (interner == null) {
        interner = Interners.newStrongInterner();
      }

      BuildEventStreamProtos.BuildEvent event;
      Map<String, String> configIdToMnemonic = new HashMap<>();
      Set<String> topLevelFileSets = new HashSet<>();
      Map<String, FileSetBuilder> fileSets = new LinkedHashMap<>();
      ImmutableSetMultimap.Builder<String, String> targetToFileSets = ImmutableSetMultimap.builder();
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
            fileSets.compute(
              event.getId().getNamedSet().getId(),
              (k, v) ->
                v != null ? v.setNamedSet(namedSet) : new FileSetBuilder().setNamedSet(namedSet));
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
                        FileSetBuilder builder = (v != null) ? v : new FileSetBuilder();
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
          fileSets, topLevelFileSets, configIdToMnemonic, startTimeMillis);
      return new ParsedBepOutput(
        buildId,
        localExecRoot,
        filesMap,
        targetToFileSets.build(),
        startTimeMillis,
        buildResult,
        stream.getBytesConsumed(),
        targetsWithErrors.build());
    }
    finally {
      semaphore.end();
    }
  }

  private static List<String> getFileSets(BuildEventStreamProtos.OutputGroup group) {
    return group.getFileSetsList().stream()
        .map(BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId::getId)
        .collect(Collectors.toList());
  }

  /**
   * Only top-level targets have configuration mnemonic, producing target, and output group data
   * explicitly provided in BEP. This method fills in that data for the transitive closure.
   */
  private static ImmutableMap<String, ParsedBepOutput.FileSet> fillInTransitiveFileSetData(
      Map<String, FileSetBuilder> fileSets,
      Set<String> topLevelFileSets,
      Map<String, String> configIdToMnemonic,
      long startTimeMillis) {
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
                                      .collect(Collectors.toUnmodifiableList()));
                      return builder.build();
                    })
                .collect(Collectors.toUnmodifiableList()))
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
    FileSetBuilder addOutputGroups(Set<String> outputGroups) {
      this.outputGroups.addAll(outputGroups);
      return this;
    }

    @CanIgnoreReturnValue
    FileSetBuilder addTargets(Set<String> targets) {
      this.targets.addAll(targets);
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

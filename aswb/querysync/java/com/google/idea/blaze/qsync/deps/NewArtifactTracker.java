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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.proto.ProtoStringInterner;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.artifacts.DigestMap;
import com.google.idea.blaze.qsync.artifacts.DigestMapImpl;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcToolchainInfo;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The artifact tracker performs the following tasks:
 *
 * <ul>
 *   <li>Keep track of which dependencies have been built, when they were build, and the set of
 *       artifacts produced by each.
 *   <li>Requests that all artifacts are cached by {@link BuildArtifactCache}.
 *   <li>Provides details of build artifacts, allowing the project proto to be updated accordingly.
 * </ul>
 */
public class NewArtifactTracker<C extends Context<C>> implements ArtifactTracker<C> {

  private static final Logger logger = Logger.getLogger(NewArtifactTracker.class.getName());

  private final BuildArtifactCache artifactCache;
  private final Function<
      TargetBuildInfo, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata.Extractor<?>>>
      targetToMetadataFn;
  private final ArtifactMetadata.Factory metadataFactory;
  private final Executor executor;
  private final Path stateFile;

  // Lock for making updates to the mutable state
  private final Object stateLock = new Object();

  // TODO(mathewi) this state should really be owned by BlazeProjectSnapshot like all other state,
  //   and updated in lock step with it.
  @GuardedBy("stateLock")
  private final Map<Label, TargetBuildInfo> builtDeps = Maps.newHashMap();

  @GuardedBy("stateLock")
  private final Map<String, CcToolchain> ccToolchainMap = Maps.newHashMap();

  public NewArtifactTracker(
      Path projectDirectory,
      BuildArtifactCache artifactCache,
      Function<TargetBuildInfo, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata.Extractor<?>>>
          targetToMetadataFn,
      ArtifactMetadata.Factory metadataFactory,
      Executor executor) {
    this.artifactCache = artifactCache;
    this.targetToMetadataFn = targetToMetadataFn;
    this.metadataFactory = metadataFactory;
    this.stateFile = projectDirectory.resolve("artifact_state");
    this.executor = executor;
    loadState();
  }

  public ImmutableCollection<TargetBuildInfo> getBuiltDeps() {
    synchronized (stateLock) {
      return ImmutableList.copyOf(builtDeps.values());
    }
  }

  @Override
  public State getStateSnapshot() {
    synchronized (stateLock) {
      return State.create(ImmutableMap.copyOf(builtDeps), ImmutableMap.copyOf(ccToolchainMap));
    }
  }

  @Override
  public void clear() throws IOException {
    synchronized (stateLock) {
      builtDeps.clear();
    }
    saveState();
  }

  record MetadataKey(BuildArtifact artifact, Class<? extends ArtifactMetadata> mdClass) {}

  private static ImmutableCollection<TargetBuildInfo> getTargetBuildInfo(
      OutputInfo outputInfo, DigestMap digestMap) {

    ImmutableSet.Builder<TargetBuildInfo> targetBuildInfoSet = ImmutableSet.builder();
    for (JavaArtifacts javaArtifacts : outputInfo.getArtifactInfo()) {
      for (JavaTargetArtifacts javaTarget : javaArtifacts.getArtifactsList()) {
        JavaArtifactInfo artifactInfo = JavaArtifactInfo.create(javaTarget, digestMap);
        TargetBuildInfo targetInfo =
            TargetBuildInfo.forJavaTarget(artifactInfo, outputInfo.getBuildContext());
        targetBuildInfoSet.add(targetInfo);
      }
    }

    for (CcCompilationInfoOuterClass.CcCompilationInfo ccInfo : outputInfo.getCcCompilationInfo()) {
      for (CcTargetInfo ccTarget : ccInfo.getTargetsList()) {
        CcCompilationInfo artifactInfo = CcCompilationInfo.create(ccTarget, digestMap);
        TargetBuildInfo targetInfo =
            TargetBuildInfo.forCcTarget(artifactInfo, outputInfo.getBuildContext());
        targetBuildInfoSet.add(targetInfo);
      }
    }
    return targetBuildInfoSet.build();
  }

  private static ImmutableList<CcToolchain> getCcToolchains(OutputInfo outputInfo) {
    ImmutableList.Builder<CcToolchain> toolchainList = ImmutableList.builder();

    for (CcCompilationInfoOuterClass.CcCompilationInfo ccInfo : outputInfo.getCcCompilationInfo()) {
      for (CcToolchainInfo proto : ccInfo.getToolchainsList()) {
        CcToolchain toolchain = CcToolchain.create(proto);
        toolchainList.add(toolchain);
      }
    }
    return toolchainList.build();
  }

  private ImmutableMap<Label, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata>>
  extractArtifactMetadata(
      Iterable<TargetBuildInfo> targetBuildInfo, DigestMap digestMap, String buildId)
      throws BuildException {
    Map<MetadataKey, ListenableFuture<ArtifactMetadata>> metadataFutures = Maps.newHashMap();
    for (TargetBuildInfo targetInfo : targetBuildInfo) {
      for (Map.Entry<BuildArtifact, ArtifactMetadata.Extractor<?>> entry :
          targetToMetadataFn.apply(targetInfo).entries()) {
        MetadataKey key = new MetadataKey(entry.getKey(), entry.getValue().metadataClass());
        if (metadataFutures.containsKey(key)) {
          // this metadata has already been requested.
          continue;
        }
        String digest =
            digestMap
                .digestForArtifactPath(entry.getKey().artifactPath(), targetInfo.label())
                .orElseThrow(
                    () ->
                        new BuildException(
                            String.format(
                                "Could not find digest for artifact path %s, target %s, build %s."
                                    + " It was requested for metadata %s.",
                                entry.getKey().artifactPath(),
                                targetInfo.label(),
                                buildId,
                                entry.getValue().getClass().getName())));
        ListenableFuture<CachedArtifact> artifact =
            artifactCache
                .get(digest)
                .orElseThrow(
                    () ->
                        new BuildException(
                            String.format(
                                "Digest %s not present in the cache, for %s built by %s in build"
                                    + " %s.  It was requested for metadata %s.",
                                digest,
                                entry.getKey().artifactPath(),
                                targetInfo.label(),
                                buildId,
                                entry.getValue().metadataClass().getName())));
        ListenableFuture<ArtifactMetadata> transformed =
            Futures.transformAsync(
                artifact,
                a -> Futures.immediateFuture(entry.getValue().extractFrom(a, entry.getKey())),
                executor);
        metadataFutures.put(key, transformed);
      }
    }

    Map<Label, ImmutableSetMultimap.Builder<BuildArtifact, ArtifactMetadata>> metadata =
        Maps.newHashMap();

    List<BuildException> failures = Lists.newArrayList();
    for (Map.Entry<MetadataKey, ListenableFuture<ArtifactMetadata>> entry :
        metadataFutures.entrySet()) {
      try {
        metadata.putIfAbsent(entry.getKey().artifact.target(), ImmutableSetMultimap.builder());
        metadata
            .get(entry.getKey().artifact.target())
            .put(entry.getKey().artifact, Uninterruptibles.getUninterruptibly(entry.getValue()));
      } catch (ExecutionException e) {
        failures.add(
            new BuildException(
                String.format(
                    "Failed to extract metadata '%s' from artifact '%s' (from %s, produced by build"
                        + " %s)",
                    entry.getKey().mdClass.getName(),
                    entry.getKey().artifact.artifactPath(),
                    entry.getKey().artifact.target(),
                    buildId),
                e));
      }
    }
    if (!failures.isEmpty()) {
      BuildException e =
          new BuildException(
              String.format("Failed to extract metadata from %d artifacts", failures.size()));
      failures.forEach(e::addSuppressed);
      throw e;
    }
    return ImmutableMap.copyOf(Maps.transformValues(metadata, ImmutableSetMultimap.Builder::build));
  }

  /**
   * Gets unique {@link TargetBuildInfo}s per target.
   *
   * <p>In some cases, the aspect can return slightly different target infos for the same target,
   * due to the way that file-to-target mapping happens inside the aspect:
   *
   * <ul>
   *   <li>The aspect uses the bazel {@code File.owner} API to determine which target built a
   *       target.
   *   <li>Depending on the dependency chain, the set of files returned for a specific target can
   *       differ.
   * </ul>
   *
   * A specific example:
   *
   * <p>The proto rules can yield classes jars for default (immutable) java classes, or mutable
   * versions of the same classes. Consider the rules:
   *
   * <ul>
   *   <li>{@code :my_proto} - the proto rule itself
   *   <li>{@code :my_java_proto} - generates java classes for the above
   *   <li>{@code :my_mutable_java_proto} - generated mutable java classes for the above
   * </ul>
   *
   * In this case, all the generated jars are attributed to the {@code :my_proto} target by the
   * Bazel {@code File.owner} API.
   *
   * <p>Now, if we have two project targets that transitively depend on the {@code :my_java_proto}
   * and {@code my_mutable_java_proto} respectively, then the {@link JavaArtifacts} generated for
   * each target will contain entries for {@code :my_proto} that differ in their jars: one will
   * contain {@code libmy_proto.jar} or similar, and the other {@code libmy_proto_mutable.jar} or
   * similar. The output for the target should be identical in all other respects.
   *
   * <p>We resolve this here by building sets of distinct {@link TargetBuildInfo} objects per
   * target. In most cases there will only be one. In cases such as the above, they will be
   * identical in all respects other than the set of jar files. So we assert that, and then combine
   * the jar files to produce a single {@link TargetBuildInfo} per target.
   */
  private static Map<Label, TargetBuildInfo> getUniqueTargetBuildInfos(
      ImmutableCollection<TargetBuildInfo> allTargets) throws BuildException {
    ImmutableListMultimap<Label, TargetBuildInfo> targetInfoByTarget =
        Multimaps.index(allTargets, TargetBuildInfo::label);
    Map<Label, TargetBuildInfo> uniqueTargetInfo = Maps.newHashMap();
    for (Label t : targetInfoByTarget.keySet()) {
      Set<TargetBuildInfo> targetInfos = Sets.newHashSet(targetInfoByTarget.get(t));
      if (targetInfos.size() == 1) {
        uniqueTargetInfo.put(t, Iterables.getOnlyElement(targetInfos));
      } else {
        TargetBuildInfo first = Iterables.get(targetInfos, 0);
        if (targetInfos.stream().skip(1).allMatch(first::equalsIgnoringJavaCompileJars)) {
          JavaArtifactInfo.Builder combinedJava =
              first.javaInfo().map(JavaArtifactInfo::toBuilder).orElse(null);
          if (combinedJava != null) {
            targetInfos.stream()
                .skip(1)
                .map(TargetBuildInfo::javaInfo)
                .flatMap(Optional::stream)
                .map(JavaArtifactInfo::jars)
                .forEach(combinedJava.jarsBuilder()::addAll);
            uniqueTargetInfo.put(t, first.toBuilder().javaInfo(combinedJava.build()).build());
          } else {
            uniqueTargetInfo.put(t, first);
          }
        } else {
          throw new BuildException(
              String.format(
                  "Multiple conflicting target info for target %s:\n  %s",
                  t,
                  targetInfos.stream().map(Object::toString).collect(Collectors.joining("\n  "))));
        }
      }
    }
    return uniqueTargetInfo;
  }

  @Override
  public void update(Set<Label> targets, OutputInfo outputInfo, C context) throws BuildException {
    ListenableFuture<?> artifactsCached =
        artifactCache.addAll(outputInfo.getOutputGroups().values(), context);

    DigestMap digestMap =
        new DigestMapImpl(
            outputInfo.getOutputGroups().values().stream()
                .map(a -> new SimpleEntry<>(a.getArtifactPath(), a.getDigest()))
                .distinct()
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)),
            outputInfo.getTargetsWithErrors());

    Map<Label, TargetBuildInfo> newTargetInfo =
        getUniqueTargetBuildInfos(getTargetBuildInfo(outputInfo, digestMap));

    ImmutableList<CcToolchain> newToolchains = getCcToolchains(outputInfo);

    // extract required metadata from the build artifacts
    ImmutableMap<Label, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata>> metadata =
        extractArtifactMetadata(
            newTargetInfo.values(), digestMap, outputInfo.getBuildContext().buildId());

    // insert this metadata into newTargetInfo
    for (Map.Entry<Label, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata>> entry :
        metadata.entrySet()) {
      newTargetInfo.put(
          entry.getKey(), newTargetInfo.get(entry.getKey()).withMetadata(entry.getValue()));
    }

    synchronized (stateLock) {
      for (TargetBuildInfo tbi : newTargetInfo.values()) {
        builtDeps.put(tbi.label(), tbi);
      }
      for (CcToolchain toolchain : newToolchains) {
        ccToolchainMap.put(toolchain.id(), toolchain);
      }

      for (Label label : targets) {
        if (!builtDeps.containsKey(label)) {
          logger.warning(
              "Target " + label + " was not built. If the target is an alias, this is expected");
          builtDeps.put(
              label,
              TargetBuildInfo.forJavaTarget(
                  JavaArtifactInfo.empty(label), outputInfo.getBuildContext()));
        }
      }
    }

    try {
      saveState();
    } catch (IOException e) {
      throw new BuildException("Failed to write artifact state", e);
    }

    try {
      Object unused = Uninterruptibles.getUninterruptibly(Futures.allAsList(artifactsCached));
    } catch (ExecutionException e) {
      throw new BuildException("Failed to cache build artifacts", e);
    }
  }

  @Override
  public Optional<ImmutableSet<Path>> getCachedFiles(Label target) {
    // TODO(b/323346056) this is only used to find built AARs for a target. Refactor that code.
    return Optional.empty();
  }

  @Override
  public ImmutableMap<String, ByteSource> getBugreportFiles() {
    return ImmutableMap.of(stateFile.getFileName().toString(), MoreFiles.asByteSource(stateFile));
  }

  private void saveState() throws IOException {
    ArtifactTrackerStateSerializer serializer;
    synchronized (stateLock) {
      serializer =
          new ArtifactTrackerStateSerializer()
              .visitDepsMap(builtDeps)
              .visitToolchainMap(ccToolchainMap);
    }
    // TODO(b/328563748) write to a new file and then rename to avoid the risk of truncation.
    try (OutputStream stream = new GZIPOutputStream(Files.newOutputStream(stateFile))) {
      serializer.toProto().writeTo(stream);
    }
  }

  private void loadState() {
    if (!Files.exists(stateFile)) {
      return;
    }
    ArtifactTrackerProto.ArtifactTrackerState state;
    try (InputStream stream = new GZIPInputStream(Files.newInputStream(stateFile))) {
      state =
          ProtoStringInterner.intern(
              ArtifactTrackerProto.ArtifactTrackerState.parseFrom(
                  stream, ExtensionRegistry.getEmptyRegistry()));
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to read artifact tracker state from " + stateFile, e);
      return;
    }

    ArtifactTrackerStateDeserializer deserializer =
        new ArtifactTrackerStateDeserializer(metadataFactory);
    deserializer.visit(state);

    synchronized (stateLock) {
      builtDeps.putAll(deserializer.getBuiltDepsMap());
      ccToolchainMap.putAll(deserializer.getCcToolchainMap());
    }
  }
}

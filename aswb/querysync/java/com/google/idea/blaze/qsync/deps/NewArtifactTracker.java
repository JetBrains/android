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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
  private final Function<TargetBuildInfo, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata>> targetToMetadataFn;
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
      Function<TargetBuildInfo, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata>> targetToMetadataFn,
      Executor executor) {
    this.artifactCache = artifactCache;
    this.targetToMetadataFn = targetToMetadataFn;
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

  record LabelMetadataKey(Label target, Path artifactPath, String extractorKey) {

  }

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

  private ImmutableMap<LabelMetadataKey, String> extractArtifactMetadata(
      Iterable<TargetBuildInfo> targetBuildInfo, DigestMap digestMap, String buildId)
      throws BuildException {
    Map<LabelMetadataKey, ListenableFuture<String>> metadataFutures = Maps.newHashMap();
    for (TargetBuildInfo targetInfo : targetBuildInfo) {
      for (Map.Entry<BuildArtifact, ArtifactMetadata> entry :
          targetToMetadataFn.apply(targetInfo).entries()) {
        LabelMetadataKey key =
            new LabelMetadataKey(
                targetInfo.label(), entry.getKey().artifactPath(), entry.getValue().key());
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
                                entry.getValue().key())));
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
                                entry.getValue().key())));
        ListenableFuture<String> transformed =
            Futures.transformAsync(
                artifact,
                a -> Futures.immediateFuture(entry.getValue().extract(a, entry.getKey())),
                executor);
        metadataFutures.put(key, transformed);
      }
    }

    ImmutableMap.Builder<LabelMetadataKey, String> metadata = ImmutableMap.builder();

    List<BuildException> failures = Lists.newArrayList();
    for (Map.Entry<LabelMetadataKey, ListenableFuture<String>> entry : metadataFutures.entrySet()) {
      try {
        metadata.put(entry.getKey(), Uninterruptibles.getUninterruptibly(entry.getValue()));
      } catch (ExecutionException e) {
        failures.add(
            new BuildException(
                String.format(
                    "Failed to extract metadata '%s' from artifact '%s' (from %s, produced by build"
                        + " %s)",
                    entry.getKey().extractorKey,
                    entry.getKey().artifactPath,
                    entry.getKey().target,
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
    return metadata.build();
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
        Maps.newHashMap(
            Maps.uniqueIndex(getTargetBuildInfo(outputInfo, digestMap), TargetBuildInfo::label));
    ImmutableList<CcToolchain> newToolchains = getCcToolchains(outputInfo);

    // extract required metadata from the build artifacts
    ImmutableMap<LabelMetadataKey, String> metadata =
        extractArtifactMetadata(
            newTargetInfo.values(), digestMap, outputInfo.getBuildContext().buildId());

    // insert this metadata into newTargetInfo
    for (Map.Entry<LabelMetadataKey, String> entry : metadata.entrySet()) {
      TargetBuildInfo.Builder builder = newTargetInfo.get(entry.getKey().target()).toBuilder();
      builder
          .artifactMetadataBuilder()
          .put(
              new TargetBuildInfo.MetadataKey(
                  entry.getKey().extractorKey, entry.getKey().artifactPath),
              entry.getValue());
      newTargetInfo.put(entry.getKey().target(), builder.build());
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
  public Iterable<Path> getBugreportFiles() {
    return ImmutableList.of(stateFile);
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

    ArtifactTrackerStateDeserializer deserializer = new ArtifactTrackerStateDeserializer();
    deserializer.visit(state);

    synchronized (stateLock) {
      builtDeps.putAll(deserializer.getBuiltDepsMap());
      ccToolchainMap.putAll(deserializer.getCcToolchainMap());
    }
  }
}

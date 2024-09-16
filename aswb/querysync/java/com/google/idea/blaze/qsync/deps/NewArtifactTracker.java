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
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.proto.ProtoStringInterner;
import com.google.idea.blaze.exception.BuildException;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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
  private final Path stateFile;

  // Lock for making updates to the mutable state
  private final Object stateLock = new Object();

  // TODO(mathewi) this state should really be owned by BlazeProjectSnapshot like all other state,
  //   and updated in lock step with it.
  @GuardedBy("stateLock")
  private final Map<Label, TargetBuildInfo> builtDeps = Maps.newHashMap();

  @GuardedBy("stateLock")
  private final Map<String, CcToolchain> ccToolchainMap = Maps.newHashMap();

  public NewArtifactTracker(Path projectDirectory, BuildArtifactCache artifactCache) {
    this.artifactCache = artifactCache;
    this.stateFile = projectDirectory.resolve("artifact_state");
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

    synchronized (stateLock) {
      for (JavaArtifacts javaArtifacts : outputInfo.getArtifactInfo()) {
        for (JavaTargetArtifacts javaTarget : javaArtifacts.getArtifactsList()) {
          JavaArtifactInfo artifactInfo = JavaArtifactInfo.create(javaTarget, digestMap);
          TargetBuildInfo targetInfo =
              TargetBuildInfo.forJavaTarget(artifactInfo, outputInfo.getBuildContext());
          builtDeps.put(artifactInfo.label(), targetInfo);
        }
      }

      for (CcCompilationInfoOuterClass.CcCompilationInfo ccInfo :
          outputInfo.getCcCompilationInfo()) {
        for (CcTargetInfo ccTarget : ccInfo.getTargetsList()) {
          CcCompilationInfo artifactInfo = CcCompilationInfo.create(ccTarget, digestMap);
          builtDeps.put(
              Label.of(ccTarget.getLabel()),
              TargetBuildInfo.forCcTarget(artifactInfo, outputInfo.getBuildContext()));
        }
        for (CcToolchainInfo proto : ccInfo.getToolchainsList()) {
          CcToolchain toolchain = CcToolchain.create(proto);
          ccToolchainMap.put(toolchain.id(), toolchain);
        }
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
      Object unused = Uninterruptibles.getUninterruptibly(artifactsCached);
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

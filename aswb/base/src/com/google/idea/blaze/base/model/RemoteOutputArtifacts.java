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
package com.google.idea.blaze.base.model;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A set of {@link RemoteOutputArtifact}s we want to retain a reference to between syncs. */
public final class RemoteOutputArtifacts
    implements ProtoWrapper<ProjectData.RemoteOutputArtifacts> {

  public static RemoteOutputArtifacts fromProjectData(@Nullable BlazeProjectData projectData) {
    return projectData == null ? EMPTY : projectData.getRemoteOutputs();
  }

  public static RemoteOutputArtifacts EMPTY = new RemoteOutputArtifacts(ImmutableMap.of());

  public final ImmutableMap<String, RemoteOutputArtifact> remoteOutputArtifacts;
  private final ImmutableSet<String> configurationMnemonics;

  private RemoteOutputArtifacts(ImmutableMap<String, RemoteOutputArtifact> remoteOutputArtifacts) {
    this.remoteOutputArtifacts = remoteOutputArtifacts;
    this.configurationMnemonics =
        remoteOutputArtifacts.values().stream()
            .map(RemoteOutputArtifacts::parseConfigurationMnemonic)
            .collect(toImmutableSet());
  }

  @Override
  public ProjectData.RemoteOutputArtifacts toProto() {
    ProjectData.RemoteOutputArtifacts.Builder proto =
        ProjectData.RemoteOutputArtifacts.newBuilder();
    remoteOutputArtifacts.values().forEach(a -> proto.addArtifacts(a.toProto()));
    return proto.build();
  }

  public static RemoteOutputArtifacts fromProto(BuildSystemName buildSystemName, ProjectData.RemoteOutputArtifacts proto) {
    ImmutableMap.Builder<String, RemoteOutputArtifact> map = ImmutableMap.builder();
    proto.getArtifactsList().stream()
        .map(it -> RemoteOutputArtifact.fromProto(buildSystemName, it))
        .filter(Objects::nonNull)
        .forEach(a -> map.put(a.getBazelOutRelativePath(), a));
    return new RemoteOutputArtifacts(map.build());
  }

  /**
   * Merges this set of outputs with another set, returning a new {@link RemoteOutputArtifacts}
   * instance.
   */
  public RemoteOutputArtifacts appendNewOutputs(Set<OutputArtifact> outputs) {
    HashMap<String, RemoteOutputArtifact> map = new HashMap<>(remoteOutputArtifacts);
    // more recently built artifacts replace existing ones with the same path
    outputs.forEach(
        a -> {
          String key = a.getBazelOutRelativePath();
          if (!(a instanceof RemoteOutputArtifact)) {
            map.remove(key);
          } else {
            RemoteOutputArtifact newOutput = (RemoteOutputArtifact) a;
            RemoteOutputArtifact other = map.get(key);
            if (other == null || other.getSyncTimeMillis() < newOutput.getSyncTimeMillis()) {
              map.put(key, newOutput);
            }
          }
        });
    return new RemoteOutputArtifacts(ImmutableMap.copyOf(map));
  }

  /**
   * Returns a new {@link RemoteOutputArtifacts} instance containing only the artifacts which should
   * be tracked according to {@link OutputsProvider}.
   */
  public RemoteOutputArtifacts removeUntrackedOutputs(
      TargetMap targets, WorkspaceLanguageSettings settings) {
    List<OutputsProvider> providers =
        Arrays.stream(OutputsProvider.EP_NAME.getExtensions())
            .filter(p -> p.isActive(settings))
            .collect(toImmutableList());
    ImmutableMap<String, RemoteOutputArtifact> tracked =
        targets.targets().stream()
            .flatMap(t -> artifactsToTrack(providers, t))
            .distinct()
            .map(this::findRemoteOutput)
            .filter(Objects::nonNull)
            .distinct()
            .collect(toImmutableMap(RemoteOutputArtifact::getBazelOutRelativePath, o -> o));
    return new RemoteOutputArtifacts(tracked);
  }

  private static Stream<ArtifactLocation> artifactsToTrack(
      List<OutputsProvider> providers, TargetIdeInfo target) {
    List<ArtifactLocation> list = new ArrayList<>();
    for (OutputsProvider provider : providers) {
      Collection<ArtifactLocation> outputs = provider.selectAllRelevantOutputs(target);
      list.addAll(outputs);
    }
    return list.stream().filter(ArtifactLocation::isGenerated);
  }

  public boolean isEmpty() {
    return remoteOutputArtifacts.isEmpty();
  }

  /**
   * Looks for a {@link RemoteOutputArtifact} with a given genfiles-relative path, returning the
   * first such match, or null if none can be found.
   */
  @Nullable
  public RemoteOutputArtifact resolveGenfilesPath(String genfilesRelativePath) {
    return configurationMnemonics.stream()
        .map(m -> findRemoteOutput(String.format("%s/genfiles/%s", m, genfilesRelativePath)))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  public RemoteOutputArtifact findRemoteOutput(ArtifactLocation location) {
    if (location.isSource()) {
      return null;
    }
    String execRootPath = location.getExecutionRootRelativePath();
    if (!execRootPath.startsWith("blaze-out/")) {
      return null;
    }
    return findRemoteOutput(execRootPath.substring("blaze-out/".length()));
  }

  @Nullable
  public RemoteOutputArtifact findRemoteOutput(String blazeOutRelativePath) {
    // first try the exact path (forwards compatibility with a future BEP format)
    RemoteOutputArtifact file = remoteOutputArtifacts.get(blazeOutRelativePath);
    if (file != null) {
      return file;
    }
    return findAlternatePathFormat(blazeOutRelativePath);
  }

  private static final ImmutableSet<String> POSSIBLY_MISSING_PATH_COMPONENTS =
      ImmutableSet.of("bin", "genfiles", "testlogs");

  @Nullable
  private RemoteOutputArtifact findAlternatePathFormat(String path) {
    // temporary code until we can get the full blaze-out-relative path from BEP
    int index = path.indexOf('/');
    int nextIndex = path.indexOf('/', index + 1);
    if (nextIndex == -1) {
      return null;
    }
    String secondPathComponent = path.substring(index + 1, nextIndex);
    if (!POSSIBLY_MISSING_PATH_COMPONENTS.contains(secondPathComponent)) {
      return null;
    }
    String alternatePath =
        String.format("%s%s", path.substring(0, index), path.substring(nextIndex));
    return remoteOutputArtifacts.get(alternatePath);
  }

  private static String parseConfigurationMnemonic(RemoteOutputArtifact output) {
    return BlazeConfigurationHandler.getConfigurationMnemonic(output.getBazelOutRelativePath());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(remoteOutputArtifacts, ((RemoteOutputArtifacts) o).remoteOutputArtifacts);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(remoteOutputArtifacts);
  }
}

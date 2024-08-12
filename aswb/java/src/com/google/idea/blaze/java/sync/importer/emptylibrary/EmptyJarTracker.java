/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer.emptylibrary;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.model.ProjectData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.filecache.ArtifactStateProtoConverter;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Class to track which JARs are empty. Each JAR is represented by it's artifact state, which is
 * `blobId` for remote artifacts and timestamp for local artifacts.
 *
 * <p>An object of this class is serialized with {@link
 * com.google.idea.blaze.java.sync.model.BlazeJavaImportResult}, which allows us to reevaluate only
 * the JARs that have updated since last sync. See {@link EmptyLibrary#removeEmptyLibraries} to see
 * how caching works.
 */
public class EmptyJarTracker implements ProtoWrapper<ProjectData.EmptyJarTracker> {

  // Map to track whether an artifact is empty or not
  ImmutableMap<ArtifactState, Boolean> stateToEmptyStatus;

  private EmptyJarTracker(Map<ArtifactState, Boolean> stateToEmptyStatus) {
    this.stateToEmptyStatus = ImmutableMap.copyOf(stateToEmptyStatus);
  }

  /**
   * Returns true if the artifact corresponding to passed `ArtifactState` is known in {@link
   * #stateToEmptyStatus} to be empty. Returns false if the Artifact is known to be non-empty, or is
   * not present in {@link #stateToEmptyStatus}
   */
  public boolean isKnownEmpty(ArtifactState artifactState) {
    return stateToEmptyStatus.getOrDefault(artifactState, false);
  }

  public ImmutableMap<String, ArtifactState> getState() {
    return stateToEmptyStatus.keySet().stream()
        .collect(ImmutableMap.toImmutableMap(ArtifactState::getKey, s -> s));
  }

  @Override
  public ProjectData.EmptyJarTracker toProto() {
    ProjectData.EmptyJarTracker.Builder trackerBuilder = ProjectData.EmptyJarTracker.newBuilder();
    stateToEmptyStatus.entrySet().stream()
        .map(
            e ->
                ProjectData.EmptyJarTracker.Entry.newBuilder()
                    .setArtifact(ArtifactStateProtoConverter.toProto(e.getKey()))
                    .setIsEmpty(e.getValue()))
        .forEach(trackerBuilder::addEntries);
    return trackerBuilder.build();
  }

  public static EmptyJarTracker fromProto(ProjectData.EmptyJarTracker proto) {
    if (proto.getEntriesCount() == 0) {
      return EmptyJarTracker.builder().build();
    }

    Builder emptyJarTrackerBuilder = builder();
    for (ProjectData.EmptyJarTracker.Entry entry : proto.getEntriesList()) {
      ArtifactState artifactState = ArtifactStateProtoConverter.fromProto(entry.getArtifact());
      if (artifactState == null) {
        continue;
      }
      emptyJarTrackerBuilder.addEntry(artifactState, entry.getIsEmpty());
    }

    return emptyJarTrackerBuilder.build();
  }

  /**
   * Returns an instance of EmptyJarTracker from `syncState`. Returns an empty JAR tracker
   * `syncState` is null, or does not contain empty JAR tracker.
   */
  static EmptyJarTracker getEmptyJarTracker(@Nullable SyncState syncState) {
    EmptyJarTracker oldTracker = null;
    if (syncState != null) {
      BlazeJavaSyncData syncData = syncState.get(BlazeJavaSyncData.class);
      if (syncData != null) {
        oldTracker = syncData.getImportResult().emptyJarTracker;
      }
    }
    // If we couldn't find a tracker from last sync, instantiate with an empty tracker, which
    // corresponds to nothing being cached.
    oldTracker = oldTracker == null ? EmptyJarTracker.builder().build() : oldTracker;
    return oldTracker;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(stateToEmptyStatus);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EmptyJarTracker)) {
      return false;
    }

    EmptyJarTracker that = (EmptyJarTracker) o;
    return Objects.equals(this.stateToEmptyStatus, that.stateToEmptyStatus);
  }

  /** Builder for {@link EmptyJarTracker} */
  public static class Builder {
    private final Map<ArtifactState, Boolean> trackerBuilder = new HashMap<>();

    @CanIgnoreReturnValue
    public Builder addEntry(ArtifactState artifactState, Boolean isEmpty) {
      // Remove currently present entry to ensure the updated artifact is used as key
      // No-op if artifactState is not present
      trackerBuilder.remove(artifactState);
      trackerBuilder.put(artifactState, isEmpty);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllEntries(Map<ArtifactState, Boolean> map) {
      map.forEach(this::addEntry);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllEntries(EmptyJarTracker other) {
      return addAllEntries(other.stateToEmptyStatus);
    }

    @CanIgnoreReturnValue
    public Builder removeEntries(ImmutableSet<ArtifactState> removedOutputs) {
      removedOutputs.forEach(trackerBuilder::remove);
      return this;
    }

    public EmptyJarTracker build() {
      return new EmptyJarTracker(ImmutableMap.copyOf(trackerBuilder));
    }
  }
}

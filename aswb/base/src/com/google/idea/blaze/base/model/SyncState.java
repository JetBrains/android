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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/** Used to save arbitrary state with the sync task. */
// SyncData#getClass omits generic types and hence we need to omit the generic type for the map key.
@SuppressWarnings("rawtypes")
public final class SyncState implements ProtoWrapper<ProjectData.SyncState> {
  private final ImmutableMap<Class<? extends SyncData>, SyncData<?>> syncStateMap;

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends SyncData<?>> T get(Class<T> klass) {
    return (T) syncStateMap.get(klass);
  }

  public <T extends SyncData<?>> Optional<T> getOptional(Class<T> klass) {
    return Optional.ofNullable(get(klass));
  }

  /** Builder for a sync state */
  public static class Builder {
    ImmutableMap.Builder<Class<? extends SyncData>, SyncData<?>> syncStateMap =
        ImmutableMap.builder();

    @CanIgnoreReturnValue
    public Builder put(SyncData<?> instance) {
      syncStateMap.put(instance.getClass(), instance);
      return this;
    }

    public SyncState build() {
      return new SyncState(syncStateMap.build());
    }
  }

  SyncState(ImmutableMap<Class<? extends SyncData>, SyncData<?>> syncStateMap) {
    this.syncStateMap = syncStateMap;
  }

  static SyncState fromProto(ProjectData.SyncState proto) {
    return new SyncState(SyncData.extract(proto));
  }

  @Override
  public ProjectData.SyncState toProto() {
    ProjectData.SyncState.Builder builder = ProjectData.SyncState.newBuilder();
    syncStateMap.values().forEach(syncData -> syncData.insert(builder));
    return builder.build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SyncState syncState = (SyncState) o;
    return Objects.equals(syncStateMap, syncState.syncStateMap);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(syncStateMap);
  }
}

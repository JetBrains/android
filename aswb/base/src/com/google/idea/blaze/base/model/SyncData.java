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

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.protobuf.Message;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Arbitrary data to be stored in the {@link SyncState}. */
public interface SyncData<P extends Message> extends ProtoWrapper<P> {
  void insert(ProjectData.SyncState.Builder builder);

  // SyncData#getClass omits generic types and hence we need to omit the generic type for the map
  // key.
  @SuppressWarnings("rawtypes")
  @Nullable
  static ImmutableMap<Class<? extends SyncData>, SyncData<?>> extract(
      ProjectData.SyncState syncState) {
    return Arrays.stream(Extractor.EP_NAME.getExtensions())
        .map(extractor -> extractor.extract(syncState))
        .filter(Objects::nonNull)
        .collect(ImmutableMap.toImmutableMap(SyncData::getClass, Functions.identity()));
  }

  /** Deserialize sync data from protobuf message. */
  interface Extractor<S extends SyncData<?>> {
    ExtensionPointName<Extractor<?>> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.SyncDataExtractor");

    @Nullable
    S extract(ProjectData.SyncState syncState);
  }
}

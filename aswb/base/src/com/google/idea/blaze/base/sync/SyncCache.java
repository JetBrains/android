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
package com.google.idea.blaze.base.sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import java.util.Map;
import javax.annotation.Nullable;

/** Computes a cache on the project data. */
public class SyncCache {
  /** Computes a value based on the sync project data. */
  public interface SyncCacheComputable<T> {
    @Nullable
    T compute(Project project, BlazeProjectData projectData);
  }

  private final Project project;
  private final Map<Object, Object> cache = Maps.newHashMap();

  public SyncCache(Project project) {
    this.project = project;
  }

  public static SyncCache getInstance(Project project) {
    return project.getService(SyncCache.class);
  }

  /** Computes a value derived from the sync project data and caches it until the next sync. */
  @Nullable
  @SuppressWarnings("unchecked")
  public synchronized <T> T get(Object key, SyncCacheComputable<T> computable) {
    if (cache.containsKey(key)) {
      return (T) cache.get(key);
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    T value = computable.compute(project, blazeProjectData);
    cache.put(key, value);
    return value;
  }

  @VisibleForTesting
  public synchronized void clear() {
    cache.clear();
  }

  static class ClearSyncCache implements SyncListener {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        ImmutableSet<Integer> buildIds,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      SyncCache syncCache = getInstance(project);
      syncCache.clear();
    }
  }
}

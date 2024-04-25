/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Wrapper around a {@link ResourceTable} that:
 *
 * <ul>
 *   <li>May compute cells in the table on-demand.
 *   <li>May change in the background, if underlying files or other sources of data have changed.
 *       Because of that access should be synchronized on the {@code ITEM_MAP_LOCK} object.
 * </ul>
 */
public abstract class AbstractResourceRepositoryWithLocking extends AbstractResourceRepository {
  /**
   * The lock used to protect map access.
   *
   * <p>In the IDE, this needs to be obtained <b>AFTER</b> the IDE read/write lock, to avoid
   * deadlocks (most readers of the repository system execute in a read action, so obtaining the
   * locks in opposite order results in deadlocks).
   */
  public static final Object ITEM_MAP_LOCK = new Object();

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @Nullable
  protected abstract ListMultimap<String, ResourceItem> getMap(
    @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType);

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @Override
  @NonNull
  protected ListMultimap<String, ResourceItem> getResourcesInternal(
      @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
    ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType);
    return map == null ? ImmutableListMultimap.of() : map;
  }

  @Override
  @NonNull
  public List<ResourceItem> getResources(@NonNull ResourceNamespace namespace,
                                         @NonNull ResourceType resourceType,
                                         @NonNull String resourceName) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResources(namespace, resourceType, resourceName);
    }
  }

  @Override
  @NonNull
  public List<ResourceItem> getResources(@NonNull ResourceNamespace namespace,
                                         @NonNull ResourceType resourceType,
                                         @NonNull Predicate<ResourceItem> filter) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResources(namespace, resourceType, filter);
    }
  }

  @Override
  @NonNull
  public ListMultimap<String, ResourceItem> getResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResources(namespace, resourceType);
    }
  }

  @Override
  @NonNull
  public Set<String> getResourceNames(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
    synchronized (ITEM_MAP_LOCK) {
      ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType);
      return map == null ? ImmutableSet.of() : ImmutableSet.copyOf(map.keySet());
    }
  }

  @Override
  public boolean hasResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType, @NonNull String resourceName) {
    synchronized (ITEM_MAP_LOCK) {
      return super.hasResources(namespace, resourceType, resourceName);
    }
  }

  @Override
  public boolean hasResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
    synchronized (ITEM_MAP_LOCK) {
      return super.hasResources(namespace, resourceType);
    }
  }

  @Override
  @NonNull
  public Set<ResourceType> getResourceTypes(@NonNull ResourceNamespace namespace) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResourceTypes(namespace);
    }
  }
}

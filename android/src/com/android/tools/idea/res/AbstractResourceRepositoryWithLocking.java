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
package com.android.tools.idea.res;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Returns the fully computed {@link ResourceTable} for this repository.
   *
   * <p>The returned object should be accessed only while holding {@link #ITEM_MAP_LOCK}.
   */
  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @NotNull
  protected abstract ResourceTable getFullTable();

  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @Nullable
  protected abstract ListMultimap<String, ResourceItem> getMap(
      @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType, boolean create);

  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @NotNull
  protected final ListMultimap<String, ResourceItem> getOrCreateMap(
      @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    //noinspection ConstantConditions - won't return null if create is false.
    return getMap(namespace, resourceType, true);
  }

  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @Override
  @NotNull
  protected ListMultimap<String, ResourceItem> getResourcesInternal(
    @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
    return map == null ? ImmutableListMultimap.of() : map;
  }

  @Override
  @NotNull
  public List<ResourceItem> getResources(@NotNull ResourceNamespace namespace,
                                         @NotNull ResourceType resourceType,
                                         @NotNull String resourceName) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResources(namespace, resourceType, resourceName);
    }
  }

  @Override
  @NotNull
  public List<ResourceItem> getResources(@NotNull ResourceNamespace namespace,
                                         @NotNull ResourceType resourceType,
                                         @NotNull Predicate<ResourceItem> filter) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResources(namespace, resourceType, filter);
    }
  }

  @Override
  @NotNull
  public ListMultimap<String, ResourceItem> getResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResources(namespace, resourceType);
    }
  }

  @Override
  public void accept(@NotNull ResourceVisitor visitor) {
    synchronized (ITEM_MAP_LOCK) {
      for (Map.Entry<ResourceNamespace, Map<ResourceType, ListMultimap<String, ResourceItem>>> entry : getFullTable().rowMap().entrySet()) {
        if (visitor.shouldVisitNamespace(entry.getKey())) {
          if (acceptByResources(entry.getValue(), visitor) == ResourceVisitor.VisitResult.ABORT) {
            return;
          }
        }
      }
    }
  }

  @Override
  public boolean hasResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType, @NotNull String resourceName) {
    synchronized (ITEM_MAP_LOCK) {
      return super.hasResources(namespace, resourceType, resourceName);
    }
  }

  @Override
  public boolean hasResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType) {
    synchronized (ITEM_MAP_LOCK) {
      return super.hasResources(namespace, resourceType);
    }
  }

  @Override
  @NotNull
  public Set<ResourceType> getResourceTypes(@NotNull ResourceNamespace namespace) {
    synchronized (ITEM_MAP_LOCK) {
      return super.getResourceTypes(namespace);
    }
  }
}

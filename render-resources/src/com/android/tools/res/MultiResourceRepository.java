/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.aar.AarResourceRepository;
import com.android.tools.environment.Logger;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A super class for several of the other repositories. Its only purpose is to be able to combine
 * multiple resource repositories and expose it as a single one, applying the “override” semantics
 * of resources: earlier children defining the same resource namespace/type/name combination will
 * replace/hide any subsequent definitions of the same resource.
 *
 * <p>In the resource repository hierarchy, MultiResourceRepository is an internal node, never a leaf.
 */
@SuppressWarnings("InstanceGuardedByStatic") // TODO: The whole locking scheme for resource repositories needs to be reworked.
public abstract class MultiResourceRepository<T> extends LocalResourceRepository<T> {
  private static final Logger LOG = Logger.getInstance(MultiResourceRepository.class);

  @GuardedBy("ITEM_MAP_LOCK")
  @NonNull private ImmutableList<LocalResourceRepository<T>> myLocalResources = ImmutableList.of();
  /** A concatenation of {@link #myLocalResources} and library resources. */
  @GuardedBy("ITEM_MAP_LOCK")
  @NonNull private ImmutableList<ResourceRepository> myChildren = ImmutableList.of();
  /** Leaf resource repositories keyed by namespace. */
  @GuardedBy("ITEM_MAP_LOCK")
  @NonNull private ImmutableListMultimap<ResourceNamespace, SingleNamespaceResourceRepository> myLeafsByNamespace =
      ImmutableListMultimap.of();
  /** Contained single-namespace resource repositories keyed by namespace. */
  @GuardedBy("ITEM_MAP_LOCK")
  @NonNull private ImmutableListMultimap<ResourceNamespace, SingleNamespaceResourceRepository> myRepositoriesByNamespace =
      ImmutableListMultimap.of();

  @GuardedBy("ITEM_MAP_LOCK")
  @NonNull private PerConfigResourceMap.ResourceItemComparator myResourceComparator =
      new PerConfigResourceMap.ResourceItemComparator(ImmutableList.of());

  @GuardedBy("ITEM_MAP_LOCK")
  private long[] myModificationCounts;

  @GuardedBy("ITEM_MAP_LOCK")
  private final ResourceTable myCachedMaps = new ResourceTable();

  /** Names of resources from local leaf repositories. */
  @GuardedBy("ITEM_MAP_LOCK")
  private final Table<SingleNamespaceResourceRepository, ResourceType, Set<String>> myResourceNames =
      Tables.newCustomTable(new HashMap<>(), () -> Maps.newEnumMap(ResourceType.class));

  /** Describes groups of resources that are out of date in {@link #myCachedMaps}. */
  @GuardedBy("ITEM_MAP_LOCK")
  private final Table<ResourceNamespace, ResourceType, Set<SingleNamespaceResourceRepository>> myUnreconciledResources =
      Tables.newCustomTable(new HashMap<>(), () -> Maps.newEnumMap(ResourceType.class));

  protected MultiResourceRepository(@NonNull String displayName) {
    super(displayName);
  }

  protected void setChildren(@NonNull List<? extends LocalResourceRepository<T>> localResources,
                             @NonNull Collection<? extends AarResourceRepository> libraryResources,
                             @NonNull Collection<? extends ResourceRepository> otherResources) {
    synchronized (ITEM_MAP_LOCK) {
      release();
      setModificationCount(ourModificationCounter.incrementAndGet());
      myLocalResources = ImmutableList.copyOf(localResources);
      int size = myLocalResources.size() + libraryResources.size() + otherResources.size();
      myChildren = ImmutableList.<ResourceRepository>builderWithExpectedSize(size)
          .addAll(myLocalResources).addAll(libraryResources).addAll(otherResources).build();

      ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> mapBuilder = ImmutableListMultimap.builder();
      computeLeafs(this, mapBuilder);
      myLeafsByNamespace = mapBuilder.build();

      mapBuilder = ImmutableListMultimap.builder();
      computeNamespaceMap(this, mapBuilder);
      myRepositoriesByNamespace = mapBuilder.build();

      myResourceComparator = new PerConfigResourceMap.ResourceItemComparator(myLeafsByNamespace.values());

      myModificationCounts = new long[localResources.size()];
      if (localResources.size() == 1) {
        // Make sure that the modification count of the child and the parent are same. This is
        // done so that we can return child's modification count, instead of ours.
        LocalResourceRepository<T> child = localResources.get(0);
        child.setModificationCount(getModificationCount());
      }
      int i = 0;
      for (LocalResourceRepository<T> child : myLocalResources) {
        child.addParent(this);
        myModificationCounts[i++] = child.getModificationCount();
      }
      myCachedMaps.clear();

      invalidateParentCaches();
    }
  }

  abstract protected void refreshChildren();

  public void onChildReset() {
    refreshChildren();
  }

  @GuardedBy("ITEM_MAP_LOCK")
  private static <T> void computeLeafs(@NonNull ResourceRepository repository,
                                   @NonNull ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> result) {
    if (repository instanceof MultiResourceRepository) {
      for (ResourceRepository child : ((MultiResourceRepository<T>)repository).myChildren) {
        computeLeafs(child, result);
      }
    } else {
      for (SingleNamespaceResourceRepository resourceRepository : repository.getLeafResourceRepositories()) {
        result.put(resourceRepository.getNamespace(), resourceRepository);
      }
    }
  }

  @GuardedBy("ITEM_MAP_LOCK")
  private static <T> void computeNamespaceMap(
      @NonNull ResourceRepository repository,
      @NonNull ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> result) {
    if (repository instanceof SingleNamespaceResourceRepository) {
      SingleNamespaceResourceRepository singleNamespaceRepository = (SingleNamespaceResourceRepository)repository;
      ResourceNamespace namespace = singleNamespaceRepository.getNamespace();
      result.put(namespace, singleNamespaceRepository);
    }
    else if (repository instanceof MultiResourceRepository) {
      for (ResourceRepository child : ((MultiResourceRepository<T>)repository).myChildren) {
        computeNamespaceMap(child, result);
      }
    }
  }

  public ImmutableList<LocalResourceRepository<T>> getLocalResources() {
    synchronized (ITEM_MAP_LOCK) {
      return myLocalResources;
    }
  }

  @NonNull
  public final List<ResourceRepository> getChildren() {
    synchronized (ITEM_MAP_LOCK) {
      return myChildren;
    }
  }

  /**
   * Returns resource repositories for the given namespace. In case of nested single-namespace repositories only the outermost
   * repositories are returned. Collectively the returned repositories are guaranteed to contain all resources in the given namespace
   * contained in this repository.
   *
   * @param namespace the namespace to return resource repositories for
   * @return a list of namespaces for the given namespace
   */
  @NonNull
  public final List<SingleNamespaceResourceRepository> getRepositoriesForNamespace(@NonNull ResourceNamespace namespace) {
    synchronized (ITEM_MAP_LOCK) {
      return myRepositoriesByNamespace.get(namespace);
    }
  }

  @Override
  public long getModificationCount() {
    synchronized (ITEM_MAP_LOCK) {
      if (myLocalResources.size() == 1) {
        return myLocalResources.get(0).getModificationCount();
      }

      // See if any of the delegates have changed.
      boolean changed = false;
      for (int i = 0; i < myLocalResources.size(); i++) {
        LocalResourceRepository<T> child = myLocalResources.get(i);
        long rev = child.getModificationCount();
        if (rev != myModificationCounts[i]) {
          myModificationCounts[i] = rev;
          changed = true;
        }
      }

      if (changed) {
        setModificationCount(ourModificationCounter.incrementAndGet());
      }

      return super.getModificationCount();
    }
  }

  @Override
  @NonNull
  public Set<ResourceNamespace> getNamespaces() {
    synchronized (ITEM_MAP_LOCK) {
      return myRepositoriesByNamespace.keySet();
    }
  }

  @Override
  @NonNull
  public ResourceVisitor.VisitResult accept(@NonNull ResourceVisitor visitor) {
    synchronized (ITEM_MAP_LOCK) {
      for (ResourceNamespace namespace : getNamespaces()) {
        if (visitor.shouldVisitNamespace(namespace)) {
          for (ResourceType type : ResourceType.values()) {
            if (visitor.shouldVisitResourceType(type)) {
              ListMultimap<String, ResourceItem> map = getMap(namespace, type);
              if (map != null) {
                for (ResourceItem item : map.values()) {
                  if (visitor.visit(item) == ResourceVisitor.VisitResult.ABORT) {
                    return ResourceVisitor.VisitResult.ABORT;
                  }
                }
              }
            }
          }
        }
      }
    }

    return ResourceVisitor.VisitResult.CONTINUE;
  }

  @GuardedBy("ITEM_MAP_LOCK")
  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
    ImmutableList<SingleNamespaceResourceRepository> repositoriesForNamespace = myLeafsByNamespace.get(namespace);
    if (repositoriesForNamespace.size() == 1) {
      SingleNamespaceResourceRepository repository = repositoriesForNamespace.get(0);
      return getResourcesUnderLock(repository, namespace, type);
    }

    ListMultimap<String, ResourceItem> map = myCachedMaps.get(namespace, type);
    Set<SingleNamespaceResourceRepository> unreconciledRepositories = null;
    if (map != null) {
      unreconciledRepositories = myUnreconciledResources.get(namespace, type);
      if (unreconciledRepositories == null) {
        return map;
      }
    }

    // Merge all items of the given type.
    Stopwatch stopwatch = LOG.isDebugEnabled() ? Stopwatch.createStarted() : null;

    if (map == null) {
      for (SingleNamespaceResourceRepository repository : repositoriesForNamespace) {
        ListMultimap<String, ResourceItem> items = getResourcesUnderLock(repository, namespace, type);
        if (!items.isEmpty()) {
          if (map == null) {
            // Create a new map.
            // We only add a duplicate item if there isn't an item with the same qualifiers, and it
            // is not a styleable or an id. Styleables and ids are allowed to be defined in multiple
            // places even with the same qualifiers.
            map = type == ResourceType.STYLEABLE || type == ResourceType.ID ?
                  ArrayListMultimap.create() : new PerConfigResourceMap(myResourceComparator);
            myCachedMaps.put(namespace, type, map);
          }
          map.putAll(items);

          if (repository instanceof LocalResourceRepository) {
            myResourceNames.put(repository, type, ImmutableSet.copyOf(items.keySet()));
          }
        }
      }
    }
    else {
      // Update a partially out of date map.
      for (SingleNamespaceResourceRepository unreconciledRepository : unreconciledRepositories) {
        // Delete all resources that belonged to unreconciledRepository.
        Predicate<ResourceItem> filter = item -> item.getRepository().equals(unreconciledRepository);
        Set<String> names = myResourceNames.get(unreconciledRepository, type);
        if (names != null) {
          PerConfigResourceMap perConfigMap = map instanceof PerConfigResourceMap ? (PerConfigResourceMap)map : null;
          for (String name : names) {
            if (perConfigMap != null) {
              perConfigMap.removeIf(name, filter);
            }
            else {
              List<ResourceItem> items = map.get(name);
              items.removeIf(filter);
              if (items.isEmpty()) {
                map.removeAll(name);
              }
            }
          }
        }
        // Add all resources from unreconciledRepository.
        ListMultimap<String, ResourceItem> unreconciledResources = getResourcesUnderLock(unreconciledRepository, namespace, type);
        map.putAll(unreconciledResources);

        assert unreconciledRepository instanceof LocalResourceRepository;
        myResourceNames.put(unreconciledRepository, type, ImmutableSet.copyOf(unreconciledResources.keySet()));
        if (map.isEmpty()) {
          myCachedMaps.remove(namespace, type);
        }
      }

      myUnreconciledResources.remove(namespace, type);
    }

    if (stopwatch != null) {
      LOG.debug(String.format(Locale.US,
                              "Merged %d resources of type %s in %s for %s.",
                              map == null ? 0 : map.size(),
                              type,
                              stopwatch,
                              getClass().getSimpleName()));
    }

    return map;
  }

  @GuardedBy("ITEM_MAP_LOCK")
  @NonNull
  private static <T> ListMultimap<String, ResourceItem> getResourcesUnderLock(@NonNull SingleNamespaceResourceRepository repository,
                                                                              @NonNull ResourceNamespace namespace,
                                                                              @NonNull ResourceType type) {
    ListMultimap<String, ResourceItem> map;
    if (repository instanceof LocalResourceRepository) {
      map = ((LocalResourceRepository<T>)repository).getMapPackageAccessible(namespace, type);
      return map == null ? ImmutableListMultimap.of() : map;
    }
    return repository.getResources(namespace, type);
  }

  protected final void release() {
    synchronized (ITEM_MAP_LOCK) {
      for (LocalResourceRepository<T> child : myLocalResources) {
        child.removeParent(this);
      }
    }
  }

  /**
   * Notifies this repository that all its caches are no longer valid.
   */
  @GuardedBy("ITEM_MAP_LOCK")
  public void invalidateCache() {
    clearCachedData();
    setModificationCount(ourModificationCounter.incrementAndGet());

    invalidateParentCaches();
  }

  @GuardedBy("ITEM_MAP_LOCK")
  private void clearCachedData() {
    myCachedMaps.clear();
    myResourceNames.clear();
    myUnreconciledResources.clear();
  }

  protected final void onLowMemory() {
    synchronized (ITEM_MAP_LOCK) {
      clearCachedData();
    }
    LOG.warn(getDisplayName() + ": Cached data cleared due to low memory");
  }

  /**
   * Notifies this delegating repository that the given dependent repository has invalidated
   * resources of the given types.
   */
  @GuardedBy("ITEM_MAP_LOCK")
  public void invalidateCache(@NonNull SingleNamespaceResourceRepository repository, @NonNull ResourceType... types) {
    ResourceNamespace namespace = repository.getNamespace();

    // Since myLeafsByNamespace updates are not atomic with respect to grandchildren updates, it is
    // possible that the repository that triggered cache invalidation is not in myLeafsByNamespace.
    // In such a case we don't need to do anything.
    ImmutableList<SingleNamespaceResourceRepository> leafs = myLeafsByNamespace.get(namespace);
    if (leafs.contains(repository)) {
      // Update myUnreconciledResources only if myCachedMaps is used for this namespace.
      if (leafs.size() != 1) {
        for (ResourceType type : types) {
          if (myCachedMaps.get(namespace, type) != null) {
            Set<SingleNamespaceResourceRepository> repositories = myUnreconciledResources.get(namespace, type);
            if (repositories == null) {
              repositories = new HashSet<>();
              myUnreconciledResources.put(namespace, type, repositories);
            }
            repositories.add(repository);
          }
        }

        setModificationCount(ourModificationCounter.incrementAndGet());
      }

      invalidateParentCaches(repository, types);
    }
  }

  @Override
  public void invokeAfterPendingUpdatesFinish(@NonNull Executor executor, @NonNull Runnable callback) {
    List<LocalResourceRepository<T>> repositories = getLocalResources();
    AtomicInteger count = new AtomicInteger(repositories.size());
    for (LocalResourceRepository<T> childRepository : repositories) {
      childRepository.invokeAfterPendingUpdatesFinish(MoreExecutors.directExecutor(), () -> {
        if (count.decrementAndGet() == 0) {
          executor.execute(callback);
        }
      });
    }
  }

  @Override
  @NonNull
  protected Set<T> computeResourceDirs() {
    synchronized (ITEM_MAP_LOCK) {
      Set<T> result = new HashSet<>();
      for (LocalResourceRepository<T> resourceRepository : myLocalResources) {
        result.addAll(resourceRepository.computeResourceDirs());
      }
      return result;
    }
  }

  @Override
  @NonNull
  public Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories() {
    synchronized (ITEM_MAP_LOCK) {
      return myLeafsByNamespace.values();
    }
  }

  @VisibleForTesting
  @Override
  public int getFileRescans() {
    synchronized (ITEM_MAP_LOCK) {
      int count = 0;
      for (LocalResourceRepository<T> resourceRepository : myLocalResources) {
        count += resourceRepository.getFileRescans();
      }
      return count;
    }
  }
}

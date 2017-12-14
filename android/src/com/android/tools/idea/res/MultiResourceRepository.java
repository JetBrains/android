/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The  is a super class for several of the other repositories; it’s not really used on its own. Its only purpose is to be able to combine
 * multiple resource repositories and expose it as a single one, applying the “override” semantics of resources: later children defining the
 * same resource type+name combination will replace/hide any previous definitions of the same resource.
 *
 * <p>In the resource repository hierarchy, the MultiResourceRepository is an internal node, never a leaf.
 */
@SuppressWarnings("InstanceGuardedByStatic") // TODO: The whole locking scheme for resource repositories needs to be reworked.
public abstract class MultiResourceRepository extends LocalResourceRepository {
  @GuardedBy("ITEM_MAP_LOCK")
  private List<? extends LocalResourceRepository> myChildren;

  @GuardedBy("ITEM_MAP_LOCK")
  private long[] myModificationCounts;

  @GuardedBy("ITEM_MAP_LOCK")
  private ResourceTable myFullTable;

  @GuardedBy("ITEM_MAP_LOCK")
  private Set<ResourceNamespace> myCachedNamespaces;

  @GuardedBy("ITEM_MAP_LOCK")
  private final ResourceTable myCachedMaps = new ResourceTable();

  @GuardedBy("ITEM_MAP_LOCK")
  private final Map<ResourceType, Boolean> myCachedHasResourcesOfType = Maps.newEnumMap(ResourceType.class);

  @GuardedBy("ITEM_MAP_LOCK")
  private Map<String, DataBindingInfo> myDataBindingResourceFiles = Maps.newHashMap();

  @GuardedBy("ITEM_MAP_LOCK")
  private long myDataBindingResourceFilesModificationCount = Long.MIN_VALUE;

  MultiResourceRepository(@NotNull String displayName, @NotNull List<? extends LocalResourceRepository> children) {
    super(displayName);
    setChildren(children);
  }

  protected void setChildren(@NotNull List<? extends LocalResourceRepository> children) {
    synchronized (ITEM_MAP_LOCK) {
      if (myChildren != null) {
        for (int i = myChildren.size() - 1; i >= 0; i--) {
          LocalResourceRepository resources = myChildren.get(i);
          resources.removeParent(this);
        }
      }
      setModificationCount(ourModificationCounter.incrementAndGet());
      myChildren = children;
      myModificationCounts = new long[children.size()];
      if (children.size() == 1) {
        // Make sure that the modification count of the child and the parent are same. This is
        // done so that we can return child's modification count, instead of ours.
        LocalResourceRepository child = children.get(0);
        child.setModificationCount(getModificationCount());
      }
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        LocalResourceRepository resources = myChildren.get(i);
        resources.addParent(this);
        myModificationCounts[i] = resources.getModificationCount();
      }
      myFullTable = null;
      myCachedMaps.clear();
      myCachedHasResourcesOfType.clear();
    }

    invalidateParentCaches();
  }

  public List<? extends LocalResourceRepository> getChildren() {
    synchronized (ITEM_MAP_LOCK) {
      return ImmutableList.copyOf(myChildren);
    }
  }

  @Override
  public long getModificationCount() {
    synchronized (ITEM_MAP_LOCK) {
      if (myChildren.size() == 1) {
        return myChildren.get(0).getModificationCount();
      }

      // See if any of the delegates have changed
      boolean changed = false;
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        LocalResourceRepository resources = myChildren.get(i);
        long rev = resources.getModificationCount();
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

  @Nullable
  @Override
  public DataBindingInfo getDataBindingInfoForLayout(String layoutName) {
    synchronized (ITEM_MAP_LOCK) {
      for (LocalResourceRepository child : myChildren) {
        DataBindingInfo info = child.getDataBindingInfoForLayout(layoutName);
        if (info != null) {
          return info;
        }
      }
      return null;
    }
  }

  @NotNull
  @Override
  public Map<String, DataBindingInfo> getDataBindingResourceFiles() {
    synchronized (ITEM_MAP_LOCK) {
      long modificationCount = getModificationCount();
      if (myDataBindingResourceFilesModificationCount == modificationCount) {
        return myDataBindingResourceFiles;
      }
      Map<String, DataBindingInfo> selected = Maps.newHashMap();
      for (LocalResourceRepository child : myChildren) {
        Map<String, DataBindingInfo> childFiles = child.getDataBindingResourceFiles();
        if (childFiles != null) {
          selected.putAll(childFiles);
        }
      }
      myDataBindingResourceFiles = Collections.unmodifiableMap(selected);
      myDataBindingResourceFilesModificationCount = modificationCount;
      return myDataBindingResourceFiles;
    }
  }

  @NotNull
  @Override
  public synchronized Set<ResourceNamespace> getNamespaces() {
    synchronized (ITEM_MAP_LOCK) {
      if (myCachedNamespaces == null) {
        if (myChildren.size() == 1) {
          myCachedNamespaces = myChildren.get(0).getNamespaces();
        } else {
          myCachedNamespaces = new SmartHashSet<>();
          for (LocalResourceRepository child : myChildren) {
            myCachedNamespaces.addAll(child.getNamespaces());
          }
        }
      }

      return myCachedNamespaces;
    }
  }

  @NonNull
  @Override
  protected ResourceTable getFullTable() {
    synchronized (ITEM_MAP_LOCK) {
      if (myFullTable == null) {
        if (myChildren.size() == 1) {
          myFullTable = myChildren.get(0).getItems();
        }
        else {
          myFullTable = new ResourceTable();
          for (ResourceNamespace namespace : getNamespaces()) {
            for (ResourceType type : ResourceType.values()) {
              ListMultimap<String, ResourceItem> map = getMap(namespace, type, false);
              if (map != null) {
                myFullTable.put(namespace, type, map);
              }
            }
          }
        }
      }

      return myFullTable;
    }
  }

  @Nullable
  @Override
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace,
                                                      @NonNull ResourceType type,
                                                      boolean create) {
    synchronized (ITEM_MAP_LOCK) {
      // Should I assert !create here? If we try to manipulate the cache it won't work right...
      ListMultimap<String, ResourceItem> map = myCachedMaps.get(namespace, type);
      if (map != null) {
        return map;
      }

      if (myChildren.size() == 1) {
        LocalResourceRepository child = myChildren.get(0);
        if (child instanceof MultiResourceRepository) {
          return ((MultiResourceRepository)child).getMap(namespace, type);
        }
        return child.getItems().get(namespace, type);
      }

      map = ArrayListMultimap.create();
      Set<LocalResourceRepository> visited = Sets.newHashSet();
      SetMultimap<String, String> seenQualifiers = HashMultimap.create();
      // Merge all items of the given type
      merge(visited, namespace, type, seenQualifiers, map);

      myCachedMaps.put(namespace, type, map);

      return map;
    }
  }

  @Override
  protected void doMerge(@NotNull Set<LocalResourceRepository> visited,
                         @NotNull ResourceNamespace namespace,
                         @NotNull ResourceType type,
                         @NotNull SetMultimap<String, String> seenQualifiers,
                         @NotNull ListMultimap<String, ResourceItem> result) {
    synchronized (ITEM_MAP_LOCK) {
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        myChildren.get(i).merge(visited, namespace, type, seenQualifiers, result);
      }
    }
  }

  @Override
  public boolean hasResourcesOfType(@NotNull ResourceType type) {
    synchronized (ITEM_MAP_LOCK) {
      if (myChildren.size() == 1) {
        return myChildren.get(0).hasResourcesOfType(type);
      }

      Boolean cachedResult = myCachedHasResourcesOfType.get(type);
      if (cachedResult != null) {
        return cachedResult;
      }

      Set<LocalResourceRepository> visited = Sets.newHashSet();
      boolean result = computeHasResourcesOfType(type, visited);
      myCachedHasResourcesOfType.put(type, result);
      return result;
    }
  }

  @Override
  protected boolean computeHasResourcesOfType(@NotNull ResourceType type, @NotNull Set<LocalResourceRepository> visited) {
    synchronized (ITEM_MAP_LOCK) {
      if (!visited.add(this)) {
        return false;
      }
      for (LocalResourceRepository child : myChildren) {
        if (child.computeHasResourcesOfType(type, visited)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public void dispose() {
    synchronized (ITEM_MAP_LOCK) {
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        LocalResourceRepository resources = myChildren.get(i);
        resources.removeParent(this);
        Disposer.dispose(resources);
      }
    }
  }

  /**
   * Notifies this delegating repository that the given dependent repository has invalidated all resources.
   */
  public void invalidateCache(@NotNull LocalResourceRepository repository) {
    synchronized (ITEM_MAP_LOCK) {
      assert myChildren.contains(repository) : repository;

      myCachedNamespaces = null;
      myCachedMaps.clear();
      myCachedHasResourcesOfType.clear();
      myFullTable = null;
      setModificationCount(ourModificationCounter.incrementAndGet());

      invalidateParentCaches();
    }
  }

  /**
   * Notifies this delegating repository that the given dependent repository has invalidated
   * resources of the given types in the given namespace.
   */
  public void invalidateCache(@NotNull LocalResourceRepository repository, @NotNull ResourceNamespace namespace, @NotNull ResourceType... types) {
    synchronized (ITEM_MAP_LOCK) {
      assert myChildren.contains(repository) : repository;

      for (ResourceType type : types) {
        myCachedNamespaces = null;
        myCachedMaps.remove(namespace, type);

        if (ResourceNamespace.TODO.equals(namespace)) {
          myCachedHasResourcesOfType.remove(type);
        }
      }

      myFullTable = null;
      setModificationCount(ourModificationCounter.incrementAndGet());

      invalidateParentCaches(namespace, types);
    }
  }

  @Override
  @VisibleForTesting
  public boolean isScanPending(@NonNull PsiFile psiFile) {
    synchronized (ITEM_MAP_LOCK) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        LocalResourceRepository resources = myChildren.get(i);
        if (resources.isScanPending(psiFile)) {
          return true;
        }
      }

      return false;
    }
  }

  @Override
  public void sync() {
    super.sync();

    for (LocalResourceRepository childRepository : getChildren()) {
      childRepository.sync();
    }
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    synchronized (ITEM_MAP_LOCK) {
      Set<VirtualFile> result = Sets.newHashSet();
      for (LocalResourceRepository resourceRepository : myChildren) {
        result.addAll(resourceRepository.computeResourceDirs());
      }
      return result;
    }
  }
}

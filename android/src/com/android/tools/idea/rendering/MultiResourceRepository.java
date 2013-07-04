/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation") // Deprecated com.android.util.Pair is required by ProjectCallback interface
public abstract class MultiResourceRepository extends ProjectResources {
  protected List<? extends ProjectResources> myChildren;
  private long[] myModificationCounts;
  private Map<ResourceType, ListMultimap<String, ResourceItem>> myItems = Maps.newEnumMap(ResourceType.class);
  private final Map<ResourceType, ListMultimap<String, ResourceItem>> myCachedTypeMaps = Maps.newEnumMap(ResourceType.class);

  MultiResourceRepository(@NotNull String displayName, @NotNull List<? extends ProjectResources> children) {
    super(displayName);
    setChildren(children);
  }

  protected void setChildren(@NotNull List<? extends ProjectResources> children) {
    if (myChildren != null) {
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        ProjectResources resources = myChildren.get(i);
        resources.removeParent(this);
      }
    }
    myChildren = children;
    myModificationCounts = new long[children.size()];
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      resources.addParent(this);
      myModificationCounts[i] = resources.getModificationCount();
    }
    myGeneration++;
    clearCache();
  }

  private void clearCache() {
    myItems = null;
    myCachedTypeMaps.clear();
  }

  public List<? extends ProjectResources> getChildren() {
    return myChildren;
  }

  @Nullable
  @Override
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> pair = super.resolveResourceId(id);
    if (pair != null) {
      return pair;
    }

    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      Pair<ResourceType, String> resolved = resources.resolveResourceId(id);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String resolveStyleable(int[] id) {
    String resourceName = super.resolveStyleable(id);
    if (resourceName != null) {
      return resourceName;
    }

    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      String resolved = resources.resolveStyleable(id);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getResourceId(ResourceType type, String name) {
    Integer id = super.getResourceId(type, name);
    if (id != null) {
      return id;
    }

    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      Integer resolved = resources.getResourceId(type, name);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  @Override
  public void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                   Map<IntArrayWrapper, String> styleableId2name,
                                   Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    super.setCompiledResources(id2res, styleableId2name, res2id);
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      resources.setCompiledResources(id2res, styleableId2name, res2id);
    }
  }

  @Override
  public long getModificationCount() {
    if (myChildren.size() == 1) {
      return myChildren.get(0).getModificationCount();
    }

    // See if any of the delegates have changed
    boolean changed = false;
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      long rev = resources.getModificationCount();
      if (rev != myModificationCounts[i]) {
        myModificationCounts[i] = rev;
        changed = true;
      }
    }
    if (changed) {
      myGeneration++;
    }

    return myGeneration;
  }

  @NonNull
  @Override
  protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
    if (myItems == null) {
      if (myChildren.size() == 1) {
        myItems = myChildren.get(0).getItems();
      }
      else {
        Map<ResourceType, ListMultimap<String, ResourceItem>> map = Maps.newEnumMap(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
          map.put(type, getMap(type, false)); // should pass create is true, but as described below we interpret this differently
        }
        myItems = map;
      }
    }

    return myItems;
  }

  @Nullable
  @Override
  protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
    // Should I assert !create here? If we try to manipulate the cache it won't work right...
    ListMultimap<String, ResourceItem> map = myCachedTypeMaps.get(type);
    if (map != null) {
      return map;
    }

    if (myChildren.size() == 1) {
      return myChildren.get(0).getItems().get(type);
    }

    map = ArrayListMultimap.create();
    myCachedTypeMaps.put(type, map);

    // Merge all items of the given type
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      Map<ResourceType, ListMultimap<String, ResourceItem>> items = resources.getItems();
      ListMultimap<String, ResourceItem> m = items.get(type);
      if (m == null) {
        continue;
      }

      // TODO: Start with JUST the first map here (which often contains most of the keys) and then
      // only merge in 1...n
      for (ResourceItem item : m.values()) {
        String name = item.getName();
        if (map.containsKey(name)) {
          // The item already exists in this map; only add if there isn't an item with the
          // same qualifiers
          String qualifiers = item.getSource().getQualifiers();
          boolean contains = false;
          List<ResourceItem> list = map.get(name);
          assert list != null;
          for (ResourceItem existing : list) {
            if (qualifiers.equals(existing.getSource().getQualifiers())) {
              contains = true;
              break;
            }
          }
          if (!contains) {
            map.put(name, item);
          }
        }
        else {
          map.put(name, item);
        }
      }
    }

    return map;
  }

  @NonNull
  @Override
  protected ListMultimap<String, ResourceItem> getMap(ResourceType type) {
    return super.getMap(type);
  }

  @NonNull
  @Override
  public Map<ResourceType, ListMultimap<String, ResourceItem>> getItems() {
    return getMap();
  }

  @Override
  public void dispose() {
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      resources.removeParent(this);
      resources.dispose();
    }
  }

  /**
   * Notifies this delegating repository that the given dependent repository has invalidated
   * resources of the given types (empty means all)
   */
  public void invalidateCache(@NotNull ProjectResources repository, @Nullable ResourceType... types) {
    assert myChildren.contains(repository) : repository;

    if (types == null || types.length == 0) {
      myCachedTypeMaps.clear();
    }
    else {
      for (ResourceType type : types) {
        myCachedTypeMaps.remove(type);
      }
    }
    myItems = null;
    myGeneration++;

    invalidateItemCaches(types);
  }

  @Override
  boolean isScanPending(@NonNull PsiFile psiFile) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ProjectResources resources = myChildren.get(i);
      if (resources.isScanPending(psiFile)) {
        return true;
      }
    }

    return false;
  }

  @VisibleForTesting
  int getChildCount() {
    return myChildren.size();
  }
}

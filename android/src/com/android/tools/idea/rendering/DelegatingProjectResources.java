package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.MergeConsumer;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.google.common.collect.*;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
@SuppressWarnings("deprecation") // Deprecated com.android.util.Pair is required by ProjectCallback interface
class DelegatingProjectResources extends ProjectResources {
  private final List<ProjectResources> myDelegates;
  private final long[] myModificationCounts;

  DelegatingProjectResources(@NotNull List<ProjectResources> delegates) {
    super();
    myDelegates = delegates;
    assert delegates.size() >= 2; // factory should delegate to plain FileProjectResourceRepository if not
    myModificationCounts = new long[delegates.size()];
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      myModificationCounts[i] = resources.getModificationCount();
    }
  }

  @NotNull
  public static ProjectResources create(@NotNull AndroidFacet facet) {
    List<AndroidFacet> libraries = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    boolean includeLibraries = false;

    ProjectResources main = get(facet.getModule(), includeLibraries);
    if (libraries.isEmpty()) {
      return main;
    }
    List<ProjectResources> resources = Lists.newArrayListWithExpectedSize(libraries.size());
    for (AndroidFacet f : libraries) {
      ProjectResources r = get(f.getModule(), includeLibraries);
      resources.add(r);
    }

    resources.add(main);

    return new DelegatingProjectResources(resources);
  }

  @Nullable
  @Override
  public Pair<ResourceType, String> resolveResourceId(int id) {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.sync();
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
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.sync();
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
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.sync();
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
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.setCompiledResources(id2res, styleableId2name, res2id);
    }
  }

  @Override
  public void sync() {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.sync();
    }
  }

  @SuppressWarnings("ForLoopReplaceableByForEach")
  @NonNull
  @Override
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredResources(@NonNull FolderConfiguration referenceConfig) {
    Map<ResourceType, Map<String, ResourceValue>> typeMap = Maps.newEnumMap(ResourceType.class);
    for (ResourceType type : ResourceType.values()) {
      // create the map
      Map<String, ResourceValue> map = Maps.newHashMapWithExpectedSize(100);
      typeMap.put(type, map);

      for (int i = 0, n = myDelegates.size(); i < n; i++) {
        ProjectResources resources = myDelegates.get(i);
        resources.sync();

        // get the local results and put them in the map
        ListMultimap<String, ResourceItem> items = resources.getItems().get(type);
        if (items == null) {
          continue;
        }

        boolean framework = resources.isFramework();
        Set<String> keys = items.keySet();
        for (String key : keys) {
          List<ResourceItem> keyItems = items.get(key);

          // look for the best match for the given configuration
          // the match has to be of type ResourceFile since that's what the input list contains
          ResourceItem match = (ResourceItem) referenceConfig.findMatchingConfigurable(keyItems);
          if (match != null) {
            ResourceValue value = match.getResourceValue(framework);
            if (value != null) {
              map.put(match.getName(), value);
            }
          }
        }
      }
    }

    return typeMap;
  }

  @SuppressWarnings("ForLoopReplaceableByForEach")
  @NonNull
  @Override
  public Map<String, ResourceValue> getConfiguredResources(@NonNull ResourceType type, @NonNull FolderConfiguration referenceConfig) {

    // create the map
    Map<String, ResourceValue> map = Maps.newHashMapWithExpectedSize(100);

    for (int i = 0, n = myDelegates.size(); i < n; i++) {
      ProjectResources resources = myDelegates.get(i);
      resources.sync();

      // get the resource item for the given type
      ListMultimap<String, ResourceItem> items = resources.getItems().get(type);
      if (items == null) {
        continue;
      }

      boolean framework = resources.isFramework();
      Set<String> keys = items.keySet();
      for (String key : keys) {
        List<ResourceItem> keyItems = items.get(key);

        // look for the best match for the given configuration
        // the match has to be of type ResourceFile since that's what the input list contains
        ResourceItem match = (ResourceItem) referenceConfig.findMatchingConfigurable(keyItems);
        if (match != null) {
          ResourceValue value = match.getResourceValue(framework);
          if (value != null) {
            map.put(match.getName(), value);
          }
        }
      }
    }

    return map;
  }

  @Nullable
  @Override
  public ResourceValue getConfiguredValue(@NonNull ResourceType type,
                                          @NonNull String name,
                                          @NonNull FolderConfiguration referenceConfig) {

    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.sync();
      ResourceValue value = resources.getConfiguredValue(type, name, referenceConfig);
      if (value != null) {
        return value;
      }
    }

    return null;
  }

  @Override
  public long getModificationCount() {
    // See if any of the delegates have changed
    boolean changed = false;
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
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
  public Collection<String> getItemsOfType(@NonNull ResourceType type) {
    Set<String> items = Sets.newHashSet();
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      items.addAll(resources.getItemsOfType(type));
    }

    return items;
  }

  @NonNull
  @Override
  public Map<ResourceType, ListMultimap<String, ResourceItem>> getItems() {
    // TODO: I should *cache* this, and reuse it since it's needed a lot; that would make
    // synthesizing query answers much faster, as long as I can invalidate it!
    return combineItems();
  }

  private Map<ResourceType, ListMultimap<String, ResourceItem>> combineItems() {
    Map<ResourceType, ListMultimap<String, ResourceItem>> allItems = Maps.newEnumMap(ResourceType.class);
    for (ProjectResources resources : myDelegates) {
      Map<ResourceType, ListMultimap<String, ResourceItem>> items = resources.getItems();
      for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : items.entrySet()) {
        ResourceType type = entry.getKey();
        ListMultimap<String, ResourceItem> map = entry.getValue();

        // TODO: Do a proper/full merge here!
        if (map == null) {
          continue;
        }

        ListMultimap<String, ResourceItem> fullMap = allItems.get(type);
        if (fullMap == null) {
          fullMap = ArrayListMultimap.create();
          allItems.put(type, fullMap);
        }

        for (Map.Entry<String, ResourceItem> mapEntry : map.entries()) {
          // TODO: Implement overlay/shadowing here!!!
          String key = mapEntry.getKey();
          ResourceItem value = mapEntry.getValue();
          fullMap.put(key, value);
        }
      }
    }

    return allItems;
  }

  @Nullable
  @Override
  public List<ResourceItem> getResourceItem(@NonNull ResourceType resourceType, @NonNull String resourceName) {
    return super.getResourceItem(resourceType, resourceName);
  }

  @Override
  public boolean hasResourceItem(@NonNull String url) {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      if (resources.hasResourceItem(url)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean hasResourceItem(@NonNull ResourceType resourceType, @NonNull String resourceName) {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      if (resources.hasResourceItem(resourceType, resourceName)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean hasResourcesOfType(@NonNull ResourceType resourceType) {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      if (resources.hasResourcesOfType(resourceType)) {
        return true;
      }
    }

    return false;
  }

  @NonNull
  @Override
  public List<ResourceType> getAvailableResourceTypes() {
    Set<ResourceType> types = EnumSet.noneOf(ResourceType.class);
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      types.addAll(resources.getAvailableResourceTypes());
    }

    return Lists.newArrayList(types);
  }

  @Nullable
  @Override
  public ResourceFile getMatchingFile(@NonNull String name, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      ResourceFile matchingFile = resources.getMatchingFile(name, type, config);
      if (matchingFile != null) {
        return matchingFile;
      }
    }

    return null;
  }

  @NonNull
  @Override
  public SortedSet<String> getLanguages() {
    SortedSet<String> languages = myDelegates.get(0).getLanguages();
    for (int i = myDelegates.size() - 1; i >= 1; i--) { // deliberately skipping i == 0
      ProjectResources resources = myDelegates.get(i);
      languages.addAll(resources.getLanguages());
    }

    return languages;
  }

  @NonNull
  @Override
  public SortedSet<String> getRegions(@NonNull String currentLanguage) {
    SortedSet<String> regions = myDelegates.get(0).getRegions(currentLanguage);
    for (int i = myDelegates.size() - 1; i >= 1; i--) { // deliberately skipping i == 0
      ProjectResources resources = myDelegates.get(i);
      regions.addAll(resources.getRegions(currentLanguage));
    }

    return regions;
  }

  @Override
  public void refresh() {
    // TODO: If in the future we cache information, such as mItems, clear them here
    myGeneration++;
  }

  @NonNull
  @Override
  public MergeConsumer<ResourceItem> getMergeConsumer() {
    throw new IllegalStateException(); // Merging is only done on individual project resources
  }

  @Nullable
  @Override
  protected Collection<String> getMergeIds() {
    // Should be called on individual delegated project resources
    return null;
  }

  @Override
  public void mergeIds() {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.mergeIds();
    }
  }

  @Override
  public void dispose() {
    for (int i = myDelegates.size() - 1; i >= 0; i--) {
      ProjectResources resources = myDelegates.get(i);
      resources.dispose();
    }
  }
}

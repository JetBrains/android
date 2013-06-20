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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceRepository;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public abstract class ProjectResources extends ResourceRepository implements Disposable, ModificationTracker {
  protected static final Logger LOG = Logger.getInstance(ProjectResources.class);
  protected long myGeneration;

  protected ProjectResources() {
    super(false);
  }

  @NotNull
  public static ProjectResources get(@NotNull Module module, boolean includeLibraries) {
    ProjectResources projectResources = get(module, includeLibraries, true);
    assert projectResources != null;
    return projectResources;
  }

  @Nullable
  public static ProjectResources get(@NotNull Module module, boolean includeLibraries, boolean createIfNecessary) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return facet.getProjectResources(includeLibraries, createIfNecessary);
    }

    return null;
  }

  @NotNull
  public static ProjectResources create(@NotNull AndroidFacet facet, boolean includeLibraries) {
    if (includeLibraries) {
      return DelegatingProjectResources.create(facet);
    } else {
      return FileProjectResourceRepository.create(facet);
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public final boolean isFramework() {
    return false;
  }

  // For ProjectCallback

  @Nullable
  public abstract Pair<ResourceType, String> resolveResourceId(int id);

  @Nullable
  public abstract String resolveStyleable(int[] id);

  @Nullable
  public abstract Integer getResourceId(ResourceType type, String name);

  public abstract void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                           Map<IntArrayWrapper, String> styleableId2name,
                                           Map<ResourceType, TObjectIntHashMap<String>> res2id);

  public abstract void sync();

  // ---- Implements ModificationCount ----

  /**
   * Returns the current generation of the project resources. Any time the project resources are updated,
   * the generation increases. This can be used to force refreshing of layouts etc (which will cache
   * configured project resources) when the project resources have changed since last render.
   * <p>
   * Note that the generation is not a change count. If you change the contents of a layout drawable XML file,
   * that will not affect the {@link ResourceItem} and {@link ResourceValue} results returned from
   * this repository; we only store the presence of file based resources like layouts, menus, and drawables.
   * Therefore, only additions or removals of these files will cause a generation change.
   * <p>
   * Value resource files, such as string files, will cause generation changes when they are edited.
   * Later we should consider only updating the generation when the actual values are changed (such that
   * we can ignore whitespace changes, comment changes, reordering changes (outside of arrays), and so on.
   * The natural time to implement this is when we reimplement this class to directly work on top of
   * the PSI data structures, rather than simply using a PSI listener and calling super methods to
   * process ResourceFile objects as is currently done.
   *
   * @return the generation id
   */
  @Override
  public long getModificationCount() {
    sync();
    // First sync in case there are pending changes which will rev the generation
    return myGeneration;
  }

  // Code related to updating the resources

  @NonNull
  @Override
  public Collection<String> getItemsOfType(@NonNull ResourceType type) {
    sync();
    return super.getItemsOfType(type);
  }

  @NonNull
  @Override
  public Map<ResourceType, ListMultimap<String, ResourceItem>> getItems() {
    sync();
    return super.getItems();
  }

  @Nullable
  @Override
  public List<ResourceItem> getResourceItem(@NonNull ResourceType resourceType, @NonNull String resourceName) {
    sync();
    return super.getResourceItem(resourceType, resourceName);
  }

  @Override
  public boolean hasResourceItem(@NonNull String url) {
    sync();
    return super.hasResourceItem(url);
  }

  @Override
  public boolean hasResourceItem(@NonNull ResourceType resourceType, @NonNull String resourceName) {
    sync();
    return super.hasResourceItem(resourceType, resourceName);
  }

  @Override
  public boolean hasResourcesOfType(@NonNull ResourceType resourceType) {
    sync();
    return super.hasResourcesOfType(resourceType);
  }

  @NonNull
  @Override
  public List<ResourceType> getAvailableResourceTypes() {
    sync();
    return super.getAvailableResourceTypes();
  }

  @Nullable
  @Override
  public ResourceFile getMatchingFile(@NonNull String name, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    sync();
    return super.getMatchingFile(name, type, config);
  }

  @NonNull
  @Override
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredResources(@NonNull FolderConfiguration referenceConfig) {
    sync();
    return super.getConfiguredResources(referenceConfig);
  }

  @NonNull
  @Override
  public Map<String, ResourceValue> getConfiguredResources(@NonNull ResourceType type, @NonNull FolderConfiguration referenceConfig) {
    sync();
    return super.getConfiguredResources(type, referenceConfig);
  }

  @Nullable
  @Override
  public ResourceValue getConfiguredValue(@NonNull ResourceType type, @NonNull String name, @NonNull FolderConfiguration referenceConfig) {
    sync();
    return super.getConfiguredValue(type, name, referenceConfig);
  }

  @NonNull
  @Override
  public SortedSet<String> getLanguages() {
    sync();
    return super.getLanguages();
  }

  @NonNull
  @Override
  public SortedSet<String> getRegions(@NonNull String currentLanguage) {
    sync();
    return super.getRegions(currentLanguage);
  }

  public abstract void refresh();
}

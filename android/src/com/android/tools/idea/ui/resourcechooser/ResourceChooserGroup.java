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
package com.android.tools.idea.ui.resourcechooser;

import com.android.SdkConstants;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.DataFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.res.AppResourceRepository;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("UnnecessaryFullyQualifiedName") // We have both resources and res2 ResourceItems; keep both explicit
class ResourceChooserGroup {
  private final @NotNull List<ResourceChooserItem> myItems = new ArrayList<>();
  private final @NotNull String myLabel;
  private final @NotNull ResourceType myType;

  // Longer term we may want to let users see private resources and copy them to their projects
  // instead. For now, we just filter them out:
  public static final boolean FILTER_OUT_PRIVATE = true;

  public ResourceChooserGroup(@NotNull String label,
                              @NotNull ResourceType type,
                              @NotNull AndroidFacet facet,
                              boolean framework,
                              boolean includeFileResources) {
    assert type != ResourceType.MIPMAP; // We fold these into the drawable category instead

    myType = type;
    myLabel = label;

    if (framework) {
      AndroidPlatform androidPlatform = AndroidPlatform.getInstance(facet.getModule());
      if (androidPlatform == null) {
        return;
      }
      AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(androidPlatform.getTarget());
      AbstractResourceRepository frameworkResources;
      frameworkResources = targetData.getFrameworkResources(type == ResourceType.STRING);
      if (frameworkResources == null) {
        return;
      }
      addFrameworkItems(type, includeFileResources, frameworkResources);
      if (type == ResourceType.DRAWABLE) {
        // Include mipmaps too
        addFrameworkItems(ResourceType.MIPMAP, includeFileResources, frameworkResources);
      }
    } else {
      AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(facet);

      //noinspection ConstantConditions
      ResourceVisibilityLookup lookup = FILTER_OUT_PRIVATE ? repository.getResourceVisibility(facet) : null;
      addProjectItems(type, includeFileResources, repository, lookup);
      if (type == ResourceType.DRAWABLE) {
        // Include mipmaps too
        addProjectItems(ResourceType.MIPMAP, includeFileResources, repository, lookup);
      }
    }

    sortItems();
  }

  public ResourceChooserGroup(@NotNull String label, @NotNull ResourceType type,
                              @NotNull AndroidFacet facet, @NotNull Collection<String> attrs) {
    myType = type;
    myLabel = label;

    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(facet);
    //noinspection ConstantConditions
    ResourceVisibilityLookup lookup = FILTER_OUT_PRIVATE ? repository.getResourceVisibility(facet) : null;

    for (String name : attrs) {
      boolean framework = name.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX);
      String simpleName = framework ? ResolutionUtils.getNameFromQualifiedName(name) : name;
      if (!framework) {
        //noinspection ConstantConditions
        if (lookup != null && lookup.isPrivate(ResourceType.ATTR, simpleName)) {
          continue;
        }
      }
      myItems.add(new ResourceChooserItem.AttrItem(myType, framework, simpleName));
    }
    sortItems();
  }

  private void addFrameworkItems(@NotNull ResourceType type,
                                 boolean includeFileResources,
                                 @NotNull AbstractResourceRepository frameworkResources) {
    Map<String, List<ResourceItem>> itemsByName = new HashMap<>();
    Collection<ResourceItem> publicItems = frameworkResources.getPublicResourcesOfType(type);
    for (ResourceItem item : publicItems) {
      String name = item.getName();
      List<ResourceItem> list = itemsByName.get(name);
      if (list == null) {
        list = new ArrayList<>();
        itemsByName.put(name, list);
      }
      list.add(item);
    }

    for (Map.Entry<String, List<ResourceItem>> entry : itemsByName.entrySet()) {
      List<ResourceItem> items = entry.getValue();
      String resourceName = entry.getKey();
      if (!includeFileResources) {
        if (items.get(0).getSourceType() != DataFile.FileType.XML_VALUES) {
          continue;
        }
      }
      myItems.add(ResourceChooserItem.createFrameworkItem(myType, resourceName, items));
    }
  }

  private void addProjectItems(@NotNull ResourceType type,
                               boolean includeFileResources,
                               @NotNull AppResourceRepository repository,
                               @Nullable ResourceVisibilityLookup lookup) {
    for (String resourceName : repository.getItemsOfType(type)) {
      if (lookup != null && lookup.isPrivate(type, resourceName)) {
        continue;
      }
      List<ResourceItem> items = repository.getResourceItem(type, resourceName);
      if (items == null || items.isEmpty()) {
        continue;
      }
      if (!includeFileResources) {
        if (items.get(0).getSourceType() != DataFile.FileType.XML_VALUES) {
          continue;
        }
      }

      myItems.add(ResourceChooserItem.createProjectItem(type, resourceName, items));
    }
  }

  private void sortItems() {
    Collections.sort(myItems, (resource1, resource2) -> {
      int framework1 = resource1.isFramework() ? 1 : 0;
      int framework2 = resource2.isFramework() ? 1 : 0;
      int delta = framework1 - framework2;
      if (delta != 0) {
        return delta;
      }
      return resource1.getName().compareTo(resource2.getName());
    });
  }

  @NotNull
  public ResourceType getType() {
    return myType;
  }

  @NotNull
  public List<ResourceChooserItem> getItems() {
    return myItems;
  }

  @Override
  public String toString() {
    return myLabel;
  }

  public boolean isEmpty() {
    return getItems().isEmpty();
  }
}

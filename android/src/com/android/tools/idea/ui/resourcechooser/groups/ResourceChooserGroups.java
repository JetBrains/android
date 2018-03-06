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
package com.android.tools.idea.ui.resourcechooser.groups;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.DataFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.android.tools.idea.ui.resourcechooser.ResourceChooserItem;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class ResourceChooserGroups {
  // Longer term we may want to let users see private resources and copy them to their projects
  // instead. For now, we just filter them out:
  private static final boolean FILTER_OUT_PRIVATE_ITEMS = true;

  /**
   * Default comparator used for ResourceChooserGroups. Framework attributes are displayed at the bottom.
   */
  private static final Comparator<ResourceChooserItem> ITEM_COMPARATOR = (resource1, resource2) -> {
    int framework1 = resource1.isFramework() ? 1 : 0;
    int framework2 = resource2.isFramework() ? 1 : 0;
    int delta = framework1 - framework2;
    if (delta != 0) {
      return delta;
    }
    return resource1.getName().compareTo(resource2.getName());
  };
  /** For sample data, ResourceTypes that should only display image types */
  private static final EnumSet<ResourceType> IMAGE_RESOURCE_TYPES =
    EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP, ResourceType.COLOR);
  private static final Predicate<SampleDataResourceItem> ONLY_IMAGES_FILTER =
    (item) -> item.getContentType() == SampleDataResourceItem.ContentType.IMAGE;
  private static final Predicate<SampleDataResourceItem> NOT_IMAGES_FILTER = ONLY_IMAGES_FILTER.negate();

  private ResourceChooserGroups() {
  }

  @NotNull
  private static ImmutableList<ResourceChooserItem> getFrameworkItems(@NotNull ResourceType type,
                                                                      boolean includeFileResources,
                                                                      @NotNull AbstractResourceRepository frameworkResources,
                                                                      @NotNull ResourceType asType) {
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

    ImmutableList.Builder<ResourceChooserItem> chooserItems = ImmutableList.builder();
    for (Map.Entry<String, List<ResourceItem>> entry : itemsByName.entrySet()) {
      List<ResourceItem> items = entry.getValue();
      String resourceName = entry.getKey();
      if (!includeFileResources) {
        if (items.get(0).getSourceType() != DataFile.FileType.XML_VALUES) {
          continue;
        }
      }
      chooserItems.add(ResourceChooserItem.createFrameworkItem(asType, resourceName, items));
    }
    return chooserItems.build();
  }

  @NotNull
  private static ImmutableList<ResourceChooserItem> getProjectItems(@NotNull ResourceType type,
                                                                    boolean includeFileResources,
                                                                    @NotNull AppResourceRepository repository,
                                                                    @Nullable ResourceVisibilityLookup lookup) {
    ImmutableList.Builder<ResourceChooserItem> chooserItems = ImmutableList.builder();
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

      chooserItems.add(ResourceChooserItem.createProjectItem(type, resourceName, items));
    }
    return chooserItems.build();
  }

  @Nullable
  private static AbstractResourceRepository getFrameworkResources(@NotNull AndroidFacet facet, boolean withLocale) {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(facet.getModule());
    if (androidPlatform == null) {
      return null;
    }

    AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(androidPlatform.getTarget());
    return targetData.getFrameworkResources(withLocale);
  }

  @NotNull
  public static ResourceChooserGroup createResourceItemsGroup(@NotNull String label,
                                                              @NotNull ResourceType type,
                                                              @NotNull AndroidFacet facet,
                                                              boolean framework,
                                                              boolean includeFileResources) {
    assert type != ResourceType.MIPMAP; // We fold these into the drawable category instead

    ImmutableList.Builder<ResourceChooserItem> items = ImmutableList.builder();
    if (framework) {
      AbstractResourceRepository frameworkResources = getFrameworkResources(facet, type == ResourceType.STRING);
      if (frameworkResources != null) {
        items.addAll(getFrameworkItems(type, includeFileResources, frameworkResources, type));
        if (type == ResourceType.DRAWABLE) {
          // Include mipmaps too
          items.addAll(getFrameworkItems(ResourceType.MIPMAP, includeFileResources, frameworkResources, type));
        }
      }
    }
    else {
      ResourceRepositoryManager repoManager = ResourceRepositoryManager.getOrCreateInstance(facet);
      AppResourceRepository appResources = repoManager.getAppResources(true);

      //noinspection ConstantConditions
      ResourceVisibilityLookup lookup = FILTER_OUT_PRIVATE_ITEMS ? repoManager.getResourceVisibility() : null;
      items.addAll(getProjectItems(type, includeFileResources, appResources, lookup));
      if (type == ResourceType.DRAWABLE) {
        // Include mipmaps too
        items.addAll(getProjectItems(ResourceType.MIPMAP, includeFileResources, appResources, lookup));
      }
    }

    return new ResourceChooserGroup(label, type, ImmutableList.sortedCopyOf(ITEM_COMPARATOR, items.build()));
  }

  @NotNull
  public static ResourceChooserGroup createThemeAttributesGroup(@NotNull ResourceType type,
                                                                @NotNull AndroidFacet facet,
                                                                @NotNull Collection<String> attrs) {
    //noinspection ConstantConditions
    ResourceVisibilityLookup lookup = FILTER_OUT_PRIVATE_ITEMS
                                      ? ResourceRepositoryManager.getOrCreateInstance(facet).getResourceVisibility()
                                      : null;

    List<ResourceChooserItem> items = new ArrayList<>();
    for (String name : attrs) {
      boolean framework = name.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX);
      String simpleName = framework ? ResolutionUtils.getNameFromQualifiedName(name) : name;
      if (!framework) {
        //noinspection ConstantConditions
        if (lookup != null && lookup.isPrivate(ResourceType.ATTR, simpleName)) {
          continue;
        }
      }
      items.add(new ResourceChooserItem.AttrItem(type, framework, simpleName));
    }

    return new ResourceChooserGroup("Theme attributes", type, ImmutableList.sortedCopyOf(ITEM_COMPARATOR, items));
  }

  @NotNull
  public static ResourceChooserGroup createSampleDataGroup(@NotNull ResourceType type, @NotNull AndroidFacet facet) {
    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(facet);

    Predicate<SampleDataResourceItem> filter = IMAGE_RESOURCE_TYPES.contains(type) ? ONLY_IMAGES_FILTER : NOT_IMAGES_FILTER;
    ImmutableList<ResourceChooserItem> items =
      AppResourceRepository.getOrCreateInstance(facet).getItemsOfType(ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA).stream()
        .map(itemName -> (SampleDataResourceItem)repository.getResourceItems(ResourceNamespace.TOOLS,
                                                                             ResourceType.SAMPLE_DATA, itemName).get(0))
        .filter(filter)
        .map(item -> new ResourceChooserItem.SampleDataItem(item))
        .collect(ImmutableList.toImmutableList());
    return new ResourceChooserGroup("Sample data", type, ImmutableList.sortedCopyOf(ITEM_COMPARATOR, items));
  }
}

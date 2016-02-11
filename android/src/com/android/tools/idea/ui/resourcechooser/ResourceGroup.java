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

import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ResourceGroup {
  private final @NotNull List<ResourceItem> myItems = new ArrayList<ResourceItem>();
  private final @NotNull String myLabel;
  private final @NotNull ResourceType myType;

  public ResourceGroup(@NotNull String label,
                       @NotNull ResourceType type,
                       @NotNull AndroidFacet facet,
                       final @Nullable String namespace,
                       boolean includeFileResources) {
    myType = type;
    myLabel = label;

    final String resourceType = type.getName();

    ResourceManager manager = facet.getResourceManager(namespace);
    assert manager != null;
    Collection<String> resourceNames = manager.getValueResourceNames(type);
    for (String resourceName : resourceNames) {
      myItems.add(new ResourceItem(this, namespace, resourceName, null));
    }
    final Set<String> fileNames = new HashSet<String>();

    if (includeFileResources) {
      manager.processFileResources(resourceType, new FileResourceProcessor() {
        @Override
        public boolean process(@NotNull VirtualFile resFile, @NotNull String resName) {
          if (fileNames.add(resName)) {
            myItems.add(new ResourceItem(ResourceGroup.this, namespace, resName, resFile));
          }
          return true;
        }
      });
    }

    if (type == ResourceType.ID) {
      for (String id : manager.getIds(true)) {
        if (!resourceNames.contains(id)) {
          myItems.add(new ResourceItem(this, namespace, id, null));
        }
      }
    }
    sortItems();
  }

  public ResourceGroup(@NotNull String label, @NotNull ResourceType type, @NotNull Collection<String> attrs) {
    myType = type;
    myLabel = label;
    for (String name : attrs) {
      myItems.add(
        new ResourceItem(this, ResolutionUtils.getNamespaceFromQualifiedName(name), ResolutionUtils.getNameFromQualifiedName(name),
                         true));
    }
    sortItems();
  }

  private void sortItems() {
    Collections.sort(myItems, new Comparator<ResourceItem>() {
      @Override
      public int compare(ResourceItem resource1, ResourceItem resource2) {
        if (com.google.common.base.Objects.equal(resource1.getNamespace(), resource2.getNamespace())) {
          return resource1.getName().compareTo(resource2.getName());
        }
        if (resource1.getNamespace() == null) {
          return -1;
        }
        if (resource2.getNamespace() == null) {
          return 1;
        }
        return resource1.getNamespace().compareTo(resource2.getNamespace());
      }
    });
  }

  @NotNull
  public ResourceType getType() {
    return myType;
  }

  @NotNull
  public List<ResourceItem> getItems() {
    return myItems;
  }

  @Override
  public String toString() {
    return myLabel;
  }
}

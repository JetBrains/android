/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.MultiResourceRepository;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StringResourceRepository {
  private final Map<StringResourceKey, AbstractResourceRepository> myKeyToRepositoryMap;

  public StringResourceRepository(@NotNull MultiResourceRepository parent) {
    myKeyToRepositoryMap = new HashMap<>();

    for (AbstractResourceRepository child : parent.getChildren()) {
      VirtualFile directory = child instanceof ResourceFolderRepository ? ((ResourceFolderRepository)child).getResourceDir() : null;

      for (String name : child.getItemsOfType(ResourceType.STRING)) {
        myKeyToRepositoryMap.put(new StringResourceKey(name, directory), child);
      }
    }
  }

  @NotNull
  public StringResourceData getData(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();

    Map<StringResourceKey, StringResource> map = myKeyToRepositoryMap.keySet().stream()
      .collect(Collectors.toMap(Function.identity(), key -> new StringResource(key, getItems(key), project)));

    return new StringResourceData(facet, map);
  }

  @NotNull
  Collection<ResourceItem> getItems(@NotNull StringResourceKey key) {
    Collection<ResourceItem> items = myKeyToRepositoryMap.get(key).getResourceItem(ResourceType.STRING, key.getName());
    return items == null ? Collections.emptyList() : items;
  }
}

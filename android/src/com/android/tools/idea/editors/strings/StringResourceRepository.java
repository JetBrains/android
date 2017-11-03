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
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StringResourceRepository {
  private final Map<StringResourceKey, LocalResourceRepository> myKeyToRepositoryMap;

  public StringResourceRepository(@NotNull MultiResourceRepository parent) {
    myKeyToRepositoryMap = new LinkedHashMap<>(); // Keeps the keys insertion order

    for (LocalResourceRepository child : parent.getChildren()) {
      VirtualFile directory = child instanceof ResourceFolderRepository ? ((ResourceFolderRepository)child).getResourceDir() : null;
      child.sync();

      for (String name : child.getItemsOfType(ResourceType.STRING)) {
        myKeyToRepositoryMap.put(new StringResourceKey(name, directory), child);
      }
    }
  }

  @NotNull
  final StringResourceData getData(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();

    Map<StringResourceKey, StringResource> map = myKeyToRepositoryMap.keySet().stream()
      .collect(Collectors.toMap(Function.identity(),
                                key -> new StringResource(key, this, project),
                                (resource1, resource2) -> {
                                  throw new IllegalStateException("Duplicate key " + resource1);
                                },
                                LinkedHashMap::new
      ));

    return new StringResourceData(facet, map);
  }

  @NotNull
  public Collection<ResourceItem> getItems(@NotNull StringResourceKey key) {
    return getItems(myKeyToRepositoryMap.get(key), key);
  }

  @Nullable
  final ResourceItem getDefaultValue(@NotNull StringResourceKey key) {
    return getItem(key, item -> item.getConfiguration().getLocaleQualifier() == null);
  }

  @Nullable
  final ResourceItem getTranslation(@NotNull StringResourceKey key, @NotNull Locale locale) {
    return getItem(key, item -> localeEquals(item, locale));
  }

  private static boolean localeEquals(@NotNull Configurable item, @NotNull Locale locale) {
    LocaleQualifier qualifier = item.getConfiguration().getLocaleQualifier();
    return qualifier != null && Locale.create(qualifier).equals(locale);
  }

  @Nullable
  private ResourceItem getItem(@NotNull StringResourceKey key, @NotNull Predicate<ResourceItem> predicate) {
    LocalResourceRepository repository = myKeyToRepositoryMap.get(key);
    repository.sync();

    Optional<ResourceItem> optionalItem = getItems(repository, key).stream()
      .filter(predicate)
      .findFirst();

    return optionalItem.orElse(null);
  }

  @NotNull
  private static Collection<ResourceItem> getItems(@NotNull AbstractResourceRepository repository, @NotNull StringResourceKey key) {
    Collection<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, key.getName());
    return items == null ? Collections.emptyList() : items;
  }
}

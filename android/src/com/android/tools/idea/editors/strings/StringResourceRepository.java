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
import com.android.tools.idea.res.ModuleResourceRepository.EmptyRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class StringResourceRepository {
  private final Map<VirtualFile, LocalResourceRepository> myResourceDirectoryRespositoryMap;

  // TODO Drop support for dynamic resources?
  private final LocalResourceRepository myDynamicResourceRepository;

  private StringResourceRepository(@NotNull MultiResourceRepository parent) {
    Collection<? extends LocalResourceRepository> children = parent.getChildren();
    Map<VirtualFile, LocalResourceRepository> resourceDirectoryRespositoryMap = Maps.newLinkedHashMapWithExpectedSize(children.size());
    LocalResourceRepository dynamicResourceRespository = null;

    for (LocalResourceRepository child : children) {
      child.sync();

      if (child instanceof ResourceFolderRepository) {
        resourceDirectoryRespositoryMap.put(((ResourceFolderRepository)child).getResourceDir(), child);
      }
      else {
        assert dynamicResourceRespository == null;
        dynamicResourceRespository = child;
      }
    }

    myResourceDirectoryRespositoryMap = resourceDirectoryRespositoryMap;
    myDynamicResourceRepository = dynamicResourceRespository;
  }

  private StringResourceRepository(@NotNull ResourceFolderRepository repository) {
    repository.sync();

    myResourceDirectoryRespositoryMap = Collections.singletonMap(repository.getResourceDir(), repository);
    myDynamicResourceRepository = null;
  }

  private StringResourceRepository(@NotNull LocalResourceRepository repository) {
    repository.sync();

    myResourceDirectoryRespositoryMap = Collections.emptyMap();
    myDynamicResourceRepository = repository;
  }

  @NotNull
  public static StringResourceRepository create() {
    return new StringResourceRepository(new EmptyRepository());
  }

  @NotNull
  static StringResourceRepository create(@NotNull LocalResourceRepository repository) {
    if (repository instanceof MultiResourceRepository) {
      return new StringResourceRepository((MultiResourceRepository)repository);
    }

    if (repository instanceof ResourceFolderRepository) {
      return new StringResourceRepository((ResourceFolderRepository)repository);
    }

    return new StringResourceRepository(repository);
  }

  @NotNull
  public final StringResourceData getData(@NotNull AndroidFacet facet) {
    Map<StringResourceKey, StringResource> map = new LinkedHashMap<>();
    Project project = facet.getModule().getProject();

    myResourceDirectoryRespositoryMap.entrySet().stream()
      .flatMap(StringResourceRepository::getKeys)
      .forEach(key -> map.put(key, new StringResource(key, this, project)));

    if (myDynamicResourceRepository != null) {
      myDynamicResourceRepository.getItemsOfType(ResourceType.STRING).stream()
        .map(name -> new StringResourceKey(name, null))
        .forEach(key -> map.put(key, new StringResource(key, this, project)));
    }

    return new StringResourceData(facet, map);
  }

  @NotNull
  private static Stream<StringResourceKey> getKeys(@NotNull Entry<VirtualFile, LocalResourceRepository> entry) {
    VirtualFile directory = entry.getKey();
    return entry.getValue().getItemsOfType(ResourceType.STRING).stream().map(name -> new StringResourceKey(name, directory));
  }

  @NotNull
  public Collection<ResourceItem> getItems(@NotNull StringResourceKey key) {
    return getItems(getRepository(key), key);
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
    LocalResourceRepository repository = getRepository(key);
    repository.sync();

    Optional<ResourceItem> optionalItem = getItems(repository, key).stream()
      .filter(predicate)
      .findFirst();

    return optionalItem.orElse(null);
  }

  @NotNull
  private LocalResourceRepository getRepository(@NotNull StringResourceKey key) {
    VirtualFile directory = key.getDirectory();
    return directory == null ? myDynamicResourceRepository : myResourceDirectoryRespositoryMap.get(directory);
  }

  @NotNull
  private static Collection<ResourceItem> getItems(@NotNull AbstractResourceRepository repository, @NotNull StringResourceKey key) {
    Collection<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, key.getName());
    return items == null ? Collections.emptyList() : items;
  }
}

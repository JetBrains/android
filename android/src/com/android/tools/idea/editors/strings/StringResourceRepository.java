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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository.EmptyRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.google.common.collect.Maps;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceRepository {
  private final Map<VirtualFile, LocalResourceRepository> myResourceDirectoryRepositoryMap;

  // TODO Drop support for dynamic resources?
  private final LocalResourceRepository myDynamicResourceRepository;

  private StringResourceRepository(@NotNull MultiResourceRepository parent) {
    Collection<LocalResourceRepository> localResources = parent.getLocalResources();
    Map<VirtualFile, LocalResourceRepository> resourceDirectoryRepositoryMap = Maps.newLinkedHashMapWithExpectedSize(localResources.size());
    LocalResourceRepository dynamicResourceRepository = null;

    for (LocalResourceRepository child : localResources) {
      child.sync();

      if (child instanceof ResourceFolderRepository) {
        resourceDirectoryRepositoryMap.put(((ResourceFolderRepository)child).getResourceDir(), child);
      }
      else {
        assert dynamicResourceRepository == null;
        dynamicResourceRepository = child;
      }
    }

    myResourceDirectoryRepositoryMap = resourceDirectoryRepositoryMap;
    myDynamicResourceRepository = dynamicResourceRepository;
  }

  private StringResourceRepository(@NotNull ResourceFolderRepository repository) {
    repository.sync();

    myResourceDirectoryRepositoryMap = Collections.singletonMap(repository.getResourceDir(), repository);
    myDynamicResourceRepository = null;
  }

  private StringResourceRepository(@NotNull LocalResourceRepository repository) {
    repository.sync();

    myResourceDirectoryRepositoryMap = Collections.emptyMap();
    myDynamicResourceRepository = repository;
  }

  @NotNull
  public static StringResourceRepository empty() {
    return new StringResourceRepository(new EmptyRepository(ResourceNamespace.RES_AUTO));
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
  final Stream<StringResourceKey> getKeys() {
    Collection<Entry<VirtualFile, LocalResourceRepository>> entries = myResourceDirectoryRepositoryMap.entrySet();
    Stream<StringResourceKey> resourceDirectoryKeys = entries.stream().flatMap(StringResourceRepository::getKeys);

    if (myDynamicResourceRepository == null) {
      return resourceDirectoryKeys;
    }

    Collection<String> names = myDynamicResourceRepository.getResources(ResourceNamespace.TODO(), ResourceType.STRING).keySet();
    Stream<StringResourceKey> dynamicResourceKeys = names.stream().map(name -> new StringResourceKey(name, null));

    return Stream.concat(resourceDirectoryKeys, dynamicResourceKeys);
  }

  @NotNull
  private static Stream<StringResourceKey> getKeys(@NotNull Entry<VirtualFile, LocalResourceRepository> entry) {
    VirtualFile directory = entry.getKey();

    return entry.getValue().getResources(ResourceNamespace.TODO(), ResourceType.STRING).keySet().stream()
      .map(name -> new StringResourceKey(name, directory));
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

    Optional<ResourceItem> optionalItem = getItems(repository, key).stream()
      .filter(predicate)
      .findFirst();

    return optionalItem.orElse(null);
  }

  @NotNull
  private LocalResourceRepository getRepository(@NotNull StringResourceKey key) {
    VirtualFile directory = key.getDirectory();
    return directory == null ? myDynamicResourceRepository : myResourceDirectoryRepositoryMap.get(directory);
  }

  @NotNull
  private static Collection<ResourceItem> getItems(@NotNull LocalResourceRepository repository, @NotNull StringResourceKey key) {
    repository.sync();
    return repository.getResources(ResourceNamespace.TODO(), ResourceType.STRING, key.getName());
  }
}

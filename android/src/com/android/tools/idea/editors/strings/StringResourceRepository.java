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
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository.EmptyRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.android.tools.idea.res.PsiResourceItem;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.idea.util.FileExtensions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceRepository {
  /** Compares two PathString arguments segment by segment. */
  private static final Comparator<PathString> PATH_SEGMENT_COMPARATOR = (p1, p2) -> {
    List<String> segments1 = p1.getSegments();
    List<String> segments2 = p2.getSegments();
    int n = Math.min(segments1.size(), segments2.size());
    for (int i = 0; i < n; i++) {
      int c = segments1.get(i).compareTo(segments2.get(i));
      if (c != 0) {
        return c;
      }
    }
    return segments1.size() - segments2.size();
  };

  private final @NotNull Map<VirtualFile, ResourceFolderRepository> myResourceDirectoryRepositoryMap;

  // TODO Drop support for dynamic resources?
  private final @NotNull LocalResourceRepository myDynamicResourceRepository;

  private StringResourceRepository(@NotNull Map<VirtualFile, ResourceFolderRepository> resourceDirectoryRepositoryMap,
                                   @NotNull LocalResourceRepository dynamicResourceRepository) {
    myResourceDirectoryRepositoryMap = resourceDirectoryRepositoryMap;
    myDynamicResourceRepository = dynamicResourceRepository;
  }

  @NotNull
  public static StringResourceRepository empty() {
    return new StringResourceRepository(ImmutableMap.of(), new EmptyRepository(ResourceNamespace.RES_AUTO));
  }

  /**
   * Returns a future StringResourceRepository. The future is guaranteed to complete on the UI thread.
   */
  static @NotNull ListenableFuture<@NotNull StringResourceRepository> create(@NotNull LocalResourceRepository repository) {
    List<LocalResourceRepository> repositories =
        repository instanceof MultiResourceRepository ?
            ((MultiResourceRepository)repository).getLocalResources() : ImmutableList.of(repository);

    Map<VirtualFile, ResourceFolderRepository> repositoryMap = Maps.newLinkedHashMapWithExpectedSize(repositories.size());
    LocalResourceRepository dynamicResourceRepository = null;

    // Convert resource items to PsiResourceItem to know their locations in files.
    for (LocalResourceRepository localRepository : repositories) {
      if (localRepository instanceof ResourceFolderRepository) {
        ResourceFolderRepository folderRepository = (ResourceFolderRepository)localRepository;
        repositoryMap.put(folderRepository.getResourceDir(), folderRepository);
        // Use ordering similar to a recursive directory scan.
        Set<PathString> stringResourceSources = new TreeSet<>(PATH_SEGMENT_COMPARATOR);
        for (ResourceItem item : localRepository.getResources(folderRepository.getNamespace(), ResourceType.STRING).values()) {
          if (!(item instanceof PsiResourceItem)) {
            PathString source = item.getSource();
            if (source != null) {
              stringResourceSources.add(source);
            }
          }
        }
        for (PathString source : stringResourceSources) {
          VirtualFile file = FileExtensions.toVirtualFile(source);
          if (file != null) {
            folderRepository.convertToPsiIfNeeded(file);
          }
        }
      }
      else {
        assert dynamicResourceRepository == null;
        dynamicResourceRepository = localRepository;
      }
    }

    if (dynamicResourceRepository == null) {
      dynamicResourceRepository = new EmptyRepository(ResourceNamespace.RES_AUTO);
    }
    StringResourceRepository stringRepository = new StringResourceRepository(repositoryMap, dynamicResourceRepository);
    SettableFuture<StringResourceRepository> futureStringRepository = SettableFuture.create();
    // Return the repository only after completion of the PSI conversion.
    repository.invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance(), () -> futureStringRepository.set(stringRepository));
    return futureStringRepository;
  }

  final @NotNull Stream<StringResourceKey> getKeys() {
    Set<Entry<VirtualFile, ResourceFolderRepository>> entries = myResourceDirectoryRepositoryMap.entrySet();
    Stream<StringResourceKey> resourceDirectoryKeys = entries.stream().flatMap(StringResourceRepository::getKeys);

    Set<String> names = myDynamicResourceRepository.getResourceNames(ResourceNamespace.TODO(), ResourceType.STRING);
    Stream<StringResourceKey> dynamicResourceKeys = names.stream().map(name -> new StringResourceKey(name, null));

    return Stream.concat(resourceDirectoryKeys, dynamicResourceKeys);
  }

  private static @NotNull Stream<StringResourceKey> getKeys(@NotNull Entry<VirtualFile, ResourceFolderRepository> entry) {
    VirtualFile directory = entry.getKey();

    return entry.getValue().getResources(ResourceNamespace.TODO(), ResourceType.STRING).keySet().stream()
      .map(name -> new StringResourceKey(name, directory));
  }

  public @NotNull List<ResourceItem> getItems(@NotNull StringResourceKey key) {
    return getItems(getRepository(key), key);
  }

  final @Nullable ResourceItem getDefaultValue(@NotNull StringResourceKey key) {
    return getItem(key, item -> item.getConfiguration().getLocaleQualifier() == null);
  }

  final @Nullable ResourceItem getTranslation(@NotNull StringResourceKey key, @NotNull Locale locale) {
    return getItem(key, item -> localeEquals(item, locale));
  }

  /**
   * Executes the given callback on the UI thread after all pending repository updates finish.
   */
  public void invokeAfterPendingUpdatesFinish(@NotNull StringResourceKey key, @NotNull Runnable callback) {
    LocalResourceRepository repository = getRepository(key);
    repository.invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance(), callback);
  }

  private static boolean localeEquals(@NotNull Configurable item, @NotNull Locale locale) {
    LocaleQualifier qualifier = item.getConfiguration().getLocaleQualifier();
    return qualifier != null && Locale.create(qualifier).equals(locale);
  }

  private @Nullable ResourceItem getItem(@NotNull StringResourceKey key, @NotNull Predicate<ResourceItem> predicate) {
    LocalResourceRepository repository = getRepository(key);

    Optional<ResourceItem> optionalItem = getItems(repository, key).stream()
      .filter(predicate)
      .findFirst();

    return optionalItem.orElse(null);
  }

  private @NotNull LocalResourceRepository getRepository(@NotNull StringResourceKey key) {
    VirtualFile directory = key.getDirectory();
    return directory == null ? myDynamicResourceRepository : myResourceDirectoryRepositoryMap.get(directory);
  }

  private static @NotNull List<ResourceItem> getItems(@NotNull LocalResourceRepository repository, @NotNull StringResourceKey key) {
    return repository.getResources(ResourceNamespace.TODO(), ResourceType.STRING, key.getName());
  }
}

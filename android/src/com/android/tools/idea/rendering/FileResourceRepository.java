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
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.res2.*;
import com.android.resources.ResourceType;
import com.android.utils.ILogger;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.SoftValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * A {@link AbstractResourceRepository} for plain java.io Files; this is needed for repositories
 * in output folders such as build, where Studio will not create PsiDirectories, and
 * as a result cannot use the normal {@link ResourceFolderRepository}. This is the case
 * for example for the expanded {@code .aar} directories.
 */
public class FileResourceRepository extends LocalResourceRepository {
  private static final Logger LOG = Logger.getInstance(FileResourceRepository.class);
  protected final Map<ResourceType, ListMultimap<String, ResourceItem>> mItems = Maps.newEnumMap(ResourceType.class);
  private final File myFile;

  private final static SoftValueHashMap<File, FileResourceRepository> ourCache =
    new SoftValueHashMap<File, FileResourceRepository>();

  private FileResourceRepository(@NotNull File file) {
    super(file.getName());
    myFile = file;
  }

  @NotNull
  static FileResourceRepository get(@NotNull final File file) {
    FileResourceRepository repository = ourCache.get(file);
    if (repository == null) {
      repository = create(file);
      ourCache.put(file, repository);
    }

    return repository;
  }

  @Nullable
  @VisibleForTesting
  static FileResourceRepository getCached(@NotNull final File file) {
    return ourCache.get(file);
  }

  @NotNull
  private static FileResourceRepository create(@NotNull final File file) {
    final FileResourceRepository repository = new FileResourceRepository(file);
    try {
      ResourceMerger resourceMerger = createResourceMerger(file);
      resourceMerger.mergeData(repository.createMergeConsumer(), true /*doCleanUp*/);
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    return repository;
  }

  public static void reset() {
    ourCache.clear();
  }

  public File getResourceDirectory() {
    return myFile;
  }

  private static ResourceMerger createResourceMerger(File file) {
    ILogger logger = new LogWrapper(LOG);
    ResourceMerger merger = new ResourceMerger();

    ResourceSet resourceSet = new ResourceSet(file.getName()) {
      @Override
      protected void checkItems() throws DuplicateDataException {
        // No checking in ProjectResources; duplicates can happen, but
        // the project resources shouldn't abort initialization
      }
    };
    resourceSet.addSource(file);
    try {
      resourceSet.loadFromFiles(logger);
    }
    catch (DuplicateDataException e) {
      // This should not happen; we've subclasses ResourceSet above to a no-op in checkItems
      assert false;
    }
    catch (MergingException e) {
      LOG.error(e.getMessage());
    }
    merger.addDataSet(resourceSet);
    return merger;
  }

  @Override
  @NonNull
  protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
    return mItems;
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = mItems.get(type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      mItems.put(type, multimap);
    }
    return multimap;
  }

  // For debugging only
  @Override
  public String toString() {
    return getClass().getSimpleName() + " for " + myFile + ": @" + Integer.toHexString(System.identityHashCode(this));
  }
}

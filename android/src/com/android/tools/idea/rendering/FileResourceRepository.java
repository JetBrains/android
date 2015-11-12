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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.utils.ILogger;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.SoftValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import static com.android.SdkConstants.FN_RESOURCE_TEXT;

/**
 * A {@link AbstractResourceRepository} for plain java.io Files; this is needed for repositories
 * in output folders such as build, where Studio will not create PsiDirectories, and
 * as a result cannot use the normal {@link ResourceFolderRepository}. This is the case
 * for example for the expanded {@code .aar} directories.
 */
public class FileResourceRepository extends LocalResourceRepository {
  private static final Logger LOG = Logger.getInstance(FileResourceRepository.class);
  protected final Map<ResourceType, ListMultimap<String, ResourceItem>> myItems = Maps.newEnumMap(ResourceType.class);
  /**
   * A collection of resource id names found in the R.txt file if the file referenced by this repository is an AAR.
   * The Ids obtained using {@link #getItemsOfType(ResourceType)} by passing in {@link ResourceType#ID} only contains
   * a subset of IDs (top level ones like layout file names, and id resources in values xml file). Ids declared inside
   * layouts and menus (using "@+id/") are not included. This is done for efficiency. However, such IDs can be obtained
   * from the R.txt file, if present. And hence, this collection includes all id names from the R.txt file, but doesn't
   * have the associated {@link ResourceItem} with it.
   */
  protected Collection<String> myAarDeclaredIds;
  private final File myFile;
  /** R.txt file associated with the repository. This is only available for aars. */
  @Nullable private File myResourceTextFile;

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
      resourceMerger.mergeData(repository.createMergeConsumer(), true);
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }
    if (file.getPath().contains(AndroidGradleModel.EXPLODED_AAR)) {
      File rDotTxt = new File(file.getParentFile(), FN_RESOURCE_TEXT);
      if (rDotTxt.exists()) {
        repository.myResourceTextFile = rDotTxt;
        repository.myAarDeclaredIds = RDotTxtParser.getIdNames(rDotTxt);
      }
    }

    return repository;
  }

  @Nullable
  File getResourceTextFile() {
    return myResourceTextFile;
  }

  public static void reset() {
    ourCache.clear();
  }

  public File getResourceDirectory() {
    return myFile;
  }

  private static ResourceMerger createResourceMerger(File file) {
    ILogger logger = new LogWrapper(LOG);
    ResourceMerger merger = new ResourceMerger(0);

    ResourceSet resourceSet = new ResourceSet(file.getName(), false /* validateEnabled */);
    resourceSet.addSource(file);
    try {
      resourceSet.loadFromFiles(logger);
    }
    catch (DuplicateDataException e) {
      // This should not happen; resourceSet validation is disabled.
      assert false;
    }
    catch (MergingException e) {
      LOG.warn(e);
    }
    merger.addDataSet(resourceSet);
    return merger;
  }

  @Override
  @NonNull
  protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
    return myItems;
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myItems.get(type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myItems.put(type, multimap);
    }
    return multimap;
  }

  /** @see #myAarDeclaredIds */
  @Nullable
  protected Collection<String> getAllDeclaredIds() {
    return myAarDeclaredIds;
  }

  // For debugging only
  @Override
  public String toString() {
    return getClass().getSimpleName() + " for " + myFile + ": @" + Integer.toHexString(System.identityHashCode(this));
  }
}

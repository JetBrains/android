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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.res2.*;
import com.android.resources.ResourceType;
import com.android.tools.log.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static com.android.SdkConstants.FN_RESOURCE_TEXT;

/**
 * A {@link AbstractResourceRepository} for plain java.io Files; this is needed for repositories
 * in output folders such as build, where Studio will not create PsiDirectories, and
 * as a result cannot use the normal {@link ResourceFolderRepository}. This is the case
 * for example for the expanded {@code .aar} directories.
 *
 * <p>Most of the implementation is based on {@link ResourceMerger} which means the behavior is highly
 * consistent with what will happen at build time.
 */
public class FileResourceRepository extends LocalResourceRepository {
  private static final Logger LOG = Logger.getInstance(FileResourceRepository.class);
  protected final ResourceTable myFullTable = new ResourceTable();
  /**
   * A collection of resource id names found in the R.txt file if the file referenced by this repository is an AAR.
   * The Ids obtained using {@link #getItemsOfType(ResourceType)} by passing in {@link ResourceType#ID} only contains
   * a subset of IDs (top level ones like layout file names, and id resources in values xml file). Ids declared inside
   * layouts and menus (using "@+id/") are not included. This is done for efficiency. However, such IDs can be obtained
   * from the R.txt file, if present. And hence, this collection includes all id names from the R.txt file, but doesn't
   * have the associated {@link ResourceItem} with it.
   */
  @Nullable protected Collection<String> myAarDeclaredIds;

  @NotNull private final File myFile;
  @Nullable private final String myNamespace;
  @Nullable private final String myLibraryName;

  /** R.txt file associated with the repository. This is only available for aars. */
  @Nullable private File myResourceTextFile;

  private final static Map<File, FileResourceRepository> ourCache = ContainerUtil.createSoftValueMap();

  private FileResourceRepository(@NotNull File file, @Nullable String namespace, @Nullable String libraryName) {
    super(file.getName());
    myFile = file;
    myNamespace = namespace;
    myLibraryName = libraryName;
  }

  @NotNull
  // TODO: namespaces
  static synchronized FileResourceRepository get(@NotNull final File file, @Nullable String libraryName) {
    FileResourceRepository repository = ourCache.get(file);
    if (repository == null) {
      repository = create(file, null, libraryName);
      ourCache.put(file, repository);
    }

    return repository;
  }

  @Nullable
  @VisibleForTesting
  static synchronized FileResourceRepository getCached(@NotNull final File file) {
    return ourCache.get(file);
  }

  @NotNull
  private static FileResourceRepository create(@NotNull final File file, @Nullable String namespace, @Nullable String libraryName) {
    final FileResourceRepository repository = new FileResourceRepository(file, namespace, libraryName);
    try {
      ResourceMerger resourceMerger = createResourceMerger(file, namespace, libraryName);
      repository.getItems().update(resourceMerger);
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    // Look for a R.txt file which describes the available id's; this is
    // available both in an exploded-aar folder as well as in the build-cache
    // for AAR files
    File rDotTxt = new File(file.getParentFile(), FN_RESOURCE_TEXT);
    if (rDotTxt.exists()) {
      repository.myResourceTextFile = rDotTxt;
      repository.myAarDeclaredIds = RDotTxtParser.getIdNames(rDotTxt);
    }

    return repository;
  }

  @NotNull
  public static FileResourceRepository createForTest(@NotNull final File file, @Nullable String namespace, @Nullable String libraryName) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return create(file, namespace, libraryName);
  }

  @Nullable
  File getResourceTextFile() {
    return myResourceTextFile;
  }

  public static synchronized void reset() {
    ourCache.clear();
  }

  @NotNull
  public File getResourceDirectory() {
    return myFile;
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return myLibraryName;
  }

  private static ResourceMerger createResourceMerger(File file, String namespace, String libraryName) {
    ILogger logger = new LogWrapper(LOG).alwaysLogAsDebug(true).allowVerbose(false);
    ResourceMerger merger = new ResourceMerger(0);

    ResourceSet resourceSet = new ResourceSet(file.getName(), namespace, libraryName, false /* validateEnabled */);
    resourceSet.addSource(file);
    resourceSet.setTrackSourcePositions(false);
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
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@Nullable String namespace, @NotNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @Override
  @NotNull
  public Set<String> getNamespaces() {
    return myFullTable.rowKeySet();
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

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(myFile, !ApplicationManager.getApplication().isReadAccessAllowed());
    if (virtualFile == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.of(virtualFile);
  }
}

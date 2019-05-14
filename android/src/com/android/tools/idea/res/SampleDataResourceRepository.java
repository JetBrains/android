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

import static com.android.tools.idea.util.FileExtensions.toVirtualFile;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A repository of user-defined sample data. To define new project data sources a new file of folder needs to be created under
 * the {@code sampleData} folder in the project root. The repository provides access to the full contents of the data sources.
 * Selection of items is done by the {@link com.android.ide.common.resources.sampledata.SampleDataManager}
 * <p/>
 * The {@link SampleDataResourceRepository} currently supports three data formats:
 * <ul>
 *   <li><b>Plain text files</b>: Files that allow defining a new item per line
 *   <li><b>JSON files</b>: The SampleDataResourceRepository will extract every possible path that gives access to an array of
 *   elements and provide access to them
 *   <li><b>Directories</b>: Folders that contain a list of images
 * </ul>
 */
public class SampleDataResourceRepository extends LocalResourceRepository implements SingleNamespaceResourceRepository, Disposable {
  @NotNull private final ResourceTable myFullTable = new ResourceTable();
  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ResourceNamespace myNamespace;

  @NotNull
  public static SampleDataResourceRepository getInstance(@NotNull AndroidFacet facet) {
    return SampleDataRepositoryManager.getInstance(facet).getRepository();
  }

  private SampleDataResourceRepository(@NotNull AndroidFacet androidFacet) {
    super("Sample Data");
    myAndroidFacet = androidFacet;

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(androidFacet);
    myNamespace = repositoryManager.getNamespace();
    loadItems();

    SampleDataListener.ensureSubscribed(androidFacet.getModule().getProject());
  }

  @Override
  public void addParent(@NotNull MultiResourceRepository parent) {
    if (!myAndroidFacet.isDisposed()) {
      super.addParent(parent);
    }
  }

  /**
   * Reloads user-defined sample data from files on disk. Call this method after the sample data has been updated to reload the contents.
   */
  void reload() {
    loadItems();
  }

  @Override
  @NotNull
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myAndroidFacet);
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }

  @Override
  public void dispose() {
  }

  /**
   * Invalidates the current sample data of this repository. Call this method after the sample data has been updated
   * to reload the contents.
   */
  private void loadItems() {
    if (myAndroidFacet.isDisposed()) {
      return;
    }

    VirtualFile sampleDataDir = toVirtualFile(ProjectSystemUtil.getModuleSystem(myAndroidFacet.getModule()).getSampleDataDirectory());
    myFullTable.clear();

    if (sampleDataDir != null) {
      List<SampleDataResourceItem> items = new ArrayList<>();
      PsiManager psiManager = PsiManager.getInstance(myAndroidFacet.getModule().getProject());
      Stream<VirtualFile> childrenStream = Arrays.stream(sampleDataDir.getChildren());
      ApplicationManager.getApplication().runReadAction(() -> childrenStream
        .map(vf -> vf.isDirectory() ? psiManager.findDirectory(vf) : psiManager.findFile(vf))
        .filter(Objects::nonNull)
        .forEach(f -> items.addAll(loadItemsFromFile(f))));

      if (!items.isEmpty()) {
        synchronized (ITEM_MAP_LOCK) {
          ListMultimap<String, ResourceItem> map = myFullTable.getOrPutEmpty(myNamespace, ResourceType.SAMPLE_DATA);
          for (ResourceItem item : items) {
            assert item.getNamespace().equals(myNamespace);
            map.put(item.getName(), item);
          }
        }
      }
    }

    setModificationCount(ourModificationCounter.incrementAndGet());
    invalidateParentCaches(myNamespace, ResourceType.SAMPLE_DATA);
  }

  @NotNull
  private List<SampleDataResourceItem> loadItemsFromFile(@NotNull PsiFileSystemItem sampleDataFile) {
    try {
      return SampleDataResourceItem.getFromPsiFileSystemItem(sampleDataFile, myNamespace);
    }
    catch (IOException e) {
      LOG.warn("Error loading sample data file " + sampleDataFile.getName(), e);
      return Collections.emptyList();
    }
  }

  /**
   * Service which caches instances of {@link SampleDataResourceRepository} by their associated {@link AndroidFacet}.
   */
  static class SampleDataRepositoryManager extends AndroidFacetScopedService {
    private static final Key<SampleDataRepositoryManager> KEY = Key.create(SampleDataRepositoryManager.class.getName());
    private final Object repositoryLock = new Object();
    @GuardedBy("repositoryLock")
    private SampleDataResourceRepository repository;

    @NotNull
    public static SampleDataRepositoryManager getInstance(@NotNull AndroidFacet facet) {
      SampleDataRepositoryManager manager = facet.getUserData(KEY);

      if (manager == null) {
        manager = new SampleDataRepositoryManager(facet);
        facet.putUserData(KEY, manager);
      }

      return manager;
    }

    private SampleDataRepositoryManager(@NotNull AndroidFacet facet) {
      super(facet);
    }

    @Override
    protected void onServiceDisposal(@NotNull AndroidFacet facet) {
      // No additional logic needed, the repository is registered with Disposer as a child of this object.
    }

    @NotNull
    public SampleDataResourceRepository getRepository() {
      if (isDisposed()) {
        throw new IllegalStateException(getClass().getSimpleName() + " is disposed");
      }
      synchronized (repositoryLock) {
        if (repository == null) {
          repository = new SampleDataResourceRepository(getFacet());
          Disposer.register(this, repository);
        }
        return repository;
      }
    }

    public boolean hasRepository() {
      synchronized (repositoryLock) {
        return repository != null;
      }
    }

    /**
     * Drops the existing repository, forcing it to be recreated next time it's needed.
     */
    public void reset() {
      SampleDataResourceRepository localRepository;
      synchronized (repositoryLock) {
        localRepository = repository;
        repository = null;
      }
      if (localRepository != null) {
        Disposer.dispose(localRepository);
      }
    }
  }
}

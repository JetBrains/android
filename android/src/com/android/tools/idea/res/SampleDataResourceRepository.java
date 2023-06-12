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

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private static final Logger LOG = Logger.getInstance(SampleDataResourceRepository.class);

  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ResourceNamespace myNamespace;
  @NotNull private final Map<ResourceType, ListMultimap<String, ResourceItem>> myResourceTable = new EnumMap<>(ResourceType.class);

  @NotNull
  public static SampleDataResourceRepository getInstance(@NotNull AndroidFacet facet) {
    return SampleDataRepositoryManager.getInstance(facet).getRepository();
  }

  private SampleDataResourceRepository(@NotNull AndroidFacet androidFacet) {
    super("Sample Data");
    myAndroidFacet = androidFacet;

    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(androidFacet);
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

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    if (!namespace.equals(myNamespace)) {
      return null;
    }
    return myResourceTable.get(type);
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
  public ResourceVisitor.VisitResult accept(@NotNull ResourceVisitor visitor) {
    if (visitor.shouldVisitNamespace(myNamespace)) {
      synchronized (ITEM_MAP_LOCK) {
        if (acceptByResources(myResourceTable, visitor) == ResourceVisitor.VisitResult.ABORT) {
          return ResourceVisitor.VisitResult.ABORT;
        }
      }
    }

    return ResourceVisitor.VisitResult.CONTINUE;
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

    PsiManager psiManager = PsiManager.getInstance(myAndroidFacet.getMainModule().getProject());
    // This collects all modules and dependencies and finds the sampledata directory in all of them. The order is relevant since the
    // modules will override sampledata from parents (for example the app module from a library module).
    List<SampleDataResourceItem> items = Stream.concat(
        Stream.of(myAndroidFacet.getMainModule()),
        ProjectSystemUtil.getModuleSystem(myAndroidFacet.getMainModule()).getResourceModuleDependencies().stream())
      // Collect all the sample directories, in order
      .map((module) -> toVirtualFile(ProjectSystemUtil.getModuleSystem(module).getSampleDataDirectory()))
      .filter(Objects::nonNull)
      .flatMap((sampleDataDir) -> Arrays.stream(sampleDataDir.getChildren()))
      // Find the PsiFile or PsiDirectory for the element
      .map((sampleDataDir) -> sampleDataDir.isDirectory() ? psiManager.findDirectory(sampleDataDir) : psiManager.findFile(sampleDataDir))
      .filter(Objects::nonNull)
      .flatMap((psiElement) -> loadItemsFromFile(psiElement).stream())
      .collect(Collectors.toUnmodifiableList());

    synchronized (ITEM_MAP_LOCK) {
      myResourceTable.clear();
      if (!items.isEmpty()) {
        HashSet<String> alreadyParsedItems = new HashSet<>();
        ImmutableListMultimap.Builder<String, ResourceItem> mapBuilder = ImmutableListMultimap.builder();
        for (ResourceItem item : items) {
          assert item.getNamespace().equals(myNamespace);
          // Only add to the result if we have not parsed a sample data source with the same name. If there are collisions, we use the
          // dependency order so in, app -> lib, app sample data sources would override the lib ones.
          if (alreadyParsedItems.add(item.getName())) mapBuilder.put(item.getName(), item);
        }
        myResourceTable.put(ResourceType.SAMPLE_DATA, mapBuilder.build());
      }
      setModificationCount(ourModificationCounter.incrementAndGet());
      invalidateParentCaches(this, ResourceType.SAMPLE_DATA);
    }
  }

  @NotNull
  private List<SampleDataResourceItem> loadItemsFromFile(@NotNull PsiFileSystemItem sampleDataFile) {
    try {
      return SampleDataResourceItem.getFromPsiFileSystemItem(this, sampleDataFile);
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

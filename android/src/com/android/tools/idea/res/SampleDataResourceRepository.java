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

import static com.android.tools.idea.res.SampleDataResourceItem.ContentType.IMAGE;
import static com.android.tools.idea.res.SampleDataResourceItem.ContentType.TEXT;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.sampledata.datasource.CombinerDataSource;
import com.android.tools.idea.sampledata.datasource.DateTimeGenerator;
import com.android.tools.idea.sampledata.datasource.LoremIpsumGenerator;
import com.android.tools.idea.sampledata.datasource.NumberGenerator;
import com.android.tools.idea.sampledata.datasource.ResourceContent;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
 * A {@link LocalResourceRepository} that provides sample data to be used within "tools" attributes. This provider
 * defines a set of predefined sources that are always available but also allows to define new data sources in the project.
 * To define new project data sources a new file of folder needs to be created under the {@code sampleData} folder in the project
 * root.
 * The repository provides access to the full contents of the data sources. Selection of items is done by the
 * {@link com.android.ide.common.resources.sampledata.SampleDataManager}
 * <p/>
 * The {@link SampleDataResourceRepository} currently supports 3 data formats:
 * <ul>
 *   <li><b>Plain files</b>: Files that allow defining a new item per line
 *   <li><b>JSON files</b>: The SampleDataResourceRepository will extract every possible path that gives access to an array of
 *   elements and provide access to them
 *   <li><b>Directories</b>: Folders that contain a list of images
 * </ul>
 */
public class SampleDataResourceRepository extends MultiResourceRepository {
  public static final ResourceNamespace PREDEFINED_SAMPLES_NS = ResourceNamespace.TOOLS;

  /**
   * List of predefined data sources that are always available within studio
   */
  private static final ImmutableList<SampleDataResourceItem> PREDEFINED_SOURCES = ImmutableList.of(
    SampleDataResourceItem.getFromStaticDataSource("full_names",
                                                   new CombinerDataSource(
                                                       getResourceAsStream("sampleData/names.txt"),
                                                       getResourceAsStream("sampleData/surnames.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("first_names",
                                                   ResourceContent.fromInputStream(getResourceAsStream("sampleData/names.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("last_names",
                                                   ResourceContent.fromInputStream(getResourceAsStream("sampleData/surnames.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("cities",
                                                   ResourceContent.fromInputStream(getResourceAsStream("sampleData/cities.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("us_zipcodes",
                                                   new NumberGenerator("%05d", 20000, 99999),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("us_phones",
                                                   new NumberGenerator("(800) 555-%04d", 0, 9999),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("lorem", new LoremIpsumGenerator(false),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("lorem/random", new LoremIpsumGenerator(true),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("avatars",
                                                   ResourceContent.fromDirectory("avatars"),
                                                   IMAGE),
    SampleDataResourceItem.getFromStaticDataSource("backgrounds/scenic",
                                                   ResourceContent.fromDirectory("backgrounds/scenic"),
                                                   IMAGE),

    // TODO: Delegate path parsing to the data source to avoid all these declarations.
    SampleDataResourceItem.getFromStaticDataSource("date/day_of_week",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("E"), ChronoUnit.DAYS),
                                                   TEXT
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/ddmmyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("dd-MM-yy"), ChronoUnit.DAYS),
                                                   TEXT
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/mmddyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("MM-dd-yy"), ChronoUnit.DAYS),
                                                   TEXT
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/hhmm",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm"), ChronoUnit.MINUTES),
                                                   TEXT
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/hhmmss",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm:ss"), ChronoUnit.SECONDS),
                                                   TEXT
    ));

  private static InputStream getResourceAsStream(@NotNull String name) {
    return SampleDataResourceRepository.class.getClassLoader().getResourceAsStream(name);
  }

  @NotNull
  public static SampleDataResourceRepository getInstance(@NotNull AndroidFacet facet) {
    return SampleDataRepositoryManager.getInstance(facet).getRepository();
  }

  private final LeafRepository myUserItemsRepository;

  private SampleDataResourceRepository(@NotNull AndroidFacet androidFacet) {
    super("SampleData");

    LeafRepository predefinedItemsRepository =
        new LeafRepository("Predefined SampleData", androidFacet, ResourceNamespace.TOOLS);
    predefinedItemsRepository.addPredefinedItems();

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(androidFacet);
    myUserItemsRepository = new LeafRepository("User-defined SampleData", androidFacet, repositoryManager.getNamespace());
    myUserItemsRepository.loadItems();

    setChildren(ImmutableList.of(predefinedItemsRepository, myUserItemsRepository), ImmutableList.of());

    SampleDataListener.ensureSubscribed(androidFacet.getModule().getProject());
  }

  @Override
  public void addParent(@NotNull MultiResourceRepository parent) {
    AndroidFacet facet = myUserItemsRepository.myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    super.addParent(parent);
  }

  /**
   * Reloads user-defined sample data from files on disk. Call this method after the sample data has been updated to reload the contents.
   */
  void reload() {
    myUserItemsRepository.loadItems();
  }

  private static class LeafRepository extends LocalResourceRepository implements SingleNamespaceResourceRepository {
    private final ResourceTable myFullTable = new ResourceTable();
    private final AndroidFacet myAndroidFacet;
    private final ResourceNamespace myNamespace;

    LeafRepository(@NotNull String displayName, @NotNull AndroidFacet facet, @NotNull ResourceNamespace namespace) {
      super(displayName);
      myAndroidFacet = facet;
      myNamespace = namespace;
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

    /**
     * Invalidates the current sample data of this repository. Call this method after the sample data has been updated
     * to reload the contents.
     */
    void loadItems() {
      AndroidFacet facet = myAndroidFacet;
      if (facet == null || facet.isDisposed()) {
        return;
      }

      VirtualFile sampleDataDir = toVirtualFile(ProjectSystemUtil.getModuleSystem(facet.getModule()).getSampleDataDirectory());
      myFullTable.clear();

      if (sampleDataDir != null) {
        List<SampleDataResourceItem> items = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(facet.getModule().getProject());
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
      invalidateParentCaches(getNamespace(), ResourceType.SAMPLE_DATA);
    }

    private List<SampleDataResourceItem> loadItemsFromFile(@NotNull PsiFileSystemItem sampleDataFile) {
      try {
        return SampleDataResourceItem.getFromPsiFileSystemItem(sampleDataFile, myNamespace);
      }
      catch (IOException e) {
        LOG.warn("Error loading sample data file " + sampleDataFile.getName(), e);
        return Collections.emptyList();
      }
    }

    private void addPredefinedItems() {
      ListMultimap<String, ResourceItem> cell = myFullTable.getOrPutEmpty(PREDEFINED_SAMPLES_NS, ResourceType.SAMPLE_DATA);
      PREDEFINED_SOURCES.forEach(source -> cell.put(source.getName(), source));
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

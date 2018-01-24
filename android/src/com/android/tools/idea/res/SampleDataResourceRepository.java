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
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.android.tools.idea.sampledata.datasource.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.android.SdkConstants.FD_SAMPLE_DATA;

/**
 * A {@link LocalResourceRepository} that provides sample data to be used within "tools" attributes. This provider
 * defines a set of predefined sources that are always available but also allows to define new data sources in the project.
 * To define new project data sources a new file of folder needs to be created under the sampledata folder in the project
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
public class SampleDataResourceRepository extends LocalResourceRepository {

  public static final ResourceNamespace PREDEFINED_SAMPLES_NS = ResourceNamespace.TOOLS;

  /**
   * List of predefined data sources that are always available within studio
   */
  private static final ImmutableList<SampleDataResourceItem> PREDEFINED_SOURCES = ImmutableList.of(
    SampleDataResourceItem.getFromStaticDataSource("full_names", new CombinerDataSource(
      SampleDataResourceRepository.class.getClassLoader().getResourceAsStream("sampleData/names.txt"),
      SampleDataResourceRepository.class.getClassLoader().getResourceAsStream("sampleData/surnames.txt"))),
    SampleDataResourceItem.getFromStaticDataSource("first_names", ResourceContent.fromInputStream(
      SampleDataResourceRepository.class.getClassLoader().getResourceAsStream("sampleData/names.txt"))),
    SampleDataResourceItem.getFromStaticDataSource("last_names", ResourceContent.fromInputStream(
      SampleDataResourceRepository.class.getClassLoader()
        .getResourceAsStream("sampleData/surnames.txt"))),
    SampleDataResourceItem.getFromStaticDataSource("cities", ResourceContent.fromInputStream(
      SampleDataResourceRepository.class.getClassLoader()
        .getResourceAsStream("sampleData/cities.txt"))),
    SampleDataResourceItem.getFromStaticDataSource("us_zipcodes",
                                                   new NumberGenerator("%05d", 20000, 99999)),
    SampleDataResourceItem.getFromStaticDataSource("us_phones",
                                                   new NumberGenerator("(800) 555-%04d", 0, 9999)),
    SampleDataResourceItem.getFromStaticDataSource("lorem", new LoremIpsumGenerator(false)),
    SampleDataResourceItem.getFromStaticDataSource("lorem/random", new LoremIpsumGenerator(true)),
    SampleDataResourceItem.getFromStaticDataSource("avatars",
                                                   ResourceContent.fromDirectory("avatars")),
    SampleDataResourceItem.getFromStaticDataSource("backgrounds/scenic",
                                                   ResourceContent.fromDirectory("backgrounds/scenic")),

    // TODO: Delegate path parsing to the data source to avoid all these declarations
    SampleDataResourceItem.getFromStaticDataSource("date/day_of_week",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("E"), ChronoUnit.DAYS)
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/ddmmyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("dd-MM-yy"), ChronoUnit.DAYS)
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/mmddyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("MM-dd-yy"), ChronoUnit.DAYS)
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/hhmm",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm"), ChronoUnit.MINUTES)
    ),
    SampleDataResourceItem.getFromStaticDataSource("date/hhmmss",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm:ss"), ChronoUnit.SECONDS)
    ));


  private final ResourceTable myFullTable;
  private AndroidFacet myAndroidFacet;
  /** The PSI listener to update the repository contents when there are user changes. This is also used as the lock for add/removing the listener */
  private final PsiTreeChangeAdapter myPsiTreeChangeListener = new PsiTreeChangeAdapter() {
    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      contentsUpdated(event);
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      contentsUpdated(event);
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      contentsUpdated(event);
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      contentsUpdated(event);
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      contentsUpdated(event);
    }
  };

  @GuardedBy("myPsiTreeChangeListener")
  private boolean isPsiListenerRegistered;

  /**
   * Returns the "sampledata" directory from the project (if it exists) or null otherwise.
   * @param create when true, if the directory does not exist, it will be created
   */
  @Nullable
  public static VirtualFile getSampleDataDir(@NotNull AndroidFacet androidFacet, boolean create) throws IOException {
    VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(androidFacet);
    if (contentRoot == null) {
      LOG.warn("Unable to find content root");
      return null;
    }

    VirtualFile sampleDataDir = contentRoot.findFileByRelativePath("/" + FD_SAMPLE_DATA);
    if (sampleDataDir == null && create) {
        sampleDataDir = WriteCommandAction.runWriteCommandAction(androidFacet.getModule().getProject(),
                                                                 (ThrowableComputable<VirtualFile, IOException>)() -> contentRoot.createChildDirectory(androidFacet, FD_SAMPLE_DATA));
    }

    return sampleDataDir;
  }

  protected SampleDataResourceRepository(@NotNull AndroidFacet androidFacet) {
    super("SampleData");

    Disposer.register(androidFacet, this);

    myFullTable = new ResourceTable();
    myAndroidFacet = androidFacet;

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        rootsUpdated(event);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        rootsUpdated(event);
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        rootsUpdated(event);
      }
    }, this);

    invalidate();
  }

  private void addItems(@NotNull PsiFileSystemItem sampleDataFile) {
    try {
      List<SampleDataResourceItem> fromFile = SampleDataResourceItem.getFromPsiFileSystemItem(sampleDataFile);
      if (!fromFile.isEmpty()) {
        // All items from a single file have the same namespace, look up the table cell they all go into.
        ListMultimap<String, ResourceItem> cell = myFullTable.getOrPutEmpty(fromFile.get(0).getNamespace(), ResourceType.SAMPLE_DATA);
        fromFile.forEach(item -> cell.put(item.getName(), item));
      }
    }
    catch (IOException e) {
      LOG.warn("Error loading sample data file " + sampleDataFile.getName(), e);
    }
  }

  private void addPredefinedItems() {
    ListMultimap<String, ResourceItem> cell = myFullTable.getOrPutEmpty(PREDEFINED_SAMPLES_NS, ResourceType.SAMPLE_DATA);
    PREDEFINED_SOURCES.forEach(source -> cell.put(source.getName(), source));
  }

  /**
   * Invalidates the current sample data of this repository. Call this method after the sample data has been updated to reload the contents.
   */
  private void invalidate() {
    AndroidFacet facet = myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    VirtualFile sampleDataDir = null;
    try {
      sampleDataDir = getSampleDataDir(facet, false);
    }
    catch (IOException e) {
      LOG.warn("Error getting 'sampledir'", e);
    }
    myFullTable.clear();

    if (sampleDataDir != null) {
      PsiManager psiManager = PsiManager.getInstance(facet.getModule().getProject());
      Stream<VirtualFile> childrenStream = Arrays.stream(sampleDataDir.getChildren());
      ApplicationManager.getApplication().runReadAction(() -> childrenStream
        .map(vf -> vf.isDirectory() ? psiManager.findDirectory(vf) : psiManager.findFile(vf))
        .filter(Objects::nonNull)
        .forEach(f -> addItems(f)));

      registerPsiListener();
    }
    else {
      // There is no sample data directory, no reason to listen for PSI changes
      unregisterPsiListener();
    }

    addPredefinedItems();
    setModificationCount(ourModificationCounter.incrementAndGet());
    invalidateParentCaches(PREDEFINED_SAMPLES_NS, ResourceType.SAMPLE_DATA);
  }

  /**
   * Registers a PSI listener that will update the sample data from the sample data repository. If the listener is not
   * registered, this call has no effect.
   */
  private void registerPsiListener() {
    AndroidFacet facet = myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    synchronized (myPsiTreeChangeListener) {
      if (isPsiListenerRegistered) {
        return;
      }

      isPsiListenerRegistered = true;
      PsiManager.getInstance(facet.getModule().getProject()).addPsiTreeChangeListener(myPsiTreeChangeListener, this);
    }
  }

  /**
   * Unregisters a PSI listener that will update the sample data from the sample data repository. If the listener is already
   * registered, this call has no effect.
   */
  private void unregisterPsiListener() {
    AndroidFacet facet = myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    synchronized (myPsiTreeChangeListener) {
      if (!isPsiListenerRegistered) {
        return;
      }

      isPsiListenerRegistered = false;
      PsiManager.getInstance(facet.getModule().getProject()).removePsiTreeChangeListener(myPsiTreeChangeListener);
    }
  }

  @Override
  public void addParent(@NonNull MultiResourceRepository parent) {
    AndroidFacet facet = myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    super.addParent(parent);

    try {
      if (getSampleDataDir(facet, false) != null) {
        registerPsiListener();
      }
    }
    catch (IOException ignore) {
    }
  }

  @Override
  public void removeParent(@NonNull MultiResourceRepository parent) {
    super.removeParent(parent);

    if (!hasParents()) {
      // If we are not attached to any repository, we do not need to listen for changes
      unregisterPsiListener();
    }
  }

  /**
   * Returns if the given {@link VirtualFile} is part of the sample data directory (or the directory itself)
   */
  private static boolean isSampleDataFile(@NotNull AndroidFacet facet, @NotNull VirtualFile file) {
    VirtualFile sampleDataDir = null;
    try {
      sampleDataDir = getSampleDataDir(facet, false);
    }
    catch (IOException e) {
      LOG.warn("Error getting 'sampledir'", e);
    }

    boolean relevant = sampleDataDir != null && VfsUtilCore.isAncestor(sampleDataDir, file, false);
    // Also account for the case where the directory itself is being added or removed
    return relevant || FD_SAMPLE_DATA.equals(file.getName());
  }

  /**
   * Files have been added or removed to the sample data directory
   */
  private void rootsUpdated(@NotNull VirtualFileEvent event) {
    AndroidFacet facet = myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    if (isSampleDataFile(facet, event.getFile())) {
      LOG.debug("sampledata roots updated " + event.getFile());
      invalidate();
    }
  }

  /**
   * Files have been added or removed to the sample data directory
   * @param event
   */
  private void contentsUpdated(@NotNull PsiTreeChangeEvent event) {
    AndroidFacet facet = myAndroidFacet;
    if (facet == null || facet.isDisposed()) {
      return;
    }

    PsiFile psiFile = event.getFile();
    VirtualFile eventFile = psiFile != null ? psiFile.getVirtualFile() : null;
    if (eventFile != null && isSampleDataFile(facet, eventFile)) {
      LOG.debug("sampledata file updated " + eventFile);
      invalidate();
    }
  }

  @NonNull
  @Override
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Nullable
  @Override
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NonNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @NonNull
  @Override
  public Set<ResourceNamespace> getNamespaces() {
    return myFullTable.rowKeySet();
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }

  @Override
  public void dispose() {
    unregisterPsiListener();
    myAndroidFacet = null;
    super.dispose();
  }
}

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
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.android.tools.idea.sampledata.datasource.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.android.SdkConstants.FD_SAMPLE_DATA;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;

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
  /**
   * List of predefined data sources that are always available within studio
   */
  private static final ImmutableList<SampleDataResourceItem> PREDEFINED_SOURCES = ImmutableList.of(
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "full_names", new CombinerDataSource(
      SampleDataResourceRepository.class.getClassLoader().getResourceAsStream("sampleData/names.txt"),
      SampleDataResourceRepository.class.getClassLoader().getResourceAsStream("sampleData/surnames.txt"))),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "first_names", ResourceContent.fromInputStream(
      SampleDataResourceRepository.class.getClassLoader().getResourceAsStream("sampleData/names.txt"))),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "last_names", ResourceContent.fromInputStream(
      SampleDataResourceRepository.class.getClassLoader()
        .getResourceAsStream("sampleData/surnames.txt"))),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "cities", ResourceContent.fromInputStream(
      SampleDataResourceRepository.class.getClassLoader()
        .getResourceAsStream("sampleData/cities.txt"))),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "us_zipcodes",
                                                   new NumberGenerator("%05d", 20000, 99999)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "us_phones",
                                                   new NumberGenerator("(800) 555-%04d", 0, 9999)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "lorem", new LoremIpsumGenerator(false)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "lorem/random", new LoremIpsumGenerator(true)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "avatars",
                                                   ResourceContent.fromDirectory("avatars")),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "backgrounds/scenic",
                                                   ResourceContent.fromDirectory("backgrounds/scenic")),

    // TODO: Delegate path parsing to the data source to avoid all these declarations
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "date/day_of_week",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("E"), ChronoUnit.DAYS)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "date/ddmmyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("dd-MM-yy"), ChronoUnit.DAYS)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "date/mmddyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("MM-dd-yy"), ChronoUnit.DAYS)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "date/hhmm",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm"), ChronoUnit.MINUTES)),
    SampleDataResourceItem.getFromStaticDataSource(TOOLS_NS_NAME_PREFIX + "date/hhmmss",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm:ss"), ChronoUnit.SECONDS)));


  private final ResourceTable myFullTable;
  private AndroidFacet myAndroidFacet;

  /**
   * Returns the "sampledata" directory from the project (if it exists) or null otherwise.
   * @param create when true, if the directory does not exist, it will be created
   */
  @Contract("!null, true -> !null")
  public static VirtualFile getSampleDataDir(@NotNull AndroidFacet androidFacet, boolean create) throws IOException {
    VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(androidFacet);
    if (contentRoot == null) {
      throw new IOException("Unable to find content root");
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

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
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

    PsiManager.getInstance(androidFacet.getModule().getProject()).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
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
    }, this);

    invalidate();
  }

  private static void addItems(@NotNull ImmutableListMultimap.Builder<String, ResourceItem> items, @NotNull PsiFileSystemItem sampleDataFile) {
    try {
      SampleDataResourceItem.getFromPsiFileSystemItem(sampleDataFile).forEach(item -> items.put(item.getName(), item));
    }
    catch (IOException e) {
      LOG.warn("Error loading sample data file " + sampleDataFile.getName(), e);
    }
  }

  private static void addPredefinedItems(@NotNull ImmutableListMultimap.Builder<String, ResourceItem> items) {
    PREDEFINED_SOURCES.forEach(source -> items.put(source.getName(), source));
  }

  /**
   * Invalidates the current sample data of this repository. Call this method after the sample data has been updated to reload the contents.
   */
  private void invalidate() {
    VirtualFile sampleDataDir = null;
    try {
      sampleDataDir = getSampleDataDir(myAndroidFacet, false);
    }
    catch (IOException e) {
      LOG.warn("Error getting 'sampledir'", e);
    }
    myFullTable.clear();

    ImmutableListMultimap.Builder<String, ResourceItem> projectItems = ImmutableListMultimap.builder();

    if (sampleDataDir != null) {
      PsiManager psiManager = PsiManager.getInstance(myAndroidFacet.getModule().getProject());
      Stream<VirtualFile> childrenStream = Arrays.stream(sampleDataDir.getChildren());
      ApplicationManager.getApplication().runReadAction(() -> childrenStream
        .map(vf -> vf.isDirectory() ? psiManager.findDirectory(vf) : psiManager.findFile(vf))
        .filter(Objects::nonNull)
        .forEach(f -> addItems(projectItems, f)));
    }
    addPredefinedItems(projectItems);
    myFullTable.put(null, ResourceType.SAMPLE_DATA, projectItems.build());

    setModificationCount(ourModificationCounter.incrementAndGet());

    invalidateParentCaches(null, ResourceType.SAMPLE_DATA);
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
    if (myAndroidFacet.isDisposed()) {
      return;
    }

    if (isSampleDataFile(myAndroidFacet, event.getFile())) {
      LOG.debug("sampledata roots updated " + event.getFile());
      invalidate();
    }
  }

  /**
   * Files have been added or removed to the sample data directory
   * @param event
   */
  private void contentsUpdated(@NotNull PsiTreeChangeEvent event) {
    if (myAndroidFacet.isDisposed()) {
      return;
    }

    PsiFile psiFile = event.getFile();
    VirtualFile eventFile = psiFile != null ? psiFile.getVirtualFile() : null;
    if (eventFile != null && isSampleDataFile(myAndroidFacet, eventFile)) {
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
  protected ListMultimap<String, ResourceItem> getMap(@Nullable String namespace, @NonNull ResourceType type, boolean create) {
    return myFullTable.get(namespace, type);
  }

  @NonNull
  @Override
  public Set<String> getNamespaces() {
    return Collections.emptySet();
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }

  @Override
  public void dispose() {
    myAndroidFacet = null;
    super.dispose();
  }
}

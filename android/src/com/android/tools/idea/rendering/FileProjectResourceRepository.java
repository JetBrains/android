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
import com.android.builder.model.SourceProvider;
import com.android.ide.common.res2.DuplicateDataException;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.res2.ResourceMerger;
import com.android.ide.common.res2.ResourceSet;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.util.Pair;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.variant.view.BuildVariantView.BuildVariantSelectionChangeListener;

@SuppressWarnings("deprecation") // Deprecated com.android.util.Pair is required by ProjectCallback interface
class FileProjectResourceRepository extends ProjectResources {
  private static final Logger LOG = Logger.getInstance(FileProjectResourceRepository.class);
  protected final Module myModule;

  // Project resource ints are defined as 0x7FXX#### where XX is the resource type (layout, drawable,
  // etc...). Using FF as the type allows for 255 resource types before we get a collision
  // which should be fine.
  private static final int DYNAMIC_ID_SEED_START = 0x7fff0000;
  private ResourceMerger myResourceMerger;

  private final IntArrayWrapper myWrapper = new IntArrayWrapper(null);
  /** Map of (name, id) for resources of type {@link ResourceType#ID} coming from R.java */
  private Map<ResourceType, TObjectIntHashMap<String>> myResourceValueMap;
  /** Map of (id, [name, resType]) for all resources coming from R.java */
  private TIntObjectHashMap<Pair<ResourceType, String>> myResIdValueToNameMap;
  /** Map of (int[], name) for styleable resources coming from R.java */
  private Map<IntArrayWrapper, String> myStyleableValueToNameMap;

  private final TObjectIntHashMap<String> myName2DynamicIdMap = new TObjectIntHashMap<String>();
  private final TIntObjectHashMap<Pair<ResourceType, String>> myDynamicId2ResourceMap =
    new TIntObjectHashMap<Pair<ResourceType, String>>();
  private int myDynamicSeed = DYNAMIC_ID_SEED_START;

  private final PsiListener myListener;

  private FileProjectResourceRepository(@NotNull Module module, @NotNull ResourceMerger resourceMerger) {
    myModule = module;
    myResourceMerger = resourceMerger;

    myListener = new PsiListener();

    // TODO: There should only be one of these per project, not per module!
    PsiManager.getInstance(module.getProject()).addPsiTreeChangeListener(myListener);
    // TODO: Register with Disposer.register
  }

  @NotNull
  static FileProjectResourceRepository create(@NotNull final AndroidFacet facet) {
    boolean refresh = facet.isGradleProject() && facet.getIdeaAndroidProject() == null;
    ResourceMerger resourceMerger = createResourceMerger(facet);
    final FileProjectResourceRepository repository = new FileProjectResourceRepository(facet.getModule(), resourceMerger);
    try {
      resourceMerger.mergeData(repository.getMergeConsumer(), true /*doCleanUp*/);
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    // If the model is not yet ready, we may get an incomplete set of resource
    // directories, so in that case update the repository when the model is available.
    // TODO: Only refresh if the set of resource directories has actually changed!
    if (refresh) {
      facet.addListener(new AndroidFacet.GradleProjectAvailableListener() {
        @Override
        public void gradleProjectAvailable(@NotNull IdeaAndroidProject project) {
          facet.removeListener(this);
          repository.refresh();
        }
      });
    }

    // Also refresh the project resources whenever the variant changes
    if (facet.isGradleProject()) {
      BuildVariantView.getInstance(facet.getModule().getProject()).addListener(new BuildVariantSelectionChangeListener() {
        @Override
        public void buildVariantSelected(@NotNull AndroidFacet facet) {
          repository.refresh();
        }
      });
    }

    return repository;
  }

  private static ResourceMerger createResourceMerger(AndroidFacet facet) {
    ResourceMerger resourceMerger = new ResourceMerger();
    addAllSources(resourceMerger, facet);
    return resourceMerger;
  }

  private static void addAllSources(ResourceMerger resourceMerger, AndroidFacet facet) {
    ILogger logger = new LogWrapper(LOG);
    addSources(resourceMerger, facet.getMainSourceSet(), "main", logger);
    List<com.intellij.openapi.util.Pair<String,SourceProvider>> flavors = facet.getFlavorSourceSetsAndNames();
    if (flavors != null) {
      for (com.intellij.openapi.util.Pair<String,SourceProvider> pair : flavors) {
        String flavorName = pair.getFirst();
        SourceProvider provider = pair.getSecond();
        addSources(resourceMerger, provider, flavorName, logger);
      }
    }
    String buildTypeName = facet.getBuildTypeName();
    if (buildTypeName != null) {
      SourceProvider provider = facet.getBuildTypeSourceSet();
      if (provider != null) {
        addSources(resourceMerger, provider, buildTypeName, logger);
      }
    }
  }

  private static void addSources(ResourceMerger merger, SourceProvider provider, String name, ILogger logger) {
    ResourceSet resourceSet = new ResourceSet(name) {
      @Override
      protected void checkItems() throws DuplicateDataException {
        // No checking in ProjectResources; duplicates can happen, but
        // the project resources shouldn't abort initialization
      }
    };
    resourceSet.addSources(provider.getResDirectories());
    try {
      resourceSet.loadFromFiles(logger);
    }
    catch (DuplicateDataException e) {
      // This should not happen; we've subclasses ResourceSet above to a no-op in checkItems
      assert false;
    }
    catch (IOException e) {
      LOG.error("Failed to initialize resources", e);
    }
    merger.addDataSet(resourceSet);
  }

  @Override
  public void dispose() {
    PsiManager.getInstance(myModule.getProject()).addPsiTreeChangeListener(myListener);
  }

  @Override
  @Nullable
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> result = null;
    if (myResIdValueToNameMap != null) {
      result = myResIdValueToNameMap.get(id);
    }

    if (result == null) {
      final Pair<ResourceType, String> pair = myDynamicId2ResourceMap.get(id);
      if (pair != null) {
        result = pair;
      }
    }

    return result;
  }

  @Override
  @Nullable
  public String resolveStyleable(int[] id) {
    if (myStyleableValueToNameMap != null) {
      myWrapper.set(id);
      // A normal map lookup on int[] would only consider object identity, but the IntArrayWrapper
      // will check all the individual elements for equality. We reuse an instance for all the lookups
      // since we don't need a new one each time.
      return myStyleableValueToNameMap.get(myWrapper);
    }

    return null;
  }

  @Override
  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    final TObjectIntHashMap<String> map = myResourceValueMap != null ? myResourceValueMap.get(type) : null;

    if (map == null || !map.containsKey(name)) {
      return getDynamicId(type, name);
    }
    return map.get(name);
  }

  @Override
  public void refresh() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    ResourceMerger resourceMerger = createResourceMerger(facet);
    clear();
    try {
      resourceMerger.mergeData(getMergeConsumer(), true /*doCleanUp*/);
      mergeIds();
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    myResourceMerger = resourceMerger;
    myGeneration++;
  }

  @Override
  public void sync() {
    if (!myHaveDirtyFiles) {
      return;
    }

    // Longer term I want to *directly* merge from PSI elements; no XML parsing etc.
    List<File> addedFiles = null;
    if (!myAddedFiles.isEmpty()) {
      addedFiles = Lists.newArrayListWithExpectedSize(myAddedFiles.size());
      for (VirtualFile file : myAddedFiles) {
        myChangedFiles.remove(file);
        addedFiles.add(VfsUtilCore.virtualToIoFile(file));
      }
    }
    List<File> changedFiles = null;
    if (!myChangedFiles.isEmpty()) {
      changedFiles = Lists.newArrayListWithExpectedSize(myChangedFiles.size());
      for (VirtualFile file : myChangedFiles) {
        changedFiles.add(VfsUtilCore.virtualToIoFile(file));
      }
    }

    ILogger logger = new LogWrapper(LOG);
    boolean newGeneration = false;

    for (ResourceSet set : myResourceMerger.getDataSets()) {
      for (File root : set.getSourceFiles()) {
        String rootPath = root.getPath();

        if (addedFiles != null) {
          assert !addedFiles.isEmpty();
          newGeneration = true;
          for (File file : addedFiles) {
            if (file.getPath().startsWith(rootPath)) {
              try {
                set.updateWith(root, file, FileStatus.NEW, logger);
              }
              catch (IOException e) {
                LOG.error("Can't update new file " + file, e);
              }
            }
          }
        }

        if (changedFiles != null) {
          for (File file : changedFiles) {
            if (file.getPath().startsWith(rootPath)) {
              try {
                set.updateWith(root, file, FileStatus.CHANGED, logger);
                if (!newGeneration) {
                  String parentName = file.getParentFile().getName();
                  if (parentName.startsWith(FD_RES_VALUES)) {
                    newGeneration = true;
                  }
                }
              }
              catch (IOException e) {
                LOG.error("Can't update changed file " + file, e);
              }
            }
          }
        }

        if (!myDeletedFiles.isEmpty()) {
          newGeneration = true;
          for (File file : myDeletedFiles) {
            if (file.getPath().startsWith(rootPath)) {
              try {
                set.updateWith(root, file, FileStatus.REMOVED, logger);
              }
              catch (IOException e) {
                LOG.error("Can't update deleted file " + file, e);
              }
            }
          }
        }
      }
    }

    try {
      myResourceMerger.mergeData(getMergeConsumer(), true /*doCleanUp*/);
      mergeIds();
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    myHaveDirtyFiles = false;
    myAddedFiles.clear();
    myChangedFiles.clear();
    myDeletedFiles.clear();

    if (newGeneration) {
      myGeneration++;
    }
  }

  private int getDynamicId(ResourceType type, String name) {
    synchronized (myName2DynamicIdMap) {
      if (myName2DynamicIdMap.containsKey(name)) {
        return myName2DynamicIdMap.get(name);
      }
      final int value = ++myDynamicSeed;
      myName2DynamicIdMap.put(name, value);
      myDynamicId2ResourceMap.put(value, Pair.of(type, name));
      return value;
    }
  }

  @Override
  public void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                   Map<IntArrayWrapper, String> styleableId2name,
                                   Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    // Regularly clear dynamic seed such that we don't run out of numbers (we only have 255)
    myDynamicSeed = DYNAMIC_ID_SEED_START;
    myName2DynamicIdMap.clear();
    myDynamicId2ResourceMap.clear();

    myResourceValueMap = res2id;
    myResIdValueToNameMap = id2res;
    myStyleableValueToNameMap = styleableId2name;
    sync();
    mergeIds();
  }


  @NonNull
  @Override
  public SortedSet<String> getRegions(@NonNull String currentLanguage) {
    sync();
    return super.getRegions(currentLanguage);
  }

  // Code related to updating the resources

  private boolean myHaveDirtyFiles;
  private Set<VirtualFile> myAddedFiles = Sets.newHashSet();
  private Set<VirtualFile> myChangedFiles = Sets.newHashSet();

  // For deleted files we need to store the path, since the file
  // no longer exist (if you for example rename a file, the VirtualFile
  // will stay the same and its name change to the new name instead,
  // so if we store the "old" file in the deleted list, we'll really look
  // up the new path when processing it.
  private Set<File> myDeletedFiles = Sets.newHashSet();

  private boolean isResourceFolder(@Nullable PsiElement parent) {
    // Returns true if the given element represents a resource folder (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder)
    if (parent instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)parent;
      PsiDirectory parentDirectory = directory.getParentDirectory();
      if (parentDirectory != null) {
        VirtualFile dir = parentDirectory.getVirtualFile();
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        if (facet != null) {
          return facet.getLocalResourceManager().isResourceDir(dir);
        }
      }
    }
    return false;
  }

  private static boolean isRelevantFileType(@NotNull FileType fileType) {
    return fileType == StdFileTypes.XML ||
           (fileType.isBinary() && fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG));
  }

  private static boolean isRelevantFile(@NotNull VirtualFile file) {
    return isRelevantFileType(file.getFileType());
  }

  private static boolean isRelevantFile(@NotNull PsiFile file) {
    return isRelevantFileType(file.getFileType());
  }

  private final class PsiListener implements PsiTreeChangeListener {
    private boolean myIgnoreChildrenChanged;

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          VirtualFile file = ((PsiFile)child).getVirtualFile();
          if (file != null && isRelevantFile(file) && isResourceFolder(event.getParent())) {
            myAddedFiles.add(file);
            myHaveDirtyFiles = true;
          }
        } else if (child instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)child;
          if (isResourceFolder(directory)) {
            for (PsiFile file : directory.getFiles()) {
              VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                myAddedFiles.add(virtualFile);
                myHaveDirtyFiles = true;
              }
            }
          }
        }
      } else if (isRelevantFile(psiFile)) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          myChangedFiles.add(virtualFile);
          myHaveDirtyFiles = true;
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          VirtualFile file = ((PsiFile)child).getVirtualFile();
          if (file != null && isRelevantFile(file) && isResourceFolder(event.getParent())) {
            myDeletedFiles.add(VfsUtilCore.virtualToIoFile(file));
            myHaveDirtyFiles = true;
          }
        }// else if (child instanceof PsiDirectory) {
        //   TODO: We can't iterate the children here and record them in myDeletedFiles because dir is empty
        //   (even if we do this in #beforeChildRemoval.
        //   Fix this during the full PSI rewrite.
        //}
      } else if (isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          myChangedFiles.add(file);
          myHaveDirtyFiles = true;
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      // TODO: If getOldChild() is a PsiWhiteSpace and getNewChild() is PsiWhiteSpace, do nothing
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        if (isRelevantFile(psiFile)) {
          VirtualFile file = psiFile.getVirtualFile();
          if (file != null) {
            myChangedFiles.add(file);
            myHaveDirtyFiles = true;
          }
        }
      } else {
        // TODO: Check how renaming is affected by this. Do I get children change notifications for
        // children of a PsiDirectory?
        Throwable throwable = new Throwable();
        throwable.fillInStackTrace();
        LOG.debug("Got childReplaced event for inter-file operations; TODO: investigate", throwable);
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        if (child instanceof PsiFile && isRelevantFile((PsiFile)child)) {
          if (isResourceFolder(event.getNewParent())) {
            VirtualFile file = ((PsiFile)child).getVirtualFile();
            if (file != null) {
              myAddedFiles.add(file);
              myHaveDirtyFiles = true;
            }
          }

          PsiElement oldParent = event.getOldParent();
          if (oldParent instanceof PsiDirectory) {
            PsiDirectory directory = (PsiDirectory)oldParent;
            VirtualFile dir = directory.getVirtualFile();
            myDeletedFiles.add(new File(VfsUtilCore.virtualToIoFile(dir), ((PsiFile)child).getName()));
            myHaveDirtyFiles = true;
          }
        }
      } else {
        // Change inside a file
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null && isRelevantFile(file)) {
          myChangedFiles.add(file);
          myHaveDirtyFiles = true;
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public final void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = false;
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      if (myIgnoreChildrenChanged) {
        // We've already processed this change as one or more individual childMoved, childAdded, childRemoved etc calls
        return;
      }

      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          myChangedFiles.add(file);
          myHaveDirtyFiles = true;
        }
      } else {
        Throwable throwable = new Throwable();
        throwable.fillInStackTrace();
        LOG.debug("Got childrenChanged event for inter-file operations; TODO: investigate", throwable);
      }
    }

    @Override
    public final void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName()) {
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
            VirtualFile file = psiFile.getVirtualFile();
            if (file != null) {
              myDeletedFiles.add(VfsUtilCore.virtualToIoFile(file));
              myHaveDirtyFiles = true;
            }
          }
        }
        // The new name will be added in the post hook (propertyChanged rather than beforePropertyChange)
      }
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName() && isResourceFolder(event.getParent())) {
        PsiElement child = event.getElement();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
            VirtualFile file = psiFile.getVirtualFile();
            if (file != null) {
              myAddedFiles.add(file);
              myHaveDirtyFiles = true;
            }
          }
        }
      }

      // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource folders?
      // and what about PROP_FILE_TYPES -- can users change the type of an XML File to something else?
    }

    // Before-hooks: We don't care about these.

    @Override public final void beforeChildAddition(@NotNull PsiTreeChangeEvent event) { }
    @Override public final void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) { }
    @Override public final void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) { }
    @Override public final void beforeChildMovement(@NotNull PsiTreeChangeEvent event) { }
  }
}

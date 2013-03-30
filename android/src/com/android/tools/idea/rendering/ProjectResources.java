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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.*;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.BufferingFolderWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.EXT_PNG;
import static com.android.SdkConstants.FD_RESOURCES;

/**
 * Repository tracking project-local resources.
 * <p>
 * TODO:
 * <ul>
 *   <li>Rather than calling ResourceRepository's processFile methods, which will scan files
 *       from disk (using their own parser), etc., use the PSI structure directly. During
 *       PSI updates, we can mutate the repository model directly (and ignore changes that don't matter,
 *       e.g. a whitespace only change)</li>
 *   <li>The current handling of ProjectResources (the gather() call which performs the initial
 *       population) seems to aggregate all library projects into this ProjectResource object. That's
 *       not ideal; it means that if you make a change, all the downstream project resource objects
 *       should be updated too. We should switch this such that each ProjectResource manages ONLY
 *       their own local resources, and the code which performs a getConfiguredResources map lookup
 *       for e.g. rendering, THAT code should go and combine maps and perform overlays.</li>
 *   <li>The code uses the old res folder style from the traditional ADT directory layout. This
 *       will need to be updated to handle the new gradle based file system with resource merging.</li>
 *   <li>The PSI listeners which invalidate the model are currently only working for XML resources;
 *       they need to look for changes to .png, .gif and /.jpg/.jpeg files as well</li>
 *   <li>Get rid of the NullFolderWrapper stuff</li>
 *   <li>We should add a listener scheme. First, the initial loading of the project resources may
 *       be slow for a large project, so we should allow async loading and notification. Second,
 *       we should allow clients to know whether the resources have been updated (e.g. if you
 *       edit a strings.xml file, we should re-render the layout when the model is updated).</li>
 *   <li>We should consider allowing the project resources to be stored in a persistent index
 *       such that they can be retrieved quickly</li>
 *   <li>The ResourceResolvers should be cached as well.</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("deprecation") // The Pair class is required by the IProjectCallback
public class ProjectResources extends ResourceRepository implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ProjectResources");

  // Project resource ints are defined as 0x7FXX#### where XX is the resource type (layout, drawable,
  // etc...). Using FF as the type allows for 255 resource types before we get a collision
  // which should be fine.
  private static final int DYNAMIC_ID_SEED_START = 0x7fff0000;
  private final Module myModule;

  private final IntArrayWrapper myWrapper = new IntArrayWrapper(null);
  private Map<ResourceType, TObjectIntHashMap<String>> myResourceValueMap;
  private TIntObjectHashMap<Pair<ResourceType, String>> myResIdValueToNameMap;
  private Map<IntArrayWrapper, String> myStyleableValueToNameMap;

  private final TObjectIntHashMap<String> myName2DynamicIdMap = new TObjectIntHashMap<String>();
  private final TIntObjectHashMap<Pair<ResourceType, String>> myDynamicId2ResourceMap =
    new TIntObjectHashMap<Pair<ResourceType, String>>();
  private int myDynamicSeed = DYNAMIC_ID_SEED_START;

  private final List<ProjectResources> myLibResources;
  private final PsiListener myListener;
  private int myGeneration;

  public ProjectResources(@NotNull Module module, @NotNull IAbstractFolder resFolder, @NotNull List<ProjectResources> libResources) {
    super(resFolder, false);
    myModule = module;
    myLibResources = libResources;

    myListener = new PsiListener();
    PsiManager.getInstance(module.getProject()).addPsiTreeChangeListener(myListener);
    // TODO: Register with Disposer.register
  }

  @Nullable
  public static ProjectResources get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return facet.getProjectResources();
    }

    return null;
  }

  /**
   * Returns the current generation of the project resources. Any time the project resources are updated,
   * the generation increases. This can be used to force refreshing of layouts etc (which will cache
   * configured project resources) when the project resources have changed since last render.
   * <p>
   * Note that the generation is not a change count. If you change the contents of a layout drawable XML file,
   * that will not affect the {@link ResourceItem} and {@link ResourceValue} results returned from
   * this repository; we only store the presence of file based resources like layouts, menus, and drawables.
   * Therefore, only additions or removals of these files will cause a generation change.
   * <p>
   * Value resource files, such as string files, will cause generation changes when they are edited.
   * Later we should consider only updating the generation when the actual values are changed (such that
   * we can ignore whitespace changes, comment changes, reordering changes (outside of arrays), and so on.
   * The natural time to implement this is when we reimplement this class to directly work on top of
   * the PSI data structures, rather than simply using a PSI listener and calling super methods to
   * process ResourceFile objects as is currently done.
   *
   * @return the generation id
   */
  public int getGeneration() {
    // First sync in case there are pending changes which will rev the generation
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return myGeneration;
  }

  @Override
  public void dispose() {
    PsiManager.getInstance(myModule.getProject()).addPsiTreeChangeListener(myListener);
  }

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

  @Nullable
  public String resolveStyleable(int[] id) {
    if (myStyleableValueToNameMap != null) {
      myWrapper.set(id);
      return myStyleableValueToNameMap.get(myWrapper);
    }

    return null;
  }

  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    final TObjectIntHashMap<String> map = myResourceValueMap != null ? myResourceValueMap.get(type) : null;

    if (map == null || !map.containsKey(name)) {
      return getDynamicId(type, name);
    }
    return map.get(name);
  }

  @NotNull
  @Override
  protected ResourceItem createResourceItem(@NonNull String name) {
    return new ResourceItem(name);
  }

  @NonNull
  @Override
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredResources(@NonNull FolderConfiguration referenceConfig) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    final Map<ResourceType, Map<String, ResourceValue>> result = new HashMap<ResourceType, Map<String, ResourceValue>>();

    for (int i = myLibResources.size() - 1; i >= 0; i--) {
      putAllResourceEntries(result, myLibResources.get(i).doGetConfiguredResources(referenceConfig));
    }
    putAllResourceEntries(result, doGetConfiguredResources(referenceConfig));
    return result;
  }

  private void syncDirtyFiles() {
    if (!myHaveDirtyFiles) {
      return;
    }

    // Clear flag right away such that we don't end up syncing again during initialization
    myHaveDirtyFiles = false;
    boolean newGeneration = false;

    ScanningContext context = new ScanningContext(this);

    if (!myDeletedFiles.isEmpty()) {
      // First process
      for (File file : myDeletedFiles) {
        ResourceFile resourceFile = findResourceFile(file);
        if (resourceFile != null) {
          ResourceFolder folder = resourceFile.getFolder();
          folder.processFile(resourceFile.getFile(), ResourceDeltaKind.REMOVED, context);
          removeFile(resourceFile.getResourceTypes(), resourceFile);
        }
      }
      myDeletedFiles.clear();
      newGeneration = true;
    }

    if (!myAddedFiles.isEmpty()) {
      // First process
      for (VirtualFile file : myAddedFiles) {
        myChangedFiles.remove(file);
        ResourceFile resourceFile = getResourceFile(file);
        if (resourceFile != null) {
          ResourceFolder folder = resourceFile.getFolder();
          folder.processFile(resourceFile.getFile(), ResourceDeltaKind.ADDED, context);
        }
      }
      myAddedFiles.clear();
      newGeneration = true;
    }

    if (!myChangedFiles.isEmpty()) {
      // First process
      for (VirtualFile file : myChangedFiles) {
        ResourceFile resourceFile = getResourceFile(file);
        if (resourceFile != null) {
          ResourceFolder folder = resourceFile.getFolder();
          folder.processFile(resourceFile.getFile(), ResourceDeltaKind.CHANGED, context);
          if (ResourceFolderType.VALUES == folder.getType()) {
            // Changing the contents of a layout file, or drawable file etc, doesn't
            // change the project resources; we only store the presence or absence of
            // these types of resources.
            newGeneration = true;
          }
        }
      }
      myChangedFiles.clear();
    }

    if (newGeneration) {
      myGeneration++;
    }
  }

  @Nullable
  private ResourceFile getResourceFile(VirtualFile file) {
    return findResourceFile(VfsUtilCore.virtualToIoFile(file));
  }

  private static void putAllResourceEntries(Map<ResourceType, Map<String, ResourceValue>> result,
                                            Map<ResourceType, Map<String, ResourceValue>> from) {
    for (Map.Entry<ResourceType, Map<String, ResourceValue>> entry : from.entrySet()) {
      Map<String, ResourceValue> map = result.get(entry.getKey());

      if (map == null) {
        map = new HashMap<String, ResourceValue>();
        result.put(entry.getKey(), map);
      }
      map.putAll(entry.getValue());
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

  public void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                   Map<IntArrayWrapper, String> styleableid2name,
                                   Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    myResourceValueMap = res2id;
    myResIdValueToNameMap = id2res;
    myStyleableValueToNameMap = styleableid2name;
    mergeIdResources();
  }

  @Override
  public void postUpdateCleanUp() {
    super.postUpdateCleanUp();
    mergeIdResources();
  }

  private void mergeIdResources() {
    if (myResourceValueMap == null) {
      return;
    }

    Map<String, ResourceItem> resources = mResourceMap.get(ResourceType.ID);
    final TObjectIntHashMap<String> name2id = myResourceValueMap.get(ResourceType.ID);

    if (name2id != null) {
      final TObjectIntHashMap<String> copy;

      if (resources == null) {
        resources = new HashMap<String, ResourceItem>(name2id.size());
        mResourceMap.put(ResourceType.ID, resources);
        copy = name2id;
      }
      else {
        copy = new TObjectIntHashMap<String>(name2id);

        final List<ResourceItem> resList = new ArrayList<ResourceItem>(resources.values());
        for (ResourceItem item : resList) {
          String name = item.getName();
          if (item.isDeclaredInline()) {
            if (copy.contains(name)) {
              copy.remove(name);
            }
            else {
              resources.remove(item.getName());
            }
          }
          else {
            copy.remove(name);
          }
        }
      }

      for (Object name : copy.keys()) {
        resources.put((String)name, new InlineResourceItem((String)name));
      }
    }
  }

  public static ProjectResources create(AndroidFacet facet) {
    return gather(facet.getModule(), facet);
  }

  private static ProjectResources gather(Module module, AndroidFacet facet) {
    List<AndroidFacet> allLibraries = AndroidUtils.getAllAndroidDependencies(module, true);
    List<ProjectResources> libResources = new ArrayList<ProjectResources>();
    List<ProjectResources> emptyResList = Collections.emptyList();

    for (AndroidFacet libFacet : allLibraries) {
      if (!libFacet.equals(facet)) {
        libResources.add(loadProjectResources(libFacet, emptyResList));
      }
    }
    return loadProjectResources(facet, libResources);
  }

  @NotNull
  private static ProjectResources loadProjectResources(@NotNull AndroidFacet facet,
                                                       @NotNull List<ProjectResources> libResources) {
    final VirtualFile resourceDir = facet.getLocalResourceManager().getResourceDir();
    if (resourceDir != null) {
      final IAbstractFolder resFolder = new BufferingFolderWrapper(new File(FileUtil.toSystemDependentName(resourceDir.getPath())));
      final ProjectResources
        projectResources = new ProjectResources(facet.getModule(), resFolder, libResources);
      loadResources(projectResources, resFolder);
      return projectResources;
    }
    return new ProjectResources(facet.getModule(), new NullFolderWrapper(), libResources);
  }

  private static void loadResources(@NotNull ResourceRepository repository,
                                    @NotNull IAbstractFolder... rootFolders) {
    final ScanningContext scanningContext = new ScanningContext(repository);

    for (IAbstractFolder rootFolder : rootFolders) {
      for (IAbstractResource file : rootFolder.listMembers()) {
        if (!(file instanceof IAbstractFolder)) {
          continue;
        }
        final IAbstractFolder folder = (IAbstractFolder)file;
        final ResourceFolder resFolder = repository.processFolder(folder);
        if (resFolder != null) {
          for (final IAbstractResource childRes : folder.listMembers()) {
            if (childRes instanceof IAbstractFile) {
              resFolder.processFile((IAbstractFile)childRes, ResourceDeltaKind.ADDED, scanningContext);
            }
          }
        }
      }
    }

    final List<String> errors = scanningContext.getErrors();
    if (errors != null && errors.size() > 0) {
      LOG.debug(new RenderingException(merge(errors)));
    }
  }

  private static String merge(@NotNull Collection<String> strs) {
    final StringBuilder result = new StringBuilder();
    for (Iterator<String> it = strs.iterator(); it.hasNext(); ) {
      String str = it.next();
      result.append(str);
      if (it.hasNext()) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  @Override
  public boolean hasResourceItem(@NonNull String url) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }
    return super.hasResourceItem(url);
  }

  @Override
  public boolean hasResourceItem(@NonNull ResourceType type, @NonNull String name) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.hasResourceItem(type, name);
  }

  @Override
  @NonNull
  protected ResourceItem getResourceItem(@NonNull ResourceType type, @NonNull String name) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.getResourceItem(type, name);
  }

  @Override
  @NonNull
  public Collection<ResourceItem> getResourceItemsOfType(@NonNull ResourceType type) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.getResourceItemsOfType(type);
  }

  @Override
  public boolean hasResourcesOfType(@NonNull ResourceType type) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.hasResourcesOfType(type);
  }

  @Override
  @NonNull
  public SortedSet<String> getLanguages() {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.getLanguages();
  }

  @Override
  @NonNull
  public SortedSet<String> getRegions(@NonNull String currentLanguage) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.getRegions(currentLanguage);
  }

  @Override
  @com.android.annotations.Nullable
  public ResourceFile getMatchingFile(@NonNull String name, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.getMatchingFile(name, type, config);
  }

  @com.android.annotations.Nullable
  @Override
  public ResourceFile getMatchingFile(@NonNull String name, @NonNull ResourceFolderType type, @NonNull FolderConfiguration config) {
    if (!ensureInitialized()) {
      syncDirtyFiles();
    }

    return super.getMatchingFile(name, type, config);
  }

  private static class NullFolderWrapper implements IAbstractFolder {
    @Override
    public boolean hasFile(String name) {
      return false;
    }

    @com.android.annotations.Nullable
    @Override
    public IAbstractFile getFile(String name) {
      return null;
    }

    @com.android.annotations.Nullable
    @Override
    public IAbstractFolder getFolder(String name) {
      return null;
    }

    @Override
    public IAbstractResource[] listMembers() {
      return new IAbstractResource[0];
    }

    @Override
    public String[] list(FilenameFilter filter) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public String getName() {
      return "stub_name";
    }

    @Override
    public String getOsLocation() {
      return "stub_os_location";
    }

    @Override
    public boolean exists() {
      return false;
    }

    @com.android.annotations.Nullable
    @Override
    public IAbstractFolder getParentFolder() {
      return null;
    }

    @Override
    public boolean delete() {
      return false;
    }
  }

  private static boolean isResourceFolder(@Nullable PsiElement parent) {
    // Returns true if the given element represents a resource folder (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder)
    if (parent instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)parent;
      PsiDirectory parentDirectory = directory.getParentDirectory();
      // TODO: This will need work when we support the new build system with its multiple resource directories
      if (parentDirectory != null && FD_RESOURCES.equals(parentDirectory.getName())) {
        return true;
      }
    }
    return false;
  }


  private boolean myHaveDirtyFiles;
  private Set<VirtualFile> myAddedFiles = Sets.newHashSet();
  private Set<VirtualFile> myChangedFiles = Sets.newHashSet();

  // For deleted files we need to store the path, since the file
  // no longer exist (if you for example rename a file, the VirtualFile
  // will stay the same and its name change to the new name instead,
  // so if we store the "old" file in the deleted list, we'll really look
  // up the new path when processing it.
  private Set<File> myDeletedFiles = Sets.newHashSet();

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

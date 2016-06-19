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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.*;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.ILogger;
import com.google.common.collect.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.resources.ResourceFolderType.*;
import static com.android.tools.lint.detector.api.LintUtils.stripIdPrefix;

/**
 * Remaining work:
 * <ul>
 *   <li>Find some way to have event updates in this resource folder directly update parent repositories
 *   (typically {@link ModuleResourceRepository}</li>
 *   <li>Add defensive checks for non-read permission reads of resource values</li>
 *   <li>Idea: For {@link #rescan}; compare the removed items from the added items, and if they're the same, avoid
 *   creating a new generation.</li>
 *   <li>Register the psi project listener as a project service instead</li>
 * </ul>
 */
public final class ResourceFolderRepository extends LocalResourceRepository {
  private static final Logger LOG = Logger.getInstance(ResourceFolderRepository.class);
  private final Module myModule;
  private final AndroidFacet myFacet;
  private final PsiListener myListener;
  private final VirtualFile myResourceDir;
  private final String myLibraryName;
  private final Map<ResourceType, ListMultimap<String, ResourceItem>> myItems = Maps.newEnumMap(ResourceType.class);
  private final Map<VirtualFile, ResourceFile> myResourceFiles = Maps.newHashMap();
  // qualifiedName -> PsiResourceFile
  private Map<String, DataBindingInfo> myDataBindingResourceFiles = Maps.newHashMap();
  private long myDataBindingResourceFilesModificationCount = Long.MIN_VALUE;
  private final Object SCAN_LOCK = new Object();
  private Set<PsiFile> myPendingScans;
  private InitialScanState myInitialInitialScanState;

  @VisibleForTesting
  static int ourFullRescans;

  private ResourceFolderRepository(@NotNull AndroidFacet facet, @NotNull VirtualFile resourceDir, @Nullable String libraryName) {
    super(resourceDir.getName());
    myFacet = facet;
    myModule = facet.getModule();
    myListener = new PsiListener();
    myResourceDir = resourceDir;
    myLibraryName = libraryName;

    ResourceMerger merger = loadPreviousStateIfExists();
    myInitialInitialScanState = new InitialScanState(merger, VfsUtilCore.virtualToIoFile(myResourceDir));
    scanRemainingFiles();
    Application app = ApplicationManager.getApplication();
    // For now, automatically save the state. We may want to move this out to a separate task.
    if (!hasFreshFileCache() && !app.isUnitTestMode()) {
      saveStateToFile();
    }
    // Clear some unneeded state (resource merger holds a second map of items).
    // Skip for unit tests, which may need to test saving separately (saving is normally skipped for unit tests).
    if (!app.isUnitTestMode()) {
      myInitialInitialScanState.clearLoaderState();
    }
  }

  @NotNull
  AndroidFacet getFacet() {
    return myFacet;
  }

  VirtualFile getResourceDir() {
    return myResourceDir;
  }

  /** NOTE: You should normally use {@link ResourceFolderRegistry#get} rather than this method. */
  @NotNull
  static ResourceFolderRepository create(@NotNull final AndroidFacet facet, @NotNull VirtualFile dir, @Nullable String libraryName) {
    return new ResourceFolderRepository(facet, dir, libraryName);
  }

  /**
   * Saves the non-Psi XML state as a single blob for faster loading the second time
   * by {@link #loadPreviousStateIfExists}.
   */
  void saveStateToFile() {
    File blobRoot = ResourceFolderRepositoryFileCacheService.get().getResourceDir(myModule.getProject(), myResourceDir);
    if (blobRoot == null) {
      // The cache is invalid, do nothing
      return;
    }

    try {
      ResourcePreprocessor preprocessor = new NoOpResourcePreprocessor();
      MergeConsumer<ResourceItem> consumer = MergedResourceWriter.createWriterWithoutPngCruncher(
        blobRoot, null, null, preprocessor, FileUtil.createTempDirectory("resource", "tmp", true));
      myInitialInitialScanState.myResourceMerger.writeBlobToWithTimestamps(blobRoot, consumer);
    }
    catch (MergingException|IOException e) {
      LOG.error("Failed to saveStateToFile", e);
      // Delete the blob root just in case it's in an inconsistent state.
      FileUtil.delete(blobRoot);
    }
  }

  /**
   * Reloads ResourceFile and ResourceItems which have not changed since the last {@link #saveStateToFile}.
   * Some Resource file and items may not be covered, so {@link #scanRemainingFiles} should be run
   * to load the rest of the items.
   *
   * @return the loaded ResourceMerger -- this can be used to save state again, if the cache isn't fresh
   */
  private ResourceMerger loadPreviousStateIfExists() {
    File blobRoot = ResourceFolderRepositoryFileCacheService.get().getResourceDir(
      myModule.getProject(), myResourceDir);
    if (blobRoot == null || !blobRoot.exists()) {
      return createFreshResourceMerger();
    }
    ResourceMerger merger = new ResourceMerger(0 /* minSdk */);
    // This load may fail if the data is in an inconsistent state or the xml contains illegal
    // resource names, which the Psi parser would otherwise allow, so load failures are not
    // strictly an error.
    // loadFromBlob will check timestamps for stale data and skip.
    try {
      if (!merger.loadFromBlob(blobRoot, false)) {
        LOG.warn("failed to loadPreviousStateIfExists " + blobRoot);
        return createFreshResourceMerger();
      }
    }
    catch (MergingException e) {
      LOG.warn("failed to loadPreviousStateIfExists " + blobRoot, e);
      return createFreshResourceMerger();
    }
    // This temp resourceFiles set is just to avoid calling VfsUtil#findFileByIoFile a ton.
    Set<ResourceFile> resourceFiles = Sets.newHashSet();
    List<ResourceSet> resourceSets = merger.getDataSets();
    if (resourceSets.size() != 1) {
      LOG.error("Expecting exactly one resource set, but found " + resourceSets.size());
      return createFreshResourceMerger();
    }
    ResourceSet dataSet = resourceSets.get(0);
    List<File> sourceFiles = dataSet.getSourceFiles();
    if (sourceFiles.size() != 1) {
      LOG.error("Expecting exactly source files (res/ directories), but found " + sourceFiles.size());
      return createFreshResourceMerger();
    }
    File myResourceDirFile = VfsUtilCore.virtualToIoFile(myResourceDir);
    // Check that the dataSet we're loading actually corresponds to this resource directory.
    // This could happen if there's a hash collision in naming the cache directory.
    if (!FileUtil.filesEqual(sourceFiles.get(0), myResourceDirFile)) {
      LOG.warn(String.format("source file %1$s, does not match resource dir %2$s",
                             sourceFiles.get(0), myResourceDirFile));
      return createFreshResourceMerger();
    }
    for (Map.Entry<String, ResourceItem> entry : dataSet.getDataMap().entries()) {
      ResourceItem item = entry.getValue();
      ResourceFile file = item.getSource();
      if (file != null) {
        if (!resourceFiles.contains(file)) {
          VirtualFile vFile = VfsUtil.findFileByIoFile(file.getFile(), false);
          if (vFile == null) {
            // Cannot handle this item, mark it ignored to that it doesn't persist.
            item.setIgnoredFromDiskMerge(true);
            continue;
          }
          resourceFiles.add(file);
          myResourceFiles.put(vFile, file);
        }
        ListMultimap<String, ResourceItem> map = getMap(item.getType(), true);
        map.put(item.getName(), item);
      } else {
        // Cannot handle this item, mark it ignored to that it doesn't persist.
        item.setIgnoredFromDiskMerge(true);
      }
    }
    return merger;
  }

  private ResourceMerger createFreshResourceMerger() {
    ResourceMerger merger = new ResourceMerger(0 /* minSdk */);
    ResourceSet myData = new ResourceSet(myResourceDir.getName(), myLibraryName, false /* validateEnabled */);
    File resourceDir = VfsUtilCore.virtualToIoFile(myResourceDir);
    myData.addSource(resourceDir);
    merger.addDataSet(myData);
    return merger;
  }

  /**
   * Determine if it's unnecessary to write or update the file-backed cache.
   * If only a few items are reparsed, then the cache is fresh enough.
   *
   * @return true if this repo is backed by a fresh file cache
   */
  boolean hasFreshFileCache() {
    return myInitialInitialScanState.numXmlReparsed * 4 <= myInitialInitialScanState.numXml;
  }

  @VisibleForTesting
  InitialScanState getInitialInitialScanState() { return myInitialInitialScanState; }

  /**
   * Tracks state used by the initial scan, which may be used to save the state to a cache.
   *
   * This also tracks how fresh the repo file-cache is by tracking how many xml file were reparsed during scan.
   * The file cache omits non-XML single-file items, since those are easily derived from the file path.
   */
  static class InitialScanState {
    int numXml; // Doesn't count files that are explicitly skipped
    int numXmlReparsed;

    ResourceMerger myResourceMerger;
    ResourceSet myResourceSet;
    ILogger myILogger;
    File myResourceDir;

    public InitialScanState(ResourceMerger merger, File resourceDir) {
      myResourceMerger = merger;
      assert myResourceMerger.getDataSets().size() == 1;
      myResourceSet = myResourceMerger.getDataSets().get(0);
      myResourceSet.setShouldParseResourceIds(true);
      myResourceSet.setDontNormalizeQualifiers(true);
      myResourceSet.setTrackSourcePositions(false);
      myILogger = new LogWrapper(LOG);
      myResourceDir = resourceDir;
    }

    public void clearLoaderState() {
      myResourceMerger = null;
      myResourceSet = null;
      myILogger = null;
      myResourceDir = null;
    }

    public void countCacheHit() {
      ++numXml;
    }

    public void countCacheMiss() {
      ++numXml;
      ++numXmlReparsed;
    }

    /**
     * Load a ResourceFile into the resource merger's resource set and return it.
     *
     * @param file a resource XML file to load and parse
     * @return the resulting ResourceFile, if there is no parse error.
     * @throws MergingException
     */
    @Nullable
    ResourceFile loadFile(File file) throws MergingException {
      return myResourceSet.loadFile(myResourceDir, file, myILogger);
    }
  }

  private void scanRemainingFiles() {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (myResourceDir.isValid()) {
          PsiManager manager = PsiManager.getInstance(myFacet.getModule().getProject());
          PsiDirectory directory = manager.findDirectory(myResourceDir);
          if (directory != null) {
            scanResFolder(directory);
          }
        }
      }
    });
  }

  @Nullable
  private PsiFile ensureValid(@NotNull PsiFile psiFile) {
    if (psiFile.isValid()) {
      return psiFile;
    } else {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && virtualFile.exists()) {
        Project project = myModule.getProject();
        if (!project.isDisposed()) {
          return PsiManager.getInstance(project).findFile(virtualFile);
        }
      }
    }

    return null;
  }

  private void scanResFolder(@NotNull PsiDirectory res) {
    for (PsiDirectory dir : res.getSubdirectories()) {
      String name = dir.getName();
      ResourceFolderType folderType = getFolderType(name);
      if (folderType != null) {
        FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(name);
        if (folderConfiguration == null) {
          continue;
        }
        String qualifiers = getQualifiers(name);
        if (folderType == VALUES) {
          scanValueResFolder(dir, qualifiers);
        } else {
          scanFileResourceFolder(dir, folderType, qualifiers, folderConfiguration);
        }
      }
    }
  }

  private static String getQualifiers(String dirName) {
    int index = dirName.indexOf('-');
    return index != -1 ? dirName.substring(index + 1) : "";
  }

  private void scanFileResourceFolder(@NotNull PsiDirectory directory,
                                      ResourceFolderType folderType, String qualifiers,
                                      FolderConfiguration folderConfiguration) {
    List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
    assert resourceTypes.size() >= 1 : folderType;
    ResourceType type = resourceTypes.get(0);

    boolean idGeneratingFolder = FolderTypeRelationship.isIdGeneratingFolderType(folderType);

    ListMultimap<String, ResourceItem> map = getMap(type, true);

    for (PsiFile file : directory.getFiles()) {
      FileType fileType = file.getFileType();
      boolean idGeneratingFile = idGeneratingFolder && fileType == StdFileTypes.XML;
      if (PsiProjectListener.isRelevantFileType(fileType) || folderType == ResourceFolderType.RAW) {
        scanFileResourceFile(qualifiers, folderType, folderConfiguration, type, idGeneratingFile, map, file.getVirtualFile());
      } // TODO: Else warn about files that aren't expected to be found here?
    }
  }

  private void scanFileResourceFileAsPsi(String qualifiers,
                                         ResourceFolderType folderType,
                                         FolderConfiguration folderConfiguration,
                                         ResourceType type,
                                         boolean idGenerating,
                                         ListMultimap<String, ResourceItem> map,
                                         PsiFile file) {
    // XML or Image
    String name = ResourceHelper.getResourceName(file);
    ResourceItem item = new PsiResourceItem(name, type, null, file);

    if (idGenerating) {
      List<ResourceItem> items = Lists.newArrayList();
      items.add(item);
      map.put(name, item);
      addIds(items, file);

      PsiResourceFile resourceFile = new PsiResourceFile(file, items, qualifiers, folderType, folderConfiguration);
      scanDataBinding(resourceFile, getModificationCount());
      myResourceFiles.put(file.getVirtualFile(), resourceFile);
    } else {
      PsiResourceFile resourceFile = new PsiResourceFile(file, item, qualifiers, folderType, folderConfiguration);
      myResourceFiles.put(file.getVirtualFile(), resourceFile);
      map.put(name, item);
    }
  }

  private void scanFileResourceFile(String qualifiers,
                                    ResourceFolderType folderType,
                                    FolderConfiguration folderConfiguration,
                                    ResourceType type,
                                    boolean idGenerating,
                                    ListMultimap<String, ResourceItem> map,
                                    VirtualFile file) {
    ResourceFile resourceFile;
    if (idGenerating) {
      if (myResourceFiles.containsKey(file)) {
        myInitialInitialScanState.countCacheHit();
        return;
      }
      try {
        resourceFile = myInitialInitialScanState.loadFile(VfsUtilCore.virtualToIoFile(file));
        if (resourceFile == null) {
          // The file-based parser failed for some reason. Fall back to Psi in case it is more lax.
          // Don't count Psi items in myInitialInitialScanState.numXml, because they are never cached.
          scanFileResourceFileAsPsi(qualifiers, folderType, folderConfiguration, type, idGenerating,
                                    map, PsiManager.getInstance(myModule.getProject()).findFile(file));
          return;
        }
        ListMultimap<String, ResourceItem> idMap = getMap(ResourceType.ID, true);
        boolean isDensityBasedResource = folderType == DRAWABLE || folderType == MIPMAP;
        // We skip caching density-based resources, so don't count those against cache statistics.
        if (!isDensityBasedResource) {
          myInitialInitialScanState.countCacheMiss();
        }
        for (ResourceItem item : resourceFile.getItems()) {
          ListMultimap<String, ResourceItem> itemMap;
          if (item.getType() == ResourceType.ID) {
            itemMap = idMap;
          } else {
            assert item.getType() == type;
            itemMap = map;
          }
          itemMap.put(item.getName(), item);
          // It's not yet safe to serialize density-based resources items to blob files.
          // The ResourceValue should be an instance of DensityBasedResourceValue, but no flags are
          // serialized to the blob to indicate that.
          if (isDensityBasedResource) {
            item.setIgnoredFromDiskMerge(true);
          }
        }
      }
      catch (MergingException e) {
        // The file-based parser may not be able handle the file if it is a data-binding file.
        scanFileResourceFileAsPsi(qualifiers, folderType, folderConfiguration, type, idGenerating,
                                  map, PsiManager.getInstance(myModule.getProject()).findFile(file));
        return;
      }
    }
    else {
      // We create the items without adding it to the resource set / resource merger.
      // No need to write these out to blob files, as the item is easily reconstructed from the filename.
      String name = ResourceHelper.getResourceName(file);
      ResourceItem item = new ResourceItem(name, type, null, myLibraryName);
      map.put(name, item);
      resourceFile = new ResourceFile(VfsUtilCore.virtualToIoFile(file), item, qualifiers, folderConfiguration);
      item.setIgnoredFromDiskMerge(true);
    }
    myResourceFiles.put(file, resourceFile);
  }

  @Nullable
  @Override
  public DataBindingInfo getDataBindingInfoForLayout(String layoutName) {
    List<ResourceItem> resourceItems = getResourceItem(ResourceType.LAYOUT, layoutName);
    if (resourceItems == null) {
      return null;
    }
    for (ResourceItem item : resourceItems) {
      final ResourceFile source = item.getSource();
      if (source instanceof PsiResourceFile && ((PsiResourceFile) source).getDataBindingInfo() != null) {
        return ((PsiResourceFile) source).getDataBindingInfo();
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Map<String, DataBindingInfo> getDataBindingResourceFiles() {
    long modificationCount = getModificationCount();
    if (myDataBindingResourceFilesModificationCount == modificationCount) {
      return myDataBindingResourceFiles;
    }
    Map<String, DataBindingInfo> selected = Maps.newHashMap();
    for (ResourceFile file : myResourceFiles.values()) {
      if (file instanceof PsiResourceFile) {
        DataBindingInfo info = ((PsiResourceFile)file).getDataBindingInfo();
        if (info != null) {
          selected.put(info.getQualifiedName(), info);
        }
      }
    }
    myDataBindingResourceFiles = Collections.unmodifiableMap(selected);
    myDataBindingResourceFilesModificationCount = modificationCount;
    return myDataBindingResourceFiles;
  }

  @Nullable
  private static XmlTag getLayoutTag(PsiElement element) {
    if (!(element instanceof XmlFile)) {
      return null;
    }
    final XmlTag rootTag = ((XmlFile) element).getRootTag();
    if (rootTag != null && TAG_LAYOUT.equals(rootTag.getName())) {
      return rootTag;
    }
    return null;
  }

  @Nullable
  private static XmlTag getDataTag(XmlTag layoutTag) {
    return layoutTag.findFirstSubTag(TAG_DATA);
  }

  private static void scanDataBindingDataTag(PsiResourceFile resourceFile, @Nullable XmlTag dataTag, long modificationCount) {
    DataBindingInfo info = resourceFile.getDataBindingInfo();
    assert info != null;
    List<PsiDataBindingResourceItem> items = Lists.newArrayList();
    if (dataTag == null) {
      info.replaceItems(items, modificationCount);
      return;
    }
    Set<String> usedNames = Sets.newHashSet();
    for (XmlTag tag : dataTag.findSubTags(TAG_VARIABLE)) {
      String nameValue = tag.getAttributeValue(ATTR_NAME);
      if (nameValue == null) {
        continue;
      }
      String name = StringUtil.unescapeXml(nameValue);
      if (StringUtil.isNotEmpty(name)) {
        if (usedNames.add(name)) {
          PsiDataBindingResourceItem item = new PsiDataBindingResourceItem(name, DataBindingResourceType.VARIABLE, tag);
          item.setSource(resourceFile);
          items.add(item);
        }
      }
    }
    Set<String> usedAliases = Sets.newHashSet();
    for (XmlTag tag : dataTag.findSubTags(TAG_IMPORT)) {
      String nameValue = tag.getAttributeValue(ATTR_TYPE);
      if (nameValue == null) {
        continue;
      }
      String name = StringUtil.unescapeXml(nameValue);
      String aliasValue = tag.getAttributeValue(ATTR_ALIAS);
      String alias = null;
      if (aliasValue != null) {
        alias = StringUtil.unescapeXml(aliasValue);
      }
      if (alias == null) {
        int lastIndexOfDot = name.lastIndexOf('.');
        if (lastIndexOfDot >= 0) {
          alias = name.substring(lastIndexOfDot + 1);
        }
      }
      if (StringUtil.isNotEmpty(alias)) {
        if (usedAliases.add(name)) {
          PsiDataBindingResourceItem item = new PsiDataBindingResourceItem(name, DataBindingResourceType.IMPORT, tag);
          item.setSource(resourceFile);
          items.add(item);
        }
      }
    }

    info.replaceItems(items, modificationCount);
  }

  private void scanDataBinding(PsiResourceFile resourceFile, long modificationCount) {
    if (resourceFile.getFolderType() != LAYOUT) {
      resourceFile.setDataBindingInfo(null);
      return;
    }
    XmlTag layout = getLayoutTag(resourceFile.getPsiFile());
    if (layout == null) {
      resourceFile.setDataBindingInfo(null);
      return;
    }
    XmlTag dataTag = getDataTag(layout);
    String className;
    String classPackage;
    String modulePackage = MergedManifest.get(myFacet).getPackage();
    String classAttrValue = null;
    if (dataTag != null) {
      classAttrValue = dataTag.getAttributeValue(ATTR_CLASS);
      if (classAttrValue != null) {
        classAttrValue = StringUtil.unescapeXml(classAttrValue);
      }
    }
    if (StringUtil.isEmpty(classAttrValue)) {
      className = DataBindingUtil.convertToJavaClassName(resourceFile.getName()) + "Binding";
      classPackage = modulePackage + ".databinding";
    } else {
      int firstDotIndex = classAttrValue.indexOf('.');

      if (firstDotIndex < 0) {
        classPackage = modulePackage + ".databinding";
        className = classAttrValue;
      } else {
        int lastDotIndex = classAttrValue.lastIndexOf('.');
        if (firstDotIndex == 0) {
          classPackage = modulePackage + classAttrValue.substring(0, lastDotIndex);
        } else {
          classPackage = classAttrValue.substring(0, lastDotIndex);
        }
        className = classAttrValue.substring(lastDotIndex + 1);
      }
    }
    if (resourceFile.getDataBindingInfo() == null) {
      resourceFile.setDataBindingInfo(new DataBindingInfo(myFacet, resourceFile, className, classPackage));
    } else {
      resourceFile.getDataBindingInfo().update(className, classPackage, modificationCount);
    }
    scanDataBindingDataTag(resourceFile, dataTag, modificationCount);
  }

  @NonNull
  @Override
  protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
    return myItems;
  }

  @Nullable
  @Override
  @Contract("_, true -> !null")
  protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myItems.get(type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myItems.put(type, multimap);
    }
    return multimap;
  }

  @Override
  public void clear() {
    super.clear();
    myResourceFiles.clear();
  }

  private void addIds(List<ResourceItem> items, PsiFile file) {
    addIds(items, file, file);
  }

  private void addIds(List<ResourceItem> items, PsiElement element, PsiFile file) {
    // "@+id/" names found before processing the view tag corresponding to the id.
    Map<String, XmlTag> pendingResourceIds = Maps.newHashMap();
    Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(element, XmlTag.class);
    if (element instanceof XmlTag) {
      addId(items, file, (XmlTag)element, pendingResourceIds);
    }
    if (!xmlTags.isEmpty()) {
      for (XmlTag tag : xmlTags) {
        addId(items, file, tag, pendingResourceIds);
      }
    }
    // Add any remaining ids.
    if (!pendingResourceIds.isEmpty()) {
      ListMultimap<String, ResourceItem> map = getMap(ResourceType.ID, true);
      for (Map.Entry<String, XmlTag> entry : pendingResourceIds.entrySet()) {
        String id = entry.getKey();
        PsiResourceItem remainderItem = new PsiResourceItem(id, ResourceType.ID, entry.getValue(), file);
        items.add(remainderItem);
        map.put(id, remainderItem);
      }
    }
  }

  private void addId(List<ResourceItem> items, PsiFile file, XmlTag tag, Map<String, XmlTag> pendingResourceIds) {
    assert tag.isValid();
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (ANDROID_URI.equals(attribute.getNamespace())) {
        // For all attributes in the android namespace, check if something has a value of the form "@+id/"
        // If the attribute is not android:id, and an item for it hasn't been created yet, add it to
        // the list of pending ids.
        String value = attribute.getValue();
        if (value != null && value.startsWith(NEW_ID_PREFIX) && !ATTR_ID.equals(attribute.getLocalName())) {
          ListMultimap<String, ResourceItem> map = myItems.get(ResourceType.ID);
          String id = value.substring(NEW_ID_PREFIX.length());
          if (map != null && !map.containsKey(id) && !pendingResourceIds.containsKey(id)) {
            pendingResourceIds.put(id, tag);
          }
        }
      }
    }
    // Now process the android:id attribute.
    String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
    if (id != null) {
      if (id.startsWith(ID_PREFIX)) {
        // If the id is not "@+id/", it may still have been declared as "@+id/" in a preceding view (eg. layout_above).
        // So, we test if this is such a pending id.
        id = id.substring(ID_PREFIX.length());
        if (!pendingResourceIds.containsKey(id)) {
          return;
        }
      } else if (id.startsWith(NEW_ID_PREFIX)) {
        id = id.substring(NEW_ID_PREFIX.length());
      } else {
        return;
      }
      pendingResourceIds.remove(id);
      PsiResourceItem item = new PsiResourceItem(id, ResourceType.ID, tag, file);
      items.add(item);

      getMap(ResourceType.ID, true).put(id, item);
    }
  }

  private void scanValueResFolder(@NotNull PsiDirectory directory, String qualifiers) {
    //noinspection ConstantConditions
    assert directory.getName().startsWith(FD_RES_VALUES);

    for (PsiFile file : directory.getFiles()) {
      scanValueFile(qualifiers, file.getVirtualFile());
    }
  }

  private boolean scanValueFileAsPsi(String qualifiers, PsiFile file, FolderConfiguration folderConfiguration) {
    boolean added = false;
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.XML) {
      XmlFile xmlFile = (XmlFile)file;
      assert xmlFile.isValid();
      XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        XmlTag root = document.getRootTag();
        if (root == null) {
          return false;
        }
        if (!root.getName().equals(TAG_RESOURCES)) {
          return false;
        }
        XmlTag[] subTags = root.getSubTags(); // Not recursive, right?
        List<ResourceItem> items = Lists.newArrayListWithExpectedSize(subTags.length);
        for (XmlTag tag : subTags) {
          String name = tag.getAttributeValue(ATTR_NAME);
          if (name != null) {
            ResourceType type = AndroidResourceUtil.getType(tag);
            if (type != null) {
              ListMultimap<String, ResourceItem> map = getMap(type, true);
              ResourceItem item = new PsiResourceItem(name, type, tag, file);
              map.put(name, item);
              items.add(item);
              added = true;

              if (type == ResourceType.DECLARE_STYLEABLE) {
                // for declare styleables we also need to create attr items for its children
                XmlTag[] attrs = tag.getSubTags();
                if (attrs.length > 0) {
                  map = getMap(ResourceType.ATTR, true);

                  for (XmlTag child : attrs) {
                    String attrName = child.getAttributeValue(ATTR_NAME);
                    if (attrName != null && !attrName.startsWith(ANDROID_NS_NAME_PREFIX)
                        // Only add attr nodes for elements that specify a format or have flag/enum children; otherwise
                        // it's just a reference to an existing attr
                        && (child.getAttribute(ATTR_FORMAT) != null || child.getSubTags().length > 0)) {
                      ResourceItem attrItem = new PsiResourceItem(attrName, ResourceType.ATTR, child, file);
                      items.add(attrItem);
                      map.put(attrName, attrItem);
                    }
                  }
                }
              }
            }
          }
        }

        PsiResourceFile resourceFile = new PsiResourceFile(file, items, qualifiers, ResourceFolderType.VALUES, folderConfiguration);
        myResourceFiles.put(file.getVirtualFile(), resourceFile);
      }
    }

    return added;
  }

  private void scanValueFile(String qualifiers, VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    if (fileType == StdFileTypes.XML) {
      if (myResourceFiles.containsKey(virtualFile)) {
        myInitialInitialScanState.countCacheHit();
        return;
      }
      File file = VfsUtilCore.virtualToIoFile(virtualFile);
      try {
        ResourceFile resourceFile = myInitialInitialScanState.loadFile(file);
        if (resourceFile == null) {
          // The file-based parser failed for some reason. Fall back to Psi in case it is more lax.
          scanValueFileAsPsi(qualifiers, PsiManager.getInstance(myModule.getProject()).findFile(virtualFile),
                             FolderConfiguration.getConfigForFolder(virtualFile.getParent().getName()));
          return;
        }
        for (ResourceItem item : resourceFile.getItems()) {
          ListMultimap<String, ResourceItem> map = getMap(item.getType(), true);
          map.put(item.getName(), item);
        }
        myInitialInitialScanState.countCacheMiss();
        myResourceFiles.put(virtualFile, resourceFile);
      }
      catch (MergingException e) {
        // The file-based parser failed for some reason. Fall back to Psi in case it is more lax.
        scanValueFileAsPsi(qualifiers, PsiManager.getInstance(myModule.getProject()).findFile(virtualFile),
                           FolderConfiguration.getConfigForFolder(virtualFile.getParent().getName()));
      }
    }
  }

  // Schedule a rescan to convert any map ResourceItems to Psi if needed, and return true if conversion
  // is needed (incremental updates which rely on Psi are not possible).
  private boolean convertToPsiIfNeeded(@NotNull PsiFile psiFile, ResourceFolderType folderType) {
    ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
    if (resFile instanceof PsiResourceFile) {
      return false;
    }
    // This schedules a rescan, and when the actual rescanImmediately happens it will purge non-Psi
    // items as needed, populate psi items, and add to myFileTypes once done.
    rescan(psiFile, folderType);
    return true;
  }

  private boolean isResourceFolder(@Nullable PsiElement parent) {
    // Returns true if the given element represents a resource folder (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder)
    if (parent instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)parent;
      PsiDirectory parentDirectory = directory.getParentDirectory();
      if (parentDirectory != null) {
        VirtualFile dir = parentDirectory.getVirtualFile();
        return dir.equals(myResourceDir);
      }
    }
    return false;
  }

  private boolean isResourceFile(PsiFile psiFile) {
    return isResourceFolder(psiFile.getParent());
  }

  @Override
  public boolean isScanPending(@NonNull PsiFile psiFile) {
    synchronized (SCAN_LOCK) {
      return myPendingScans != null && myPendingScans.contains(psiFile);
    }
  }

  @VisibleForTesting
  void rescan(@NonNull final PsiFile psiFile, final @NonNull ResourceFolderType folderType) {
    synchronized(SCAN_LOCK) {
      if (isScanPending(psiFile)) {
        return;
      }

      if (myPendingScans == null) {
        myPendingScans = Sets.newHashSet();
      }
      myPendingScans.add(psiFile);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            boolean rescan;
            synchronized (SCAN_LOCK) {
              // Handled by {@link #sync()} after the {@link #rescan} call and before invokeLater ?
              rescan = myPendingScans != null && myPendingScans.contains(psiFile);
            }
            if (rescan) {
              rescanImmediately(psiFile, folderType);
              synchronized (SCAN_LOCK) {
                // myPendingScans can't be null here because the only method which clears it
                // is sync() which also requires a write lock, and we've held the write lock
                // since null checking it above
                myPendingScans.remove(psiFile);
                if (myPendingScans.isEmpty()) {
                  myPendingScans = null;
                }
              }
            }
          }
        });
      }
    });
  }

  @Override
  public void sync() {
    super.sync();

    final List<PsiFile> files;
    synchronized(SCAN_LOCK) {
      if (myPendingScans == null || myPendingScans.isEmpty()) {
        return;
      }
      files = new ArrayList<PsiFile>(myPendingScans);
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (PsiFile file : files) {
          if (file.isValid()) {
            ResourceFolderType folderType = ResourceHelper.getFolderType(file);
            if (folderType != null) {
              rescanImmediately(file, folderType);
            }
          }
        }
      }
    });

    synchronized(SCAN_LOCK) {
      myPendingScans = null;
    }
  }

  private void rescanImmediately(@NonNull final PsiFile psiFile, final @NonNull ResourceFolderType folderType) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          rescanImmediately(psiFile, folderType);
        }
      });
      return;
    }

    PsiFile file = psiFile;
    if (folderType == VALUES) {
      // For unit test tracking purposes only
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFullRescans++;

      // First delete out the previous items
      ResourceFile resourceFile = myResourceFiles.get(file.getVirtualFile());
      boolean removed = false;
      if (resourceFile != null) {
        for (ResourceItem item : resourceFile.getItems()) {
          removed |= removeItems(resourceFile, item.getType(), item.getName(), false);  // Will throw away file
        }

        myResourceFiles.remove(file.getVirtualFile());
      }

      file = ensureValid(file);
      boolean added = false;
      if (file != null) {
        // Add items for this file
        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type
        String dirName = parent.getName();
        PsiDirectory fileParent = psiFile.getParent();
        if (fileParent != null) {
          FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
          if (folderConfiguration != null) {
            added = scanValueFileAsPsi(getQualifiers(dirName), file, folderConfiguration);
          }
        }
      }

      if (added || removed) {
        // TODO: Consider doing a deeper diff of the changes to the resource items
        // to determine if the removed and added items actually differ
        myGeneration = ourModificationCounter.incrementAndGet();
        invalidateItemCaches();
      }
    } else {
      ResourceFile resFile = myResourceFiles.get(file.getVirtualFile());
      // If the old file was a PsiResourceFile, we could try to update ID ResourceItems in place.
      if (resFile != null && resFile instanceof PsiResourceFile) {
        PsiResourceFile resourceFile = (PsiResourceFile)resFile;
        // Already seen this file; no need to do anything unless it's an XML file with generated ids;
        // in that case we may need to update the id's
        if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) &&
            file.getFileType() == StdFileTypes.XML) {
          // For unit test tracking purposes only
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourFullRescans++;

          // We've already seen this resource, so no change in the ResourceItem for the
          // file itself (e.g. @layout/foo from layout-land/foo.xml). However, we may have
          // to update the id's:
          Set<String> idsBefore = Sets.newHashSet();
          Set<String> idsAfter = Sets.newHashSet();
          ListMultimap<String, ResourceItem> map = myItems.get(ResourceType.ID);
          if (map != null) {
            List<ResourceItem> idItems = Lists.newArrayList();
            for (ResourceItem item : resourceFile) {
              if (item.getType() == ResourceType.ID) {
                idsBefore.add(item.getName());
                idItems.add(item);
              }
            }
            for (String id : idsBefore) {
              // Note that ResourceFile has a flat map (not a multimap) so it doesn't
              // record all items (unlike the myItems map) so we need to remove the map
              // items manually, can't just do map.remove(item.getName(), item)
              List<ResourceItem> mapItems = map.get(id);
              if (mapItems != null && !mapItems.isEmpty()) {
                List<ResourceItem> toDelete = Lists.newArrayListWithExpectedSize(mapItems.size());
                for (ResourceItem mapItem : mapItems) {
                  if (mapItem.getSource() == resourceFile) {
                    toDelete.add(mapItem);
                  }
                }
                for (ResourceItem delete : toDelete) {
                  map.remove(delete.getName(), delete);
                }
              }
            }
            resourceFile.removeItems(idItems);
          }

          // Add items for this file
          List<ResourceItem> idItems = Lists.newArrayList();
          file = ensureValid(file);
          if (file != null) {
            addIds(idItems, file);
          }
          if (!idItems.isEmpty()) {
            resourceFile.addItems(idItems);
            for (ResourceItem item : idItems) {
              idsAfter.add(item.getName());
            }
          }

          if (!idsBefore.equals(idsAfter)) {
            myGeneration = ourModificationCounter.incrementAndGet();
          }
          scanDataBinding(resourceFile, myGeneration);
          // Identities may have changed even if the ids are the same, so update maps
          invalidateItemCaches(ResourceType.ID);
        }
      } else {
        // Remove old items first, if switching to Psi. Rescan below to add back, but with a possibly different multimap list order.
        boolean switchingToPsi = resFile != null && !(resFile instanceof PsiResourceFile);
        if (switchingToPsi) {
          removeItemsFromFile(resFile);
        }
        // For unit test tracking purposes only
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFullRescans++;

        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type
        String dirName = parent.getName();

        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        assert resourceTypes.size() >= 1 : folderType;
        ResourceType type = resourceTypes.get(0);

        boolean idGeneratingFolder = FolderTypeRelationship.isIdGeneratingFolderType(folderType);

        ListMultimap<String, ResourceItem> map = getMap(type, true);

        file = ensureValid(file);
        if (file != null) {
          PsiDirectory fileParent = psiFile.getParent();
          if (fileParent != null) {
            FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
            if (folderConfiguration != null) {
              boolean idGeneratingFile = idGeneratingFolder && file.getFileType() == StdFileTypes.XML;
              scanFileResourceFileAsPsi(getQualifiers(dirName), folderType, folderConfiguration, type, idGeneratingFile, map, file);
            }
          }
          myGeneration = ourModificationCounter.incrementAndGet();
          invalidateItemCaches();
        }
      }
    }
  }

  private boolean removeItems(ResourceFile resourceFile, ResourceType type, String name, boolean removeFromFile) {
    boolean removed = false;

    // Remove the item of the given name and type from the given resource file.
    // We CAN'T just remove items found in ResourceFile.getItems() because that map
    // flattens everything down to a single item for a given name (it's using a flat
    // map rather than a multimap) so instead we have to look up from the map instead
    ListMultimap<String, ResourceItem> map = myItems.get(type);
    if (map != null) {
      List<ResourceItem> mapItems = map.get(name);
      if (mapItems != null) {
        ListIterator<ResourceItem> iterator = mapItems.listIterator();
        while (iterator.hasNext()) {
          ResourceItem next = iterator.next();
          if (next.getSource() == resourceFile) {
            iterator.remove();
            if (removeFromFile) {
              resourceFile.removeItem(next);
            }
            removed = true;
          }
        }
      }
    }

    return removed;
  }

  /**
   * Called when a bitmap has been changed/deleted. In that case we need to clear out any caches for that
   * image held by layout lib.
   */
  private void bitmapUpdated() {
    ConfigurationManager configurationManager = myFacet.getConfigurationManager(false);
    if (configurationManager != null) {
      IAndroidTarget target = configurationManager.getTarget();
      if (target != null) {
        Module module = myFacet.getModule();
        AndroidTargetData targetData = AndroidTargetData.getTargetData(target, module);
        if (targetData != null) {
          targetData.clearLayoutBitmapCache(module);
        }
      }
    }
  }

  @NotNull
  public PsiTreeChangeListener getPsiListener() {
    return myListener;
  }

  /** PSI listener which keeps the repository up to date */
  private final class PsiListener extends PsiTreeChangeAdapter {
    private boolean myIgnoreChildrenChanged;

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        // Called when you've added a file
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          psiFile = (PsiFile)child;
          if (PsiProjectListener.isRelevantFile(psiFile)) {
            addFile(psiFile);
          }
        } else if (child instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)child;
          if (isResourceFolder(directory)) {
            for (PsiFile file : directory.getFiles()) {
              if (PsiProjectListener.isRelevantFile(file)) {
                addFile(file);
              }
            }
          }
        }
      } else if (PsiProjectListener.isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was added within a file
        ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();
          if (folderType == ResourceFolderType.VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              if (isItemElement(tag)) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                if (resFile != null) {
                  assert resFile instanceof PsiResourceFile;
                  PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                  String name = tag.getAttributeValue(ATTR_NAME);
                  if (name != null) {
                    ResourceType type = AndroidResourceUtil.getType(tag);
                    if (type == ResourceType.DECLARE_STYLEABLE) {
                      // Can't handle declare styleable additions incrementally yet; need to update paired attr items
                      rescan(psiFile, folderType);
                      return;
                    }
                    if (type != null) {
                      ListMultimap<String, ResourceItem> map = getMap(type, true);
                      ResourceItem item = new PsiResourceItem(name, type, tag, psiFile);
                      map.put(name, item);
                      resourceFile.addItems(Collections.singletonList(item));
                      myGeneration = ourModificationCounter.incrementAndGet();
                      invalidateItemCaches(type);
                      return;
                    }
                  }
                }
              }

              // See if you just added a new item inside a <style> or <array> or <declare-styleable> etc
              XmlTag parentTag = tag.getParentTag();
              if (parentTag != null && ResourceType.getEnum(parentTag.getName()) != null) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                // Yes just invalidate the corresponding cached value
                ResourceItem parentItem = findValueResourceItem(parentTag, psiFile);
                if (parentItem instanceof PsiResourceItem) {
                  if (((PsiResourceItem)parentItem).recomputeValue()) {
                    myGeneration = ourModificationCounter.incrementAndGet();
                  }
                  return;
                }
              }

              rescan(psiFile, folderType);
              // Else: fall through and do full file rescan
            } else if (parent instanceof XmlText) {
              // If the edit is within an item tag
              XmlText text = (XmlText)parent;
              handleValueXmlTextEdit(text.getParentTag(), psiFile);
              return;
            } else if (child instanceof XmlText) {
              // If the edit is within an item tag
              handleValueXmlTextEdit(parent, psiFile);
              return;
            } else if (parent instanceof XmlComment || child instanceof XmlComment) {
              // Can ignore comment edits or new comments
              return;
            }
            rescan(psiFile, folderType);
          } else if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) &&
                     psiFile.getFileType() == StdFileTypes.XML) {
            if (parent instanceof XmlComment || child instanceof XmlComment) {
              return;
            }
            if (parent instanceof XmlText ||
                (child instanceof XmlText && child.getText().trim().isEmpty())) {
              return;
            }

            if (parent instanceof XmlElement && child instanceof XmlElement) {
              if (child instanceof XmlTag) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                List<ResourceItem> ids = Lists.newArrayList();
                addIds(ids, child, psiFile);
                if (!ids.isEmpty()) {
                  ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    assert resFile instanceof PsiResourceFile;
                    PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                    resourceFile.addItems(ids);
                    myGeneration = ourModificationCounter.incrementAndGet();
                  }
                }
                return;
              } else if (child instanceof XmlAttributeValue) {
                assert parent instanceof XmlAttribute : parent;
                @SuppressWarnings("CastConflictsWithInstanceof") // IDE bug? Cast is valid.
                XmlAttribute attribute = (XmlAttribute)parent;
                // warning for separate if branches suppressed because to do.
                //noinspection IfStatementWithIdenticalBranches
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // TODO: Update it incrementally
                  rescan(psiFile, folderType);
                } else if (ArrayUtil.contains(attribute.getLocalName(), ATTRS_DATA_BINDING)
                           && ArrayUtil.contains(attribute.getParent().getLocalName(), TAGS_DATA_BINDING)) {
                  rescan(psiFile, folderType);
                }
              }
            }
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        // Called when you've removed a file
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          psiFile = (PsiFile)child;
          if (PsiProjectListener.isRelevantFile(psiFile)) {
            removeFile(psiFile);
          }
        } else if (child instanceof PsiDirectory) {
          // We can't iterate the children here because the dir is already empty.
          // Instead, try to locate the files
          String dirName = ((PsiDirectory)child).getName();
          ResourceFolderType folderType = ResourceFolderType.getFolderType(dirName);

          if (folderType != null) {
            // Make sure it's really a resource folder. We can't look at the directory
            // itself since the file no longer exists, but make sure the parent directory is
            // a resource directory root
            PsiDirectory parentDirectory = ((PsiDirectory)child).getParent();
            if (parentDirectory != null) {
              VirtualFile dir = parentDirectory.getVirtualFile();
              if  (!myFacet.getLocalResourceManager().isResourceDir(dir)) {
                return;
              }
            } else {
              return;
            }
            int index = dirName.indexOf('-');
            String qualifiers;
            if (index == -1) {
              qualifiers = "";
            } else {
              qualifiers = dirName.substring(index + 1);
            }

            // Copy file map so we can delete while iterating
            Collection<ResourceFile> resourceFiles = new ArrayList<ResourceFile>(myResourceFiles.values());
            for (ResourceFile file : resourceFiles) {
              ResourceFolderType resFolderType = ResourceHelper.getFolderType(file);
              if (folderType == resFolderType && qualifiers.equals(file.getQualifiers())) {
                removeFile(file);
              }
            }
          }
        }
      } else if (PsiProjectListener.isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was removed within a file
        ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (folderType == ResourceFolderType.VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc
              if (parent instanceof XmlTag) {
                XmlTag parentTag = (XmlTag)parent;
                if (ResourceType.getEnum(parentTag.getName()) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      myGeneration = ourModificationCounter.incrementAndGet();
                    }

                    if (resourceItem.getType() == ResourceType.ATTR) {
                      parentTag = parentTag.getParentTag();
                      if (parentTag != null && parentTag.getName().equals(ResourceType.DECLARE_STYLEABLE.getName())) {
                        ResourceItem declareStyleable = findValueResourceItem(parentTag, psiFile);
                        if (declareStyleable instanceof PsiResourceItem) {
                          if (((PsiResourceItem)declareStyleable).recomputeValue()) {
                            myGeneration = ourModificationCounter.incrementAndGet();
                          }
                        }
                      }
                    }
                    return;
                  }
                }
              }

              if (isItemElement(tag)) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                if (resFile != null) {
                  PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                  String name;
                  if (!tag.isValid()) {
                    ResourceItem item = findValueResourceItem(tag, psiFile);
                    if (item != null) {
                      name = item.getName();
                    } else {
                      // Can't find the name of the deleted tag; just do a full rescan
                      rescan(psiFile, folderType);
                      return;
                    }
                  } else {
                    name = tag.getAttributeValue(ATTR_NAME);
                  }
                  if (name != null) {
                    ResourceType type = AndroidResourceUtil.getType(tag);
                    if (type != null) {
                      ListMultimap<String, ResourceItem> map = myItems.get(type);
                      if (map == null) {
                        return;
                      }
                      if (removeItems(resourceFile, type, name, true)) {
                        myGeneration = ourModificationCounter.incrementAndGet();
                        invalidateItemCaches(type);
                      }
                    }
                  }

                  return;
                }
              }

              rescan(psiFile, folderType);
            } else if (parent instanceof XmlText) {
              // If the edit is within an item tag
              XmlText text = (XmlText)parent;
              handleValueXmlTextEdit(text.getParentTag(), psiFile);
            } else if (child instanceof XmlText) {
              handleValueXmlTextEdit(parent, psiFile);
            } else if (parent instanceof XmlComment || child instanceof XmlComment) {
              // Can ignore comment edits or removed comments
              return;
            } else {
              // Some other change: do full file rescan
              rescan(psiFile, folderType);
            }
          } else if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) &&
                     psiFile.getFileType() == StdFileTypes.XML) {
            // TODO: Handle removals of id's (values an attributes) incrementally
            rescan(psiFile, folderType);
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    private void removeFile(@Nullable ResourceFile resourceFile) {
      if (resourceFile == null) {
        // No resources for this file
        return;
      }
      // Do an exhaustive search, because the resourceFile's underlying VirtualFile is already deleted.
      for (Map.Entry<VirtualFile, ResourceFile> entry : myResourceFiles.entrySet()) {
        ResourceFile file = entry.getValue();
        if (resourceFile == file) {
          VirtualFile keyFile = entry.getKey();
          myResourceFiles.remove(keyFile);
          break;
        }
      }

      myGeneration = ourModificationCounter.incrementAndGet();
      invalidateItemCaches();

      ResourceFolderType folderType = ResourceHelper.getFolderType(resourceFile);
      // Check if there may be multiple items to remove.
      if (folderType == VALUES || FolderTypeRelationship.isIdGeneratingFolderType(folderType)) {
        removeItemsFromFile(resourceFile);
      } else if (folderType != null) {
        // Simpler: remove the file item
        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        for (ResourceType type : resourceTypes) {
          if (type != ResourceType.ID) {
            String name = LintUtils.getBaseName(resourceFile.getFile().getName());
            removeItems(resourceFile, type, name, false);  // no need since we're discarding the file
          }
        }
      } // else: not a resource folder
    }

    private void removeFile(@NotNull PsiFile psiFile) {
      assert !psiFile.isValid() || PsiProjectListener.isRelevantFile(psiFile);

      ResourceFile resourceFile = myResourceFiles.get(psiFile.getVirtualFile());
      if (resourceFile == null) {
        // No resources for this file
        return;
      }
      myResourceFiles.remove(psiFile.getVirtualFile());
      myGeneration = ourModificationCounter.incrementAndGet();
      invalidateItemCaches();

      ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
      // Check if there may be multiple items to remove.
      if (folderType == VALUES ||
          (FolderTypeRelationship.isIdGeneratingFolderType(folderType) && psiFile.getFileType() == StdFileTypes.XML)) {
        removeItemsFromFile(resourceFile);
      } else if (folderType != null) {
        // Simpler: remove the file item
        if (folderType == DRAWABLE) {
          FileType fileType = psiFile.getFileType();
          if (fileType.isBinary() && fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG)) {
            bitmapUpdated();
          }
        }

        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        for (ResourceType type : resourceTypes) {
          if (type != ResourceType.ID) {
            String name = ResourceHelper.getResourceName(psiFile);
            removeItems(resourceFile, type, name, false);  // no need since we're discarding the file
          }
        }
      } // else: not a resource folder
    }

    private void addFile(PsiFile psiFile) {
      assert PsiProjectListener.isRelevantFile(psiFile);

      // Same handling as rescan, where the initial deletion is a no-op
      ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
      if (folderType != null && isResourceFile(psiFile)) {
        rescanImmediately(psiFile, folderType);
      }
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        if (isScanPending(psiFile)) {
          return;
        }
        // This method is called when you edit within a file
        if (PsiProjectListener.isRelevantFile(psiFile)) {
          // First determine if the edit is non-consequential.
          // That's the case if the XML edited is not a resource file (e.g. the manifest file),
          // or if it's within a file that is not a value file or an id-generating file (layouts and menus),
          // such as editing the content of a drawable XML file.
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          if (folderType != null && FolderTypeRelationship.isIdGeneratingFolderType(folderType) &&
              psiFile.getFileType() == StdFileTypes.XML) {
            // The only way the edit affected the set of resources was if the user added or removed an
            // id attribute. Since these can be added redundantly we can't automatically remove the old
            // value if you renamed one, so we'll need a full file scan.
            // However, we only need to do this scan if the change appears to be related to ids; this can
            // only happen if the attribute value is changed.
            PsiElement parent = event.getParent();
            PsiElement child = event.getChild();
            if (parent instanceof XmlText || child instanceof XmlText ||
              parent instanceof XmlComment || child instanceof XmlComment) {
              return;
            }
            if (parent instanceof XmlElement && child instanceof XmlElement) {
              if (event.getOldChild() == event.getNewChild()) {
                // We're not getting accurate PSI information: we have to do a full file scan
                rescan(psiFile, folderType);
                return;
              }
              if (child instanceof XmlAttributeValue) {
                assert parent instanceof XmlAttribute : parent;
                @SuppressWarnings("CastConflictsWithInstanceof") // IDE bug? Cast is valid.
                XmlAttribute attribute = (XmlAttribute)parent;
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // for each id attribute!
                  ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    if (oldChild instanceof XmlAttributeValue && newChild instanceof XmlAttributeValue) {
                      XmlAttributeValue oldValue = (XmlAttributeValue)oldChild;
                      XmlAttributeValue newValue = (XmlAttributeValue)newChild;
                      String oldName = stripIdPrefix(oldValue.getValue());
                      String newName = stripIdPrefix(newValue.getValue());
                      if (oldName.equals(newName)) {
                        // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc)
                        return;
                      }
                      if (resFile instanceof PsiResourceFile) {
                        PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                        ResourceItem item = findResourceItem(ResourceType.ID, psiFile, oldName, xmlTag);
                        if (item != null) {
                          ListMultimap<String, ResourceItem> map = myItems.get(item.getType());
                          if (map != null) {
                            // Found the relevant item: delete it and create a new one in a new location
                            map.remove(oldName, item);
                            ResourceItem newItem = new PsiResourceItem(newName, ResourceType.ID, xmlTag, psiFile);
                            map.put(newName, newItem);
                            resourceFile.replace(item, newItem);
                            myGeneration = ourModificationCounter.incrementAndGet();
                            invalidateItemCaches(ResourceType.ID);
                            return;
                          }
                        }
                      }
                    }
                  }

                  rescan(psiFile, folderType);
                }
              } else if (parent instanceof XmlAttributeValue) {
                PsiElement grandParent = parent.getParent();
                if (grandParent instanceof XmlProcessingInstruction) {
                  // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                  // <?xml version="1.0" encoding="utf-8"?>
                  return;
                }
                assert grandParent instanceof XmlAttribute : parent;
                XmlAttribute attribute = (XmlAttribute)grandParent;
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // for each id attribute!
                  ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    String oldName = stripIdPrefix(oldChild.getText());
                    String newName = stripIdPrefix(newChild.getText());
                    if (oldName.equals(newName)) {
                      // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc)
                      return;
                    }
                    if (resFile instanceof PsiResourceFile) {
                      PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                      ResourceItem item = findResourceItem(ResourceType.ID, psiFile, oldName, xmlTag);
                      if (item != null) {
                        ListMultimap<String, ResourceItem> map = myItems.get(item.getType());
                        if (map != null) {
                          // Found the relevant item: delete it and create a new one in a new location
                          map.remove(oldName, item);
                          ResourceItem newItem = new PsiResourceItem(newName, ResourceType.ID, xmlTag, psiFile);
                          map.put(newName, newItem);
                          resourceFile.replace(item, newItem);
                          myGeneration = ourModificationCounter.incrementAndGet();
                          invalidateItemCaches(ResourceType.ID);
                          return;
                        }
                      }
                    }
                  }

                  rescan(psiFile, folderType);
                } else if (ArrayUtil.contains(attribute.getLocalName(), ATTRS_DATA_BINDING)
                           && ArrayUtil.contains(attribute.getParent().getLocalName(), TAGS_DATA_BINDING)) {
                  ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    // Data-binding files are always scanned as PsiResourceFiles.
                    PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                    myGeneration = ourModificationCounter.incrementAndGet();
                    scanDataBinding(resourceFile, myGeneration);
                  }
                }
              }

              return;
            }

            // TODO: Handle adding/removing elements in layouts incrementally

            rescan(psiFile, folderType);
          } else if (folderType == VALUES) {
            PsiElement parent = event.getParent();
            if (parent instanceof XmlElement) {
              // Editing within an XML file
              // An edit in a comment can be ignored
              // An edit in a text inside an element can be used to invalidate the ResourceValue of an element
              //    (need to search upwards since strings can have HTML content)
              // An edit between elements can be ignored
              // An edit to an attribute name (not the attribute value for the attribute named "name"...) can
              //     sometimes be ignored (if you edit type or name, consider what to do)
              // An edit of an attribute value can affect the name of type so update item
              // An edit of other parts; for example typing in a new <string> item character by character.
              // etc.

              if (parent instanceof XmlComment) {
                // Nothing to do
                return;
              }

              // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc
              if (parent instanceof XmlTag) {
                XmlTag parentTag = (XmlTag)parent;
                if (ResourceType.getEnum(parentTag.getName()) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      myGeneration = ourModificationCounter.incrementAndGet();
                    }
                    return;
                  }
                }

                if (parentTag.getName().equals(TAG_RESOURCES)
                    && event.getOldChild() instanceof XmlText
                    && event.getNewChild() instanceof XmlText) {
                  return;
                }
              }

              if (parent instanceof XmlText) {
                XmlText text = (XmlText)parent;
                handleValueXmlTextEdit(text.getParentTag(), psiFile);
                return;
              }

              if (parent instanceof XmlAttributeValue) {
                PsiElement attribute = parent.getParent();
                if (attribute instanceof XmlProcessingInstruction) {
                  // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                  // <?xml version="1.0" encoding="utf-8"?>
                  return;
                }
                PsiElement tag = attribute.getParent();
                assert attribute instanceof XmlAttribute : attribute;
                XmlAttribute xmlAttribute = (XmlAttribute)attribute;
                assert tag instanceof XmlTag : tag;
                XmlTag xmlTag = (XmlTag)tag;
                String attributeName = xmlAttribute.getName();
                // We could also special case handling of editing the type attribute, and the parent attribute,
                // but editing these is rare enough that we can just stick with the fallback full file scan for those
                // scenarios.
                if (isItemElement(xmlTag) && attributeName.equals(ATTR_NAME)) {
                  // Edited the name of the item: replace it
                  ResourceType type = AndroidResourceUtil.getType(xmlTag);
                  if (type != null) {
                    String oldName = event.getOldChild().getText();
                    String newName = event.getNewChild().getText();
                    if (oldName.equals(newName)) {
                      // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc)
                      return;
                    }
                    // findResourceItem depends on Psi in some cases, so we need to bail and rescan if not Psi.
                    if (convertToPsiIfNeeded(psiFile, folderType)) {
                      return;
                    }
                    ResourceItem item = findResourceItem(type, psiFile, oldName, xmlTag);
                    if (item != null) {
                      ListMultimap<String, ResourceItem> map = myItems.get(item.getType());
                      if (map != null) {
                        // Found the relevant item: delete it and create a new one in a new location
                        map.remove(oldName, item);
                        ResourceItem newItem = new PsiResourceItem(newName, type, xmlTag, psiFile);
                        map.put(newName, newItem);
                        ResourceFile resFile = myResourceFiles.get(psiFile.getVirtualFile());
                        if (resFile != null) {
                          PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                          resourceFile.replace(item, newItem);
                        }
                        else {
                          assert false : item;
                        }
                        myGeneration = ourModificationCounter.incrementAndGet();
                        invalidateItemCaches(type);

                        // Invalidate surrounding declare styleable if any
                        if (type == ResourceType.ATTR) {
                          XmlTag parentTag = xmlTag.getParentTag();
                          if (parentTag != null && parentTag.getName().equals(ResourceType.DECLARE_STYLEABLE.getName())) {
                            ResourceItem style = findValueResourceItem(parentTag, psiFile);
                            if (style instanceof PsiResourceItem) {
                              ((PsiResourceItem)style).recomputeValue();
                            }
                          }
                        }

                        return;
                      }
                    }
                  } else {
                    XmlTag parentTag = xmlTag.getParentTag();
                    if (parentTag != null && ResourceType.getEnum(parentTag.getName()) != null) {
                      // <style>, or <plurals>, or <array>, or <string-array>, ...
                      // Edited the attribute value of an item that is wrapped in a <style> tag: invalidate parent cached value
                      if (convertToPsiIfNeeded(psiFile, folderType)) {
                        return;
                      }
                      ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                      if (resourceItem instanceof PsiResourceItem) {
                        if (((PsiResourceItem)resourceItem).recomputeValue()) {
                          myGeneration = ourModificationCounter.incrementAndGet();
                        }
                        return;
                      }
                    }
                  }
                }
              }
            }

            // Fall through: We were not able to directly manipulate the repository to accommodate
            // the edit, so re-scan the whole value file instead
            rescan(psiFile, folderType);

          } else if (folderType == COLOR) {
            PsiElement parent = event.getParent();
            if (parent instanceof XmlElement) {
              if (parent instanceof XmlComment) {
                // Nothing to do
                return;
              }

              if (parent instanceof XmlAttributeValue) {
                PsiElement attribute = parent.getParent();
                if (attribute instanceof XmlProcessingInstruction) {
                  // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                  // <?xml version="1.0" encoding="utf-8"?>
                  return;
                }
              }

              myGeneration = ourModificationCounter.incrementAndGet();
              return;
            }
          } // else: can ignore this edit
        }
      } else {
        PsiElement parent = event.getParent();
        if (isResourceFolder(parent)) {
          PsiElement oldChild = event.getOldChild();
          PsiElement newChild = event.getNewChild();
          if (oldChild instanceof PsiFile) {
            PsiFile oldFile = (PsiFile)oldChild;
            if (PsiProjectListener.isRelevantFile(oldFile)) {
              removeFile(oldFile);
            }
          }
          if (newChild instanceof PsiFile) {
            PsiFile newFile = (PsiFile)newChild;
            if (PsiProjectListener.isRelevantFile(newFile)) {
              addFile(newFile);
            }
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    private void handleValueXmlTextEdit(@Nullable PsiElement parent, @NotNull PsiFile psiFile) {
      if (!(parent instanceof XmlTag)) {
        // Edited text outside the root element
        return;
      }
      XmlTag parentTag = (XmlTag)parent;
      String parentTagName = parentTag.getName();
      if (parentTagName.equals(TAG_RESOURCES)) {
        // Editing whitespace between top level elements; ignore
        return;
      }

      if (parentTagName.equals(TAG_ITEM)) {
        XmlTag style = parentTag.getParentTag();
        if (style != null && ResourceType.getEnum(style.getName()) != null) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          if (convertToPsiIfNeeded(psiFile, folderType)) {
            return;
          }
          // <style>, or <plurals>, or <array>, or <string-array>, ...
          // Edited the text value of an item that is wrapped in a <style> tag: invalidate
          ResourceItem item = findValueResourceItem(style, psiFile);
          if (item instanceof PsiResourceItem) {
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              myGeneration = ourModificationCounter.incrementAndGet();
            }
          }
          return;
        }
      }

      // Find surrounding item
      while (parentTag != null) {
        if (isItemElement(parentTag)) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          if (convertToPsiIfNeeded(psiFile, folderType)) {
            return;
          }
          ResourceItem item = findValueResourceItem(parentTag, psiFile);
          if (item instanceof PsiResourceItem) {
            // Edited XML value
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              myGeneration = ourModificationCounter.incrementAndGet();
            }
          }
          break;
        }
        parentTag = parentTag.getParentTag();
      }

      // Fully handled; other whitespace changes do not affect resources
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      PsiFile psiFile = event.getFile();
      //noinspection StatementWithEmptyBody
      if (psiFile == null) {
        // This is called when you move a file from one folder to another
        if (child instanceof PsiFile) {
          psiFile = (PsiFile)child;
          if (!PsiProjectListener.isRelevantFile(psiFile)) {
            return;
          }

          // If you are renaming files, determine whether we can do a simple replacement
          // (e.g. swap out ResourceFile instances), or whether it changes the type
          // (e.g. moving foo.xml from layout/ to animator/), or whether it adds or removes
          // the type (e.g. moving from invalid to valid resource directories), or whether
          // it just affects the qualifiers (special case of swapping resource file instances).
          String name = psiFile.getName();

          PsiElement oldParent = event.getOldParent();
          PsiDirectory oldParentDir;
          if (oldParent instanceof PsiDirectory) {
            oldParentDir = (PsiDirectory)oldParent;
          } else {
            // Can't find old location: treat this as a file add
            addFile(psiFile);
            return;
          }

          String oldDirName = oldParentDir.getName();
          ResourceFolderType oldFolderType = getFolderType(oldDirName);
          ResourceFolderType newFolderType = ResourceHelper.getFolderType(psiFile);

          boolean wasResourceFolder = oldFolderType != null && isResourceFolder(oldParentDir);
          boolean isResourceFolder = newFolderType != null && isResourceFile(psiFile);

          if (wasResourceFolder == isResourceFolder) {
            if (!isResourceFolder) {
              // Moved a non-resource file around: nothing to do
              return;
            }

            // Moved a resource file from one resource folder to another: we need to update
            // the ResourceFile entries for this file. We may also need to update the types.
            ResourceFile resourceFile = findResourceFile(oldDirName, name);
            if (resourceFile != null) {
              if (oldFolderType != newFolderType) {
                // In some cases we can do this cheaply, e.g. if you move from layout to menu
                // we can just look up and change @layout/foo to @menu/foo, but if you move
                // to or from values folder it gets trickier, so for now just treat this as a delete
                // followed by an add
                removeFile(resourceFile);
                addFile(psiFile);
              } else {
                VirtualFile vFile = VfsUtil.findFileByIoFile(resourceFile.getFile(), false);
                if (!(resourceFile instanceof PsiResourceFile) || vFile == null) {
                  // We can't do the simple Psi-based surgery below, so removeFile and addFile to rescan.
                  removeFile(resourceFile);
                  addFile(psiFile);
                  return;
                }
                myResourceFiles.remove(vFile);
                myResourceFiles.put(psiFile.getVirtualFile(), resourceFile);
                PsiResourceFile psiResourceFile = (PsiResourceFile)resourceFile;
                PsiDirectory newParent = psiFile.getParent();
                assert newParent != null; // Since newFolderType != null
                String newDirName = newParent.getName();
                psiResourceFile.setPsiFile(psiFile, getQualifiers(newDirName));
                myGeneration = ourModificationCounter.incrementAndGet(); // qualifiers may have changed: can affect configuration matching
                // We need to recompute resource values too, since some of these can point to
                // the old file (e.g. a drawable resource could have a DensityBasedResourceValue
                // pointing to the old file
                for (ResourceItem item : psiResourceFile) { // usually just 1
                  if (item instanceof PsiResourceItem) {
                    ((PsiResourceItem)item).recomputeValue();
                  }
                }
                invalidateItemCaches();
              }
            } else {
              // Couldn't find previous file; just add new file
              addFile(psiFile);
            }
          } else if (isResourceFolder) {
            // Moved file into resource folder: treat it as a file add
            addFile(psiFile);
          } else {
            //noinspection ConstantConditions
            assert wasResourceFolder;

            // Moved file out of resource folders: treat it as a file deletion.
            // The only trick here is that we don't actually have the PsiFile anymore.
            // Work around this by searching our PsiFile to ResourceFile map for a match.
            String dirName = oldParentDir.getName();
            ResourceFile resourceFile = findResourceFile(dirName, name);
            if (resourceFile != null) {
              removeFile(resourceFile);
            }
          }
        }
      } else {
        // Change inside a file
        // Ignore: moving elements around doesn't affect the resources
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public final void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = false;
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      PsiElement parent = event.getParent();
      // Called after children have changed. There are typically individual childMoved, childAdded etc
      // calls that we hook into for more specific details. However, there are some events we don't
      // catch using those methods, and for that we have the below handling.
      if (myIgnoreChildrenChanged) {
        // We've already processed this change as one or more individual childMoved, childAdded, childRemoved etc calls
        // However, we sometimes get some surprising (=bogus) events where the parent and the child
        // are the same, and in those cases there may be other child events we need to process
        // so fall through and process the whole file
        if (parent != event.getChild()) {
          return;
        }
      }
      else if (event.getNewChild() == null &&
               event.getOldChild() == null &&
               event.getOldParent() == null &&
               event.getNewParent() == null &&
               parent instanceof PsiFile) {
        return;
      }

      if (parent != null && parent.getChildren().length == 1 && parent.getChildren()[0] instanceof PsiWhiteSpace) {
        // This event is just adding white spaces
        return;
      }

      PsiFile psiFile = event.getFile();
      if (psiFile != null && PsiProjectListener.isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          if (folderType != null && isResourceFile(psiFile)) {
            // TODO: If I get an XmlText change and the parent is the resources tag or it's a layout, nothing to do.
            rescan(psiFile, folderType);
          }
        }
      } else {
        Throwable throwable = new Throwable();
        throwable.fillInStackTrace();
        LOG.debug("Received unexpected childrenChanged event for inter-file operations", throwable);
      }
    }

    // There are cases where a file is renamed, and I don't get a pre-notification. Use this flag
    // to detect those scenarios, and in that case, do proper cleanup.
    // (Note: There are also cases where *only* beforePropertyChange is called, not propertyChange.
    // One example is the unit test for the raw folder, where we're renaming a file, and we get
    // the beforePropertyChange notification, followed by childReplaced on the PsiDirectory, and
    // nothing else.
    private boolean mySeenPrePropertyChange;

    @Override
    public final void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName()) {
        // This is called when you rename a file (before the file has been renamed)
        PsiElement child = event.getChild();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (PsiProjectListener.isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
            removeFile(psiFile);
          }
        }
        // The new name will be added in the post hook (propertyChanged rather than beforePropertyChange)
      }

      mySeenPrePropertyChange = true;
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName() && isResourceFolder(event.getParent())) {
        // This is called when you rename a file (after the file has been renamed)
        PsiElement child = event.getElement();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (PsiProjectListener.isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
            if (!mySeenPrePropertyChange) {
              Object oldValue = event.getOldValue();
              if (oldValue instanceof String) {
                PsiDirectory parent = psiFile.getParent();
                String oldName = (String)oldValue;
                if (parent != null && parent.findFile(oldName) == null) {
                  removeFile(findResourceFile(parent.getName(), oldName));
                }
              }
            }

            addFile(psiFile);
          }
        }
      }

      // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource folders?
      // and what about PROP_FILE_TYPES -- can users change the type of an XML File to something else?

      mySeenPrePropertyChange = false;
    }
  }

  @Nullable
  private ResourceFile findResourceFile(String dirName, String fileName) {
    int index = dirName.indexOf('-');
    String qualifiers;
    String folderTypeName;
    if (index == -1) {
      qualifiers = "";
      folderTypeName = dirName;
    } else {
      qualifiers = dirName.substring(index + 1);
      folderTypeName = dirName.substring(0, index);
    }
    ResourceFolderType folderType = ResourceFolderType.getTypeByName(folderTypeName);

    for (ResourceFile resourceFile : myResourceFiles.values()) {
      String name = resourceFile.getFile().getName();
      ResourceFolderType resFolderType = ResourceHelper.getFolderType(resourceFile);
      if (folderType == resFolderType && fileName.equals(name) && qualifiers.equals(resourceFile.getQualifiers())) {
        return resourceFile;
      }
    }

    return null;
  }

  private void removeItemsFromFile(ResourceFile resourceFile) {
    for (ResourceItem item : resourceFile.getItems()) {
      removeItems(resourceFile, item.getType(), item.getName(), false);  // no need since we're discarding the file
    }
  }

  private static boolean isItemElement(XmlTag xmlTag) {
    String tag = xmlTag.getName();
    if (tag.equals(TAG_RESOURCES)) {
      return false;
    }
    return tag.equals(TAG_ITEM) || ResourceType.getEnum(tag) != null;
  }

  @Nullable
  private ResourceItem findValueResourceItem(XmlTag tag, @NotNull PsiFile file) {
    if (!tag.isValid()) {
      // This function should only be used if we know file's items are PsiResourceItems.
      ResourceFile resFile = myResourceFiles.get(file.getVirtualFile());
      if (resFile != null) {
        assert resFile instanceof PsiResourceFile;
        PsiResourceFile resourceFile = (PsiResourceFile)resFile;
        for (ResourceItem item : resourceFile) {
          PsiResourceItem pri = (PsiResourceItem)item;
          XmlTag xmlTag = pri.getTag();
          if (xmlTag == tag) {
            return item;
          }
        }
      }
      return null;
    }
    String name = tag.getAttributeValue(ATTR_NAME);
    return name != null ? findValueResourceItem(tag, file, name) : null;
  }

  @Nullable
  private ResourceItem findValueResourceItem(XmlTag tag, @NotNull PsiFile file, String name) {
    ResourceType type = AndroidResourceUtil.getType(tag);
    return findResourceItem(type, file, name, tag);
  }

  @Nullable
  private ResourceItem findResourceItem(@Nullable ResourceType type, @NotNull PsiFile file, @Nullable String name, @Nullable XmlTag tag) {
    if (type != null && name != null) {
      ListMultimap<String, ResourceItem> map = myItems.get(type);
      if (map != null) {
        List<ResourceItem> items = map.get(name);
        assert items != null;
        File ioFile = VfsUtilCore.virtualToIoFile(file.getVirtualFile());
        if (tag != null) {
          for (ResourceItem item : items) {
            ResourceFile resourceFile = item.getSource();
            if (resourceFile != null && FileUtil.filesEqual(resourceFile.getFile(), ioFile)) {
              assert item instanceof PsiResourceItem;
              PsiResourceItem psiItem = (PsiResourceItem)item;
              if (psiItem.getTag() == tag) {
                return item;
              }
            }
          }
          return null;
        }

        for (ResourceItem item : items) {
          ResourceFile resourceFile = item.getSource();
          if (resourceFile != null && FileUtil.filesEqual(resourceFile.getFile(), ioFile)) {
            return item;
          }
        }
      }
    }

    return null;
  }

  // For debugging only
  @Override
  public String toString() {
    return getClass().getSimpleName() + " for " + myResourceDir + ": @" + Integer.toHexString(System.identityHashCode(this));
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    return Sets.newHashSet(myResourceDir);
  }

  // Not a super-robust comparator. Compares a subset of the repo state for testing.
  // Returns false if a difference is found.
  // Returns true if the repositories are roughly equivalent.
  @VisibleForTesting
  boolean equalFilesItems(ResourceFolderRepository other) {
    File myResourceDirFile = VfsUtilCore.virtualToIoFile(myResourceDir);
    File otherResourceDir = VfsUtilCore.virtualToIoFile(other.myResourceDir);
    if (!FileUtil.filesEqual(myResourceDirFile, otherResourceDir)) {
      return false;
    }

    if (myResourceFiles.size() != other.myResourceFiles.size()) {
      return false;
    }
    for (Map.Entry<VirtualFile, ResourceFile> fileEntry : myResourceFiles.entrySet()) {
      ResourceFile otherResFile = other.myResourceFiles.get(fileEntry.getKey());
      if (otherResFile == null) {
        return false;
      }
      if (!FileUtil.filesEqual(fileEntry.getValue().getFile(), otherResFile.getFile())) {
        return false;
      }
    }

    if (myItems.size() != other.myItems.size()) {
      return false;
    }
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : myItems.entrySet()) {
      ListMultimap<String, ResourceItem> ownEntries = entry.getValue();
      ListMultimap<String, ResourceItem> otherEntries = other.myItems.get(entry.getKey());
      if (otherEntries == null || otherEntries.size() != ownEntries.size()) {
        return false;
      }
      for (Map.Entry<String, ResourceItem> itemEntry : ownEntries.entries()) {
        List<ResourceItem> otherItemsList = otherEntries.get(itemEntry.getKey());
        if (otherItemsList == null) {
          return false;
        }
        final ResourceItem item = itemEntry.getValue();
        if (!ContainerUtil.exists(otherItemsList, new Condition<ResourceItem>() {
          @Override
          public boolean value(ResourceItem resourceItem) {
            // Use #compareTo instead of #equals because #equals compares pointers of mSource.
            if (resourceItem.compareTo(item) != 0) {
              return false;
            }
            // #compareTo doesn't check the ResourceValue. At least check that getValue is equivalent (getRawXmlText may be different).
            // Skip ID type resources, where the ResourceValues are not important and where blob writing doesn't preserve the value.
            if (item.getType() != ResourceType.ID) {
              ResourceValue resValue = item.getResourceValue(false);
              ResourceValue otherResValue = resourceItem.getResourceValue(false);
              if (resValue == null || otherResValue == null) {
                if (resValue != otherResValue) {
                  return false;
                }
              }
              else {
                String resValueStr = resValue.getValue();
                String otherResValueStr = otherResValue.getValue();
                if (resValueStr == null || otherResValueStr == null) {
                  if (resValueStr != otherResValueStr) {
                    return false;
                  }
                }
                else {
                  if (!resValueStr.equals(otherResValueStr)) {
                    return false;
                  }
                }
              }
            }
            // We can only compareValueWith (compare equivalence of XML nodes) for VALUE items.
            // For others, the XML node may be different before and after serialization.
            ResourceFile source = item.getSource();
            ResourceFile otherSource = resourceItem.getSource();
            if (source != null && otherSource != null) {
              ResourceFolderType ownFolderType = ResourceHelper.getFolderType(source);
              ResourceFolderType otherFolderType = ResourceHelper.getFolderType(otherSource);
              if (otherFolderType != ownFolderType) {
                return false;
              }
              if (otherFolderType == VALUES) {
                return resourceItem.compareValueWith(item);
              }
            }
            return true;
          }
        })) {
          return false;
        }
      }
    }

    // Only compare the keys.
    return myDataBindingResourceFiles.keySet().equals(other.myDataBindingResourceFiles.keySet());
  }
}

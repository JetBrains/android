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

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTRS_DATA_BINDING;
import static com.android.SdkConstants.ATTR_ALIAS;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.EXT_PNG;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TAGS_DATA_BINDING;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_IMPORT;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_VARIABLE;
import static com.android.resources.ResourceFolderType.COLOR;
import static com.android.resources.ResourceFolderType.DRAWABLE;
import static com.android.resources.ResourceFolderType.FONT;
import static com.android.resources.ResourceFolderType.LAYOUT;
import static com.android.resources.ResourceFolderType.MIPMAP;
import static com.android.resources.ResourceFolderType.RAW;
import static com.android.resources.ResourceFolderType.VALUES;
import static com.android.resources.ResourceFolderType.getFolderType;
import static com.android.resources.ResourceFolderType.getTypeByName;
import static com.android.tools.idea.res.PsiProjectListener.isRelevantFile;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;
import static org.jetbrains.android.util.AndroidResourceUtil.getResourceTypeForResourceTag;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.TextResourceValue;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.resources.MergeConsumer;
import com.android.ide.common.resources.MergedResourceWriter;
import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.NoOpResourcePreprocessor;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceMerger;
import com.android.ide.common.resources.ResourceMergerItem;
import com.android.ide.common.resources.ResourcePreprocessor;
import com.android.ide.common.resources.ResourceSet;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link ResourceFolderRepository} is leaf in the repository tree, and is used for user editable resources (e.g. the resources in the
 * project, typically the res/main source set.) Each ResourceFolderRepository contains the resources provided by a single res folder. This
 * repository is built on top of IntelliJ’s PSI infrastructure. This allows it (along with PSI listeners) to be updated incrementally; for
 * example, when it notices that the user is editing the value inside a <string> element in a value folder XML file, it will directly update
 * the resource value for the given resource item, and so on.
 *
 * <p>For efficiency, the ResourceFolderRepository is initialized using non-PSI parsers and then
 * lazily switches to PSI parsers after edits. See also {@code README.md} in this package.
 *
 * <p>Remaining work:
 * <ul>
 * <li>Find some way to have event updates in this resource folder directly update parent repositories
 * (typically {@link ModuleResourceRepository}</li>
 * <li>Add defensive checks for non-read permission reads of resource values</li>
 * <li>Idea: For {@link #rescan}; compare the removed items from the added items, and if they're the same, avoid
 * creating a new generation.</li>
 * <li>Register the psi project listener as a project service instead</li>
 * </ul>
 */
public final class ResourceFolderRepository extends LocalResourceRepository implements SingleNamespaceResourceRepository {
  private static final Logger LOG = Logger.getInstance(ResourceFolderRepository.class);

  private final Module myModule;
  private final AndroidFacet myFacet;
  private final PsiListener myListener;
  private final VirtualFile myResourceDir;
  @NotNull private final ResourceNamespace myNamespace;

  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  private final ResourceTable myFullTable = new ResourceTable();

  private final Map<VirtualFile, ResourceItemSource<? extends ResourceItem>> sources = new HashMap<>();
  // qualifiedName -> PsiResourceFile
  private Map<String, DataBindingLayoutInfo> myDataBindingResourceFiles = new HashMap<>();
  private long myDataBindingResourceFilesModificationCount = Long.MIN_VALUE;
  private final Object SCAN_LOCK = new Object();
  private Set<PsiFile> myPendingScans;

  /**
   * State of the initial scan, which uses {@link ResourceSet} and falls back to the PSI scanner on errors.
   *
   * <p>In production code the field is cleared after object construction is done; in tests it's kept for inspection.
   *
   * <p>This is only used in the constructor, from the constructing thread, with no risk of unsynchronized access.
   */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") // See above.
  @VisibleForTesting
  InitialScanState myInitialScanState;

  @VisibleForTesting
  static int ourFullRescans;

  private ResourceFolderRepository(@NotNull AndroidFacet facet, @NotNull VirtualFile resourceDir, @NotNull ResourceNamespace namespace) {
    super(resourceDir.getName());
    myFacet = facet;
    myModule = facet.getModule();
    myListener = new PsiListener();
    myResourceDir = resourceDir;
    myNamespace = namespace;

    ResourceMerger merger = loadPreviousStateIfExists();
    myInitialScanState = new InitialScanState(merger, VfsUtilCore.virtualToIoFile(myResourceDir));
    scanRemainingFiles();
    Application app = ApplicationManager.getApplication();

    // TODO(b/76409654): figure out how to store the state in namespaced projects.
    if (!hasFreshFileCache() && !namespacesUsed() && !app.isUnitTestMode()) {
      saveStateToFile();
    }
    // Clear some unneeded state (myInitialScanState's resource merger holds a second map of items).
    // Skip for unit tests, which may need to test saving separately (saving is normally skipped for unit tests).
    if (!app.isUnitTestMode()) {
      myInitialScanState = null;
    }
  }

  @NotNull
  AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public VirtualFile getResourceDir() {
    return myResourceDir;
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  /** NOTE: You should normally use {@link ResourceFolderRegistry#get} rather than this method. */
  @NotNull
  static ResourceFolderRepository create(@NotNull AndroidFacet facet, @NotNull VirtualFile dir,
                                         @NotNull ResourceNamespace namespace) {
    return new ResourceFolderRepository(facet, dir, namespace);
  }

  /**
   * Saves the non-Psi XML state as a single blob for faster loading the second time
   * by {@link #loadPreviousStateIfExists}.
   */
  @VisibleForTesting
  void saveStateToFile() {
    File blobRoot = ResourceFolderRepositoryFileCacheService.get().getResourceDir(myModule.getProject(), myResourceDir);
    if (blobRoot == null) {
      // The cache is invalid, do nothing.
      return;
    }

    try {
      ResourcePreprocessor preprocessor = NoOpResourcePreprocessor.INSTANCE;
      File tempDirectory = FileUtil.createTempDirectory("resource", "tmp", false);
      try {
        MergeConsumer<ResourceMergerItem> consumer =
            MergedResourceWriter.createWriterWithoutPngCruncher(blobRoot, null, null, preprocessor, tempDirectory);
        myInitialScanState.myResourceMerger.writeBlobToWithTimestamps(blobRoot, consumer);
      } finally {
        FileUtil.delete(tempDirectory);
      }
    }
    catch (MergingException | IOException e) {
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
    if (namespacesUsed()) {
      // TODO(b/76409654): figure out how to store the state in namespaced projects.
      return createFreshResourceMerger();
    }

    File blobRoot = ResourceFolderRepositoryFileCacheService.get().getResourceDir(myModule.getProject(), myResourceDir);
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
    // This temp resourceFiles set is just to avoid calling VfsUtil.findFileByIoFile repeatedly.
    Set<ResourceFile> resourceFiles = new HashSet<>();
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
      LOG.warn(String.format("source file %1$s, does not match resource dir %2$s", sourceFiles.get(0), myResourceDirFile));
      return createFreshResourceMerger();
    }

    // Items to be inserted into the repo, while holding ITEM_MAP_LOCK. The loop below does too much I/O to hold the lock the whole time.
    Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();

    for (ResourceMergerItem item: dataSet.getDataMap().values()) {
      ResourceFile file = item.getSourceFile();
      if (file != null) {
        if (!resourceFiles.contains(file)) {
          VirtualFile vFile = VfsUtil.findFileByIoFile(file.getFile(), false);
          if (vFile == null) {
            // Cannot handle this item, mark it ignored so that it doesn't persist.
            item.setIgnoredFromDiskMerge(true);
            continue;
          }
          resourceFiles.add(file);
          sources.put(vFile, new ResourceFileAdapter(file));
        }
        addToResult(result, item);
      } else {
        // Cannot handle this item, mark it ignored to that it doesn't persist.
        item.setIgnoredFromDiskMerge(true);
      }
    }

    commitToRepository(result);

    return merger;
  }

  private boolean namespacesUsed() {
    return ResourceRepositoryManager.getInstance(myFacet).getNamespacing() != AaptOptions.Namespacing.DISABLED;
  }

  private static void addToResult(Map<ResourceType, ListMultimap<String, ResourceItem>> result, ResourceItem item) {
    // The insertion order matters, see AppResourceRepositoryTest#testStringOrder.
    result.computeIfAbsent(item.getType(), t -> LinkedListMultimap.create()).put(item.getName(), item);
  }

  /**
   * Inserts the computed resources into this repository, while holding the global repository lock.
   */
  private void commitToRepository(Map<ResourceType, ListMultimap<String, ResourceItem>> itemsByType) {
    synchronized (ITEM_MAP_LOCK) {
      for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : itemsByType.entrySet()) {
        getMap(myNamespace, entry.getKey(), true).putAll(entry.getValue());
      }
    }
  }

  private ResourceMerger createFreshResourceMerger() {
    ResourceMerger merger = new ResourceMerger(0 /* minSdk */);
    ResourceSet myData = new ResourceSet(myResourceDir.getName(), myNamespace, null, false /* validateEnabled */);
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
  @VisibleForTesting
  boolean hasFreshFileCache() {
    return myInitialScanState.numXmlReparsed * 4 <= myInitialScanState.numXml;
  }

  /**
   * Tracks state used by the initial scan, which may be used to save the state to a cache.
   *
   * This also tracks how fresh the repo file-cache is by tracking how many xml file were reparsed during scan.
   * The file cache omits non-XML single-file items, since those are easily derived from the file path.
   */
  static class InitialScanState {
    int numXml; // Doesn't count files that are explicitly skipped
    int numXmlReparsed;

    final ResourceMerger myResourceMerger;
    final ResourceSet myResourceSet;
    final ILogger myILogger;
    final File myResourceDir;
    final Collection<PsiFileResourceQueueEntry> myPsiFileResourceQueue = new ArrayList<>();
    final Collection<PsiValueResourceQueueEntry> myPsiValueResourceQueue = new ArrayList<>();

    InitialScanState(ResourceMerger merger, File resourceDir) {
      myResourceMerger = merger;
      assert myResourceMerger.getDataSets().size() == 1;
      myResourceSet = myResourceMerger.getDataSets().get(0);
      myResourceSet.setShouldParseResourceIds(true);
      myResourceSet.setDontNormalizeQualifiers(true);
      myResourceSet.setTrackSourcePositions(false);
      myILogger = new LogWrapper(LOG).alwaysLogAsDebug(true).allowVerbose(false);
      myResourceDir = resourceDir;
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

    public void queuePsiFileResourceScan(PsiFileResourceQueueEntry data) {
      myPsiFileResourceQueue.add(data);
    }

    public void queuePsiValueResourceScan(PsiValueResourceQueueEntry data) {
      myPsiValueResourceQueue.add(data);
    }
  }

  /**
   * Tracks file-based resources where init via VirtualFile failed. We retry init via PSI for these files.
   */
  private static class PsiFileResourceQueueEntry {
    public final VirtualFile file;
    public final String qualifiers;
    public final ResourceFolderType folderType;
    public final FolderConfiguration folderConfiguration;

    PsiFileResourceQueueEntry(VirtualFile file, String qualifiers,
                              ResourceFolderType folderType, FolderConfiguration folderConfiguration) {
      this.file = file;
      this.qualifiers = qualifiers;
      this.folderType = folderType;
      this.folderConfiguration = folderConfiguration;
    }
  }

  /**
   * Tracks value resources where init via VirtualFile failed. We retry init via PSI for these files.
   */
  private static class PsiValueResourceQueueEntry {
    public final VirtualFile file;
    public final String qualifiers;
    public final FolderConfiguration folderConfiguration;

    PsiValueResourceQueueEntry(VirtualFile file, String qualifiers, FolderConfiguration folderConfiguration) {
      this.file = file;
      this.qualifiers = qualifiers;
      this.folderConfiguration = folderConfiguration;
    }
  }

  private void scanRemainingFiles() {
    if (!myResourceDir.isValid()) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(() -> getPsiDirsForListener(myResourceDir));

    Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();
    scanResFolder(result, myResourceDir);
    ApplicationManager.getApplication().runReadAction(() -> scanQueuedPsiResources(result));
    commitToRepository(result);
  }

  /**
   * Currently, {@link com.intellij.psi.impl.file.impl.PsiVFSListener} requires that at least the parent directory of each file has been
   * accessed as PSI before bothering to notify any listener of events. So, make a quick pass to grab the necessary PsiDirectories.
   *
   * @param resourceDir the root resource directory.
   */
  private void getPsiDirsForListener(@NotNull VirtualFile resourceDir) {
    PsiManager manager = PsiManager.getInstance(myModule.getProject());
    PsiDirectory resourceDirPsi = manager.findDirectory(resourceDir);
    if (resourceDirPsi != null) {
      resourceDirPsi.getSubdirectories();
    }
  }

  /**
   * For resource files that failed when scanning with a VirtualFile, retry with PsiFile.
   */
  private void scanQueuedPsiResources(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result) {
    PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
    for (PsiValueResourceQueueEntry valueResource : myInitialScanState.myPsiValueResourceQueue) {
      PsiFile file = psiManager.findFile(valueResource.file);
      if (file != null) {
        scanValueFileAsPsi(result, file, valueResource.folderConfiguration);
      }
    }
    for (PsiFileResourceQueueEntry fileResource : myInitialScanState.myPsiFileResourceQueue) {
      if (!fileResource.file.isValid()) {
        continue;
      }

      PsiFile file = psiManager.findFile(fileResource.file);
      if (file != null) {
        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(fileResource.folderType);
        assert resourceTypes.size() >= 1 : fileResource.folderType;
        ResourceType type = resourceTypes.get(0);
        scanFileResourceFileAsPsi(result, fileResource.folderType, fileResource.folderConfiguration,
                                  type, true, file);
      }
    }
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

  private void scanResFolder(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                             @NotNull VirtualFile resDir) {
    for (VirtualFile subDir : resDir.getChildren()) {
      if (subDir.isValid() && subDir.isDirectory()) {
        String name = subDir.getName();
        ResourceFolderType folderType = getFolderType(name);
        if (folderType != null) {
          FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(name);
          if (folderConfiguration == null) {
            continue;
          }
          String qualifiers = getQualifiers(name);
          if (folderType == VALUES) {
            scanValueResFolder(result, subDir, qualifiers, folderConfiguration);
          }
          else {
            scanFileResourceFolder(result, subDir, folderType, qualifiers, folderConfiguration);
          }
        }
      }
    }
  }

  @NotNull
  private static String getQualifiers(String dirName) {
    int index = dirName.indexOf('-');
    return index != -1 ? dirName.substring(index + 1) : "";
  }

  private void scanFileResourceFolder(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                      @NotNull VirtualFile directory,
                                      @NotNull ResourceFolderType folderType,
                                      String qualifiers,
                                      @NotNull FolderConfiguration folderConfiguration) {
    List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
    assert resourceTypes.size() >= 1 : folderType;
    ResourceType type = resourceTypes.get(0);

    boolean idGeneratingFolder = FolderTypeRelationship.isIdGeneratingFolderType(folderType);

    for (VirtualFile file : directory.getChildren()) {
      if (file.isValid() && !file.isDirectory()) {
        FileType fileType = file.getFileType();
        boolean idGeneratingFile = idGeneratingFolder && fileType == StdFileTypes.XML;
        if (PsiProjectListener.isRelevantFileType(fileType) || folderType == RAW) {
          scanFileResourceFile(result, qualifiers, folderType, folderConfiguration, type, idGeneratingFile, file);
        } // TODO: Else warn about files that aren't expected to be found here?
      }
    }
  }

  private void scanFileResourceFileAsPsi(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                         @NotNull ResourceFolderType folderType,
                                         @NotNull FolderConfiguration folderConfiguration,
                                         @NotNull ResourceType type,
                                         boolean idGenerating,
                                         @NotNull PsiFile file) {
    // XML or image.
    String resourceName = ResourceHelper.getResourceName(file);
    if (FileResourceNameValidator.getErrorTextForNameWithoutExtension(resourceName, folderType) != null) {
      return; // Not a valid file resource name.
    }

    PsiResourceItem item = PsiResourceItem.forFile(resourceName, type, myNamespace, file, false);

    if (idGenerating) {
      List<PsiResourceItem> items = new ArrayList<>();
      items.add(item);
      addToResult(result, item);
      addIds(result, items, file);

      PsiResourceFile resourceFile = new PsiResourceFile(file, items, folderType, folderConfiguration);
      scanDataBinding(resourceFile, getModificationCount());
      sources.put(file.getVirtualFile(), resourceFile);
    } else {
      PsiResourceFile resourceFile = new PsiResourceFile(file, Collections.singletonList(item), folderType, folderConfiguration);
      sources.put(file.getVirtualFile(), resourceFile);
      addToResult(result, item);
    }
  }

  private void scanFileResourceFile(Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                    String qualifiers,
                                    ResourceFolderType folderType,
                                    FolderConfiguration folderConfiguration,
                                    ResourceType type,
                                    boolean idGenerating,
                                    VirtualFile file) {
    ResourceFile resourceFile;
    if (idGenerating) {
      if (sources.containsKey(file)) {
        myInitialScanState.countCacheHit();
        return;
      }
      try {
        resourceFile = myInitialScanState.loadFile(VfsUtilCore.virtualToIoFile(file));
        if (resourceFile == null) {
          // The file-based parser failed for some reason. Fall back to Psi in case it is more lax.
          // Don't count Psi items in myInitialScanState.numXml, because they are never cached.
          myInitialScanState.queuePsiFileResourceScan(new PsiFileResourceQueueEntry(file, qualifiers, folderType, folderConfiguration));
          return;
        }
        boolean isDensityBasedResource = folderType == DRAWABLE || folderType == MIPMAP;
        // We skip caching density-based resources, so don't count those against cache statistics.
        if (!isDensityBasedResource) {
          myInitialScanState.countCacheMiss();
        }
        for (ResourceMergerItem item : resourceFile.getItems()) {
          addToResult(result, item);
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
        myInitialScanState.queuePsiFileResourceScan(
          new PsiFileResourceQueueEntry(file, qualifiers, folderType, folderConfiguration));
        return;
      }
    }
    else {
      // We create the items without adding it to the resource set / resource merger.
      // No need to write these out to blob files, as the item is easily reconstructed from the filename.
      String name = ResourceHelper.getResourceName(file);
      ResourceMergerItem item = new ResourceMergerItem(name, myNamespace, type, null, getLibraryName());
      addToResult(result, item);
      resourceFile = new ResourceFile(VfsUtilCore.virtualToIoFile(file), item, folderConfiguration);
      item.setIgnoredFromDiskMerge(true);
    }
    sources.put(file, new ResourceFileAdapter(resourceFile));
  }

  @Override
  @Nullable
  public DataBindingLayoutInfo getDataBindingLayoutInfo(String layoutName) {
    List<ResourceItem> resourceItems = getResources(myNamespace, ResourceType.LAYOUT, layoutName);
    for (ResourceItem item : resourceItems) {
      if (item instanceof PsiResourceItem) {
        PsiResourceFile source = ((PsiResourceItem)item).getSourceFile();
        if (source != null) {
          return source.getDataBindingLayoutInfo();
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Map<String, DataBindingLayoutInfo> getDataBindingResourceFiles() {
    long modificationCount = getModificationCount();
    if (myDataBindingResourceFilesModificationCount == modificationCount) {
      return myDataBindingResourceFiles;
    }
    myDataBindingResourceFilesModificationCount = modificationCount;
    Map<String, List<DefaultDataBindingLayoutInfo>> infoFilesByConfiguration = sources.values().stream()
      .map(resourceFile -> resourceFile instanceof PsiResourceFile ? (((PsiResourceFile)resourceFile).getDataBindingLayoutInfo()) : null)
      .filter(Objects::nonNull)
      .collect(Collectors.groupingBy(DefaultDataBindingLayoutInfo::getFileName));

    Map<String, DataBindingLayoutInfo> selected = infoFilesByConfiguration.entrySet().stream().flatMap(entry -> {
      if (entry.getValue().size() == 1) {
        DefaultDataBindingLayoutInfo info = entry.getValue().get(0);
        info.setMergedInfo(null);
        return entry.getValue().stream();
      } else {
        MergedDataBindingLayoutInfo mergedDataBindingLayoutInfo = new MergedDataBindingLayoutInfo(entry.getValue());
        entry.getValue().forEach(info -> info.setMergedInfo(mergedDataBindingLayoutInfo));
        ArrayList<DataBindingLayoutInfo> list = new ArrayList<>(1 + entry.getValue().size());
        list.add(mergedDataBindingLayoutInfo);
        list.addAll(entry.getValue());
        return list.stream();
      }
    }).collect(Collectors.toMap(
      DataBindingLayoutInfo::getQualifiedName,
      (DataBindingLayoutInfo kls) -> kls
    ));
    myDataBindingResourceFiles = Collections.unmodifiableMap(selected);
    return myDataBindingResourceFiles;
  }

  @Nullable
  private static XmlTag getLayoutTag(PsiElement element) {
    if (!(element instanceof XmlFile)) {
      return null;
    }
    XmlTag rootTag = ((XmlFile) element).getRootTag();
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
    DefaultDataBindingLayoutInfo info = resourceFile.getDataBindingLayoutInfo();
    assert info != null;
    List<PsiDataBindingResourceItem> items = new ArrayList<>();
    if (dataTag == null) {
      info.replaceItems(items, modificationCount);
      return;
    }
    Set<String> usedNames = new HashSet<>();
    for (XmlTag tag : dataTag.findSubTags(TAG_VARIABLE)) {
      String nameValue = tag.getAttributeValue(ATTR_NAME);
      if (nameValue == null) {
        continue;
      }
      String name = StringUtil.unescapeXmlEntities(nameValue);
      if (StringUtil.isNotEmpty(name)) {
        if (usedNames.add(name)) {
          PsiDataBindingResourceItem item = new PsiDataBindingResourceItem(name, DataBindingResourceType.VARIABLE, tag, resourceFile);
          items.add(item);
        }
      }
    }
    Set<String> usedAliases = new HashSet<>();
    for (XmlTag tag : dataTag.findSubTags(TAG_IMPORT)) {
      String typeValue = tag.getAttributeValue(ATTR_TYPE);
      if (typeValue == null) {
        continue;
      }
      String type = StringUtil.unescapeXmlEntities(typeValue);
      String aliasValue = tag.getAttributeValue(ATTR_ALIAS);
      String alias = aliasValue == null ? null : StringUtil.unescapeXmlEntities(aliasValue);
      if (alias == null) {
        int lastIndexOfDot = type.lastIndexOf('.');
        if (lastIndexOfDot >= 0) {
          alias = type.substring(lastIndexOfDot + 1);
        }
      }
      if (StringUtil.isNotEmpty(alias)) {
        if (usedAliases.add(type)) {
          PsiDataBindingResourceItem item = new PsiDataBindingResourceItem(alias, DataBindingResourceType.IMPORT, tag, resourceFile);
          items.add(item);
        }
      }
    }

    info.replaceItems(items, modificationCount);
  }

  private void scanDataBinding(PsiResourceFile resourceFile, long modificationCount) {
    if (resourceFile.getFolderType() != LAYOUT) {
      resourceFile.setDataBindingLayoutInfo(null);
      return;
    }
    XmlTag layout = getLayoutTag(resourceFile.getPsiFile());
    if (layout == null) {
      resourceFile.setDataBindingLayoutInfo(null);
      return;
    }
    XmlTag dataTag = getDataTag(layout);
    String className;
    String classPackage;
    String modulePackage = MergedManifestManager.getSnapshot(myFacet).getPackage();
    String classAttrValue = null;
    if (dataTag != null) {
      classAttrValue = dataTag.getAttributeValue(ATTR_CLASS);
      if (classAttrValue != null) {
        classAttrValue = StringUtil.unescapeXmlEntities(classAttrValue);
      }
    }
    boolean hasClassNameAttr;
    if (StringUtil.isEmpty(classAttrValue)) {
      className = DataBindingUtil.convertToJavaClassName(resourceFile.getName()) + "Binding";
      classPackage = modulePackage + ".databinding";
      hasClassNameAttr = false;
    } else {
      hasClassNameAttr = true;
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
    if (resourceFile.getDataBindingLayoutInfo() == null) {
      resourceFile
        .setDataBindingLayoutInfo(new DefaultDataBindingLayoutInfo(myFacet, resourceFile, className, classPackage, hasClassNameAttr));
    } else {
      resourceFile.getDataBindingLayoutInfo().update(className, classPackage, hasClassNameAttr, modificationCount);
    }
    scanDataBindingDataTag(resourceFile, dataTag, modificationCount);
  }

  @Override
  @NotNull
  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Override
  @Nullable
  @Contract("_, _, true -> !null")
  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = LinkedListMultimap.create(); // use LinkedListMultimap to preserve ordering for editors that show original order.
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @NotNull
  @Override
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  private void addIds(Map<ResourceType, ListMultimap<String, ResourceItem>>result, List<PsiResourceItem> items, PsiFile file) {
    addIds(result, items, file, false);
  }

  private void addIds(Map<ResourceType, ListMultimap<String, ResourceItem>> result, List<PsiResourceItem> items, PsiElement element,
                      boolean calledFromPsiListener) {
    // "@+id/" names found before processing the view tag corresponding to the id.
    Map<String, XmlTag> pendingResourceIds = new HashMap<>();
    Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(element, XmlTag.class);
    if (element instanceof XmlTag) {
      addId(result, items, (XmlTag)element, pendingResourceIds, calledFromPsiListener);
    }
    if (!xmlTags.isEmpty()) {
      for (XmlTag tag : xmlTags) {
        addId(result, items, tag, pendingResourceIds, calledFromPsiListener);
      }
    }
    // Add any remaining ids.
    if (!pendingResourceIds.isEmpty()) {
      for (Map.Entry<String, XmlTag> entry : pendingResourceIds.entrySet()) {
        String id = entry.getKey();
        PsiResourceItem item = PsiResourceItem.forXmlTag(id, ResourceType.ID, myNamespace, entry.getValue(), calledFromPsiListener);
        items.add(item);
        addToResult(result, item);
      }
    }
  }

  private void addId(Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                     List<PsiResourceItem> items,
                     XmlTag tag,
                     Map<String, XmlTag> pendingResourceIds,
                     boolean calledFromPsiListener) {
    assert tag.isValid();
    ListMultimap<String, ResourceItem> idMultimap = result.get(ResourceType.ID);
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (ANDROID_URI.equals(attribute.getNamespace())) {
        // For all attributes in the android namespace, check if something has a value of the form "@+id/"
        // If the attribute is not android:id, and an item for it hasn't been created yet, add it to
        // the list of pending ids.
        String value = attribute.getValue();
        if (value != null && value.startsWith(NEW_ID_PREFIX) && !ATTR_ID.equals(attribute.getLocalName())) {
          String id = value.substring(NEW_ID_PREFIX.length());
          if (isValidResourceName(id) && idMultimap != null && !idMultimap.containsKey(id) && !pendingResourceIds.containsKey(id)) {
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

      if (isValidResourceName(id)) {
        pendingResourceIds.remove(id);
        PsiResourceItem item = PsiResourceItem.forXmlTag(id, ResourceType.ID, myNamespace, tag, calledFromPsiListener);
        items.add(item);
        addToResult(result, item);
      }
    }
  }

  private void scanValueResFolder(Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                  @NotNull VirtualFile directory,
                                  String qualifiers,
                                  FolderConfiguration folderConfiguration) {
    assert directory.getName().startsWith(FD_RES_VALUES);

    for (VirtualFile file : directory.getChildren()) {
      if (file.isValid() && !file.isDirectory()) {
        scanValueFile(result, qualifiers, file, folderConfiguration);
      }
    }
  }

  private boolean scanValueFileAsPsi(Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                     PsiFile file,
                                     FolderConfiguration folderConfiguration) {
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
        List<PsiResourceItem> items = new ArrayList<>(subTags.length);
        for (XmlTag tag : subTags) {
          String name = tag.getAttributeValue(ATTR_NAME);
          ResourceType type = getResourceTypeForResourceTag(tag);
          if (type != null && isValidResourceName(name)) {
            PsiResourceItem item = PsiResourceItem.forXmlTag(name, type, myNamespace, tag, false);
            addToResult(result, item);
            items.add(item);
            added = true;

            if (type == ResourceType.STYLEABLE) {
              // For styleables we also need to create attr items for its children.
              XmlTag[] attrs = tag.getSubTags();
              if (attrs.length > 0) {
                for (XmlTag child : attrs) {
                  String attrName = child.getAttributeValue(ATTR_NAME);
                  if (isValidResourceName(attrName) && !attrName.startsWith(ANDROID_NS_NAME_PREFIX)
                      // Only add attr nodes for elements that specify a format or have flag/enum children; otherwise
                      // it's just a reference to an existing attr.
                      && (child.getAttribute(ATTR_FORMAT) != null || child.getSubTags().length > 0)) {
                    PsiResourceItem attrItem = PsiResourceItem.forXmlTag(attrName, ResourceType.ATTR, myNamespace, child, false);
                    items.add(attrItem);
                    addToResult(result, attrItem);
                  }
                }
              }
            }
          }
        }

        PsiResourceFile resourceFile = new PsiResourceFile(file, items, VALUES, folderConfiguration);
        sources.put(file.getVirtualFile(), resourceFile);
      }
    }

    return added;
  }

  @Contract(value = "null -> false")
  private static boolean isValidResourceName(@Nullable String name) {
    return !StringUtil.isEmpty(name) && ValueResourceNameValidator.getErrorText(name, null) == null;
  }

  private void scanValueFile(Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                             String qualifiers,
                             VirtualFile virtualFile,
                             FolderConfiguration folderConfiguration) {
    FileType fileType = virtualFile.getFileType();
    if (fileType == StdFileTypes.XML) {
      if (sources.containsKey(virtualFile)) {
        myInitialScanState.countCacheHit();
        return;
      }
      File file = VfsUtilCore.virtualToIoFile(virtualFile);
      try {
        ResourceFile resourceFile = myInitialScanState.loadFile(file);
        if (resourceFile == null) {
          // The file-based parser failed for some reason. Fall back to Psi in case it is more lax.
          myInitialScanState.queuePsiValueResourceScan(new PsiValueResourceQueueEntry(virtualFile, qualifiers, folderConfiguration));
          return;
        }
        for (ResourceItem item : resourceFile.getItems()) {
          addToResult(result, item);
        }
        myInitialScanState.countCacheMiss();
        sources.put(virtualFile, new ResourceFileAdapter(resourceFile));
      }
      catch (MergingException e) {
        // The file-based parser failed for some reason. Fall back to Psi in case it is more lax.
        myInitialScanState.queuePsiValueResourceScan(new PsiValueResourceQueueEntry(virtualFile, qualifiers, folderConfiguration));
      }
    }
  }

  // Schedule a rescan to convert any map ResourceItems to Psi if needed, and return true if conversion
  // is needed (incremental updates which rely on Psi are not possible).
  private boolean convertToPsiIfNeeded(@NotNull PsiFile psiFile, ResourceFolderType folderType) {
    ResourceItemSource resFile = sources.get(psiFile.getVirtualFile());
    if (resFile instanceof PsiResourceFile) {
      return false;
    }
    // This schedules a rescan, and when the actual rescanImmediately happens it will purge non-Psi
    // items as needed, populate psi items, and add to myFileTypes once done.
    rescan(psiFile, folderType);
    return true;
  }

  /**
   * Returns true if the given element represents a resource folder
   * (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder)
   */
  private boolean isResourceFolder(@Nullable PsiElement parent) {
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

  /**
   * Returns true if the given element represents a resource folder
   * (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder)
   */
  private boolean isResourceFolder(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      VirtualFile parentDirectory = virtualFile.getParent();
      if (parentDirectory != null) {
        return parentDirectory.equals(myResourceDir);
      }
    }
    return false;
  }

  private boolean isResourceFile(@NotNull PsiFile psiFile) {
    return isResourceFolder(psiFile.getParent());
  }

  private boolean isResourceFile(@NotNull VirtualFile virtualFile) {
    VirtualFile parent = virtualFile.getParent();
    return parent != null && isResourceFolder(parent);
  }

  @Override
  boolean isScanPending(@NotNull PsiFile psiFile) {
    synchronized (SCAN_LOCK) {
      return myPendingScans != null && myPendingScans.contains(psiFile);
    }
  }

  @VisibleForTesting
  void rescan(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    synchronized(SCAN_LOCK) {
      if (isScanPending(psiFile)) {
        return;
      }

      if (myPendingScans == null) {
        myPendingScans = new HashSet<>();
      }
      myPendingScans.add(psiFile);
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!psiFile.isValid()) return;

      ApplicationManager.getApplication().runWriteAction(() -> {
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
      });
    });
  }

  @Override
  public void sync() {
    super.sync();

    List<PsiFile> files;
    synchronized(SCAN_LOCK) {
      if (myPendingScans == null || myPendingScans.isEmpty()) {
        return;
      }
      files = new ArrayList<>(myPendingScans);
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (PsiFile file : files) {
        if (file.isValid()) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(file);
          if (folderType != null) {
            rescanImmediately(file, folderType);
          }
        }
      }
    });

    synchronized(SCAN_LOCK) {
      myPendingScans = null;
    }
  }

  private void rescanImmediately(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      ApplicationManager.getApplication().runReadAction(() -> rescanImmediately(psiFile, folderType));
      return;
    }

    if (psiFile.getProject().isDisposed()) return;

    Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();

    PsiFile file = psiFile;
    if (folderType == VALUES) {
      // For unit test tracking purposes only
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFullRescans++;

      // First delete out the previous items
      ResourceItemSource<? extends ResourceItem> source = this.sources.get(file.getVirtualFile());
      boolean removed = false;
      if (source != null) {
        for (ResourceItem item : source) {
          removed |= !removeItems(item.getType(), item.getName(), source).isEmpty();
        }

        this.sources.remove(file.getVirtualFile());
      }

      file = ensureValid(file);
      boolean added = false;
      if (file != null) {
        // Add items for this file
        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type
        PsiDirectory fileParent = psiFile.getParent();
        if (fileParent != null) {
          FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
          if (folderConfiguration != null) {
            added = scanValueFileAsPsi(result, file, folderConfiguration);
          }
        }
      }

      if (added || removed) {
        // TODO: Consider doing a deeper diff of the changes to the resource items
        // to determine if the removed and added items actually differ
        setModificationCount(ourModificationCounter.incrementAndGet());
        invalidateParentCaches();
      }
    } else {
      ResourceItemSource<? extends ResourceItem> source = sources.get(file.getVirtualFile());
      // If the old file was a PsiResourceFile, we could try to update ID ResourceItems in place.
      if (source instanceof PsiResourceFile) {
        PsiResourceFile psiResourceFile = (PsiResourceFile)source;
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
          Set<String> idsBefore = new HashSet<>();
          synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, ResourceType.ID);
            if (map != null) {
              List<PsiResourceItem> idItems = new ArrayList<>();
              for (PsiResourceItem item : psiResourceFile) {
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
                  List<ResourceItem> toDelete = new ArrayList<>(mapItems.size());
                  for (ResourceItem mapItem : mapItems) {
                    if (mapItem instanceof PsiResourceItem && ((PsiResourceItem)mapItem).getSourceFile() == psiResourceFile) {
                      toDelete.add(mapItem);
                    }
                  }
                  for (ResourceItem delete : toDelete) {
                    map.remove(delete.getName(), delete);
                  }
                }
              }
              for (PsiResourceItem item : idItems) {
                psiResourceFile.removeItem(item);
              }
            }
          }

          // Add items for this file.
          List<PsiResourceItem> idItems = new ArrayList<>();
          file = ensureValid(file);
          if (file != null) {
            addIds(result, idItems, file);
          }
          if (!idItems.isEmpty()) {
            for (PsiResourceItem item : idItems) {
              psiResourceFile.addItem(item);
            }
          }

          rescanJustDataBinding(psiFile);
          // Identities may have changed even if the ids are the same, so update maps
          invalidateParentCaches(myNamespace, ResourceType.ID);
        }
      } else {
        // Remove old items first, if switching to Psi. Rescan below to add back, but with a possibly different multimap list order.
        boolean switchingToPsi = source != null;
        if (switchingToPsi) {
          removeItemsFromSource(source);
        }
        // For unit test tracking purposes only
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFullRescans++;

        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type

        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        assert resourceTypes.size() >= 1 : folderType;
        ResourceType type = resourceTypes.get(0);

        boolean idGeneratingFolder = FolderTypeRelationship.isIdGeneratingFolderType(folderType);

        file = ensureValid(file);
        if (file != null) {
          PsiDirectory fileParent = psiFile.getParent();
          if (fileParent != null) {
            FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
            if (folderConfiguration != null) {
              boolean idGeneratingFile = idGeneratingFolder && file.getFileType() == StdFileTypes.XML;
              scanFileResourceFileAsPsi(result, folderType, folderConfiguration, type, idGeneratingFile, file);
            }
          }
          setModificationCount(ourModificationCounter.incrementAndGet());
          invalidateParentCaches();
        }
      }
    }

    commitToRepository(result);
  }

  /**
   * @see #removeItems(ResourceType, String, ResourceItemSource, XmlTag)
   */
  @NotNull
  private <T extends ResourceItem> List<T> removeItems(@NotNull ResourceType type,
                                                       @NotNull String name,
                                                       @NotNull ResourceItemSource<T> source) {
    return removeItems(type, name, source, null);
  }

  /**
   * Removes resource items matching the given type, name, source file and tag from {@link #myFullTable}. The tag is optional and only
   * {@link PsiResourceItem}s will be checked against it.
   *
   * @return removed elements
   */
  @NotNull
  private <T extends ResourceItem> List<T> removeItems(@NotNull ResourceType type,
                                                       @NotNull String name,
                                                       @NotNull ResourceItemSource<T> source,
                                                       @Nullable XmlTag xmlTag) {
    List<T> removed = new ArrayList<>();

    synchronized (ITEM_MAP_LOCK) {
      // Remove the item of the given name and type from the given resource file.
      // We CAN'T just remove items found in ResourceFile.getItems() because that map
      // flattens everything down to a single item for a given name (it's using a flat
      // map rather than a multimap) so instead we have to look up from the map instead
      ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, type);
      if (map != null) {
        List<ResourceItem> mapItems = map.get(name);
        if (mapItems != null) {
          ListIterator<ResourceItem> iterator = mapItems.listIterator();
          while (iterator.hasNext()) {
            ResourceItem item = iterator.next();
            if (source.isSourceOf(item)) {
              if (xmlTag == null || !(item instanceof PsiResourceItem) || ((PsiResourceItem)item).wasTag(xmlTag)) {
                iterator.remove();

                //noinspection unchecked: We know that `item` comes from `source` so it's of the correct type.
                removed.add((T)item);
              }
            }
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
    Module module = myFacet.getModule();
    ConfigurationManager configurationManager = ConfigurationManager.findExistingInstance(module);
    if (configurationManager != null) {
      IAndroidTarget target = configurationManager.getTarget();
      if (target != null) {
        AndroidTargetData targetData = AndroidTargetData.getTargetData(target, module);
        if (targetData != null) {
          targetData.clearLayoutBitmapCache(module);
        }
      }
    }
  }

  /**
   * Called when a font file has been changed/deleted. This removes the corresponding file from the
   * Typeface cache inside layoutlib.
   */
  private void clearFontCache(VirtualFile virtualFile) {
    Module module = myFacet.getModule();
    ConfigurationManager configurationManager = ConfigurationManager.findExistingInstance(module);
    if (configurationManager != null) {
      IAndroidTarget target = configurationManager.getConfiguration(virtualFile).getTarget();
      if (target != null) {
        AndroidTargetData targetData = AndroidTargetData.getTargetData(target, module);
        if (targetData != null) {
          targetData.clearFontCache(virtualFile.getPath());
        }
      }
    }
  }

  @NotNull
  public PsiTreeChangeListener getPsiListener() {
    return myListener;
  }

  /** PSI listener which together with VfsListener keeps the repository up to date. */
  // TODO: Reduce reliance on PsiListener as much as possible and use VfsListener instead. See the description of PsiTreeChangeEvent.
  private final class PsiListener extends PsiTreeChangeAdapter {
    private boolean myIgnoreChildrenChanged;

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was added within a file
        ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();
          if (folderType == VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              if (isItemElement(tag)) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                ResourceItemSource<? extends ResourceItem> source = sources.get(psiFile.getVirtualFile());
                if (source != null) {
                  assert source instanceof PsiResourceFile;
                  PsiResourceFile psiResourceFile = (PsiResourceFile)source;
                  String name = tag.getAttributeValue(ATTR_NAME);
                  if (isValidResourceName(name)) {
                    ResourceType type = getResourceTypeForResourceTag(tag);
                    if (type == ResourceType.STYLEABLE) {
                      // Can't handle declare styleable additions incrementally yet; need to update paired attr items
                      rescan(psiFile, folderType);
                      return;
                    }
                    if (type != null) {
                      PsiResourceItem item = PsiResourceItem.forXmlTag(name, type, myNamespace, tag, true);
                      synchronized (ITEM_MAP_LOCK) {
                        getMap(myNamespace, type, true).put(name, item);
                        psiResourceFile.addItem(item);
                        setModificationCount(ourModificationCounter.incrementAndGet());
                        invalidateParentCaches(myNamespace, type);
                        return;
                      }
                    }
                  }
                }
              }

              // See if you just added a new item inside a <style> or <array> or <declare-styleable> etc
              XmlTag parentTag = tag.getParentTag();
              if (parentTag != null && getResourceTypeForResourceTag(parentTag) != null) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                // Yes just invalidate the corresponding cached value
                ResourceItem parentItem = findValueResourceItem(parentTag, psiFile);
                if (parentItem instanceof PsiResourceItem) {
                  if (((PsiResourceItem)parentItem).recomputeValue()) {
                    setModificationCount(ourModificationCounter.incrementAndGet());
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
                if (affectsDataBinding((XmlTag)child)) {
                  rescanJustDataBinding(psiFile);
                }
                List<PsiResourceItem> ids = new ArrayList<>();
                Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();
                addIds(result, ids, child, true);
                commitToRepository(result);
                if (!ids.isEmpty()) {
                  ResourceItemSource<? extends ResourceItem> resFile = sources.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    assert resFile instanceof PsiResourceFile;
                    PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;
                    for (PsiResourceItem id : ids) {
                      psiResourceFile.addItem(id);
                    }
                    setModificationCount(ourModificationCounter.incrementAndGet());
                    invalidateParentCaches(myNamespace, ResourceType.ID);
                  }
                }
                return;
              } else if (child instanceof XmlAttribute || parent instanceof XmlAttribute) {
                // we check both because invalidation might come from XmlAttribute if it is inserted at once.
                XmlAttribute attribute = parent instanceof XmlAttribute
                                         ? (XmlAttribute)parent
                                         : (XmlAttribute) child;
                // warning for separate if branches suppressed because to do.
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // TODO: Update it incrementally
                  rescan(psiFile, folderType);
                } else if (affectsDataBinding(attribute)){
                  rescanJustDataBinding(psiFile);
                }
              }
            }
          } else if (folderType == FONT) {
            clearFontCache(psiFile.getVirtualFile());
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was removed within a file
        ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (folderType == VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc
              if (parent instanceof XmlTag) {
                XmlTag parentTag = (XmlTag)parent;
                if (getResourceTypeForResourceTag(parentTag) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      setModificationCount(ourModificationCounter.incrementAndGet());
                    }

                    if (resourceItem.getType() == ResourceType.ATTR) {
                      parentTag = parentTag.getParentTag();
                      if (parentTag != null && getResourceTypeForResourceTag(parentTag) == ResourceType.STYLEABLE) {
                        ResourceItem declareStyleable = findValueResourceItem(parentTag, psiFile);
                        if (declareStyleable instanceof PsiResourceItem) {
                          if (((PsiResourceItem)declareStyleable).recomputeValue()) {
                            setModificationCount(ourModificationCounter.incrementAndGet());
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
                ResourceItemSource<? extends ResourceItem> source = sources.get(psiFile.getVirtualFile());
                if (source != null) {
                  PsiResourceFile resourceFile = (PsiResourceFile)source;
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
                    ResourceType type = getResourceTypeForResourceTag(tag);
                    if (type != null) {
                      synchronized (ITEM_MAP_LOCK) {
                        ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, type);
                        if (map == null) {
                          return;
                        }
                        List<PsiResourceItem> removed = removeItems(type, name, resourceFile, tag);
                        if (!removed.isEmpty()) {
                          for (PsiResourceItem item : removed) {
                            resourceFile.removeItem(item);
                          }
                          setModificationCount(ourModificationCounter.incrementAndGet());
                          invalidateParentCaches(myNamespace, type);
                        }
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
          } else if (folderType == FONT) {
            clearFontCache(psiFile.getVirtualFile());
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    private void removeFile(@Nullable ResourceItemSource<? extends ResourceItem> source) {
      if (source == null) {
        // No resources for this file
        return;
      }
      // Do an exhaustive search, because the source's underlying VirtualFile is already deleted.
      for (Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>> entry : sources.entrySet()) {
        if (source == entry.getValue()) {
          VirtualFile keyFile = entry.getKey();
          sources.remove(keyFile);
          break;
        }
      }

      setModificationCount(ourModificationCounter.incrementAndGet());
      invalidateParentCaches();
      removeItemsFromSource(source);
    }

    private void addFile(PsiFile psiFile) {
      assert isRelevantFile(psiFile);

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
        if (isRelevantFile(psiFile)) {
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
                XmlAttribute attribute = (XmlAttribute)parent;
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // for each id attribute!
                  ResourceItemSource<? extends ResourceItem> source = sources.get(psiFile.getVirtualFile());
                  if (source != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    if (oldChild instanceof XmlAttributeValue && newChild instanceof XmlAttributeValue) {
                      XmlAttributeValue oldValue = (XmlAttributeValue)oldChild;
                      XmlAttributeValue newValue = (XmlAttributeValue)newChild;
                      ResourceUrl oldResourceUrl = ResourceUrl.parse(oldValue.getValue());
                      ResourceUrl newResourceUrl = ResourceUrl.parse(newValue.getValue());

                      // Make sure to compare name as well as urlType, e.g. if both have @+id or not.
                      if (Objects.equals(oldResourceUrl, newResourceUrl)) {
                        // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc)
                        return;
                      }

                      if (handleIdChange(psiFile, source, xmlTag, newResourceUrl, stripIdPrefix(oldValue.getValue()))) {
                        return;
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
                  ResourceItemSource<? extends ResourceItem> resFile = sources.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    ResourceUrl oldResourceUrl = ResourceUrl.parse(oldChild.getText());
                    ResourceUrl newResourceUrl = ResourceUrl.parse(newChild.getText());

                    // Make sure to compare name as well as urlType, e.g. if both have @+id or not.
                    if (Objects.equals(oldResourceUrl, newResourceUrl)) {
                      // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc)
                      return;
                    }

                    if (handleIdChange(psiFile, resFile, xmlTag, newResourceUrl, stripIdPrefix(oldChild.getText()))) {
                      return;
                    }
                  }

                  rescan(psiFile, folderType);
                } else if (affectsDataBinding(attribute)) {
                  rescanJustDataBinding(psiFile);
                } else if (folderType != VALUES) {
                  // This is an XML change within an ID generating folder to something that it's not an ID. While we do not need
                  // to generate the ID, we need to notify that something relevant has changed.
                  // One example of this change would be an edit to a drawable.
                  setModificationCount(ourModificationCounter.incrementAndGet());
                }
              }

              return;
            }

            // TODO: Handle adding/removing elements in layouts incrementally

            rescan(psiFile, folderType);
          } else if (folderType == VALUES) {
            // This is a folder that *may* contain XML files. Check if this is a relevant XML edit.
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
                if (getResourceTypeForResourceTag(parentTag) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      setModificationCount(ourModificationCounter.incrementAndGet());
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
                  // Edited the name of the item: replace it.
                  ResourceType type = getResourceTypeForResourceTag(xmlTag);
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
                      synchronized (ITEM_MAP_LOCK) {
                        ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, item.getType());
                        if (map != null) {
                          // Found the relevant item: delete it and create a new one in a new location
                          map.remove(oldName, item);
                          if (isValidResourceName(newName)) {
                            PsiResourceItem newItem = PsiResourceItem.forXmlTag(newName, type, myNamespace, xmlTag, true);
                            map.put(newName, newItem);
                            ResourceItemSource<? extends ResourceItem> resFile = sources.get(psiFile.getVirtualFile());
                            if (resFile != null) {
                              PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                              resourceFile.removeItem((PsiResourceItem)item);
                              resourceFile.addItem(newItem);
                            }
                            else {
                              assert false : item;
                            }
                          }
                          setModificationCount(ourModificationCounter.incrementAndGet());
                          invalidateParentCaches(myNamespace, type);
                        }
                      }

                      // Invalidate surrounding declare styleable if any
                      if (type == ResourceType.ATTR) {
                        XmlTag parentTag = xmlTag.getParentTag();
                        if (parentTag != null && getResourceTypeForResourceTag(parentTag) == ResourceType.STYLEABLE) {
                          ResourceItem style = findValueResourceItem(parentTag, psiFile);
                          if (style instanceof PsiResourceItem) {
                            ((PsiResourceItem)style).recomputeValue();
                          }
                        }
                      }

                      return;
                    }
                  } else {
                    XmlTag parentTag = xmlTag.getParentTag();
                    if (parentTag != null && getResourceTypeForResourceTag(parentTag) != null) {
                      // <style>, or <plurals>, or <array>, or <string-array>, ...
                      // Edited the attribute value of an item that is wrapped in a <style> tag: invalidate parent cached value
                      if (convertToPsiIfNeeded(psiFile, folderType)) {
                        return;
                      }
                      ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                      if (resourceItem instanceof PsiResourceItem) {
                        if (((PsiResourceItem)resourceItem).recomputeValue()) {
                          setModificationCount(ourModificationCounter.incrementAndGet());
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

              setModificationCount(ourModificationCounter.incrementAndGet());
              return;
            }
          } else if (folderType == FONT) {
            clearFontCache(psiFile.getVirtualFile());
          } else if (folderType != null) {
            PsiElement parent = event.getParent();

            if (parent instanceof XmlElement) {
              if (parent instanceof XmlComment) {
                // Nothing to do
                return;
              }

              // A change to an XML file that does not require adding/removing resources. This could be a change to the contents of an XML
              // file in the raw folder.
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
          } // else: can ignore this edit
        }
      }

      myIgnoreChildrenChanged = true;
    }

    /**
     * Tries to handle changes to an {@code android:id} tag incrementally.
     *
     * @return true if incremental change succeeded, false otherwise (i.e. a rescan is necessary).
     */
    private boolean handleIdChange(@NotNull PsiFile psiFile,
                                   @NotNull ResourceItemSource<? extends ResourceItem> resFile,
                                   @NotNull XmlTag xmlTag,
                                   @Nullable ResourceUrl newResourceUrl,
                                   @NotNull String oldName) {
      if (resFile instanceof PsiResourceFile) {
        PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;
        ResourceItem item = findResourceItem(ResourceType.ID, psiFile, oldName, xmlTag);
        synchronized (ITEM_MAP_LOCK) {
          ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, ResourceType.ID);
          if (map != null) {
            boolean madeChanges = false;

            if (item != null) {
              // Found the relevant item: delete it and create a new one in a new location
              map.remove(oldName, item);
              if (psiResourceFile.isSourceOf(item)) {
                psiResourceFile.removeItem((PsiResourceItem)item);
              }
              madeChanges = true;
            }

            if (newResourceUrl != null) {
              String newName = newResourceUrl.name;
              if (newResourceUrl.urlType == ResourceUrl.UrlType.CREATE && isValidResourceName(newName)) {
                PsiResourceItem newItem = PsiResourceItem.forXmlTag(newName, ResourceType.ID, myNamespace, xmlTag, true);
                map.put(newName, newItem);
                psiResourceFile.addItem(newItem);
                madeChanges = true;
              }
            }

            if (madeChanges) {
              setModificationCount(ourModificationCounter.incrementAndGet());
              invalidateParentCaches(myNamespace, ResourceType.ID);
            }
            return true;
          }
        }
      }
      return false;
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
        if (style != null && ResourceType.fromXmlTagName(style.getName()) != null) {
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
              setModificationCount(ourModificationCounter.incrementAndGet());
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
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
          }
          break;
        }
        parentTag = parentTag.getParentTag();
      }

      // Fully handled; other whitespace changes do not affect resources.
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        // This is called when you move a file from one folder to another.
        if (child instanceof PsiFile) {
          psiFile = (PsiFile)child;
          if (!isRelevantFile(psiFile)) {
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
            // Can't find old location: treat this as a file add.
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
            ResourceItemSource<? extends ResourceItem> source = findSource(oldDirName, name);
            if (source != null) {
              if (oldFolderType != newFolderType) {
                // In some cases we can do this cheaply, e.g. if you move from layout to menu
                // we can just look up and change @layout/foo to @menu/foo, but if you move
                // to or from values folder it gets trickier, so for now just treat this as a delete
                // followed by an add
                removeFile(source);
                addFile(psiFile);
              } else {
                VirtualFile vFile = source.getVirtualFile();
                if (!(source instanceof PsiResourceFile) || vFile == null) {
                  // We can't do the simple Psi-based surgery below, so removeFile and addFile to rescan.
                  removeFile(source);
                  addFile(psiFile);
                  return;
                }
                sources.remove(vFile);
                sources.put(psiFile.getVirtualFile(), source);
                PsiResourceFile psiResourceFile = (PsiResourceFile)source;
                PsiDirectory newParent = psiFile.getParent();
                assert newParent != null; // Since newFolderType != null
                String newDirName = newParent.getName();
                FolderConfiguration config = FolderConfiguration.getConfigForFolder(newDirName);
                if (config == null) {
                  config = new FolderConfiguration();
                }
                psiResourceFile.setPsiFile(psiFile, config);
                setModificationCount(ourModificationCounter.incrementAndGet()); // Qualifiers may have changed: can affect configuration matching
                // We need to recompute resource values too, since some of these can point to
                // the old file (e.g. a drawable resource could have a DensityBasedResourceValue
                // pointing to the old file
                for (ResourceItem item : psiResourceFile) { // usually just 1
                  if (item instanceof PsiResourceItem) {
                    ((PsiResourceItem)item).recomputeValue();
                  }
                }
                invalidateParentCaches();
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
            ResourceItemSource<? extends ResourceItem> resourceFile = findSource(dirName, name);
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
      else if (event instanceof PsiTreeChangeEventImpl && (((PsiTreeChangeEventImpl)event).isGenericChange())) {
          return;
      }

      if (parent != null && parent.getChildren().length == 1 && parent.getChildren()[0] instanceof PsiWhiteSpace) {
        // This event is just adding white spaces
        return;
      }

      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
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

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.getPropertyName() && isResourceFolder(event.getParent())) {
        // This is called when you rename a file (after the file has been renamed)
        PsiElement child = event.getElement();
        if (child instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)child;
          if (isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
            // There are cases where a file is renamed, and I don't get a pre-notification. Use this flag
            // to detect those scenarios, and in that case, do proper cleanup.
            // (Note: There are also cases where *only* beforePropertyChange is called, not propertyChange.
            // One example is the unit test for the raw folder, where we're renaming a file, and we get
            // the beforePropertyChange notification, followed by childReplaced on the PsiDirectory, and
            // nothing else.
            Object oldValue = event.getOldValue();
            if (oldValue instanceof String) {
              PsiDirectory parent = psiFile.getParent();
              String oldName = (String)oldValue;
              if (parent != null && parent.findFile(oldName) == null) {
                ResourceItemSource<? extends ResourceItem> source;

                // Depending on the implementation of ResourceItemSource, the underlying
                // resource file might either have the old file name or the new file name.
                // Resources converted to PSI use PsiResourceFile and thus PsiFile which already
                // has the new name by now, as opposed to ResourceFileAdapter, ResourceFile and File.
                // So we first try to find the cached source using the old name and if we can't
                // find one, we use the new name, which is the current name of the psiFile
                // (psiFile.getName())
                source = findSource(parent.getName(), oldName);
                if (source == null) {
                  source = findSource(parent.getName(), psiFile.getName());
                }
                removeFile(source);
              }
            }

            addFile(psiFile);
          }
        }
      }

      // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource folders?
      // and what about PROP_FILE_TYPES -- can users change the type of an XML File to something else?
    }

    /**
     * Checks if changes in the given attribute affects data binding.
     *
     * @param attribute The XML attribute
     * @return true if changes in this element would affect data binding
     */
    private boolean affectsDataBinding(@NotNull XmlAttribute attribute) {
      return ArrayUtil.contains(attribute.getLocalName(), ATTRS_DATA_BINDING)
             && ArrayUtil.contains(attribute.getParent().getLocalName(), TAGS_DATA_BINDING);
    }

    /**
     * Checks if changes in the given XmlTag affects data binding.
     *
     * @param xmlTag the tag to check
     * @return true if changes in the xml tag would affect data binding info, false otherwise
     */
    private boolean affectsDataBinding(@NotNull XmlTag xmlTag) {
      return ArrayUtil.contains(xmlTag.getLocalName(), TAGS_DATA_BINDING);
    }
  }

  void onFileCreated(@NotNull VirtualFile file) {
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    if (folderType == null) {
      return;
    }

    if (isResourceFile(file) && isRelevantFile(file)) {
      PsiFile psiFile = PsiManager.getInstance(myModule.getProject()).findFile(file);
      if (psiFile != null) {
        rescanImmediately(psiFile, folderType);
      }
    }
  }

  void onFileOrDirectoryRemoved(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      for (Iterator<Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>>> iterator = sources.entrySet().iterator();
           iterator.hasNext(); ) {
        Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>> entry = iterator.next();
        iterator.remove();
        VirtualFile sourceFile = entry.getKey();
        if (VfsUtilCore.isAncestor(file, sourceFile, true)) {
          onSourceRemoved(sourceFile, entry.getValue());
        }
      }
    }
    else {
      ResourceItemSource<? extends ResourceItem> source = sources.remove(file);
      if (source != null) {
        onSourceRemoved(file, source);
      }
    }
  }

  private void onSourceRemoved(@NotNull VirtualFile file, @NotNull ResourceItemSource<? extends ResourceItem> source) {
    setModificationCount(ourModificationCounter.incrementAndGet());
    invalidateParentCaches();

    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    // Check if there may be multiple items to remove.
    if (folderType == VALUES ||
        (folderType != null && FolderTypeRelationship.isIdGeneratingFolderType(folderType) && file.getFileType() == StdFileTypes.XML)) {
      removeItemsFromSource(source);
    } else if (folderType != null) {
      // Simpler: remove the file item.
      if (folderType == DRAWABLE) {
        FileType fileType = file.getFileType();
        if (fileType.isBinary() && fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG)) {
          bitmapUpdated();
        }
      }

      if (folderType == FONT) {
        clearFontCache(file);
      }

      List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
      for (ResourceType type : resourceTypes) {
        if (type != ResourceType.ID) {
          String name = ResourceHelper.getResourceName(file);
          removeItems(type, name, source);
        }
      }
    } // else: not a resource folder
  }

  private void rescanJustDataBinding(@NotNull PsiFile psiFile) {
    ResourceItemSource<? extends ResourceItem> resFile = sources.get(psiFile.getVirtualFile());
    if (resFile != null) {
      // Data-binding files are always scanned as PsiResourceFiles.
      PsiResourceFile resourceFile = (PsiResourceFile)resFile;

      // TODO: this is a targeted workaround for b/77658263, but we need to fix the invalid Psi eventually.
      // At this point, it's possible resFile._psiFile is invalid and has a different FileViewProvider than psiFile, even though in theory
      // they should be identical.
      if (!resourceFile.getPsiFile().isValid()) {
        resourceFile.setPsiFile(psiFile, resourceFile.getFolderConfiguration());
      }

      setModificationCount(ourModificationCounter.incrementAndGet());
      scanDataBinding(resourceFile, getModificationCount());
    }
  }

  @Nullable
  private ResourceItemSource<? extends ResourceItem> findSource(String dirName, String fileName) {
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
    ResourceFolderType folderType = getTypeByName(folderTypeName);
    FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForQualifierString(qualifiers);
    if (folderConfiguration == null) {
      folderConfiguration = new FolderConfiguration();
    }

    for (ResourceItemSource<? extends  ResourceItem> source : sources.values()) {
      String sourceFilename;
      // The file may not exist anymore in the filesystem, so VFS won't find it. For the case of ResourceFile, get the File object directly.
      if (source instanceof ResourceFileAdapter) {
        sourceFilename = ((ResourceFileAdapter)source).getResourceFile().getFile().getName();
      } else {
        VirtualFile virtualFile = source.getVirtualFile();
        sourceFilename = virtualFile == null ? null : virtualFile.getName();
      }
      if (folderType == source.getFolderType() &&
          fileName.equals(sourceFilename) &&
          folderConfiguration.equals(source.getFolderConfiguration())) {
        return source;
      }
    }

    return null;
  }

  private void removeItemsFromSource(ResourceItemSource<? extends ResourceItem> source) {
    synchronized (ITEM_MAP_LOCK) {
      for (ResourceItem item : source) {
        removeItems(item.getType(), item.getName(), source);
      }
    }
  }

  private static boolean isItemElement(XmlTag xmlTag) {
    String tag = xmlTag.getName();
    if (tag.equals(TAG_RESOURCES)) {
      return false;
    }
    return tag.equals(TAG_ITEM) || ResourceType.fromXmlTagName(tag) != null;
  }

  @Nullable
  private ResourceItem findValueResourceItem(XmlTag tag, @NotNull PsiFile file) {
    if (!tag.isValid()) {
      // This function should only be used if we know file's items are PsiResourceItems.
      ResourceItemSource<? extends ResourceItem> resFile = sources.get(file.getVirtualFile());
      if (resFile != null) {
        assert resFile instanceof PsiResourceFile;
        PsiResourceFile resourceFile = (PsiResourceFile)resFile;
        for (ResourceItem item : resourceFile) {
          PsiResourceItem pri = (PsiResourceItem)item;
          if (pri.wasTag(tag)) {
            return item;
          }
        }
      }
      return null;
    }
    String name = tag.getAttributeValue(ATTR_NAME);
    synchronized (ITEM_MAP_LOCK) {
      return name != null ? findValueResourceItem(tag, file, name) : null;
    }
  }

  @Nullable
  private ResourceItem findValueResourceItem(XmlTag tag, @NotNull PsiFile file, String name) {
    ResourceType type = getResourceTypeForResourceTag(tag);
    return findResourceItem(type, file, name, tag);
  }

  @Nullable
  private ResourceItem findResourceItem(@Nullable ResourceType type, @NotNull PsiFile file, @Nullable String name, @Nullable XmlTag tag) {
    if (type == null || name == null) {
      return null;
    }

    // Do IO work before obtaining the lock:
    File ioFile = VfsUtilCore.virtualToIoFile(file.getVirtualFile());

    synchronized (ITEM_MAP_LOCK) {
      ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, type);
      if (map == null) {
        return null;
      }
      List<ResourceItem> items = map.get(name);
      assert items != null;
      if (tag != null) {
        // Only PsiResourceItems can match.
        for (ResourceItem resourceItem : items) {
          if (resourceItem instanceof PsiResourceItem) {
            PsiResourceItem psiResourceItem = (PsiResourceItem)resourceItem;
            if (psiResourceItem.wasTag(tag)) {
              return resourceItem;
            }
          }
        }
      }
      else {
        // Check all items for the right source file.
        for (ResourceItem item : items) {
          if (item instanceof PsiResourceItem) {
            if (Objects.equals(((PsiResourceItem)item).getPsiFile(), file)) {
              return item;
            }
          }
          else {
            ResourceFile resourceFile = ((ResourceMergerItem)item).getSourceFile();
            if (resourceFile != null && FileUtil.filesEqual(resourceFile.getFile(), ioFile)) {
              return item;
            }
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

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return Collections.singleton(myResourceDir);
  }

  /**
   * Not a super-robust comparator. Compares a subset of the repo state for testing. Returns false if a difference is found. Returns true if
   * the repositories are roughly equivalent.
   */
  @VisibleForTesting
  boolean equalFilesItems(ResourceFolderRepository other) {
    File myResourceDirFile = VfsUtilCore.virtualToIoFile(myResourceDir);
    File otherResourceDir = VfsUtilCore.virtualToIoFile(other.myResourceDir);
    if (!FileUtil.filesEqual(myResourceDirFile, otherResourceDir)) {
      return false;
    }

    if (sources.size() != other.sources.size()) {
      return false;
    }
    for (Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>> fileEntry : sources.entrySet()) {
      ResourceItemSource<? extends ResourceItem> otherResFile = other.sources.get(fileEntry.getKey());
      if (otherResFile == null) {
        return false;
      }
      if (!Objects.equals(fileEntry.getValue().getVirtualFile(), otherResFile.getVirtualFile())) {
        return false;
      }
    }

    synchronized (ITEM_MAP_LOCK) {
      ResourceTable otherResourceTable = other.getFullTable();
      if (myFullTable.size() != otherResourceTable.size()) {
        return false;
      }

      for (Table.Cell<ResourceNamespace, ResourceType, ListMultimap<String, ResourceItem>> cell : myFullTable.cellSet()) {
        assert cell.getColumnKey() != null; // We don't store null as the ResourceType.

        ListMultimap<String, ResourceItem> ownEntries = cell.getValue();
        ListMultimap<String, ResourceItem> otherEntries = otherResourceTable.get(cell.getRowKey(), cell.getColumnKey());

        if ((otherEntries == null) != (ownEntries == null)) {
          return false;
        }

        if (ownEntries == null) {
          // This means they're both null.
          continue;
        }

        if (otherEntries.size() != ownEntries.size()) {
          return false;
        }

        for (Map.Entry<String, ResourceItem> itemEntry : ownEntries.entries()) {
          List<ResourceItem> otherItemsList = otherEntries.get(itemEntry.getKey());
          if (otherItemsList == null) {
            return false;
          }
          ResourceItem item = itemEntry.getValue();
          if (!ContainerUtil.exists(otherItemsList, otherItem -> {
            if (!item.getReferenceToSelf().equals(otherItem.getReferenceToSelf())) {
              return false;
            }

            if (Objects.equals(item.getSource(), otherItem.getSource())) {
              // Items with the same references and same files should produce equal ResourceValues, including DensityResourceValues.
              ResourceValue resourceValue = item.getResourceValue();
              ResourceValue otherResourceValue = otherItem.getResourceValue();
              switch (item.getType()) {
                case ID:
                  // Skip ID type resources, where the ResourceValues are not important and where blob writing doesn't preserve the value.
                  break;
                case STRING:
                  if (resourceValue instanceof TextResourceValue && otherResourceValue instanceof TextResourceValue) {
                    // Resource merger changes the namespace prefix for the XLIFF namespace, so the raw XML values are not equal. For now
                    // compare the escaped values.
                    if (!Objects.equals(resourceValue.getValue(), otherResourceValue.getValue())) {
                      return false;
                    }
                    break;
                  }
                  // otherwise, fall through.
                default:
                  if (!Objects.equals(resourceValue, otherResourceValue)) {
                    return false;
                  }
                  break;
              }
            }

            return true;
          })) {
            return false;
          }
        }
      }
    }

    // Only compare the keys.
    return myDataBindingResourceFiles.keySet().equals(other.myDataBindingResourceFiles.keySet());
  }
}

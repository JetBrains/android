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
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.resources.ResourceFolderType.COLOR;
import static com.android.resources.ResourceFolderType.DRAWABLE;
import static com.android.resources.ResourceFolderType.FONT;
import static com.android.resources.ResourceFolderType.VALUES;
import static com.android.tools.idea.res.AndroidFileChangeListener.isRelevantFile;
import static com.android.tools.idea.res.IdeResourcesUtil.getResourceTypeForResourceTag;
import static com.android.tools.idea.resources.base.RepositoryLoader.portableFileName;
import static com.android.tools.idea.resources.base.ResourceSerializationUtil.createPersistentCache;
import static com.android.tools.idea.resources.base.ResourceSerializationUtil.writeResourcesToStream;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceMergerItem;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceVisibility;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.resources.base.Base128InputStream;
import com.android.tools.idea.resources.base.BasicDensityBasedFileResourceItem;
import com.android.tools.idea.resources.base.BasicFileResourceItem;
import com.android.tools.idea.resources.base.BasicResourceItem;
import com.android.tools.idea.resources.base.BasicValueResourceItemBase;
import com.android.tools.idea.resources.base.LoadableResourceRepository;
import com.android.tools.idea.resources.base.RepositoryConfiguration;
import com.android.tools.idea.resources.base.RepositoryLoader;
import com.android.tools.idea.resources.base.ResourceSerializationUtil;
import com.android.tools.idea.resources.base.ResourceSourceFile;
import com.android.tools.idea.util.FileExtensions;
import com.android.utils.FlightRecorder;
import com.android.utils.SdkUtils;
import com.android.utils.TraceUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
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
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

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
 * (typically {@link ModuleResourceRepository})</li>
 * <li>Add defensive checks for non-read permission reads of resource values</li>
 * <li>Idea: For {@link #scheduleScan}; compare the removed items from the added items, and if they're the same, avoid
 * creating a new generation.</li>
 * <li>Register the PSI project listener as a project service instead.</li>
 * </ul>
 */
public final class ResourceFolderRepository extends LocalResourceRepository implements LoadableResourceRepository {
  /**
   * Increment when making changes that may affect content of repository cache files.
   * Used together with CachingData.codeVersion. Important for developer builds.
   */
  static final String CACHE_FILE_FORMAT_VERSION = "2";
  private static final byte[] CACHE_FILE_HEADER = "Resource cache".getBytes(UTF_8);
  /**
   * Maximum fraction of resources out of date in the cache for the cache to be considered fresh.
   * <p>
   * Loading without cache takes approximately twice as long as with the cache. This means that
   * if x% of all resources are loaded from sources because the cache is not completely up to date,
   * it introduces approximately x% loading time overhead. 5% overhead seems acceptable since it
   * is well within natural variation. Since cache file creation is asynchronous, the cost of
   * keeping cache fresh is pretty low.
   */
  private static final double CACHE_STALENESS_THRESHOLD = 0.05;
  private static final Comparator<ResourceItemSource<? extends ResourceItem>> SOURCE_COMPARATOR =
      Comparator.comparing(ResourceItemSource::getFolderConfiguration);
  private static final Logger LOG = Logger.getInstance(ResourceFolderRepository.class);
  private static final Tracer TRACER = new Tracer(false);

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final PsiTreeChangeListener myPsiListener;
  @NotNull private final VirtualFile myResourceDir;
  @NotNull private final ResourceNamespace myNamespace;
  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the {@link BasicFileResourceItem#getSource()} method.
   */
  @NotNull private final String myResourcePathPrefix;
  /**
   * Same as {@link #myResourcePathPrefix} but in a form of {@link PathString}.  Used to produce
   * resource paths returned by the {@link BasicFileResourceItem#getOriginalSource()} method.
   */
  @NotNull private final PathString myResourcePathBase;

  // Statistics of the initial repository loading.
  private int myNumXmlFilesLoadedInitially; // Doesn't count files that were explicitly skipped.
  private int myNumXmlFilesLoadedInitiallyFromSources;

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @NotNull private final Map<ResourceType, ListMultimap<String, ResourceItem>> myResourceTable = new EnumMap<>(ResourceType.class);

  @NotNull private final Map<VirtualFile, ResourceItemSource<? extends ResourceItem>> mySources = new HashMap<>();
  @NotNull private final PsiManager myPsiManager;
  @NotNull private final PsiNameHelper myPsiNameHelper;
  @NotNull private final WolfTheProblemSolver myWolfTheProblemSolver;
  @NotNull private final PsiDocumentManager myPsiDocumentManager;

  @NotNull private final Object scanLock = new Object();
  @GuardedBy("scanLock")
  @NotNull private final Set<VirtualFile> myPendingScans = new HashSet<>();
  @GuardedBy("scanLock")
  @NotNull private final HashMap<VirtualFile, ProgressIndicator> myRunningScans = new HashMap<>();

  @VisibleForTesting static int ourFullRescans;
  @VisibleForTesting static int ourLayoutlibCacheFlushes;

  /**
   * Creates a ResourceFolderRepository and loads its contents.
   * <p>
   * If {@code cachingData} is not null, an attempt is made
   * to load resources from the cache file specified in {@code cachingData}. While loading from the cache resources
   * defined in the XML files that changed recently are skipped. Whether an XML has changed or not is determined by
   * comparing the combined hash of the file modification time and the length obtained by calling
   * {@link VirtualFile#getTimeStamp()} and {@link VirtualFile#getLength()} with the hash value stored in the cache.
   * The checks are located in {@link #deserializeResourceSourceFile} and {@link #deserializeFileResourceItem}.
   * <p>
   * The remaining resources are then loaded by parsing XML files that were not present in the cache or were newer
   * than their cached versions.
   * <p>
   * If a significant (determined by {@link #CACHE_STALENESS_THRESHOLD}} percentage of resources was loaded by parsing
   * XML files and {@code cachingData.cacheCreationExecutor} is not null, the new cache file is created using that
   * executor, possibly after this method has already returned.
   * <p>
   * After creation the contents of the repository are maintained to be up to date by listening to VFS and PSI events.
   * <p>
   * NOTE: You should normally use {@link ResourceFolderRegistry#get} rather than this method.
   */
  @NotNull
  static ResourceFolderRepository create(@NotNull AndroidFacet facet, @NotNull VirtualFile dir, @NotNull ResourceNamespace namespace,
                                         @Nullable ResourceFolderRepositoryCachingData cachingData) {
    return new ResourceFolderRepository(facet, dir, namespace, cachingData);
  }

  private ResourceFolderRepository(@NotNull AndroidFacet facet,
                                   @NotNull VirtualFile resourceDir,
                                   @NotNull ResourceNamespace namespace,
                                   @Nullable ResourceFolderRepositoryCachingData cachingData) {
    super(resourceDir.getName());
    myFacet = facet;
    myResourceDir = resourceDir;
    myNamespace = namespace;
    myResourcePathPrefix = portableFileName(myResourceDir.getPath()) + '/';
    myResourcePathBase = new PathString(myResourcePathPrefix);
    myPsiManager = PsiManager.getInstance(getProject());
    myPsiDocumentManager = PsiDocumentManager.getInstance(getProject());
    myPsiNameHelper = PsiNameHelper.getInstance(getProject());
    myWolfTheProblemSolver = WolfTheProblemSolver.getInstance(getProject());

    PsiTreeChangeListener psiListener = StudioFlags.INCREMENTAL_RESOURCE_REPOSITORIES.get()
                                        ? new IncrementalUpdatePsiListener()
                                        : new SimplePsiListener();
    myPsiListener = LOG.isDebugEnabled()
                    ? new LoggingPsiTreeChangeListener(psiListener, LOG)
                    : psiListener;

    Loader loader = new Loader(this, cachingData);
    loader.load();

    if (StudioFlags.RESOURCE_REPOSITORY_TRACE_UPDATES.get()) {
      startTracing();
    }
  }

  @NotNull
  public VirtualFile getResourceDir() {
    return myResourceDir;
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null; // Resource folder is not a library.
  }

  @Override
  @NotNull
  public Path getOrigin() {
    return Paths.get(myResourceDir.getPath());
  }

  @Override
  @NotNull
  public String getResourceUrl(@NotNull String relativeResourcePath) {
    return myResourcePathPrefix + relativeResourcePath;
  }

  @Override
  @NotNull
  public PathString getSourceFile(@NotNull String relativeResourcePath, boolean forFileResource) {
    return myResourcePathBase.resolve(relativeResourcePath);
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  @Override
  public boolean containsUserDefinedResources() {
    return true;
  }

  private static void addToResult(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result, @NotNull ResourceItem item) {
    // The insertion order matters, see AppResourceRepositoryTest#testStringOrder.
    result.computeIfAbsent(item.getType(), t -> LinkedListMultimap.create()).put(item.getName(), item);
  }

  /**
   * Inserts the given resources into this repository, while holding the global repository lock.
   */
  private void commitToRepository(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> itemsByType) {
    synchronized (ITEM_MAP_LOCK) {
      commitToRepositoryWithoutLock(itemsByType);
    }
  }

  /**
   * Inserts the given resources into this repository without acquiring any locks. Safe to call only while
   * holding {@link #ITEM_MAP_LOCK} or during construction of ResourceFolderRepository.
   */
  @SuppressWarnings("GuardedBy")
  private void commitToRepositoryWithoutLock(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> itemsByType) {
    TRACER.log(() -> "ResourceFolderRepository.commitToRepositoryWithoutLock");
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : itemsByType.entrySet()) {
      for (ResourceItem item : entry.getValue().values()) {
        TRACER.log(() -> "Committing " + item.getType() + '/' + item.getName());
      }
      getOrCreateMap(entry.getKey()).putAll(entry.getValue());
    }
  }

  /**
   * Determines if it's unnecessary to write or update the file-backed cache.
   * If only a few items were reparsed, then the cache is fresh enough.
   *
   * @return true if this repo is backed by a fresh file cache
   */
  @VisibleForTesting
  boolean hasFreshFileCache() {
    return myNumXmlFilesLoadedInitiallyFromSources <= myNumXmlFilesLoadedInitially * CACHE_STALENESS_THRESHOLD;
  }

  @TestOnly
  int getNumXmlFilesLoadedInitially() {
    return myNumXmlFilesLoadedInitially;
  }

  @TestOnly
  int getNumXmlFilesLoadedInitiallyFromSources() {
    return myNumXmlFilesLoadedInitiallyFromSources;
  }

  @Nullable
  private PsiFile ensureValid(@NotNull PsiFile psiFile) {
    if (psiFile.isValid()) {
      return psiFile;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null && virtualFile.exists() && !getProject().isDisposed()) {
      return myPsiManager.findFile(virtualFile);
    }

    return null;
  }

  private void scanFileResourceFileAsPsi(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                         @NotNull ResourceFolderType folderType,
                                         @NotNull FolderConfiguration folderConfiguration,
                                         @NotNull ResourceType type,
                                         boolean idGenerating,
                                         @NotNull PsiFile file) {
    // XML or image.
    String resourceName = SdkUtils.fileNameToResourceName(file.getName());
    if (!checkResourceFilename(file, folderType)) {
      return; // Not a valid file resource name.
    }

    RepositoryConfiguration configuration = new RepositoryConfiguration(this, folderConfiguration);
    PsiResourceItem item = PsiResourceItem.forFile(resourceName, type, this, file, false);

    if (idGenerating) {
      List<PsiResourceItem> items = new ArrayList<>();
      items.add(item);
      addToResult(result, item);
      addIds(result, items, file);

      PsiResourceFile resourceFile = new PsiResourceFile(file, items, folderType, configuration);
      mySources.put(file.getVirtualFile(), resourceFile);
    } else {
      PsiResourceFile resourceFile = new PsiResourceFile(file, Collections.singletonList(item), folderType, configuration);
      mySources.put(file.getVirtualFile(), resourceFile);
      addToResult(result, item);
    }
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

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @NotNull
  private ListMultimap<String, ResourceItem> getOrCreateMap(@NotNull ResourceType type) {
    // Use LinkedListMultimap to preserve ordering for editors that show original order.
    return myResourceTable.computeIfAbsent(type, k -> LinkedListMultimap.create());
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  private void addIds(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>>result,
                      @NotNull List<PsiResourceItem> items,
                      @NotNull PsiFile file) {
    addIds(result, items, file, false);
  }

  private void addIds(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                      @NotNull List<PsiResourceItem> items,
                      @NotNull PsiElement element,
                      boolean calledFromPsiListener) {
    Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(element, XmlTag.class);
    if (element instanceof XmlTag) {
      addIds(result, items, (XmlTag)element, calledFromPsiListener);
    }
    if (!xmlTags.isEmpty()) {
      for (XmlTag tag : xmlTags) {
        addIds(result, items, tag, calledFromPsiListener);
      }
    }
  }

  private void addIds(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                      @NotNull List<PsiResourceItem> items,
                      @NotNull XmlTag tag,
                      boolean calledFromPsiListener) {
    assert tag.isValid();

    for (XmlAttribute attribute : tag.getAttributes()) {
      PsiResourceItem item = createIdFromAttribute(attribute, calledFromPsiListener);
      if (item != null) {
        items.add(item);
        addToResult(result, item);
      }
    }
  }

  @Nullable
  private PsiResourceItem createIdFromAttribute(XmlAttribute attribute, boolean calledFromPsiListener) {
    PsiResourceItem item = null;
    String attributeValue = StringUtil.notNullize(attribute.getValue()).trim();
    if (attributeValue.startsWith(NEW_ID_PREFIX) && !attribute.getNamespace().equals(TOOLS_URI)) {
      String id = attributeValue.substring(NEW_ID_PREFIX.length());
      if (isValidValueResourceName(id)) {
        item = PsiResourceItem.forXmlTag(id, ResourceType.ID, this, attribute.getParent(), calledFromPsiListener);
      }
    }
    return item;
  }

  private boolean scanValueFileAsPsi(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                     @NotNull PsiFile file, @NotNull FolderConfiguration folderConfiguration) {
    boolean added = false;
    FileType fileType = file.getFileType();
    if (fileType == XmlFileType.INSTANCE) {
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
          if (type != null && isValidValueResourceName(name)) {
            PsiResourceItem item = PsiResourceItem.forXmlTag(name, type, this, tag, false);
            addToResult(result, item);
            items.add(item);
            added = true;

            if (type == ResourceType.STYLEABLE) {
              // For styleables we also need to create attr items for its children.
              XmlTag[] attrs = tag.getSubTags();
              if (attrs.length > 0) {
                for (XmlTag child : attrs) {
                  String attrName = child.getAttributeValue(ATTR_NAME);
                  if (isValidValueResourceName(attrName) && !attrName.startsWith(ANDROID_NS_NAME_PREFIX)
                      // Only add attr nodes for elements that specify a format or have flag/enum children; otherwise
                      // it's just a reference to an existing attr.
                      && (child.getAttribute(ATTR_FORMAT) != null || child.getSubTags().length > 0)) {
                    PsiResourceItem attrItem = PsiResourceItem.forXmlTag(attrName, ResourceType.ATTR, this, child, false);
                    items.add(attrItem);
                    addToResult(result, attrItem);
                  }
                }
              }
            }
          }
        }

        PsiResourceFile resourceFile = new PsiResourceFile(file, items, VALUES, new RepositoryConfiguration(this, folderConfiguration));
        mySources.put(file.getVirtualFile(), resourceFile);
      }
    }

    return added;
  }

  @Contract(value = "null -> false")
  private static boolean isValidValueResourceName(@Nullable String name) {
    return !StringUtil.isEmpty(name) && ValueResourceNameValidator.getErrorText(name, null) == null;
  }

  private boolean checkResourceFilename(@NotNull PathString file, @NotNull ResourceFolderType folderType) {
    if (FileResourceNameValidator.getErrorTextForFileResource(file.getFileName(), folderType) != null) {
      VirtualFile virtualFile = FileExtensions.toVirtualFile(file);
      if (virtualFile != null) {
        myWolfTheProblemSolver.reportProblemsFromExternalSource(virtualFile, this);
      }
    }
    return myPsiNameHelper.isIdentifier(SdkUtils.fileNameToResourceName(file.getFileName()));
  }

  private boolean checkResourceFilename(@NotNull PsiFile file, @NotNull ResourceFolderType folderType) {
    if (FileResourceNameValidator.getErrorTextForFileResource(file.getName(), folderType) != null) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myWolfTheProblemSolver.reportProblemsFromExternalSource(virtualFile, this);
      }
    }
    return myPsiNameHelper.isIdentifier(SdkUtils.fileNameToResourceName(file.getName()));
  }

  /**
   * Schedules a rescan to convert any map ResourceItems to PSI if needed, and returns true if conversion
   * was needed (incremental updates which rely on PSI were not possible).
   */
  private boolean convertToPsiIfNeeded(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    ResourceItemSource<? extends ResourceItem> resFile = mySources.get(virtualFile);
    if (resFile instanceof PsiResourceFile) {
      return false;
    }
    // This schedules a rescan, and when the actual scan happens it will purge non-PSI
    // items as needed, populate psi items, and add to myFileTypes once done.
    if (LOG.isDebugEnabled()) {
      LOG.debug("Converting to PSI ", psiFile);
    }
    TRACER.log(() -> "ResourceFolderRepository.convertToPsiIfNeeded " + psiFile.getVirtualFile() + " converting to PSI");
    scheduleScan(virtualFile, folderType);
    return true;
  }

  /**
   * Returns true if the given element represents a resource folder
   * (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder).
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

  private boolean isResourceFile(@NotNull VirtualFile virtualFile) {
    VirtualFile parent = virtualFile.getParent();
    return parent != null && isResourceFolder(parent);
  }

  private boolean isResourceFile(@NotNull PsiFile psiFile) {
    return isResourceFile(psiFile.getVirtualFile());
  }

  @Override
  final boolean isScanPending(@NotNull VirtualFile virtualFile) {
    synchronized (scanLock) {
      return myPendingScans.contains(virtualFile);
    }
  }

  void scheduleScan(@NotNull VirtualFile virtualFile) {
    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(virtualFile);
    if (folderType != null) {
      scheduleScan(virtualFile, folderType);
    }
  }

  private void scheduleScan(@NotNull VirtualFile virtualFile, @NotNull ResourceFolderType folderType) {
    TRACER.log(() -> "ResourceFolderRepository.scheduleScan " + virtualFile);
    synchronized (scanLock) {
      if (!myPendingScans.add(virtualFile)) {
        TRACER.log(() -> "ResourceFolderRepository.scheduleScan " + virtualFile + " pending already");
        return;
      }
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      TRACER.log(() -> "ResourceFolderRepository.scheduleScan " + virtualFile + " later");
      if (!virtualFile.isValid() || !isScanPending(virtualFile)) {
        TRACER.log(() -> "ResourceFolderRepository.scheduleScan " + virtualFile + " pending already");
        return;
      }
      PsiFile psiFile = findPsiFile(virtualFile);
      if (psiFile == null) {
        TRACER.log(() -> "ResourceFolderRepository.scheduleScan no PSI " + virtualFile);
        return;
      }

      ApplicationManager.getApplication().runWriteAction(() -> {
        ProgressIndicator runHandle;
        synchronized (scanLock) {
          if (!myPendingScans.remove(virtualFile)) {
            TRACER.log(() -> "ResourceFolderRepository.scheduleScan " + virtualFile + " scanned already");
            return;
          }
          runHandle = new EmptyProgressIndicator();
          ProgressIndicator oldRunHandle = myRunningScans.replace(virtualFile, runHandle);
          if (oldRunHandle != null) {
            oldRunHandle.cancel();
          }
        }

        try {
          ProgressManager.getInstance().runProcess(() -> scan(psiFile, folderType), runHandle);
        }
        finally {
          synchronized (scanLock) {
            myRunningScans.remove(virtualFile, runHandle);
            TRACER.log(() -> "ResourceFolderRepository.scheduleScan " + virtualFile + " finished scanning");
          }
        }
      });
    });
  }

  @UiThread
  @Override
  public void sync() {
    super.sync();

    TRACER.log(() -> "ResourceFolderRepository.sync");
    List<VirtualFile> files;
    NonCancellableIndicator runHandle;
    synchronized (scanLock) {
      if (myPendingScans.isEmpty() && myRunningScans.isEmpty()) {
        return;
      }
      runHandle = new NonCancellableIndicator();
      files = new ArrayList<>(myRunningScans.size() + myPendingScans.size());
      for (VirtualFile file : myRunningScans.keySet()) {
        files.add(file);
        ProgressIndicator oldRunHandle = myRunningScans.replace(file, runHandle);
        if (oldRunHandle != null) {
          oldRunHandle.cancel();
        }
      }
      for (VirtualFile file : myPendingScans) {
        files.add(file);
        myRunningScans.put(file, runHandle);
      }
      myPendingScans.clear();
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        for (VirtualFile virtualFile : files) {
          if (virtualFile.isValid()) {
            ResourceFolderType folderType = IdeResourcesUtil.getFolderType(virtualFile);
            if (folderType != null) {
              PsiFile psiFile = findPsiFile(virtualFile);
              if (psiFile != null) {
                scan(psiFile, folderType);
              }
            }
          }
        }
      }
      finally {
        synchronized (scanLock) {
          for (VirtualFile file : files) {
            myRunningScans.remove(file, runHandle);
          }
        }
      }
      TRACER.log(() -> "ResourceFolderRepository.sync end");
    });
  }

  @Nullable
  private PsiFile findPsiFile(@NotNull VirtualFile virtualFile) {
    try {
      return PsiManager.getInstance(getProject()).findFile(virtualFile);
    }
    catch (AlreadyDisposedException e) {
      return null;
    }
  }

  /**
   * Recursively scans the files under the resource directory that match the filter. Replaces the
   * {@link BasicResourceItem BasicResourceItems} in this repository with {@link PsiResourceItem PsiResourceItems}.
   */
  @Slow
  public void scanRecursively(@NotNull VirtualFileFilter filter) {
    PsiManager manager = PsiManager.getInstance(myFacet.getModule().getProject());

    VfsUtilCore.iterateChildrenRecursively(myResourceDir, filter, virtualFile -> {
      if (virtualFile.isDirectory()) {
        return true;
      }

      PsiFile psiFile = manager.findFile(virtualFile);
      assert psiFile != null;

      ResourceFolderType type = IdeResourcesUtil.getFolderType(virtualFile);
      assert type != null;

      scan(psiFile, type);
      return true;
    });
  }

  private void scan(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      ApplicationManager.getApplication().runReadAction(() -> scan(psiFile, folderType));
      return;
    }

    TRACER.log(() -> "ResourceFolderRepository.scan " + psiFile.getVirtualFile());

    ProgressManager.checkCanceled();
    if (psiFile.getProject().isDisposed()) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Rescanning ", psiFile);
    }

    Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();

    PsiFile file = psiFile;
    if (folderType == VALUES) {
      // For unit test tracking purposes only.
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFullRescans++;

      // First delete out the previous items.
      ResourceItemSource<? extends ResourceItem> source = this.mySources.remove(file.getVirtualFile());
      boolean removed = false;
      if (source != null) {
        removed = removeItemsFromSource(source);
      }

      file = ensureValid(file);
      boolean added = false;
      if (file != null) {
        // Add items for this file.
        PsiDirectory parent = file.getParent();
        assert parent != null; // Since we have a folder type.
        PsiDirectory fileParent = psiFile.getParent();
        if (fileParent != null) {
          FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
          if (folderConfiguration != null) {
            ProgressManager.checkCanceled();
            added = scanValueFileAsPsi(result, file, folderConfiguration);
          }
        }
      }

      if (added || removed) {
        // TODO: Consider doing a deeper diff of the changes to the resource items
        //       to determine if the removed and added items actually differ.
        setModificationCount(ourModificationCounter.incrementAndGet());
        invalidateParentCaches(this, ResourceType.values());
      }
    } else if (checkResourceFilename(file, folderType)) {
      ResourceItemSource<? extends ResourceItem> source = mySources.get(file.getVirtualFile());
      if (source instanceof PsiResourceFile && file.getFileType() == XmlFileType.INSTANCE) {
        // If the old file was a PsiResourceFile for an XML file, we can update ID ResourceItems in place.
        PsiResourceFile psiResourceFile = (PsiResourceFile)source;
        // Already seen this file; no need to do anything unless it's an XML file with generated ids;
        // in that case we may need to update the id's.
        if (FolderTypeRelationship.isIdGeneratingFolderType(folderType)) {
          // For unit test tracking purposes only.
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourFullRescans++;

          // We've already seen this resource, so no change in the ResourceItem for the
          // file itself (e.g. @layout/foo from layout-land/foo.xml). However, we may have
          // to update the id's:
          Set<String> idsBefore = new HashSet<>();
          synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> idMultimap = myResourceTable.get(ResourceType.ID);
            if (idMultimap != null) {
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
                List<ResourceItem> mapItems = idMultimap.get(id);
                if (mapItems != null && !mapItems.isEmpty()) {
                  List<ResourceItem> toDelete = new ArrayList<>(mapItems.size());
                  for (ResourceItem mapItem : mapItems) {
                    if (mapItem instanceof PsiResourceItem && ((PsiResourceItem)mapItem).getSourceFile() == psiResourceFile) {
                      toDelete.add(mapItem);
                    }
                  }
                  for (ResourceItem delete : toDelete) {
                    idMultimap.remove(delete.getName(), delete);
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
            ProgressManager.checkCanceled();
            addIds(result, idItems, file);
          }
          if (!idItems.isEmpty()) {
            for (PsiResourceItem item : idItems) {
              psiResourceFile.addItem(item);
            }
          }

          // Identities may have changed even if the ids are the same, so update maps.
          setModificationCount(ourModificationCounter.incrementAndGet());
          invalidateParentCaches(this, ResourceType.ID);
        }
      } else {
        // Either we're switching to PSI or the file is not XML (image or font), which is not incremental. Remove old items first, rescan
        // below to add back, but with a possibly different multimap list order.
        if (source != null) {
          removeItemsFromSource(source);
        }
        // For unit test tracking purposes only
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFullRescans++;

        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type

        ResourceType type = FolderTypeRelationship.getNonIdRelatedResourceType(folderType);
        boolean idGeneratingFolder = FolderTypeRelationship.isIdGeneratingFolderType(folderType);

        ProgressManager.checkCanceled();
        clearLayoutlibCaches(file.getVirtualFile(), folderType);

        file = ensureValid(file);
        if (file != null) {
          PsiDirectory fileParent = psiFile.getParent();
          if (fileParent != null) {
            FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
            if (folderConfiguration != null) {
              boolean idGeneratingFile = idGeneratingFolder && file.getFileType() == XmlFileType.INSTANCE;
              ProgressManager.checkCanceled();
              scanFileResourceFileAsPsi(result, folderType, folderConfiguration, type, idGeneratingFile, file);
            }
          }
          setModificationCount(ourModificationCounter.incrementAndGet());
          invalidateParentCaches(this, ResourceType.values());
        }
      }
    }

    commitToRepository(result);
    TRACER.log(() -> "ResourceFolderRepository.scan " + psiFile.getVirtualFile() + " end");
  }

  private void scan(@NotNull VirtualFile file) {
    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
    if (folderType == null) {
      return;
    }

    if (isResourceFile(file) && isRelevantFile(file)) {
      PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile != null) {
        Document document = myPsiDocumentManager.getDocument(psiFile);
        if (document != null && myPsiDocumentManager.isUncommited(document)) {
          // The Document has uncommitted changes, so scanning the PSI will yield stale results. Request a commit and scan once it's done.
          if (LOG.isDebugEnabled()) {
            LOG.debug("Committing ", document);
          }
          ApplicationManager.getApplication().invokeLater(() -> {
            WriteAction.run(() -> myPsiDocumentManager.commitDocument(document));
            ReadAction.run(() -> scan(psiFile, folderType));
          });
          return;
        }

        scan(psiFile, folderType);
      }
    }
  }

  /**
   * Removes resource items matching the given source file and tag.
   *
   * @return true if any resource items were removed from the repository
   */
  private boolean removeItemsForTag(@NotNull ResourceItemSource<PsiResourceItem> source,
                                    @NotNull XmlTag xmlTag,
                                    @NotNull ResourceType resourceType) {
    boolean changed = false;

    synchronized (ITEM_MAP_LOCK) {
      for (Iterator<PsiResourceItem> sourceIter = source.iterator(); sourceIter.hasNext();) {
        PsiResourceItem item = sourceIter.next();
        if (item.wasTag(xmlTag)) {
          ListMultimap<String, ResourceItem> map = myResourceTable.get(resourceType);
          List<ResourceItem> items = map.get(item.getName());
          for (Iterator<ResourceItem> iter = items.iterator(); iter.hasNext(); ) {
            ResourceItem candidate = iter.next();
            if (candidate == item) {
              iter.remove();
              changed = true;
              break;
            }
          }
          sourceIter.remove();
        }
      }

      return changed;
    }
  }

  /**
   * Removes all resource items associated the given source file.
   *
   * @return true if any resource items were removed from the repository
   */
  private boolean removeItemsFromSource(@NotNull ResourceItemSource<? extends ResourceItem> source) {
    boolean changed = false;

    synchronized (ITEM_MAP_LOCK) {
      for (ResourceItem item : source) {
        ListMultimap<String, ResourceItem> map = myResourceTable.get(item.getType());
        List<ResourceItem> items = map.get(item.getName());
        for (Iterator<ResourceItem> iter = items.iterator(); iter.hasNext(); ) {
          ResourceItem candidate = iter.next();
          if (candidate == item) {
            iter.remove();
            changed = true;
            break;
          }
        }
        if (items.isEmpty()) {
          map.removeAll(item.getName());
        }
      }
    }
    return changed;
  }

  /**
   * Find the {@link com.android.tools.idea.configurations.Configuration} for the provided file and
   * it's associated {@link AndroidTargetData} asynchronously and then run the provided consumer on the EDT
   */
  private void getAndroidTargetDataThenRun(@NotNull VirtualFile file, @NotNull Consumer<AndroidTargetData> consumer) {
    Module module = myFacet.getModule();
    ConfigurationManager configurationManager = ConfigurationManager.findExistingInstance(module);
    if (configurationManager == null) {
      return;
    }
    CompletableFuture.supplyAsync(() -> {
      IAndroidTarget target = configurationManager.getConfiguration(file).getTarget();
      if (target != null) {
        return AndroidTargetData.getTargetData(target, module);
      }
      return null;
    }, PooledThreadExecutor.INSTANCE)
      .thenAcceptAsync((target) -> {
        if (target != null) {
          consumer.accept(target);
        }
      }, EdtExecutorService.getInstance());
  }

  /**
   * Called when a bitmap has been changed/deleted. In that case we need to clear out any caches for that
   * image held by layout lib.
   */
  private void bitmapUpdated(@NotNull VirtualFile bitmap) {
    Module module = myFacet.getModule();
    getAndroidTargetDataThenRun(bitmap, (targetData) -> targetData.clearLayoutBitmapCache(module));
  }

  /**
   * Called when a font file has been changed/deleted. This removes the corresponding file from the
   * Typeface cache inside layoutlib.
   */
  void clearFontCache(@NotNull VirtualFile virtualFile) {
    getAndroidTargetDataThenRun(virtualFile, (targetData) -> targetData.clearFontCache(virtualFile.getPath()));
  }

  @NotNull
  public PsiTreeChangeListener getPsiListener() {
    return myPsiListener;
  }

  @Nullable
  private VirtualFile psiToVirtual(@Nullable PsiFile psiFile) {
    return psiFile == null ? null : psiFile.getVirtualFile();
  }

  protected void setModificationCount(long count) {
    TRACER.log(() -> "ResourceFolderRepository.setModificationCount " + count);
    super.setModificationCount(count);
  }

  static void startTracing() {
    FlightRecorder.initialize(StudioFlags.RESOURCE_REPOSITORY_TRACE_SIZE.get());
    TRACER.enabled = true;
  }

  static void stopTracingAndDump() {
    TRACER.enabled = false;
    List<Object> trace = FlightRecorder.getAndClear();
    LOG.info("Resource update trace:\n" + Joiner.on('\n').join(trace) + "\n-------------------------");
  }

  static boolean isTracingActive() {
    return TRACER.enabled;
  }

  /**
   * PSI listener which schedules a full file rescan after every change.
   *
   * @see IncrementalUpdatePsiListener
   */
  private final class SimplePsiListener extends PsiTreeAnyChangeAbstractAdapter {
    @Override
    protected void onChange(@Nullable PsiFile psiFile) {
      TRACER.log(() -> "SimplePsiListener.onChange " + psiToVirtual(psiFile));
      ResourceFolderType folderType = IdeResourcesUtil.getFolderType(psiFile);
      if (folderType != null && psiFile != null && isResourceFile(psiFile)) {
        scheduleScan(psiFile.getVirtualFile(), folderType);
      }
      TRACER.log(() -> "SimplePsiListener.onChange " + psiToVirtual(psiFile) + " end");
    }
  }

  /**
   * PSI listener which keeps the repository up to date. It handles simple edits synchronously and schedules rescans for other events.
   *
   * @see IncrementalUpdatePsiListener
   */
  private final class IncrementalUpdatePsiListener extends PsiTreeChangeAdapter {
    private boolean myIgnoreChildrenChanged;

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      TRACER.log(() -> "IncrementalUpdatePsiListener.childAdded " + psiToVirtual(event.getFile()));
      try {
        PsiFile psiFile = event.getFile();
        if (psiFile != null && isRelevantFile(psiFile)) {
          VirtualFile virtualFile = psiFile.getVirtualFile();
          // If the file is currently being scanned, schedule a new scan to avoid a race condition
          // between the incremental update and the running scan.
          if (rescheduleScanIfRunning(virtualFile)) {
            return;
          }

          // Some child was added within a file.
          ResourceFolderType folderType = IdeResourcesUtil.getFolderType(psiFile);
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
                  ResourceItemSource<? extends ResourceItem> source = mySources.get(virtualFile);
                  if (source != null) {
                    assert source instanceof PsiResourceFile;
                    PsiResourceFile psiResourceFile = (PsiResourceFile)source;
                    String name = tag.getAttributeValue(ATTR_NAME);
                    if (isValidValueResourceName(name)) {
                      ResourceType type = getResourceTypeForResourceTag(tag);
                      if (type == ResourceType.STYLEABLE) {
                        // Can't handle declare styleable additions incrementally yet; need to update paired attr items.
                        scheduleScan(virtualFile, folderType);
                        return;
                      }
                      if (type != null) {
                        PsiResourceItem item = PsiResourceItem.forXmlTag(name, type, ResourceFolderRepository.this, tag, true);
                        synchronized (ITEM_MAP_LOCK) {
                          getOrCreateMap(type).put(name, item);
                          psiResourceFile.addItem(item);
                          setModificationCount(ourModificationCounter.incrementAndGet());
                          invalidateParentCaches(ResourceFolderRepository.this, type);
                          return;
                        }
                      }
                    }
                  }
                }

                // See if you just added a new item inside a <style> or <array> or <declare-styleable> etc.
                XmlTag parentTag = tag.getParentTag();
                if (parentTag != null && getResourceTypeForResourceTag(parentTag) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value.
                  ResourceItem parentItem = findValueResourceItem(parentTag, psiFile);
                  if (parentItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)parentItem).recomputeValue()) {
                      setModificationCount(ourModificationCounter.incrementAndGet());
                    }
                    TRACER.log(() -> "IncrementalUpdatePsiListener.childAdded " + psiToVirtual(event.getFile()) +
                                     " recomputed: " + parentItem);
                    return;
                  }
                }

                scheduleScan(virtualFile, folderType);
                // Else: fall through and do full file rescan.
              }
              else if (parent instanceof XmlText) {
                // If the edit is within an item tag.
                XmlText text = (XmlText)parent;
                handleValueXmlTextEdit(text.getParentTag(), psiFile);
                return;
              }
              else if (child instanceof XmlText) {
                // If the edit is within an item tag.
                handleValueXmlTextEdit(parent, psiFile);
                return;
              }
              else if (parent instanceof XmlComment || child instanceof XmlComment) {
                // Can ignore comment edits or new comments.
                return;
              }
              scheduleScan(virtualFile, folderType);
            }
            else if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) && psiFile.getFileType() == XmlFileType.INSTANCE) {
              if (parent instanceof XmlComment || child instanceof XmlComment) {
                return;
              }
              if (parent instanceof XmlText || (child instanceof XmlText && child.getText().trim().isEmpty())) {
                return;
              }

              if (parent instanceof XmlElement && child instanceof XmlElement) {
                if (child instanceof XmlTag) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  List<PsiResourceItem> ids = new ArrayList<>();
                  Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();
                  addIds(result, ids, child, true);
                  commitToRepository(result);
                  if (!ids.isEmpty()) {
                    ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
                    if (resFile != null) {
                      assert resFile instanceof PsiResourceFile;
                      PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;
                      for (PsiResourceItem id : ids) {
                        psiResourceFile.addItem(id);
                      }
                      setModificationCount(ourModificationCounter.incrementAndGet());
                      invalidateParentCaches(ResourceFolderRepository.this, ResourceType.ID);
                    }
                  }
                  return;
                }

                if (child instanceof XmlAttribute || parent instanceof XmlAttribute) {
                  // We check both because invalidation might come from XmlAttribute if it is inserted at once.
                  XmlAttribute attribute = parent instanceof XmlAttribute ? (XmlAttribute)parent : (XmlAttribute)child;

                  PsiResourceItem newIdResource = createIdFromAttribute(attribute, true);
                  if (newIdResource != null) {
                    if (convertToPsiIfNeeded(psiFile, folderType)) {
                      return;
                    }

                    synchronized (ITEM_MAP_LOCK) {
                      ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
                      if (resFile != null) {
                        assert resFile instanceof PsiResourceFile;
                        PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;
                        psiResourceFile.addItem(newIdResource);
                        getOrCreateMap(ResourceType.ID).put(newIdResource.getName(), newIdResource);
                        setModificationCount(ourModificationCounter.incrementAndGet());
                        invalidateParentCaches(ResourceFolderRepository.this, ResourceType.ID);
                        return;
                      }
                    }
                  }
                }
              }
            }
            else if (folderType == FONT) {
              clearFontCache(psiFile.getVirtualFile());
            }
          }
        }

        myIgnoreChildrenChanged = true;
      }
      finally {
        TRACER.log(() -> "IncrementalUpdatePsiListener.childAdded " + psiToVirtual(event.getFile()) + " end");
      }
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      TRACER.log(() -> "IncrementalUpdatePsiListener.childRemoved " + psiToVirtual(event.getFile()));
      try {
        PsiFile psiFile = event.getFile();
        if (psiFile != null && isRelevantFile(psiFile)) {
          VirtualFile virtualFile = psiFile.getVirtualFile();
          // If the file is currently being scanned, schedule a new scan to avoid a race condition
          // between the incremental update and the running scan.
          if (rescheduleScanIfRunning(virtualFile)) {
            return;
          }

          // Some child was removed within a file.
          ResourceFolderType folderType = IdeResourcesUtil.getFolderType(virtualFile);
          if (folderType != null && isResourceFile(virtualFile)) {
            PsiElement child = event.getChild();
            PsiElement parent = event.getParent();

            if (folderType == VALUES) {
              if (child instanceof XmlTag) {
                XmlTag tag = (XmlTag)child;

                // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc.
                if (parent instanceof XmlTag) {
                  XmlTag parentTag = (XmlTag)parent;
                  if (getResourceTypeForResourceTag(parentTag) != null) {
                    if (convertToPsiIfNeeded(psiFile, folderType)) {
                      return;
                    }
                    // Yes just invalidate the corresponding cached value.
                    ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                    if (resourceItem instanceof PsiResourceItem) {
                      if (((PsiResourceItem)resourceItem).recomputeValue()) {
                        setModificationCount(ourModificationCounter.incrementAndGet());
                      }
                      TRACER.log(() -> "IncrementalUpdatePsiListener.childRemoved " + psiToVirtual(event.getFile()) +
                                       " recomputed: " + resourceItem);

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
                  ResourceItemSource<? extends ResourceItem> source = mySources.get(virtualFile);
                  if (source != null) {
                    PsiResourceFile resourceFile = (PsiResourceFile)source;
                    String name;
                    if (!tag.isValid()) {
                      ResourceItem item = findValueResourceItem(tag, psiFile);
                      if (item != null) {
                        name = item.getName();
                      }
                      else {
                        // Can't find the name of the deleted tag; just do a full rescan
                        scheduleScan(virtualFile, folderType);
                        return;
                      }
                    }
                    else {
                      name = tag.getAttributeValue(ATTR_NAME);
                    }
                    if (name != null) {
                      ResourceType type = getResourceTypeForResourceTag(tag);
                      if (type != null) {
                        synchronized (ITEM_MAP_LOCK) {
                          boolean removed = removeItemsForTag(resourceFile, tag, type);
                          if (removed) {
                            setModificationCount(ourModificationCounter.incrementAndGet());
                            invalidateParentCaches(ResourceFolderRepository.this, type);
                          }
                        }
                      }
                    }

                    return;
                  }
                }

                scheduleScan(virtualFile, folderType);
              }
              else if (parent instanceof XmlText) {
                // If the edit is within an item tag.
                XmlText text = (XmlText)parent;
                handleValueXmlTextEdit(text.getParentTag(), psiFile);
              }
              else if (child instanceof XmlText) {
                handleValueXmlTextEdit(parent, psiFile);
              }
              else if (parent instanceof XmlComment || child instanceof XmlComment) {
                // Can ignore comment edits or removed comments.
                return;
              }
              else {
                // Some other change: do full file rescan.
                scheduleScan(virtualFile, folderType);
              }
            }
            else if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) && psiFile.getFileType() == XmlFileType.INSTANCE) {
              // TODO: Handle removals of id's (values an attributes) incrementally.
              scheduleScan(virtualFile, folderType);
            }
            else if (folderType == FONT) {
              clearFontCache(virtualFile);
            }
          }
        }

        myIgnoreChildrenChanged = true;
      }
      finally {
        TRACER.log(() -> "IncrementalUpdatePsiListener.childRemoved " + psiToVirtual(event.getFile()) + " end");
      }
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()));
      try {
        PsiFile psiFile = event.getFile();
        if (psiFile != null) {
          VirtualFile virtualFile = psiFile.getVirtualFile();
          // If the file is currently being scanned, schedule a new scan to avoid a race condition
          // between the incremental update and the running scan.
          if (rescheduleScanIfRunning(virtualFile)) {
            return;
          }

          // This method is called when you edit within a file.
          if (isRelevantFile(virtualFile)) {
            // First determine if the edit is non-consequential.
            // That's the case if the XML edited is not a resource file (e.g. the manifest file),
            // or if it's within a file that is not a value file or an id-generating file (layouts and menus),
            // such as editing the content of a drawable XML file.
            ResourceFolderType folderType = IdeResourcesUtil.getFolderType(virtualFile);
            if (folderType != null && FolderTypeRelationship.isIdGeneratingFolderType(folderType) &&
                psiFile.getFileType() == XmlFileType.INSTANCE) {
              // The only way the edit affected the set of resources was if the user added or removed an
              // id attribute. Since these can be added redundantly we can't automatically remove the old
              // value if you renamed one, so we'll need a full file scan.
              // However, we only need to do this scan if the change appears to be related to ids; this can
              // only happen if the attribute value is changed.
              PsiElement parent = event.getParent();
              PsiElement child = event.getChild();
              if (parent instanceof XmlText || child instanceof XmlText || parent instanceof XmlComment || child instanceof XmlComment) {
                return;
              }
              if (parent instanceof XmlElement && child instanceof XmlElement) {
                if (event.getOldChild() == event.getNewChild()) {
                  // We're not getting accurate PSI information: we have to do a full file scan.
                  scheduleScan(virtualFile, folderType);
                  return;
                }
                if (child instanceof XmlAttributeValue) {
                  assert parent instanceof XmlAttribute : parent;
                  XmlAttribute attribute = (XmlAttribute)parent;

                  PsiElement oldChild = event.getOldChild();
                  PsiElement newChild = event.getNewChild();
                  if (oldChild instanceof XmlAttributeValue && newChild instanceof XmlAttributeValue) {
                    String oldText = ((XmlAttributeValue)oldChild).getValue().trim();
                    String newText = ((XmlAttributeValue)newChild).getValue().trim();
                    if (oldText.startsWith(NEW_ID_PREFIX) || newText.startsWith(NEW_ID_PREFIX)) {
                      ResourceItemSource<? extends ResourceItem> source = mySources.get(psiFile.getVirtualFile());
                      if (source != null) {
                        ResourceUrl oldResourceUrl = ResourceUrl.parse(oldText);
                        ResourceUrl newResourceUrl = ResourceUrl.parse(newText);

                        // Make sure to compare name as well as urlType, e.g. if both have @+id or not.
                        if (Objects.equals(oldResourceUrl, newResourceUrl)) {
                          // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc).
                          return;
                        }

                        if (handleIdsChange(source, attribute.getParent())) {
                          return;
                        }
                      }

                      scheduleScan(virtualFile, folderType);
                      return;
                    }
                  }
                }
                else if (parent instanceof XmlAttributeValue) {
                  PsiElement grandParent = parent.getParent();
                  if (grandParent instanceof XmlProcessingInstruction) {
                    // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                    // <?xml version="1.0" encoding="utf-8"?>
                    return;
                  }
                  assert grandParent instanceof XmlAttribute : parent;
                  XmlAttribute attribute = (XmlAttribute)grandParent;
                  XmlTag xmlTag = attribute.getParent();
                  String oldText = StringUtil.notNullize(event.getOldChild().getText()).trim();
                  String newText = StringUtil.notNullize(event.getNewChild().getText()).trim();
                  TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()) +
                                   " oldText: \"" + oldText + "\" newText: \"" + newText + "\"");
                  if (oldText.startsWith(NEW_ID_PREFIX) || newText.startsWith(NEW_ID_PREFIX)) {
                    ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
                    if (resFile != null) {
                      ResourceUrl oldResourceUrl = ResourceUrl.parse(oldText);
                      ResourceUrl newResourceUrl = ResourceUrl.parse(newText);

                      // Make sure to compare name as well as urlType, e.g. if both have @+id or not.
                      if (Objects.equals(oldResourceUrl, newResourceUrl)) {
                        // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc).
                        return;
                      }

                      if (handleIdsChange(resFile, xmlTag)) {
                        return;
                      }
                    }

                    scheduleScan(virtualFile, folderType);
                    return;
                  }
                }
                // This is an XML change within an ID generating folder to something that it's not an ID. While we do not need
                // to generate the ID, we need to notify that something relevant has changed.
                // One example of this change would be an edit to a drawable.
                setModificationCount(ourModificationCounter.incrementAndGet());
                return;
              }

              // TODO: Handle adding/removing elements in layouts incrementally.

              scheduleScan(virtualFile, folderType);
            }
            else if (folderType == VALUES) {
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

                // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc.
                if (parent instanceof XmlTag) {
                  XmlTag parentTag = (XmlTag)parent;
                  if (getResourceTypeForResourceTag(parentTag) != null) {
                    if (convertToPsiIfNeeded(psiFile, folderType)) {
                      return;
                    }
                    // Yes just invalidate the corresponding cached value.
                    ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                    if (resourceItem instanceof PsiResourceItem) {
                      if (((PsiResourceItem)resourceItem).recomputeValue()) {
                        setModificationCount(ourModificationCounter.incrementAndGet());
                      }
                      TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()) +
                                       " recomputed: " + resourceItem);
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
                      TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()) +
                                       " oldName: \"" + oldName + "\" newName: \"" + newName + "\"");
                      if (oldName.equals(newName)) {
                        // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc).
                        return;
                      }
                      // findResourceItem depends on PSI in some cases, so we need to bail and rescan if not PSI.
                      if (convertToPsiIfNeeded(psiFile, folderType)) {
                        return;
                      }
                      ResourceItem item = findResourceItem(type, psiFile, oldName, xmlTag);
                      if (item != null) {
                        synchronized (ITEM_MAP_LOCK) {
                          ListMultimap<String, ResourceItem> map = myResourceTable.get(item.getType());
                          if (map != null) {
                            // Found the relevant item: delete it and create a new one in a new location.
                            map.remove(oldName, item);
                            if (isValidValueResourceName(newName)) {
                              PsiResourceItem newItem =
                                  PsiResourceItem.forXmlTag(newName, type, ResourceFolderRepository.this, xmlTag, true);
                              map.put(newName, newItem);
                              ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
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
                            invalidateParentCaches(ResourceFolderRepository.this, type);
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
                            TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()) +
                                             " recomputed: " + style);
                          }
                        }

                        return;
                      }
                    }
                    else {
                      XmlTag parentTag = xmlTag.getParentTag();
                      if (parentTag != null && getResourceTypeForResourceTag(parentTag) != null) {
                        // <style>, or <plurals>, or <array>, or <string-array>, ...
                        // Edited the attribute value of an item that is wrapped in a <style> tag: invalidate parent cached value.
                        if (convertToPsiIfNeeded(psiFile, folderType)) {
                          return;
                        }
                        ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                        if (resourceItem instanceof PsiResourceItem) {
                          if (((PsiResourceItem)resourceItem).recomputeValue()) {
                            setModificationCount(ourModificationCounter.incrementAndGet());
                          }
                          TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()) +
                                           " recomputed: " + resourceItem);
                          return;
                        }
                      }
                    }
                  }
                }
              }

              // Fall through: We were not able to directly manipulate the repository to accommodate
              // the edit, so re-scan the whole value file instead.
              scheduleScan(virtualFile, folderType);
            }
            else if (folderType == COLOR) {
              PsiElement parent = event.getParent();
              if (parent instanceof XmlElement) {
                if (parent instanceof XmlComment) {
                  return; // Nothing to do.
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
            }
            else if (folderType == FONT) {
              clearFontCache(psiFile.getVirtualFile());
            }
            else if (folderType != null) {
              PsiElement parent = event.getParent();

              if (parent instanceof XmlElement) {
                if (parent instanceof XmlComment) {
                  return; // Nothing to do.
                }

                // A change to an XML file that does not require adding/removing resources.
                // This could be a change to the contents of an XML file in the raw folder.
                setModificationCount(ourModificationCounter.incrementAndGet());
              }
            } // else: can ignore this edit.
          }
        }

        myIgnoreChildrenChanged = true;
      }
      finally {
        TRACER.log(() -> "IncrementalUpdatePsiListener.childReplaced " + psiToVirtual(event.getFile()) + " end");
      }
    }

    /**
     * If the given resource file is currently being scanned, reschedules the ongoing scan.
     *
     * @param virtualFile the resource file to check
     * @return true if the scan is pending or has been rescheduled, false otherwise
     */
    private boolean rescheduleScanIfRunning(@NotNull VirtualFile virtualFile) {
      synchronized (scanLock) {
        if (myPendingScans.contains(virtualFile)) {
          TRACER.log(() -> "IncrementalUpdatePsiListener.rescheduleScanIfRunning " + virtualFile + " scan is already pending");
          return true;
        }
        if (myRunningScans.containsKey(virtualFile)) {
          TRACER.log(() -> "IncrementalUpdatePsiListener.rescheduleScanIfRunning " + virtualFile + " rescheduling scan");
          scheduleScan(virtualFile);
          return true;
        }
      }
      return false;
    }

    /**
     * Tries to handle changes to attributes that contain "@+id" incrementally.
     *
     * <p>To correctly handle multiple ids declared in the same tag, this means deleting all existing ID resources associated with {@code
     * xmlTag} and creating new ones for every attribute that contains "@+id".
     *
     * @return true if incremental change succeeded, false otherwise (i.e. a rescan is necessary).
     */
    private boolean handleIdsChange(@NotNull ResourceItemSource<? extends ResourceItem> resFile, @NotNull XmlTag xmlTag) {
      if (!(resFile instanceof PsiResourceFile)) {
        return false;
      }
      PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;

      synchronized (ITEM_MAP_LOCK) {
        removeItemsForTag(psiResourceFile, xmlTag, ResourceType.ID);
        Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();
        ArrayList<PsiResourceItem> ids = new ArrayList<>();
        addIds(result, ids, xmlTag, true);
        commitToRepository(result);
        ids.forEach(psiResourceFile::addItem);
        setModificationCount(ourModificationCounter.incrementAndGet());
        invalidateParentCaches(ResourceFolderRepository.this, ResourceType.ID);
        return true;
      }
    }

    private void handleValueXmlTextEdit(@Nullable PsiElement parent, @NotNull PsiFile psiFile) {
      if (!(parent instanceof XmlTag)) {
        // Edited text outside the root element.
        return;
      }
      XmlTag parentTag = (XmlTag)parent;
      String parentTagName = parentTag.getName();
      if (parentTagName.equals(TAG_RESOURCES)) {
        // Editing whitespace between top level elements; ignore.
        return;
      }

      if (parentTagName.equals(TAG_ITEM)) {
        XmlTag style = parentTag.getParentTag();
        if (style != null && ResourceType.fromXmlTagName(style.getName()) != null) {
          ResourceFolderType folderType = IdeResourcesUtil.getFolderType(psiFile);
          assert folderType != null;
          if (convertToPsiIfNeeded(psiFile, folderType)) {
            return;
          }
          // <style>, or <plurals>, or <array>, or <string-array>, ...
          // Edited the text value of an item that is wrapped in a <style> tag: invalidate.
          ResourceItem item = findValueResourceItem(style, psiFile);
          if (item instanceof PsiResourceItem) {
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
            TRACER.log(() -> "IncrementalUpdatePsiListener.handleValueXmlTextEdit " + psiFile.getVirtualFile() + " recomputed: " + item);
          }
          return;
        }
      }

      // Find surrounding item.
      while (parentTag != null) {
        if (isItemElement(parentTag)) {
          ResourceFolderType folderType = IdeResourcesUtil.getFolderType(psiFile);
          assert folderType != null;
          if (convertToPsiIfNeeded(psiFile, folderType)) {
            return;
          }
          ResourceItem item = findValueResourceItem(parentTag, psiFile);
          if (item instanceof PsiResourceItem) {
            // Edited XML value.
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
            TRACER.log(() -> "IncrementalUpdatePsiListener.handleValueXmlTextEdit " + psiFile.getVirtualFile() + " recomputed: " + item);
          }
          break;
        }
        parentTag = parentTag.getParentTag();
      }

      // Fully handled; other whitespace changes do not affect resources.
    }

    @Override
    public final void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      TRACER.log(() -> "IncrementalUpdatePsiListener.beforeChildrenChange " + psiToVirtual(event.getFile()));
      myIgnoreChildrenChanged = false;
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      TRACER.log(() -> "IncrementalUpdatePsiListener.childrenChanged " + psiToVirtual(event.getFile()));
      PsiElement parent = event.getParent();
      // Called after children have changed. There are typically individual childMoved, childAdded etc
      // calls that we hook into for more specific details. However, there are some events we don't
      // catch using those methods, and for that we have the below handling.
      if (myIgnoreChildrenChanged) {
        // We've already processed this change as one or more individual childMoved, childAdded, childRemoved etc calls.
        // However, we sometimes get some surprising (=bogus) events where the parent and the child
        // are the same, and in those cases there may be other child events we need to process
        // so fall through and process the whole file.
        if (parent != event.getChild()) {
          TRACER.log(() -> "IncrementalUpdatePsiListener.childrenChanged " + psiToVirtual(event.getFile()) + " event already processed");
          return;
        }
      }
      else if (event instanceof PsiTreeChangeEventImpl && ((PsiTreeChangeEventImpl)event).isGenericChange()) {
        TRACER.log(() -> "IncrementalUpdatePsiListener.childrenChanged " + psiToVirtual(event.getFile()) + " generic change");
        return;
      }

      // Avoid the next check for files. If they have not been loaded, getFirstChild will trigger a file load
      // that can be expensive.
      PsiElement firstChild = parent != null && !(parent instanceof PsiFile) ? parent.getFirstChild() : null;
      if (firstChild instanceof PsiWhiteSpace && firstChild == parent.getLastChild()) {
        TRACER.log(() -> "IncrementalUpdatePsiListener.childrenChanged " + psiToVirtual(event.getFile()) + " white space");
        // This event is just adding white spaces.
        return;
      }

      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          ResourceFolderType folderType = IdeResourcesUtil.getFolderType(psiFile);
          if (folderType != null && isResourceFile(psiFile)) {
            // TODO: If I get an XmlText change and the parent is the resources tag or it's a layout, nothing to do.
            scheduleScan(virtualFile, folderType);
          }
        }
      } else {
        Throwable throwable = new Throwable();
        throwable.fillInStackTrace();
        LOG.debug("Received unexpected childrenChanged event for inter-file operations", throwable);
      }
      TRACER.log(() -> "IncrementalUpdatePsiListener.childrenChanged " + psiToVirtual(event.getFile()) + " end");
    }
  }

  void onFileCreated(@NotNull VirtualFile file) {
    TRACER.log(() -> "ResourceFolderRepository.onFileCreated " + file);
    scheduleScan(file);
  }

  @NotNull
  private Project getProject() {
    return myFacet.getModule().getProject();
  }

  void onFileOrDirectoryRemoved(@NotNull VirtualFile file) {
    TRACER.log(() -> "ResourceFolderRepository.onFileOrDirectoryRemoved " + file);
    if (file.isDirectory()) {
      for (Iterator<Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>>> iterator = mySources.entrySet().iterator();
           iterator.hasNext(); ) {
        Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>> entry = iterator.next();
        iterator.remove();
        VirtualFile sourceFile = entry.getKey();
        if (VfsUtilCore.isAncestor(file, sourceFile, true)) {
          ResourceItemSource<? extends ResourceItem> source = entry.getValue();
          onSourceRemoved(sourceFile, source);
        }
      }
    }
    else {
      ResourceItemSource<? extends ResourceItem> source = mySources.remove(file);
      if (source != null) {
        onSourceRemoved(file, source);
      }
      myWolfTheProblemSolver.clearProblemsFromExternalSource(file, this);
    }
  }

  private void onSourceRemoved(@NotNull VirtualFile file, @NotNull ResourceItemSource<? extends ResourceItem> source) {
    TRACER.log(() -> "ResourceFolderRepository.onSourceRemoved " + file);

    boolean removed = removeItemsFromSource(source);
    if (removed) {
      setModificationCount(ourModificationCounter.incrementAndGet());
      invalidateParentCaches(this, ResourceType.values());
    }

    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
    if (folderType != null) {
      clearLayoutlibCaches(file, folderType);
    }
  }

  private void clearLayoutlibCaches(@NotNull VirtualFile file, @NotNull ResourceFolderType folderType) {
    if (SdkConstants.EXT_XML.equals(file.getExtension())) {
      return;
    }
    if (folderType == DRAWABLE) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod: for testing
      ourLayoutlibCacheFlushes++;
      bitmapUpdated(file);
    }
    else if (folderType == FONT) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod: for testing
      ourLayoutlibCacheFlushes++;
      clearFontCache(file);
    }
  }

  private static boolean isItemElement(@NotNull XmlTag xmlTag) {
    String tag = xmlTag.getName();
    if (tag.equals(TAG_RESOURCES)) {
      return false;
    }
    return tag.equals(TAG_ITEM) || ResourceType.fromXmlTagName(tag) != null;
  }

  @Nullable
  private ResourceItem findValueResourceItem(@NotNull XmlTag tag, @NotNull PsiFile file) {
    if (!tag.isValid()) {
      // This function should only be used if we know file's items are PsiResourceItems.
      ResourceItemSource<? extends ResourceItem> resFile = mySources.get(file.getVirtualFile());
      if (resFile != null) {
        assert resFile instanceof PsiResourceFile;
        PsiResourceFile resourceFile = (PsiResourceFile)resFile;
        for (PsiResourceItem item : resourceFile) {
          if (item.wasTag(tag)) {
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
  private ResourceItem findValueResourceItem(@NotNull XmlTag tag, @NotNull PsiFile file, @NotNull String name) {
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
      ListMultimap<String, ResourceItem> map = myResourceTable.get(type);
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
  @NotNull
  public String toString() {
    return getClass().getSimpleName() + " for " + myResourceDir + ": @" + Integer.toHexString(System.identityHashCode(this));
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return Collections.singleton(myResourceDir);
  }

  /**
   * {@inheritDoc}
   * <p>
   * This override is needed because this repository uses {@link VfsResourceFile} that is a subclass of
   * {@link ResourceSourceFile} used by {@link RepositoryLoader}. If the combined hash of file timestamp
   * and length doesn't match the stream, the method returns an invalid {@link VfsResourceFile} containing
   * a null {@link VirtualFile} reference. Validity of the {@link VfsResourceFile} is checked later inside
   * the {@link Loader#addResourceItem(BasicResourceItem, ResourceFolderRepository)} method. This process
   * creates few objects that are discarded later, but an alternative of returning null instead of an invalid
   * {@link VfsResourceFile} would lead to pretty unnatural nullability conditions in {@link RepositoryLoader}.
   * @see VfsResourceFile#serialize
   */
  @Override
  @NotNull
  public VfsResourceFile deserializeResourceSourceFile(
      @NotNull Base128InputStream stream, @NotNull List<RepositoryConfiguration> configurations) throws IOException {
    String relativePath = stream.readString();
    if (relativePath == null) {
      throw Base128InputStream.StreamFormatException.invalidFormat();
    }
    int configIndex = stream.readInt();
    RepositoryConfiguration configuration = configurations.get(configIndex);
    VirtualFile virtualFile =
        ((ResourceFolderRepository)configuration.getRepository()).getResourceDir().findFileByRelativePath(relativePath);
    if (!stream.validateContents(FileTimeStampLengthHasher.hash(virtualFile))) {
      virtualFile = null;
    }

    return new VfsResourceFile(virtualFile, configuration);
  }

  /**
   * {@inheritDoc}
   * <p>
   * This override is needed because this repository uses {@link VfsFileResourceItem} that is a subclass of
   * {@link BasicFileResourceItem} used by {@link RepositoryLoader}. If the combined hash of file timestamp
   * and length doesn't match the stream, the method returns an invalid {@link VfsFileResourceItem} containing
   * a null {@link VirtualFile} reference. Validity of the {@link VfsFileResourceItem} is checked later inside
   * the {@link Loader#addResourceItem(BasicResourceItem, ResourceFolderRepository)} method. This process
   * creates few objects that are discarded later, but an alternative of returning null instead of an invalid
   * {@link VfsFileResourceItem} would lead to pretty unnatural nullability conditions in {@link RepositoryLoader}.
   * @see VfsFileResourceItem#serialize
   * @see BasicFileResourceItem#serialize
   */
  @Override
  @NotNull
  public BasicFileResourceItem deserializeFileResourceItem(@NotNull Base128InputStream stream,
                                                           @NotNull ResourceType resourceType,
                                                           @NotNull String name,
                                                           @NotNull ResourceVisibility visibility,
                                                           @NotNull List<RepositoryConfiguration> configurations) throws IOException {
    String relativePath = stream.readString();
    if (relativePath == null) {
      throw Base128InputStream.StreamFormatException.invalidFormat();
    }
    int configIndex = stream.readInt();
    RepositoryConfiguration configuration = configurations.get(configIndex);
    int encodedDensity = stream.readInt();

    VirtualFile virtualFile =
        ((ResourceFolderRepository)configuration.getRepository()).getResourceDir().findFileByRelativePath(relativePath);
    boolean idGenerating = false;
    String folderName = new PathString(relativePath).getParentFileName();
    if (folderName != null) {
      ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
      idGenerating = folderType != null && FolderTypeRelationship.isIdGeneratingFolderType(folderType);
    }
    if (idGenerating) {
      if (!stream.validateContents(FileTimeStampLengthHasher.hash(virtualFile))) {
        virtualFile = null;
      }

      if (encodedDensity == 0) {
        return new VfsFileResourceItem(resourceType, name, configuration, visibility, relativePath, virtualFile);
      }

      Density density = Density.values()[encodedDensity - 1];
      return new VfsDensityBasedFileResourceItem(resourceType, name, configuration, visibility, relativePath, virtualFile, density);
    }
    else {
      // The resource item corresponding to a file that is not id-generating is valid regardless of the changes to
      // the contents of the file. BasicFileResourceItem and BasicDensityBasedFileResourceItem are sufficient in
      // this case since there is no need for timestamp/length check.
      if (encodedDensity == 0) {
        return new BasicFileResourceItem(resourceType, name, configuration, visibility, relativePath);
      }

      Density density = Density.values()[encodedDensity - 1];
      return new BasicDensityBasedFileResourceItem(resourceType, name, configuration, visibility, relativePath, density);
    }
  }

  @Override
  protected void invalidateParentCaches() {
    synchronized (ITEM_MAP_LOCK) {
      super.invalidateParentCaches();
    }
  }

  @Override
  protected void invalidateParentCaches(@NotNull SingleNamespaceResourceRepository repository, @NotNull ResourceType... types) {
    synchronized (ITEM_MAP_LOCK) {
      super.invalidateParentCaches(repository, types);
    }
  }

  /**
   * Tracks state used by the initial scan, which may be used to save the state to a cache.
   * The file cache omits non-XML single-file items, since those are easily derived from the file path.
   */
  private static class Loader extends RepositoryLoader<ResourceFolderRepository> {
    @NotNull private final ResourceFolderRepository myRepository;
    @NotNull private final VirtualFile myResourceDir;
    @NotNull private final PsiManager myPsiManager;
    @Nullable private final ResourceFolderRepositoryCachingData myCachingData;
    @NotNull private final Map<ResourceType, ListMultimap<String, ResourceItem>> myResources = new EnumMap<>(ResourceType.class);
    @NotNull private final Map<VirtualFile, ResourceItemSource<BasicResourceItem>> mySources = new HashMap<>();
    @NotNull private final Map<VirtualFile, BasicFileResourceItem> myFileResources = new HashMap<>();
    // The following two fields are used as a cache of size one for quick conversion from a PathString to a VirtualFile.
    @Nullable private VirtualFile myLastVirtualFile;
    @Nullable private PathString myLastPathString;

    @NotNull Set<VirtualFile> myFilesToReparseAsPsi = new HashSet<>();
    private final FileDocumentManager myFileDocumentManager;

    Loader(@NotNull ResourceFolderRepository repository, @Nullable ResourceFolderRepositoryCachingData cachingData) {
      super(VfsUtilCore.virtualToIoFile(repository.myResourceDir).toPath(), null, repository.getNamespace());
      myRepository = repository;
      myResourceDir = repository.myResourceDir;
      myPsiManager = repository.myPsiManager;
      myCachingData = cachingData;
      // TODO: Support visibility without relying on ResourceVisibilityLookup.
      myDefaultVisibility = ResourceVisibility.UNDEFINED;
      myFileDocumentManager = FileDocumentManager.getInstance();
    }

    public void load() {
      if (!myResourceDir.isValid()) {
        return;
      }

      loadFromPersistentCache();

      ApplicationManager.getApplication().runReadAction(this::getPsiDirsForListener);

      scanResFolder();

      populateRepository();

      ApplicationManager.getApplication().runReadAction(this::scanQueuedPsiResources);

      if (myCachingData != null && !myRepository.hasFreshFileCache()) {
        Executor executor = myCachingData.getCacheCreationExecutor();
        if (executor != null) {
          executor.execute(this::createCacheFile);
        }
      }
    }

    private void loadFromPersistentCache() {
      if (myCachingData == null) {
        return;
      }

      byte[] fileHeader = getCacheFileHeader(myCachingData);
      try (Base128InputStream stream = new Base128InputStream(myCachingData.getCacheFile())) {
        if (!stream.validateContents(fileHeader)) {
          return; // Cache file header doesn't match.
        }
        ResourceSerializationUtil.readResourcesFromStream(stream, Maps.newHashMapWithExpectedSize(1000), null, myRepository,
                                                          item -> addResourceItem(item, myRepository));
      }
      catch (NoSuchFileException ignored) {
        // Cache file does not exist.
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        // Remove incomplete data.
        mySources.clear();
        myFileResources.clear();

        LOG.warn("Failed to load resources from cache file " + myCachingData.getCacheFile(), e);
      }
    }

    protected byte @NotNull [] getCacheFileHeader(@NotNull ResourceFolderRepositoryCachingData cachingData) {
      return ResourceSerializationUtil.getCacheFileHeader(stream -> {
        stream.write(CACHE_FILE_HEADER);
        stream.writeString(CACHE_FILE_FORMAT_VERSION);
        stream.writeString(myResourceDir.getPath());
        stream.writeString(cachingData.getCodeVersion());
      });
    }

    private void createCacheFile() {
      assert myCachingData != null;
      byte[] header = getCacheFileHeader(myCachingData);
      try {
        createPersistentCache(myCachingData.getCacheFile(), header, stream -> writeResourcesToStream(myResources, stream, config -> true));
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    private void scanResFolder() {
      try {
        for (VirtualFile subDir : myResourceDir.getChildren()) {
          if (subDir.isValid() && subDir.isDirectory()) {
            String folderName = subDir.getName();
            FolderInfo folderInfo = FolderInfo.create(folderName, myFolderConfigCache);
            if (folderInfo != null) {
              RepositoryConfiguration configuration = getConfiguration(myRepository, folderInfo.configuration);
              for (VirtualFile file : subDir.getChildren()) {
                if (file.getName().startsWith(".")) {
                  continue; // Skip file with the name starting with a dot.
                }
                // If there is an unsaved Document for this file, data read from persistent cache may be stale and data read using
                // loadResourceFile below will be stale as it reads straight from disk. Schedule a PSI-based parse.
                if (myFileDocumentManager.isFileModified(file)) {
                  myFilesToReparseAsPsi.add(file);
                  continue;
                }

                if (folderInfo.folderType == VALUES ? mySources.containsKey(file) : myFileResources.containsKey(file)) {
                  if (isParsableFile(file, folderInfo)) {
                    countCacheHit();
                  }
                  continue;
                }

                PathString pathString = FileExtensions.toPathString(file);
                myLastVirtualFile = file;
                myLastPathString = pathString;
                try {
                  loadResourceFile(pathString, folderInfo, configuration);
                  if (isParsableFile(file, folderInfo)) {
                    countCacheMiss();
                  }
                }
                catch (ParsingException e) {
                  // Reparse the file as PSI. The PSI parser is more forgiving than KXmlParser because
                  // it is designed to work with potentially malformed files in the middle of editing.
                  myFilesToReparseAsPsi.add(file);
                }
              }
            }
          }
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error("Failed to load resources from " + myResourceDirectoryOrFile, e);
      }

      super.finishLoading(myRepository);

      // Associate file resources with sources.
      for (Map.Entry<VirtualFile, BasicFileResourceItem> entry : myFileResources.entrySet()) {
        VirtualFile virtualFile = entry.getKey();
        BasicFileResourceItem item = entry.getValue();
        ResourceItemSource<BasicResourceItem> source =
            mySources.computeIfAbsent(virtualFile, file -> new VfsResourceFile(file, item.getRepositoryConfiguration()));
        source.addItem(item);
      }

      // Populate the myResources map.
      List<ResourceItemSource<BasicResourceItem>> sortedSources = new ArrayList<>(mySources.values());
      // Sort sources according to folder configurations to have deterministic ordering of resource items in myResources.
      sortedSources.sort(SOURCE_COMPARATOR);
      for (ResourceItemSource<BasicResourceItem> source : sortedSources) {
        for (ResourceItem item : source) {
          getOrCreateMap(item.getType()).put(item.getName(), item);
        }
      }
    }

    private void loadResourceFile(
        @NotNull PathString file, @NotNull FolderInfo folderInfo, @NotNull RepositoryConfiguration configuration) {
      if (folderInfo.resourceType == null) {
        if (isXmlFile(file)) {
          parseValueResourceFile(file, configuration);
        }
      }
      else if (myRepository.checkResourceFilename(file, folderInfo.folderType)) {
        if (isXmlFile(file) && folderInfo.isIdGenerating) {
          parseIdGeneratingResourceFile(file, configuration);
        }

        BasicFileResourceItem item = createFileResourceItem(file, folderInfo.resourceType, configuration, folderInfo.isIdGenerating);
        addResourceItem(item, (ResourceFolderRepository)item.getRepository());
      }
    }

    private static boolean isParsableFile(@NotNull VirtualFile file, @NotNull FolderInfo folderInfo) {
      return (folderInfo.folderType == VALUES || folderInfo.isIdGenerating) && isXmlFile(file.getName());
    }

    private void populateRepository() {
      myRepository.mySources.putAll(mySources);
      myRepository.commitToRepositoryWithoutLock(myResources);
    }

    @NotNull
    private ListMultimap<String, ResourceItem> getOrCreateMap(@NotNull ResourceType resourceType) {
      return myResources.computeIfAbsent(resourceType, type -> LinkedListMultimap.create());
    }

    @Override
    @NotNull
    protected InputStream getInputStream(@NotNull PathString file) throws IOException {
      VirtualFile virtualFile = getVirtualFile(file);
      if (virtualFile == null) {
        throw new NoSuchFileException(file.getNativePath());
      }
      return virtualFile.getInputStream();
    }

    @Nullable
    private VirtualFile getVirtualFile(@NotNull PathString file) {
      return file.equals(myLastPathString) ? myLastVirtualFile : FileExtensions.toVirtualFile(file);
    }

    /**
     * Currently, {@link com.intellij.psi.impl.file.impl.PsiVFSListener} requires that at least the parent directory of each file has been
     * accessed as PSI before bothering to notify any listener of events. So, make a quick pass to grab the necessary PsiDirectories.
     */
    private void getPsiDirsForListener() {
      PsiDirectory resourceDirPsi = myPsiManager.findDirectory(myResourceDir);
      if (resourceDirPsi != null) {
        resourceDirPsi.getSubdirectories();
      }
    }

    @Override
    protected void addResourceItem(@NotNull BasicResourceItem item, @NotNull ResourceFolderRepository repository) {
      if (item instanceof BasicValueResourceItemBase) {
        VfsResourceFile sourceFile = (VfsResourceFile)((BasicValueResourceItemBase)item).getSourceFile();
        VirtualFile virtualFile = sourceFile.getVirtualFile();
        if (virtualFile != null && virtualFile.isValid() && !virtualFile.isDirectory()) {
          sourceFile.addItem(item);
          mySources.put(virtualFile, sourceFile);
        }
      }
      else if (item instanceof VfsFileResourceItem) {
        VfsFileResourceItem fileResourceItem = (VfsFileResourceItem)item;
        VirtualFile virtualFile = fileResourceItem.getVirtualFile();
        if (virtualFile != null && virtualFile.isValid() && !virtualFile.isDirectory()) {
          myFileResources.put(virtualFile, fileResourceItem);
        }
      }
      else if (item instanceof BasicFileResourceItem) {
        BasicFileResourceItem fileResourceItem = (BasicFileResourceItem)item;
        VirtualFile virtualFile = getVirtualFile(fileResourceItem.getSource());
        if (virtualFile != null && virtualFile.isValid() && !virtualFile.isDirectory()) {
          myFileResources.put(virtualFile, fileResourceItem);
        }
      }
      else {
        throw new IllegalArgumentException("Unexpected type: " + item.getClass().getName());
      }
    }

    @NotNull
    private BasicFileResourceItem createFileResourceItem(@NotNull PathString file,
                                                         @NotNull ResourceType resourceType,
                                                         @NotNull RepositoryConfiguration configuration,
                                                         boolean idGenerating) {
      String resourceName = SdkUtils.fileNameToResourceName(file.getFileName());
      ResourceVisibility visibility = getVisibility(resourceType, resourceName);
      Density density = null;
      if (DensityBasedResourceValue.isDensityBasedResourceType(resourceType)) {
        DensityQualifier densityQualifier = configuration.getFolderConfiguration().getDensityQualifier();
        if (densityQualifier != null) {
          density = densityQualifier.getValue();
        }
      }
      return createFileResourceItem(file, resourceType, resourceName, configuration, visibility, density, idGenerating);
    }

    @Override
    @NotNull
    protected ResourceSourceFile createResourceSourceFile(@NotNull PathString file, @NotNull RepositoryConfiguration configuration) {
      VirtualFile virtualFile = getVirtualFile(file);
      return new VfsResourceFile(virtualFile, configuration);
    }

    @NotNull
    private BasicFileResourceItem createFileResourceItem(@NotNull PathString file,
                                                         @NotNull ResourceType type,
                                                         @NotNull String name,
                                                         @NotNull RepositoryConfiguration configuration,
                                                         @NotNull ResourceVisibility visibility,
                                                         @Nullable Density density,
                                                         boolean idGenerating) {
      if (!idGenerating) {
        return super.createFileResourceItem(file, type, name, configuration, visibility, density);
      }

      VirtualFile virtualFile = getVirtualFile(file);
      String relativePath = getResRelativePath(file);
      return density == null ?
             new VfsFileResourceItem(type, name, configuration, visibility, relativePath, virtualFile) :
             new VfsDensityBasedFileResourceItem(type, name, configuration, visibility, relativePath, virtualFile, density);
    }

    @Override
    protected void handleParsingError(@NotNull PathString file, @NotNull Exception e) {
      throw new ParsingException(e);
    }

    /**
     * For resource files that failed when scanning with a VirtualFile, retry with PsiFile.
     */
    private void scanQueuedPsiResources() {
      for (VirtualFile file : myFilesToReparseAsPsi) {
        myRepository.scan(file);
      }
    }

    private void countCacheHit() {
      ++myRepository.myNumXmlFilesLoadedInitially;
    }

    private void countCacheMiss() {
      ++myRepository.myNumXmlFilesLoadedInitially;
      ++myRepository.myNumXmlFilesLoadedInitiallyFromSources;
    }
  }

  private static class ParsingException extends RuntimeException {
    ParsingException(Throwable cause) {
      super(cause);
    }
  }

  private static class NonCancellableIndicator extends AbstractProgressIndicatorExBase {
    @Override
    public void cancel() {
    }

    @Override
    public boolean isCanceled() {
      return false;
    }
  }

  private static class Tracer {
    boolean enabled;

    Tracer(boolean enabled) {
      this.enabled = enabled;
    }

    void log(@NotNull Supplier<?> lazyRecord) {
      if (enabled) {
        FlightRecorder.log(() -> TraceUtils.currentTime() + ' ' + lazyRecord.get());
      }
    }
  }
}

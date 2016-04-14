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
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.resources.ResourceFolderType.*;
import static com.android.tools.idea.rendering.PsiProjectListener.isRelevantFile;
import static com.android.tools.idea.rendering.PsiProjectListener.isRelevantFileType;
import static com.android.tools.idea.rendering.ResourceHelper.getFolderType;
import static com.android.tools.lint.detector.api.LintUtils.stripIdPrefix;

/**
 * Remaining work:
 * <ul>
 *   <li>Find some way to have event updates in this resource folder directly update parent repositories
 *   (typically {@link ModuleResourceRepository}</li>
 *   <li>consider *initializing* this repository initially from IO files to not force full modelling of
 *   XML objects for all these tiny files (translations etc) ? Or find some way to persist the data in the index.</li>
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
  private final Map<ResourceType, ListMultimap<String, ResourceItem>> myItems = Maps.newEnumMap(ResourceType.class);
  private final Map<PsiFile, PsiResourceFile> myResourceFiles = Maps.newHashMap();
  // qualifiedName -> PsiResourceFile
  private Map<String, DataBindingInfo> myDataBindingResourceFiles = Maps.newHashMap();
  private long myDataBindingResourceFilesModificationCount = Long.MIN_VALUE;
  private final Object SCAN_LOCK = new Object();
  private Set<PsiFile> myPendingScans;

  @VisibleForTesting
  static int ourFullRescans;

  private ResourceFolderRepository(@NotNull AndroidFacet facet, @NotNull VirtualFile resourceDir) {
    super(resourceDir.getName());
    myFacet = facet;
    myModule = facet.getModule();
    myListener = new PsiListener();
    myResourceDir = resourceDir;
    scan();
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
  static ResourceFolderRepository create(@NotNull final AndroidFacet facet, @NotNull VirtualFile dir) {
    return new ResourceFolderRepository(facet, dir);
  }

  private void scan() {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        PsiManager manager = PsiManager.getInstance(myFacet.getModule().getProject());
        if (myResourceDir.isValid()) {
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
      ResourceFolderType folderType = ResourceFolderType.getFolderType(name);
      if (folderType != null) {
        String qualifiers = getQualifiers(name);
        FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(name);
        if (folderConfiguration == null) {
          continue;
        }
        if (folderType == VALUES) {
          scanValueResFolder(dir, qualifiers, folderConfiguration);
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

  private void scanFileResourceFolder(@NotNull PsiDirectory directory, ResourceFolderType folderType, String qualifiers,
                                      FolderConfiguration folderConfiguration) {
    List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
    assert resourceTypes.size() >= 1 : folderType;
    ResourceType type = resourceTypes.get(0);

    boolean idGenerating = resourceTypes.size() > 1;
    assert !idGenerating || resourceTypes.size() == 2 && resourceTypes.get(1) == ResourceType.ID;

    ListMultimap<String, ResourceItem> map = getMap(type, true);

    for (PsiFile file : directory.getFiles()) {
      FileType fileType = file.getFileType();
      if (isRelevantFileType(fileType) || folderType == ResourceFolderType.RAW) {
        scanFileResourceFile(qualifiers, folderType, folderConfiguration, type, idGenerating, map, file);

      } // TODO: Else warn about files that aren't expected to be found here?
    }
  }

  private void scanFileResourceFile(String qualifiers,
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
      myResourceFiles.put(file, resourceFile);
    } else {
      PsiResourceFile resourceFile = new PsiResourceFile(file, item, qualifiers, folderType, folderConfiguration);
      myResourceFiles.put(file, resourceFile);
      map.put(name, item);
    }
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
    for (PsiResourceFile file : myResourceFiles.values()) {
      DataBindingInfo info = file.getDataBindingInfo();
      if (info != null) {
        selected.put(info.getQualifiedName(), info);
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
    String modulePackage = ManifestInfo.get(myFacet.getModule(), false).getPackage();
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

  private void scanValueResFolder(@NotNull PsiDirectory directory, String qualifiers, FolderConfiguration folderConfiguration) {
    //noinspection ConstantConditions
    assert directory.getName().startsWith(FD_RES_VALUES);

    for (PsiFile file : directory.getFiles()) {
      scanValueFile(qualifiers, file, folderConfiguration);
    }
  }

  private boolean scanValueFile(String qualifiers, PsiFile file, FolderConfiguration folderConfiguration) {
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
                  map = myItems.get(ResourceType.ATTR);
                  if (map == null) {
                    map = ArrayListMultimap.create();
                    myItems.put(ResourceType.ATTR, map);
                  }

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
        myResourceFiles.put(file, resourceFile);
      }
    }

    return added;
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
      PsiResourceFile resourceFile = myResourceFiles.get(file);
      boolean removed = false;
      if (resourceFile != null) {
        for (ResourceItem item : resourceFile) {
          removed |= removeItems(resourceFile, item.getType(), item.getName(), false);  // Will throw away file
        }

        myResourceFiles.remove(file);
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
            added = scanValueFile(getQualifiers(dirName), file, folderConfiguration);
          }
        }
      }

      if (added || removed) {
        // TODO: Consider doing a deeper diff of the changes to the resource items
        // to determine if the removed and added items actually differ
        myGeneration++;
        invalidateItemCaches();
      }
    } else {
      PsiResourceFile resourceFile = myResourceFiles.get(file);
      if (resourceFile != null) {
        // Already seen this file; no need to do anything unless it's a layout or
        // menu file; in that case we may need to update the id's
        if (folderType == LAYOUT || folderType == MENU) {
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
            myGeneration++;
          }
          scanDataBinding(resourceFile, myGeneration);
          // Identities may have changed even if the ids are the same, so update maps
          invalidateItemCaches(ResourceType.ID);
        }
      } else {
        // For unit test tracking purposes only
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFullRescans++;

        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type
        String dirName = parent.getName();

        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        assert resourceTypes.size() >= 1 : folderType;
        ResourceType type = resourceTypes.get(0);

        boolean idGenerating = resourceTypes.size() > 1;
        assert !idGenerating || resourceTypes.size() == 2 && resourceTypes.get(1) == ResourceType.ID;

        ListMultimap<String, ResourceItem> map = getMap(type, true);

        file = ensureValid(file);
        if (file != null) {
          PsiDirectory fileParent = psiFile.getParent();
          if (fileParent != null) {
            FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
            if (folderConfiguration != null) {
              scanFileResourceFile(getQualifiers(dirName), folderType, folderConfiguration, type, idGenerating, map, file);
            }
          }
          myGeneration++;
          invalidateItemCaches();
        }
      }
    }
  }

  private boolean removeItems(PsiResourceFile resourceFile, ResourceType type, String name, boolean removeFromFile) {
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
          if (isRelevantFile(psiFile)) {
            addFile(psiFile);
          }
        } else if (child instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)child;
          if (isResourceFolder(directory)) {
            for (PsiFile file : directory.getFiles()) {
              if (isRelevantFile(file)) {
                addFile(file);
              }
            }
          }
        }
      } else if (isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was added within a file
        ResourceFolderType folderType = getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();
          if (folderType == ResourceFolderType.VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              if (isItemElement(tag)) {
                PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                if (resourceFile != null) {
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
                      myGeneration++;
                      invalidateItemCaches(type);
                      return;
                    }
                  }
                }
              }

              // See if you just added a new item inside a <style> or <array> or <declare-styleable> etc
              XmlTag parentTag = tag.getParentTag();
              if (parentTag != null && ResourceType.getEnum(parentTag.getName()) != null) {
                // Yes just invalidate the corresponding cached value
                ResourceItem parentItem = findValueResourceItem(parentTag, psiFile);
                if (parentItem instanceof PsiResourceItem) {
                  if (((PsiResourceItem)parentItem).recomputeValue()) {
                    myGeneration++;
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
          } else if (folderType == LAYOUT || folderType == MENU) {
            if (parent instanceof XmlComment || child instanceof XmlComment) {
              return;
            }
            if (parent instanceof XmlText ||
                (child instanceof XmlText && child.getText().trim().isEmpty())) {
              return;
            }

            if (parent instanceof XmlElement && child instanceof XmlElement) {
              if (child instanceof XmlTag) {
                List<ResourceItem> ids = Lists.newArrayList();
                addIds(ids, child, psiFile);
                if (!ids.isEmpty()) {
                  PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                  if (resourceFile != null) {
                    resourceFile.addItems(ids);
                    myGeneration++;
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
          if (isRelevantFile(psiFile)) {
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
            Collection<PsiResourceFile> resourceFiles = new ArrayList<PsiResourceFile>(myResourceFiles.values());
            for (PsiResourceFile file : resourceFiles) {
              if (folderType == file.getFolderType() && qualifiers.equals(file.getQualifiers())) {
                removeFile(file);
              }
            }
          }
        }
      } else if (isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was removed within a file
        ResourceFolderType folderType = getFolderType(psiFile);
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
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      myGeneration++;
                    }

                    if (resourceItem.getType() == ResourceType.ATTR) {
                      parentTag = parentTag.getParentTag();
                      if (parentTag != null && parentTag.getName().equals(ResourceType.DECLARE_STYLEABLE.getName())) {
                        ResourceItem declareStyleable = findValueResourceItem(parentTag, psiFile);
                        if (declareStyleable instanceof PsiResourceItem) {
                          if (((PsiResourceItem)declareStyleable).recomputeValue()) {
                            myGeneration++;
                          }
                        }
                      }
                    }
                    return;
                  }
                }
              }

              if (isItemElement(tag)) {
                PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                if (resourceFile != null) {
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
                        myGeneration++;
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
          } else if (folderType == LAYOUT || folderType == MENU) {
            // TODO: Handle removals of id's (values an attributes) incrementally
            rescan(psiFile, folderType);
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    private void removeFile(@Nullable PsiResourceFile resourceFile) {
      if (resourceFile == null) {
        // No resources for this file
        return;
      }
      for (Map.Entry<PsiFile, PsiResourceFile> entry : myResourceFiles.entrySet()) {
        PsiResourceFile file = entry.getValue();
        if (resourceFile == file) {
          PsiFile psiFile = entry.getKey();
          myResourceFiles.remove(psiFile);
          break;
        }
      }

      myGeneration++;
      invalidateItemCaches();

      ResourceFolderType folderType = resourceFile.getFolderType();
      if (folderType == VALUES || folderType == LAYOUT || folderType == MENU) {
        removeItemsFromFile(resourceFile);
      } else if (folderType != null) {
        // Remove the file item
        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        for (ResourceType type : resourceTypes) {
          if (type != ResourceType.ID) {
            String name = LintUtils.getBaseName(resourceFile.getName());
            removeItems(resourceFile, type, name, false);  // no need since we're discarding the file
          }
        }
      } // else: not a resource folder
    }

    private void removeFile(PsiFile psiFile) {
      assert !psiFile.isValid() || isRelevantFile(psiFile);

      PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
      if (resourceFile == null) {
        // No resources for this file
        return;
      }
      myResourceFiles.remove(psiFile);
      myGeneration++;
      invalidateItemCaches();

      ResourceFolderType folderType = getFolderType(psiFile);
      if (folderType == VALUES || folderType == LAYOUT || folderType == MENU) {
        removeItemsFromFile(resourceFile);
      } else if (folderType != null) {
        if (folderType == DRAWABLE) {
          FileType fileType = psiFile.getFileType();
          if (fileType.isBinary() && fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG)) {
            bitmapUpdated();
          }
        }

        // Remove the file item
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
      assert isRelevantFile(psiFile);

      // Same handling as rescan, where the initial deletion is a no-op
      ResourceFolderType folderType = getFolderType(psiFile);
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
          ResourceFolderType folderType = getFolderType(psiFile);
          if (folderType == LAYOUT || folderType == MENU) {
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
                  PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                  if (resourceFile != null) {
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
                      ResourceItem item = findResourceItem(ResourceType.ID, psiFile, oldName, xmlTag);
                      if (item != null) {
                        ListMultimap<String, ResourceItem> map = myItems.get(item.getType());
                        if (map != null) {
                          // Found the relevant item: delete it and create a new one in a new location
                          map.remove(oldName, item);
                          ResourceItem newItem = new PsiResourceItem(newName, ResourceType.ID, xmlTag, psiFile);
                          map.put(newName, newItem);
                          resourceFile.replace(item, newItem);
                          myGeneration++;
                          invalidateItemCaches(ResourceType.ID);
                          return;
                        }
                      }
                    }
                  }

                  rescan(psiFile, folderType);
                }
              } else if (parent instanceof XmlAttributeValue) {
                assert parent.getParent() instanceof XmlAttribute : parent;
                XmlAttribute attribute = (XmlAttribute)parent.getParent();
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // for each id attribute!
                  PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                  if (resourceFile != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    String oldName = stripIdPrefix(oldChild.getText());
                    String newName = stripIdPrefix(newChild.getText());
                    if (oldName.equals(newName)) {
                      // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc)
                      return;
                    }
                    ResourceItem item = findResourceItem(ResourceType.ID, psiFile, oldName, xmlTag);
                    if (item != null) {
                      ListMultimap<String, ResourceItem> map = myItems.get(item.getType());
                      if (map != null) {
                        // Found the relevant item: delete it and create a new one in a new location
                        map.remove(oldName, item);
                        ResourceItem newItem = new PsiResourceItem(newName, ResourceType.ID, xmlTag, psiFile);
                        map.put(newName, newItem);
                        resourceFile.replace(item, newItem);
                        myGeneration++;
                        invalidateItemCaches(ResourceType.ID);
                        return;
                      }
                    }
                  }

                  rescan(psiFile, folderType);
                } else if (ArrayUtil.contains(attribute.getLocalName(), ATTRS_DATA_BINDING)
                           && ArrayUtil.contains(attribute.getParent().getLocalName(), TAGS_DATA_BINDING)) {
                  PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                  if (resourceFile != null) {
                    myGeneration++;
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

              // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc
              if (parent instanceof XmlTag) {
                XmlTag parentTag = (XmlTag)parent;
                if (ResourceType.getEnum(parentTag.getName()) != null) {
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      myGeneration++;
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
              } else if (parent instanceof XmlComment) {
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
                    ResourceItem item = findResourceItem(type, psiFile, oldName, xmlTag);
                    if (item != null) {
                      ListMultimap<String, ResourceItem> map = myItems.get(item.getType());
                      if (map != null) {
                        // Found the relevant item: delete it and create a new one in a new location
                        map.remove(oldName, item);
                        ResourceItem newItem = new PsiResourceItem(newName, type, xmlTag, psiFile);
                        map.put(newName, newItem);
                        PsiResourceFile resourceFile = myResourceFiles.get(psiFile);
                        if (resourceFile != null) {
                          resourceFile.replace(item, newItem);
                        }
                        else {
                          assert false : item;
                        }
                        myGeneration++;
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
                      ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                      if (resourceItem instanceof PsiResourceItem) {
                        if (((PsiResourceItem)resourceItem).recomputeValue()) {
                          myGeneration++;
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

              myGeneration++;
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
            if (isRelevantFile(oldFile)) {
              removeFile(oldFile);
            }
          }
          if (newChild instanceof PsiFile) {
            PsiFile newFile = (PsiFile)newChild;
            if (isRelevantFile(newFile)) {
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
          // <style>, or <plurals>, or <array>, or <string-array>, ...
          // Edited the text value of an item that is wrapped in a <style> tag: invalidate
          ResourceItem item = findValueResourceItem(style, psiFile);
          if (item instanceof PsiResourceItem) {
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              myGeneration++;
            }
          }
          return;
        }
      }

      // Find surrounding item
      while (parentTag != null) {
        if (isItemElement(parentTag)) {
          ResourceItem item = findValueResourceItem(parentTag, psiFile);
          if (item instanceof PsiResourceItem) {
            // Edited XML value
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              myGeneration++;
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
            // Can't find old location: treat this as a file add
            addFile(psiFile);
            return;
          }

          String oldDirName = oldParentDir.getName();
          ResourceFolderType oldFolderType = ResourceFolderType.getFolderType(oldDirName);
          ResourceFolderType newFolderType = getFolderType(psiFile);

          boolean wasResourceFolder = oldFolderType != null && isResourceFolder(oldParentDir);
          boolean isResourceFolder = newFolderType != null && isResourceFile(psiFile);

          if (wasResourceFolder == isResourceFolder) {
            if (!isResourceFolder) {
              // Moved a non-resource file around: nothing to do
              return;
            }

            // Moved a resource file from one resource folder to another: we need to update
            // the ResourceFile entries for this file. We may also need to update the types.
            PsiResourceFile resourceFile = findResourceFile(oldDirName, name);
            if (resourceFile != null) {
              if (oldFolderType != newFolderType) {
                // In some cases we can do this cheaply, e.g. if you move from layout to menu
                // we can just look up and change @layout/foo to @menu/foo, but if you move
                // to or from values folder it gets trickier, so for now just treat this as a delete
                // followed by an add
                removeFile(resourceFile);
                addFile(psiFile);
              } else {
                myResourceFiles.remove(resourceFile.getPsiFile());
                myResourceFiles.put(psiFile, resourceFile);
                PsiDirectory newParent = psiFile.getParent();
                assert newParent != null; // Since newFolderType != null
                String newDirName = newParent.getName();
                resourceFile.setPsiFile(psiFile, getQualifiers(newDirName));
                myGeneration++; // qualifiers may have changed: can affect configuration matching
                // We need to recompute resource values too, since some of these can point to
                // the old file (e.g. a drawable resource could have a DensityBasedResourceValue
                // pointing to the old file
                for (ResourceItem item : resourceFile) { // usually just 1
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
            PsiResourceFile resourceFile = findResourceFile(dirName, name);
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
      if (psiFile != null && isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          ResourceFolderType folderType = getFolderType(psiFile);
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
          if (isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
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
          if (isRelevantFile(psiFile) && isResourceFolder(event.getParent())) {
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
  private PsiResourceFile findResourceFile(String dirName, String fileName) {
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

    for (PsiResourceFile file : myResourceFiles.values()) {
      String name = file.getName();
      if (folderType == file.getFolderType() && fileName.equals(name) && qualifiers.equals(file.getQualifiers())) {
        return file;
      }
    }

    return null;
  }

  private void removeItemsFromFile(PsiResourceFile resourceFile) {
    for (ResourceItem item : resourceFile) {
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
  private ResourceItem findValueResourceItem(XmlTag tag, PsiFile file) {
    if (!tag.isValid()) {
      PsiResourceFile resourceFile = myResourceFiles.get(file);
      if (resourceFile != null) {
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
  private ResourceItem findValueResourceItem(XmlTag tag, PsiFile file, String name) {
    ResourceType type = AndroidResourceUtil.getType(tag);
    return findResourceItem(type, file, name, tag);
  }

  @Nullable
  private ResourceItem findResourceItem(@Nullable ResourceType type, @Nullable PsiFile file, @Nullable String name, @Nullable XmlTag tag) {
    if (type != null && name != null) {
      ListMultimap<String, ResourceItem> map = myItems.get(type);
      if (map != null) {
        List<ResourceItem> items = map.get(name);
        assert items != null;
        if (tag != null) {
          for (ResourceItem item : items) {
            assert item instanceof PsiResourceItem;
            PsiResourceItem psiItem = (PsiResourceItem)item;
            if (psiItem.getTag() == tag) {
              return item;
            }
          }
        }

        for (ResourceItem item : items) {
          assert item instanceof PsiResourceItem;
          PsiResourceItem psiItem = (PsiResourceItem)item;
          PsiFile virtualFile = psiItem.getPsiFile();
          if (virtualFile == file) {
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
}

/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.resourceManagers;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.AndroidValueResourcesIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Collections.addAll;

/**
 * @author coyote
 */
public abstract class ResourceManager {
  protected final Project myProject;

  protected ResourceManager(@NotNull Project project) {
    myProject = project;
  }

  /** Returns all the resource directories for this module <b>and all of its module dependencies</b>
   *  grouped by library name. A <code>null</code> string is used for the library name for system, application
   *  and folder resources.
   */
  @NotNull
  public abstract Multimap<String, VirtualFile> getAllResourceDirs();

  /** Returns all the resource directories for this module only */
  @NotNull
  public abstract List<VirtualFile> getResourceDirs();

  /** Returns true if the given directory is a resource directory in this module */
  public abstract boolean isResourceDir(@NotNull VirtualFile dir);

  public boolean processFileResources(@NotNull ResourceFolderType folderType, @NotNull FileResourceProcessor processor) {
    return processFileResources(folderType, processor, true);
  }

  public boolean processFileResources(@NotNull ResourceFolderType folderType, @NotNull FileResourceProcessor processor,
                                      boolean withDependencies) {
    return processFileResources(folderType, processor, withDependencies, true);
  }

  public boolean processFileResources(@NotNull ResourceFolderType folderType, @NotNull FileResourceProcessor processor,
                                       boolean withDependencies, boolean publicOnly) {
    Multimap<String, VirtualFile> resDirs;
    if (withDependencies) {
      resDirs = getAllResourceDirs();
    } else {
      resDirs = HashMultimap.create();
      resDirs.putAll(null, getResourceDirs());
    }

    for (Map.Entry<String, Collection<VirtualFile>> entry : resDirs.asMap().entrySet()) {
      for (VirtualFile resSubdir : AndroidResourceUtil.getResourceSubdirs(folderType,  entry.getValue())) {
        ResourceFolderType resType = ResourceFolderType.getFolderType(resSubdir.getName());

        if (resType != null) {
          assert folderType.equals(resType);
          String resTypeName = resType.getName();
          for (VirtualFile resFile : resSubdir.getChildren()) {
            String resName = AndroidCommonUtils.getResourceName(resTypeName, resFile.getName());

            if (!resFile.isDirectory() && (!publicOnly || isResourcePublic(resTypeName, resName))) {
              if (!processor.process(resFile, resName, entry.getKey())) {
                return false;
              }
            }
          }
        }
      }
    }
    return true;
  }

  @NotNull
  public VirtualFile[] getResourceOverlayDirs() {
    return VirtualFile.EMPTY_ARRAY;
  }

  protected boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    return true;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirs(@NotNull ResourceFolderType resourceType) {
    return AndroidResourceUtil.getResourceSubdirs(resourceType, getAllResourceDirs().values());
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull ResourceFolderType resourceType,
                                         @Nullable String resName,
                                         boolean distinguishDelimetersInName,
                                         @NotNull String... extensions) {
    return findResourceFiles(resourceType, resName, distinguishDelimetersInName, true, extensions);
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull ResourceFolderType resourceFolderType,
                                         @Nullable String resName1,
                                         boolean distinguishDelimitersInName,
                                         boolean withDependencies,
                                         @NotNull String... extensions) {
    List<PsiFile> result = new ArrayList<>();
    Set<String> extensionSet = new HashSet<>();
    addAll(extensionSet, extensions);

    processFileResources(resourceFolderType, (resFile, resName, libraryName) -> {
      String extension = resFile.getExtension();

      if ((extensions.length == 0 || extensionSet.contains(extension)) &&
          (resName1 == null || AndroidUtils.equal(resName1, resName, distinguishDelimitersInName))) {
        PsiFile file = AndroidPsiUtils.getPsiFileSafely(myProject, resFile);
        if (file != null) {
          result.add(file);
        }
      }
      return true;
    }, withDependencies);
    return result;
  }

  @NotNull
  public <T> Multimap<String, T> findResourceFilesByLibraryName(@NotNull ResourceFolderType folderType, @NotNull Class<T> fileClass) {
    Multimap<String, T> result = HashMultimap.create();
    processFileResources(
      folderType,
      (resFile, resName, libraryName) -> {
        PsiFile file = AndroidPsiUtils.getPsiFileSafely(myProject, resFile);
        if (file != null && fileClass.isInstance(file)) {
          result.put(libraryName, fileClass.cast(file));
        }
        return true;
      },
      true);
    return result;
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull ResourceFolderType resourceType) {
    return findResourceFiles(resourceType, null, true);
  }

  protected List<Pair<Resources, VirtualFile>> getResourceElements(@Nullable Set<VirtualFile> files) {
    return getRootDomElements(Resources.class, files);
  }

  private <T extends DomElement> List<Pair<T, VirtualFile>> getRootDomElements(@NotNull Class<T> elementType,
                                                                               @Nullable Set<VirtualFile> files) {
    List<Pair<T, VirtualFile>> result = new ArrayList<>();
    for (VirtualFile file : getAllValueResourceFiles()) {
      if ((files == null || files.contains(file)) && file.isValid()) {
        T element = AndroidUtils.loadDomElement(myProject, file, elementType);
        if (element != null) {
          result.add(Pair.create(element, file));
        }
      }
    }
    return result;
  }

  @NotNull
  protected Set<VirtualFile> getAllValueResourceFiles() {
    Set<VirtualFile> files = new HashSet<>();

    for (VirtualFile valueResourceDir : getResourceSubdirs(ResourceFolderType.VALUES)) {
      for (VirtualFile valueResourceFile : valueResourceDir.getChildren()) {
        if (!valueResourceFile.isDirectory() && valueResourceFile.getFileType().equals(StdFileTypes.XML)) {
          files.add(valueResourceFile);
        }
      }
    }
    return files;
  }

  protected List<ResourceElement> getValueResources(@NotNull ResourceType resourceType, @Nullable Set<VirtualFile> files) {
    List<ResourceElement> result = new ArrayList<>();
    List<Pair<Resources, VirtualFile>> resourceFiles = getResourceElements(files);
    for (Pair<Resources, VirtualFile> pair : resourceFiles) {
      Resources resources = pair.getFirst();
      ApplicationManager.getApplication().runReadAction(() -> {
        if (!resources.isValid() || myProject.isDisposed()) {
          return;
        }
        List<ResourceElement> valueResources = AndroidResourceUtil.getValueResourcesFromElement(resourceType, resources);
        for (ResourceElement valueResource : valueResources) {
          String resName = valueResource.getName().getValue();

          if (resName != null && isResourcePublic(resourceType.getName(), resName)) {
            result.add(valueResource);
          }
        }
      });
    }
    return result;
  }

  @Nullable
  public String getValueResourceType(@NotNull XmlTag tag) {
    ResourceFolderType fileResType = getFileResourceFolderType(tag.getContainingFile());
    if (ResourceFolderType.VALUES == fileResType) {
      return tag.getName();
    }
    return null;
  }

  @Nullable
  public ResourceFolderType getFileResourceFolderType(@NotNull PsiFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ResourceFolderType>() {
      @Nullable
      @Override
      public ResourceFolderType compute() {
        PsiDirectory dir = file.getContainingDirectory();
        if (dir == null) {
          return null;
        }

        PsiDirectory possibleResDir = dir.getParentDirectory();
        if (possibleResDir == null || !isResourceDir(possibleResDir.getVirtualFile())) {
          return null;
        }
        return ResourceFolderType.getFolderType(dir.getName());
      }
    });
  }

  @Nullable
  public String getFileResourceType(@NotNull PsiFile file) {
    ResourceFolderType folderType = getFileResourceFolderType(file);
    return folderType == null ? null : folderType.getName();
  }

  @NotNull
  private Set<String> getFileResourcesNames(@NotNull ResourceFolderType resourceType) {
    Set<String> result = new HashSet<>();

    processFileResources(resourceType, (resFile, resName, libraryName) -> {
      result.add(resName);
      return true;
    });
    return result;
  }

  @NotNull
  public Collection<String> getValueResourceNames(@NotNull ResourceType resourceType) {
    Set<String> result = new HashSet<>();
    boolean attr = ResourceType.ATTR == resourceType;

    for (ResourceEntry entry : getValueResourceEntries(resourceType)) {
      String name = entry.getName();

      if (!attr || !name.startsWith("android:")) {
        result.add(name);
      }
    }
    return result;
  }

  @NotNull
  public Collection<ResourceEntry> getValueResourceEntries(@NotNull ResourceType resourceType) {
    FileBasedIndex index = FileBasedIndex.getInstance();
    ResourceEntry typeMarkerEntry = AndroidValueResourcesIndex.createTypeMarkerKey(resourceType.getName());
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);

    Map<VirtualFile, Set<ResourceEntry>> file2resourceSet = new HashMap<>();

    index.processValues(AndroidValueResourcesIndex.INDEX_ID, typeMarkerEntry, null, new FileBasedIndex.ValueProcessor<ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo>>() {
      @Override
      public boolean process(@NotNull VirtualFile file, ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo> infos) {
        for (AndroidValueResourcesIndex.MyResourceInfo info : infos) {
          Set<ResourceEntry> resourcesInFile = file2resourceSet.get(file);

          if (resourcesInFile == null) {
            resourcesInFile = new HashSet<>();
            file2resourceSet.put(file, resourcesInFile);
          }
          resourcesInFile.add(info.getResourceEntry());
        }
        return true;
      }
    }, scope);

    List<ResourceEntry> result = new ArrayList<>();

    for (VirtualFile file : getAllValueResourceFiles()) {
      Set<ResourceEntry> entries = file2resourceSet.get(file);

      if (entries != null) {
        for (ResourceEntry entry : entries) {
          if (isResourcePublic(entry.getType(), entry.getName())) {
            result.add(entry);
          }
        }
      }
    }
    return result;
  }

  /**
   * Get the collection of resource names that match the given type.
   * @param type the type of resource
   * @return resource names
   */
  @NotNull
  public Collection<String> getResourceNames(@NotNull ResourceType type) {
    return getResourceNames(type, false);
  }

  @NotNull
  public Collection<String> getResourceNames(@NotNull ResourceType resourceType, boolean publicOnly) {
    Set<String> result = new HashSet<>();
    result.addAll(getValueResourceNames(resourceType));

    List<ResourceFolderType> folders = FolderTypeRelationship.getRelatedFolders(resourceType);
    if (!folders.isEmpty()) {
      for (ResourceFolderType folderType : folders) {
        if (folderType != ResourceFolderType.VALUES) {
          result.addAll(getFileResourcesNames(folderType));
        }
      }
    }
    if (resourceType == ResourceType.ID) {
      result.addAll(getIds(true));
    }
    return result;
  }

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  // searches only declarations such as "@+id/..."
  @NotNull
  public List<XmlAttributeValue> findIdDeclarations(@NotNull String id) {
    if (!isResourcePublic(ResourceType.ID.getName(), id)) {
      return Collections.emptyList();
    }

    List<XmlAttributeValue> declarations = new ArrayList<>();
    Collection<VirtualFile> files =
      FileBasedIndex.getInstance().getContainingFiles(AndroidIdIndex.INDEX_ID, "+" + id, GlobalSearchScope.allScope(myProject));
    Set<VirtualFile> fileSet = new HashSet<>(files);
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (VirtualFile subdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile file : subdir.getChildren()) {
        if (fileSet.contains(file)) {
          PsiFile psiFile = psiManager.findFile(file);

          if (psiFile instanceof XmlFile) {
            psiFile.accept(new XmlRecursiveElementVisitor() {
              @Override
              public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
                if (AndroidResourceUtil.isIdDeclaration(attributeValue)) {
                  String idInAttr = AndroidResourceUtil.getResourceNameByReferenceText(attributeValue.getValue());

                  if (id.equals(idInAttr)) {
                    declarations.add(attributeValue);
                  }
                }
              }
            });
          }
        }
      }
    }
    return declarations;
  }

  @NotNull
  public Collection<String> getIds(boolean declarationsOnly) {

    if (myProject.isDisposed()) {
      return Collections.emptyList();
    }
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);

    FileBasedIndex index = FileBasedIndex.getInstance();
    Map<VirtualFile, Set<String>> file2idEntries = new HashMap<>();

    index.processValues(AndroidIdIndex.INDEX_ID, AndroidIdIndex.MARKER, null, new FileBasedIndex.ValueProcessor<Set<String>>() {
      @Override
      public boolean process(@NotNull VirtualFile file, Set<String> value) {
        file2idEntries.put(file, value);
        return true;
      }
    }, scope);

    Set<String> result = new HashSet<>();

    for (VirtualFile resSubdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile resFile : resSubdir.getChildren()) {
        Set<String> idEntries = file2idEntries.get(resFile);

        if (idEntries != null) {
          for (String idEntry : idEntries) {
            if (idEntry.startsWith("+")) {
              idEntry = idEntry.substring(1);
            }
            else if (declarationsOnly) {
              continue;
            }
            if (isResourcePublic(ResourceType.ID.getName(), idEntry)) {
              result.add(idEntry);
            }
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirsToSearchIds() {
    List<VirtualFile> resSubdirs = new ArrayList<>();
    for (ResourceFolderType type : FolderTypeRelationship.getIdGeneratingFolderTypes()) {
      resSubdirs.addAll(getResourceSubdirs(type));
    }
    return resSubdirs;
  }

  public List<ResourceElement> findValueResources(@NotNull String resType, @NotNull String resName) {
    return findValueResources(resType, resName, true);
  }

  // not recommended to use, because it is too slow
  @NotNull
  public List<ResourceElement> findValueResources(@NotNull String resourceType,
                                                  @NotNull String resourceName,
                                                  boolean distinguishDelimitersInName) {
    List<ValueResourceInfoImpl> resources = findValueResourceInfos(resourceType, resourceName, distinguishDelimitersInName, false);
    List<ResourceElement> result = new ArrayList<>();

    for (ValueResourceInfoImpl resource : resources) {
      ResourceElement domElement = resource.computeDomElement();

      if (domElement != null) {
        result.add(domElement);
      }
    }
    return result;
  }

  public void collectLazyResourceElements(@NotNull String resType,
                                          @NotNull String resName,
                                          boolean withAttrs,
                                          @NotNull PsiElement context,
                                          @NotNull Collection<PsiElement> elements) {
    List<ValueResourceInfoImpl> valueResources = findValueResourceInfos(resType, resName, false, withAttrs);

    for (ValueResourceInfo resource : valueResources) {
      elements.add(new LazyValueResourceElementWrapper(resource, context));
    }
    if (resType.equals("id")) {
      elements.addAll(findIdDeclarations(resName));
    }
    if (elements.isEmpty()) {
      ResourceFolderType folderType = ResourceFolderType.getTypeByName(resType);
      if (folderType != null) {
        for (PsiFile file : findResourceFiles(folderType, resName, false)) {
          elements.add(new FileResourceElementWrapper(file));
        }
      }
    }
  }

  @NotNull
  public List<ValueResourceInfoImpl> findValueResourceInfos(@NotNull String resourceType,
                                                            @NotNull String resourceName,
                                                            boolean distinguishDelimetersInName,
                                                            boolean searchAttrs) {
    ResourceType type = resourceType.startsWith("+") ? ResourceType.ID : ResourceType.getEnum(resourceType);
    if (type == null ||
        !AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(type) &&
        (type != ResourceType.ATTR || !searchAttrs)) {
      return Collections.emptyList();
    }
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    List<ValueResourceInfoImpl> result = new ArrayList<>();
    Set<VirtualFile> valueResourceFiles = getAllValueResourceFiles();

    FileBasedIndex.getInstance()
      .processValues(AndroidValueResourcesIndex.INDEX_ID, AndroidValueResourcesIndex.createTypeNameMarkerKey(resourceType, resourceName),
                     null, new FileBasedIndex.ValueProcessor<ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo>>() {
      @Override
      public boolean process(@NotNull VirtualFile file, ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo> infos) {
        for (AndroidValueResourcesIndex.MyResourceInfo info : infos) {
          String name = info.getResourceEntry().getName();

          if (AndroidUtils.equal(resourceName, name, distinguishDelimetersInName)) {
            if (valueResourceFiles.contains(file)) {
              result.add(new ValueResourceInfoImpl(info.getResourceEntry().getName(), type, file, myProject, info.getOffset()));
            }
          }
        }
        return true;
      }
      },
      scope);
    return result;
  }
}

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

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalResourceManager extends ResourceManager {
  private final AndroidFacet myFacet;
  private final Object myAttrDefsLock = new Object();
  @GuardedBy("myAttrDefsLock")
  private AttributeDefinitions myAttrDefs;

  @Nullable
  public static LocalResourceManager getInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? ModuleResourceManagers.getInstance(facet).getLocalResourceManager() : null;
  }

  @Nullable
  public static LocalResourceManager getInstance(@NotNull PsiElement element) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    return facet != null ? ModuleResourceManagers.getInstance(facet).getLocalResourceManager() : null;
  }

  public LocalResourceManager(@NotNull AndroidFacet facet) {
    super(facet.getModule().getProject());
    myFacet = facet;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public boolean isResourceDir(@NotNull VirtualFile dir) {
    for (VirtualFile resDir : ResourceFolderManager.getInstance(myFacet).getFolders()) {
      if (dir.equals(resDir)) {
        return true;
      }
    }
    for (VirtualFile resDir : AndroidRootUtil.getResourceOverlayDirs(myFacet)) {
      if (dir.equals(resDir)) {
        return true;
      }
    }

    return false;
  }

  @Override
  @NotNull
  public AttributeDefinitions getAttributeDefinitions() {
    synchronized (myAttrDefsLock) {
      if (myAttrDefs == null) {
        ResourceManager frameworkResourceManager = ModuleResourceManagers.getInstance(myFacet).getFrameworkResourceManager();
        AttributeDefinitions frameworkAttributes = frameworkResourceManager == null ? null : frameworkResourceManager.getAttributeDefinitions();
        myAttrDefs = AttributeDefinitionsImpl.create(frameworkAttributes, getResourceRepository());
      }
      return myAttrDefs;
    }
  }

  public void invalidateAttributeDefinitions() {
    synchronized (myAttrDefsLock) {
      myAttrDefs = null;
    }
  }

  @NotNull
  public List<Attr> findAttrs(@NotNull ResourceNamespace namespace, @NotNull String name) {
    List<Attr> list = new ArrayList<>();
    List<Resources> rootElements = loadPsiForFilesContainingResource(namespace, ResourceType.ATTR, name);
    for (Resources root : rootElements) {
      for (Attr attr : root.getAttrs()) {
        ResourceReference resourceReference = attr.getName().getValue();
        if (resourceReference!= null && name.equals(resourceReference.getName())) {
          list.add(attr);
        }
      }
      for (DeclareStyleable styleable : root.getDeclareStyleables()) {
        for (Attr attr : styleable.getAttrs()) {
          ResourceReference resourceReference = attr.getName().getValue();
          if (resourceReference!= null && name.equals(resourceReference.getName())) {
            list.add(attr);
          }
        }
      }
    }
    return list;
  }

  public List<DeclareStyleable> findStyleables(@NotNull ResourceNamespace namespace, @NotNull String name) {
    List<DeclareStyleable> list = new ArrayList<>();
    List<Resources> rootElements = loadPsiForFilesContainingResource(namespace, ResourceType.STYLEABLE, name);
    for (Resources root : rootElements) {
      for (DeclareStyleable styleable : root.getDeclareStyleables()) {
        if (name.equals(styleable.getName().getValue())) {
          list.add(styleable);
        }
      }
    }

    return list;
  }

  public List<Attr> findStyleableAttributesByFieldName(@NotNull ResourceNamespace namespace, @NotNull String fieldName) {
    int index = fieldName.lastIndexOf('_');

    // Find the first underscore character where the next character is lower case. In other words, if we have
    // this field name:
    //    Eeny_Meeny_miny_moe
    // we want to assume that the styleableName is "Eeny_Meeny" and the attribute name
    // is "miny_moe".
    while (index >= 0) {
      int prev = fieldName.lastIndexOf('_', index - 1);
      if (prev == -1 || Character.isUpperCase(fieldName.charAt(prev + 1))) {
        break;
      }
      index = prev;
    }

    if (index < 0) {
      return Collections.emptyList();
    }

    String styleableName = fieldName.substring(0, index);
    String attrName = fieldName.substring(index + 1);

    List<Attr> list = new ArrayList<>();
    List<Resources> rootElements = loadPsiForFilesContainingResource(namespace, ResourceType.STYLEABLE, styleableName);
    for (Resources root : rootElements) {
      for (DeclareStyleable styleable : root.getDeclareStyleables()) {
        if (styleableName.equals(styleable.getName().getValue())) {
          for (Attr attr : styleable.getAttrs()) {
            ResourceReference resourceReference = attr.getName().getValue();
            if (resourceReference != null) {
              if (ResourcesUtil.resourceNameToFieldName(resourceReference.getQualifiedName()).equals(attrName) ||
                  resourceReference.getName().equals(attrName)) {
                list.add(attr);
              }
            }
          }
        }
      }
    }

    return list;
  }

  /**
   * Returns a list of root PSI elements for XML files containing definitions of the given resource.
   */
  @NotNull
  private List<Resources> loadPsiForFilesContainingResource(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType,
                                                            @NotNull String name) {
    return getResourceRepository()
        .getResources(namespace, resourceType, name)
        .stream()
        .map(item -> IdeResourcesUtil.getSourceAsVirtualFile(item))
        .filter(file -> Objects.nonNull(file))
        .distinct()
        .map(file -> AndroidUtils.loadDomElement(myProject, file, Resources.class))
        .filter(res -> Objects.nonNull(res))
        .collect(Collectors.toList());
  }

  @NotNull
  public List<PsiElement> findResourcesByField(@NotNull PsiField field) {
    String type = IdeResourcesUtil.getResourceClassName(field);
    if (type == null) {
      return Collections.emptyList();
    }

    String fieldName = field.getName();
    if (fieldName == null) {
      return Collections.emptyList();
    }

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(field);
    if (repositoryManager == null) {
      return Collections.emptyList();
    }
    ResourceNamespace namespace = repositoryManager.getNamespace();
    return findResourcesByFieldName(namespace, type, fieldName);
  }

  @NotNull
  public List<PsiElement> findResourcesByFieldName(@NotNull ResourceNamespace namespace, @NotNull String resClassName,
                                                   @NotNull String fieldName) {
    List<PsiElement> targets = new ArrayList<>();
    if (resClassName.equals(ResourceType.ID.getName())) {
      targets.addAll(findIdDeclarations(namespace, fieldName));
    }

    ResourceFolderType folderType = ResourceFolderType.getTypeByName(resClassName);
    if (folderType != null) {
      targets.addAll(findResourceFiles(namespace, folderType, fieldName, false, true));
    }

    for (ResourceElement element : findValueResources(namespace, resClassName, fieldName, false)) {
      targets.add(element.getName().getXmlAttributeValue());
    }

    if (resClassName.equals(ResourceType.ATTR.getName())) {
      for (Attr attr : findAttrs(namespace, fieldName)) {
        targets.add(attr.getName().getXmlAttributeValue());
      }
    }
    else if (resClassName.equals(ResourceType.STYLEABLE.getName())) {
      for (DeclareStyleable styleable : findStyleables(namespace, fieldName)) {
        targets.add(styleable.getName().getXmlAttributeValue());
      }

      for (Attr attr : findStyleableAttributesByFieldName(namespace, fieldName)) {
        targets.add(attr.getName().getXmlAttributeValue());
      }
    }

    return targets;
  }

  /**
   * Equivalent to calling {@code findResourceFiles(namespace, resourceFolderType, null, true, true)}.
   * @see #findResourceFiles(ResourceNamespace, ResourceFolderType, String, boolean, boolean)
   */
  @NotNull
  public Collection<PsiFile> findResourceFiles(@NotNull ResourceNamespace namespace, @NotNull ResourceFolderType resourceFolderType) {
    return findResourceFiles(namespace, resourceFolderType, null, true, true);
  }

  /**
   * Returns all files containing resource definitions matching the supplied parameters.
   * The returned files include the ones containing overridden resource definitions.
   *
   * @param namespace the namespace of the resources
   * @param resourceFolderType the type of the resource folder
   * @param nameToLookFor the name of the resource, or null to consider all resources matching
   *     {@code namespace} and {@code resourceFolderType}
   * @param distinguishDelimitersInName determines whether delimiters in resource name should be
   *     matched exactly or not
   * @param withDependencies determines whether resources belonging to the module's dependencies
   *     should be included or not
   * @return the files containing resource definitions
   */
  @NotNull
  public Collection<PsiFile> findResourceFiles(@NotNull ResourceNamespace namespace,
                                               @NotNull ResourceFolderType resourceFolderType,
                                               @Nullable String nameToLookFor,
                                               boolean distinguishDelimitersInName,
                                               boolean withDependencies) {
    Set<PsiFile> result = new LinkedHashSet<>();
    ResourceRepository repository =
        withDependencies ? ResourceRepositoryManager.getAppResources(myFacet) : ResourceRepositoryManager.getModuleResources(myFacet);
    Collection<SingleNamespaceResourceRepository> repositories = repository.getLeafResourceRepositories();
    if (resourceFolderType == ResourceFolderType.VALUES) {
      for (ResourceType resourceType : FolderTypeRelationship.getRelatedResourceTypes(resourceFolderType)) {
        findResourceFiles(repositories, namespace, resourceType, resourceFolderType, nameToLookFor, distinguishDelimitersInName, result);
      }
    }
    else {
      ResourceType resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(resourceFolderType);
      findResourceFiles(repositories, namespace, resourceType, resourceFolderType, nameToLookFor, distinguishDelimitersInName, result);
    }

    return result;
  }

  private void findResourceFiles(@NotNull Collection<SingleNamespaceResourceRepository> repositories,
                                 @NotNull ResourceNamespace namespace,
                                 @NotNull ResourceType resourceType,
                                 @NotNull ResourceFolderType resourceFolderType,
                                 @Nullable String nameToLookFor,
                                 boolean distinguishDelimitersInName,
                                 @NotNull Set<PsiFile> result) {
    for (ResourceRepository leafRepository : repositories) {
      Collection<ResourceItem> items;
      if (nameToLookFor == null) {
        items = leafRepository.getResources(namespace, resourceType).values();
      }
      else if (distinguishDelimitersInName) {
        items = leafRepository.getResources(namespace, resourceType, nameToLookFor);
      }
      else {
        items = leafRepository.getResources(namespace, resourceType, item -> AndroidUtils.equal(nameToLookFor, item.getName(), false));
      }

      for (ResourceItem item : items) {
        VirtualFile resFile = IdeResourcesUtil.getSourceAsVirtualFile(item);
        if (resFile != null && ResourceFolderType.getFolderType(resFile.getParent().getName()) == resourceFolderType) {
          PsiFile file = AndroidPsiUtils.getPsiFileSafely(myProject, resFile);
          if (file != null) {
            result.add(file);
          }
        }
      }
    }
  }

  @Override
  @NotNull
  protected Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories() {
    return getResourceRepository().getLeafResourceRepositories();
  }

  @Override
  @NotNull
  protected List<ResourceItem> getResources(
      @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType, @NotNull String resName) {
    return getResourceRepository().getResources(namespace, resourceType, resName);
  }

  @NotNull
  private ResourceRepository getResourceRepository() {
    return ResourceRepositoryManager.getAppResources(myFacet);
  }
}

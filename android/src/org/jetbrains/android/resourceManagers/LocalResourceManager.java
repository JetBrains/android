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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.res.LocalResourceRepository;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import com.android.tools.dom.attrs.AttributeDefinitions;
import com.android.tools.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalResourceManager extends ResourceManager {
  private final AndroidFacet myFacet;
  private volatile CachedValue<AttributeDefinitions> myAttrDefs;

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
    myAttrDefs = createAttributeDefinitions(myFacet, myProject);
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
    return myAttrDefs.getValue();
  }

  public void invalidateAttributeDefinitions() {
    myAttrDefs = createAttributeDefinitions(myFacet, myProject);
  }

  private static CachedValue<AttributeDefinitions> createAttributeDefinitions(AndroidFacet facet, Project project) {
    return CachedValuesManager.getManager(project).createCachedValue(facet, () -> {
      ResourceManager frameworkResourceManager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
      AttributeDefinitions frameworkAttributes = frameworkResourceManager == null ? null : frameworkResourceManager.getAttributeDefinitions();

      LocalResourceRepository<VirtualFile> resourceRepository = StudioResourceRepositoryManager.getAppResources(facet);

      AttributeDefinitions attrDefs = AttributeDefinitionsImpl.create(frameworkAttributes, resourceRepository);

      ModificationTracker resourceRepositoryTracker = resourceRepository::getModificationCount;
      return new CachedValueProvider.Result<>(attrDefs, resourceRepositoryTracker);
    }, false);
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
      withDependencies ? StudioResourceRepositoryManager.getAppResources(myFacet) : StudioResourceRepositoryManager.getModuleResources(myFacet);
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
        items = leafRepository.getResources(namespace, resourceType, item -> AndroidUtils.equalIgnoringDelimiters(nameToLookFor, item.getName()));
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
    return StudioResourceRepositoryManager.getAppResources(myFacet).getLeafResourceRepositories();
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.psi;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.sampledata.SampleDataManager;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResolvableResourceItem;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.android.tools.idea.resources.base.BasicResourceItem;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.ValueResourceInfoImpl;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResourceManagerToPsiResolver implements AndroidResourceToPsiResolver {
  @NotNull public static final ResourceManagerToPsiResolver INSTANCE = new ResourceManagerToPsiResolver();

  private ResourceManagerToPsiResolver() {}

  @Override
  @Nullable
  public PsiElement resolveToDeclaration(@NotNull ResourceItem resourceItem, @NotNull Project project) {
    VirtualFile source = ResourceHelper.getSourceAsVirtualFile(resourceItem);
    if (source == null) {
      return null;
    }

    if (resourceItem.isFileBased()) {
      return PsiManager.getInstance(project).findFile(source);
    }

    if (resourceItem.getType() == ResourceType.ID) {
      XmlAttribute xmlAttribute = AndroidResourceUtil.getIdDeclarationAttribute(project, resourceItem);
      return xmlAttribute == null ? null : xmlAttribute.getValueElement();
    }

    return new ValueResourceInfoImpl(resourceItem, source, project).computeXmlElement();
  }

  @Override
  @NotNull
  public ResolveResult[] resolveReference(@NotNull ResourceValue resourceValue,
                                          @NotNull XmlElement context,
                                          @NotNull AndroidFacet facet) {
    ResourceNamespace resolvedNamespace = ResourceHelper.resolveResourceNamespace(context, resourceValue.getPackage());
    if (resolvedNamespace == null) {
      return ResolveResult.EMPTY_ARRAY;
    }

    boolean attrReference = resourceValue.getPrefix() == '?';

    List<PsiElement> elements = new ArrayList<>();
    if (resourceValue.getType() != null && resourceValue.getResourceName() != null) {
      ResourceManager manager =
        ModuleResourceManagers.getInstance(facet).getResourceManager(resolvedNamespace.getPackageName(), context);
      if (manager != null) {
        manager.collectLazyResourceElements(resolvedNamespace, resourceValue.getType().getName(), resourceValue.getResourceName(),
                                            attrReference, context, elements);
      }
    }

    List<ResolveResult> result = new ArrayList<>();

    // TODO: remove these special cases and just handle all resources in a uniform way.
    if (elements.isEmpty() && resourceValue.getResourceName() != null && resolvedNamespace != ResourceNamespace.ANDROID) {
      // Dynamic items do not appear in the XML scanning file index; look for
      // these in the resource repositories.
      LocalResourceRepository resources = ResourceRepositoryManager.getAppResources(facet.getModule());
      ResourceType resourceType = resourceValue.getType();
      if (resourceType != null && (resourceType != ResourceType.ATTR || attrReference)) {
        // If not, it could be some broken source, such as @android/test.
        assert resources != null;

        String resourceName = resourceValue.getResourceName();
        if (resourceType == ResourceType.SAMPLE_DATA) {
          resourceName = SampleDataManager.getResourceNameFromSampleReference(resourceName);
        }

        List<ResourceItem> items = resources.getResources(resolvedNamespace, resourceType, resourceName);
        if (FolderTypeRelationship.getRelatedFolders(resourceType).contains(ResourceFolderType.VALUES)) {
          for (ResourceItem item : items) {
            if (item instanceof ResolvableResourceItem) {
              result.add(((ResolvableResourceItem)item).createResolveResult());
            }
            else if (item instanceof BasicResourceItem && !((BasicResourceItem)item).isUserDefined()) {
              result.add(new AarResourceResolveResult((BasicResourceItem)item));
            }
            else {
              XmlTag tag = AndroidResourceUtil.getItemTag(facet.getModule().getProject(), item);
              if (tag != null) {
                elements.add(tag);
              }
            }
          }
        }
        else if (resourceType == ResourceType.SAMPLE_DATA && context.getParent() instanceof XmlAttribute) {
          // The mock references can only be applied to "tools:" attributes.
          XmlAttribute attribute = (XmlAttribute)context.getParent();
          if (TOOLS_URI.equals(attribute.getNamespace())) {
            items.stream()
                 .filter(SampleDataResourceItem.class::isInstance)
                 .forEach(sampleDataItem -> result.add(((SampleDataResourceItem)sampleDataItem).createResolveResult()));
          }
        }
      }
    }

    if (elements.size() > 1) {
      elements.sort(AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    }

    for (PsiElement target : elements) {
      result.add(new PsiElementResolveResult(target));
    }

    return result.toArray(ResolveResult.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public PsiElement[] getXmlAttributeNameGotoDeclarationTargets(@NotNull String attributeName,
                                                                @NotNull ResourceNamespace namespace,
                                                                @NotNull PsiElement context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    if (facet == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(facet);
    ResourceRepository repository = namespace.equals(ResourceNamespace.ANDROID) ?
                                    repositoryManager.getFrameworkResources(ImmutableSet.of()) : repositoryManager.getAppResources();
    if (repository == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    ArrayList<PsiElement> elementList = new ArrayList<>();
    for (ResourceItem resourceItem : repository.getResources(namespace, ResourceType.ATTR, attributeName)) {
      VirtualFile file = toVirtualFile(resourceItem.getSource());
      if (file == null) {
        continue;
      }
      elementList.add(
        new LazyValueResourceElementWrapper(new ValueResourceInfoImpl(resourceItem, file, facet.getModule().getProject()), context));
    }
    return elementList.toArray(PsiElement.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public PsiElement[] getGotoDeclarationTargets(@NotNull ResourceReference resourceReference,
                                                @NotNull PsiElement context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    if (facet == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    ResourceType resourceType = resourceReference.getResourceType();
    ResourceNamespace namespace = resourceReference.getNamespace();
    String resourceName = resourceReference.getName();

    List<PsiElement> resourceList = new ArrayList<>();

    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
    ResourceManager manager = namespace == ResourceNamespace.ANDROID
                                  ? resourceManagers.getFrameworkResourceManager(false)
                                  : resourceManagers.getLocalResourceManager();
    if (manager == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    manager.collectLazyResourceElements(namespace,
                                        resourceType.getName(),
                                        resourceName,
                                        true,
                                        context,
                                        resourceList);

    if (manager instanceof LocalResourceManager) {
      LocalResourceManager localManager = (LocalResourceManager)manager;

      if (resourceType.equals(ResourceType.STYLEABLE)) {
        for (DeclareStyleable styleable : localManager.findStyleables(namespace, resourceName)) {
          resourceList.add(styleable.getName().getXmlAttributeValue());
        }

        for (Attr styleable : localManager.findStyleableAttributesByFieldName(namespace, resourceName)) {
          resourceList.add(styleable.getName().getXmlAttributeValue());
        }
      }
    }

    if (resourceList.size() > 1) {
      // Sort to ensure the output is stable, and to prefer the base folders.
      resourceList.sort(AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    }

    return resourceList.toArray(PsiElement.EMPTY_ARRAY);
  }

  private static class AarResourceResolveResult implements ResolveResult {
    @Nullable private final PsiElement myElement;

    AarResourceResolveResult(@NotNull BasicResourceItem resourceItem) {
      // TODO(sprigogin): Parse the attached source and obtain the corresponding element.
      myElement = null;
    }

    @Override
    @Nullable
    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public boolean isValidResult() {
      return false;
    }
  }
}

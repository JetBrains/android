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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.sampledata.SampleDataManager;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResolvableResourceItem;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElementWithRequiredName;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

public class ResourceManagerToPsiResolver implements AndroidResourceToPsiResolver {

  @NotNull
  public static final ResourceManagerToPsiResolver INSTANCE = new ResourceManagerToPsiResolver();

  private ResourceManagerToPsiResolver() {}

  @NotNull
  @Override
  public ResolveResult[] resolveToPsi(@NotNull ResourceValue resourceValue,
                                      @NotNull XmlElement element,
                                      @NotNull AndroidFacet facet) {
    ResourceNamespace resolvedNamespace = ResourceHelper.resolveResourceNamespace(element, resourceValue.getPackage());
    if (resolvedNamespace == null) {
      return ResolveResult.EMPTY_ARRAY;
    }

    boolean attrReference = resourceValue.getPrefix() == '?';

    List<PsiElement> elements = new ArrayList<>();
    if (resourceValue.getType() != null && resourceValue.getResourceName() != null) {
      ResourceManager manager =
        ModuleResourceManagers.getInstance(facet).getResourceManager(resolvedNamespace.getPackageName(), element);
      if (manager != null) {
        manager.collectLazyResourceElements(resolvedNamespace, resourceValue.getType().getName(), resourceValue.getResourceName(),
                                            attrReference, element, elements);
      }
    }

    List<ResolveResult> result = new ArrayList<>();

    // TODO: remove these special cases and just handle all resources in a uniform way.
    if (elements.isEmpty() && resourceValue.getResourceName() != null && resolvedNamespace != ResourceNamespace.ANDROID) {
      // Dynamic items do not appear in the XML scanning file index; look for
      // these in the resource repositories.
      LocalResourceRepository resources = ResourceRepositoryManager.getAppResources(facet.getModule());
      ResourceType resourceType = resourceValue.getType();
      if (resourceType != null && (resourceType != ResourceType.ATTR || attrReference)) { // If not, it could be some broken source, such as @android/test
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
            else {
              XmlTag tag = LocalResourceRepository.getItemTag(facet.getModule().getProject(), item);
              if (tag != null) {
                elements.add(tag);
              }
            }
          }
        }
        else if (resourceType == ResourceType.SAMPLE_DATA && element.getParent() instanceof XmlAttribute) {
          // The mock references can only be applied to tools: attributes
          XmlAttribute attribute = (XmlAttribute)element.getParent();
          if (TOOLS_URI.equals(attribute.getNamespace())) {
            items.stream()
                 .filter(SampleDataResourceItem.class::isInstance)
                 .map(SampleDataResourceItem.class::cast)
                 .forEach(sampleDataItem -> result.add(sampleDataItem.createResolveResult()));
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

  @NotNull
  @Override
  public PsiElement[] getGotoDeclarationTargets(@NotNull AndroidResourceUtil.MyReferredResourceFieldInfo info,
                                                @NotNull PsiReferenceExpression refExpr) {
    AndroidFacet facet = AndroidFacet.getInstance(refExpr);
    if (facet == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    String nestedClassName = info.getClassName();
    String fieldName = info.getFieldName();
    List<PsiElement> resourceList = new ArrayList<>();

    if (info.isFromManifest()) {
      collectManifestElements(nestedClassName, fieldName, facet, resourceList);
    }
    else {
      ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
      ResourceManager manager = info.getNamespace() == ResourceNamespace.ANDROID
                                    ? resourceManagers.getFrameworkResourceManager(false)
                                    : resourceManagers.getLocalResourceManager();
      if (manager == null) {
        return PsiElement.EMPTY_ARRAY;
      }
      manager.collectLazyResourceElements(info.getNamespace(), nestedClassName, fieldName, false, refExpr, resourceList);

      if (manager instanceof LocalResourceManager) {
        LocalResourceManager localManager = (LocalResourceManager)manager;

        if (nestedClassName.equals(ResourceType.ATTR.getName())) {
          for (Attr attr : localManager.findAttrs(info.getNamespace(), fieldName)) {
            resourceList.add(attr.getName().getXmlAttributeValue());
          }
        }
        else if (nestedClassName.equals(ResourceType.STYLEABLE.getName())) {
          for (DeclareStyleable styleable : localManager.findStyleables(info.getNamespace(), fieldName)) {
            resourceList.add(styleable.getName().getXmlAttributeValue());
          }

          for (Attr styleable : localManager.findStyleableAttributesByFieldName(info.getNamespace(), fieldName)) {
            resourceList.add(styleable.getName().getXmlAttributeValue());
          }
        }
      }
    }

    if (resourceList.size() > 1) {
      // Sort to ensure the output is stable, and to prefer the base folders.
      resourceList.sort(AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    }

    return resourceList.toArray(PsiElement.EMPTY_ARRAY);
  }

  private static void collectManifestElements(@NotNull String nestedClassName,
                                              @NotNull String fieldName,
                                              @NotNull AndroidFacet facet,
                                              @NotNull List<PsiElement> result) {
    Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return;
    }
    List<? extends ManifestElementWithRequiredName> list;

    if ("permission".equals(nestedClassName)) {
      list = manifest.getPermissions();
    }
    else if ("permission_group".equals(nestedClassName)) {
      list = manifest.getPermissionGroups();
    }
    else {
      return;
    }
    for (ManifestElementWithRequiredName domElement : list) {
      AndroidAttributeValue<String> nameAttribute = domElement.getName();
      String unqualifiedName = StringUtil.getShortName(StringUtil.notNullize(nameAttribute.getValue()));

      if (AndroidUtils.equal(unqualifiedName, fieldName, false)) {
        XmlElement psiElement = nameAttribute.getXmlAttributeValue();

        if (psiElement != null) {
          result.add(psiElement);
        }
      }
    }
  }
}

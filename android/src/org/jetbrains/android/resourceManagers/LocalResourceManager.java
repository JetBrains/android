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
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalResourceManager extends ResourceManager {
  private AttributeDefinitions myAttrDefs;
  private final AndroidFacet myFacet;

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
  @NotNull
  protected ResourceNamespace getResourceNamespace() {
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(myFacet);
    return repositoryManager.getNamespace();
  }

  @Override
  @NotNull
  public AbstractResourceRepository getResourceRepository() {
    return ProjectResourceRepository.getOrCreateInstance(myFacet);
  }

  @Override
  public boolean isResourceDir(@NotNull VirtualFile dir) {
    for (VirtualFile resDir : getResourceDirs()) {
      if (dir.equals(resDir)) {
        return true;
      }
    }
    for (VirtualFile dir1 : getResourceOverlayDirs()) {
      if (dir.equals(dir1)) {
        return true;
      }
    }

    return false;
  }

  @Override
  @NotNull
  public List<VirtualFile> getResourceDirs() {
    return ResourceFolderManager.getInstance(myFacet).getFolders();
  }

  @NotNull
  @Override
  public Multimap<String, VirtualFile> getAllResourceDirs() {
    return AppResourceRepository.getOrCreateInstance(myFacet).getAllResourceDirs();
  }

  @NotNull
  @Override
  public VirtualFile[] getResourceOverlayDirs() {
    return AndroidRootUtil.getResourceOverlayDirs(getFacet());
  }

  @Override
  @NotNull
  public AttributeDefinitions getAttributeDefinitions() {
    if (myAttrDefs == null) {
      ResourceManager systemResourceManager = ModuleResourceManagers.getInstance(myFacet).getSystemResourceManager();
      AttributeDefinitions systemAttributeDefinitions =
          systemResourceManager == null ? null : systemResourceManager.getAttributeDefinitions();
      ApplicationManager.getApplication().runReadAction(() -> {
        myAttrDefs = new AttributeDefinitionsImpl(systemAttributeDefinitions,
                                                  findResourceFilesByLibraryName(ResourceFolderType.VALUES));
      });
    }
    return myAttrDefs;
  }

  public void invalidateAttributeDefinitions() {
    myAttrDefs = null;
  }

  @NotNull
  public List<Attr> findAttrs(@NotNull String name) {
    List<Attr> list = new ArrayList<>();
    for (Pair<Resources, VirtualFile> pair : getResourceElements()) {
      Resources res = pair.getFirst();
      for (Attr attr : res.getAttrs()) {
        if (name.equals(attr.getName().getValue())) {
          list.add(attr);
        }
      }
      for (DeclareStyleable styleable : res.getDeclareStyleables()) {
        for (Attr attr : styleable.getAttrs()) {
          if (name.equals(attr.getName().getValue())) {
            list.add(attr);
          }
        }
      }
    }
    return list;
  }

  public List<DeclareStyleable> findStyleables(@NotNull String name) {
    List<DeclareStyleable> list = new ArrayList<>();
    for (Pair<Resources, VirtualFile> pair : getResourceElements()) {
      Resources res = pair.getFirst();
      for (DeclareStyleable styleable : res.getDeclareStyleables()) {
        if (name.equals(styleable.getName().getValue())) {
          list.add(styleable);
        }
      }
    }

    return list;
  }

  public List<Attr> findStyleableAttributesByFieldName(@NotNull String fieldName) {
    int index = fieldName.lastIndexOf('_');

    // Find the earlier _ where the next character is lower case. In other words, if we have
    // this field name:
    //    Eeny_Meeny_miny_moe
    // we want to assume that the styleableName is "Eeny_Meeny" and the attribute name
    // is "miny_moe".
    while (index != -1) {
      int prev = fieldName.lastIndexOf('_', index - 1);
      if (prev == -1 || Character.isUpperCase(fieldName.charAt(prev + 1))) {
        break;
      }
      index = prev;
    }

    if (index == -1) {
      return Collections.emptyList();
    }

    String styleableName = fieldName.substring(0, index);
    String attrName = fieldName.substring(index + 1);

    List<Attr> list = new ArrayList<>();
    for (Pair<Resources, VirtualFile> pair : getResourceElements()) {
      Resources res = pair.getFirst();
      for (DeclareStyleable styleable : res.getDeclareStyleables()) {
        if (styleableName.equals(styleable.getName().getValue())) {
          for (Attr attr : styleable.getAttrs()) {
            if (attrName.equals(attr.getName().getValue())) {
              list.add(attr);
            }
          }
        }
      }
    }

    return list;
  }

  @NotNull
  public List<PsiElement> findResourcesByField(@NotNull PsiField field) {
    String type = AndroidResourceUtil.getResourceClassName(field);
    if (type == null) {
      return Collections.emptyList();
    }

    String fieldName = field.getName();
    if (fieldName == null) {
      return Collections.emptyList();
    }
    return findResourcesByFieldName(type, fieldName);
  }

  @NotNull
  public List<PsiElement> findResourcesByFieldName(@NotNull String resClassName, @NotNull String fieldName) {
    ResourceNamespace namespace = getResourceNamespace();
    List<PsiElement> targets = new ArrayList<>();
    if (resClassName.equals(ResourceType.ID.getName())) {
      targets.addAll(findIdDeclarations(namespace, fieldName));
    }
    ResourceFolderType folderType = ResourceFolderType.getTypeByName(resClassName);
    if (folderType != null) {
      targets.addAll(findResourceFiles(folderType, fieldName, false, true));
    }
    for (ResourceElement element : findValueResources(namespace, resClassName, fieldName, false)) {
      targets.add(element.getName().getXmlAttributeValue());
    }
    if (resClassName.equals(ResourceType.ATTR.getName())) {
      for (Attr attr : findAttrs(fieldName)) {
        targets.add(attr.getName().getXmlAttributeValue());
      }
    }
    else if (resClassName.equals(ResourceType.STYLEABLE.getName())) {
      for (DeclareStyleable styleable : findStyleables(fieldName)) {
        targets.add(styleable.getName().getXmlAttributeValue());
      }

      for (Attr attr : findStyleableAttributesByFieldName(fieldName)) {
        targets.add(attr.getName().getXmlAttributeValue());
      }
    }
    return targets;
  }
}

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

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class LocalResourceManager extends ResourceManager {
  private AttributeDefinitions myAttrDefs;
  protected final AndroidFacet myFacet;

  public LocalResourceManager(@NotNull AndroidFacet facet) {
    super(facet.getModule().getProject());
    myFacet = facet;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  /**
   * Gets all resource directories reachable from the facet (modules and libraries).
   * @return resource directories
   */
  @NotNull
  @Override
  public VirtualFile[] getAllResourceDirs() {
    Set<VirtualFile> result = AppResourceRepository.getAppResources(myFacet, true).getResourceDirs();
    return VfsUtilCore.toVirtualFileArray(result);
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
    return myFacet.getAllResourceDirectories();
  }

  public List<Pair<Resources, VirtualFile>> getResourceElements() {
    return getResourceElements(null);
  }

  @NotNull
  @Override
  public VirtualFile[] getResourceOverlayDirs() {
    return AndroidRootUtil.getResourceOverlayDirs(getFacet());
  }

  @NotNull
  public List<ResourceElement> getValueResources(@NotNull final ResourceType resourceType) {
    return getValueResources(resourceType, null);
  }

  @Nullable
  public static LocalResourceManager getInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? facet.getLocalResourceManager() : null;
  }

  @Nullable
  public static LocalResourceManager getInstance(@NotNull PsiElement element) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    return facet != null ? facet.getLocalResourceManager() : null;
  }

  @Override
  @NotNull
  public AttributeDefinitions getAttributeDefinitions() {
    if (myAttrDefs == null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          List<XmlFile> xmlResFiles = new ArrayList<XmlFile>();
          for (PsiFile file : findResourceFiles("values")) {
            if (file instanceof XmlFile) {
              xmlResFiles.add((XmlFile)file);
            }
          }
          myAttrDefs = new AttributeDefinitionsImpl(xmlResFiles.toArray(new XmlFile[xmlResFiles.size()]));
        }
      });
    }
    return myAttrDefs;
  }

  public void invalidateAttributeDefinitions() {
    myAttrDefs = null;
  }

  @NotNull
  public List<Attr> findAttrs(@NotNull String name) {
    List<Attr> list = new ArrayList<Attr>();
    for (Pair<Resources, VirtualFile> pair : getResourceElements()) {
      final Resources res = pair.getFirst();
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
    List<DeclareStyleable> list = new ArrayList<DeclareStyleable>();
    for (Pair<Resources, VirtualFile> pair : getResourceElements()) {
      final Resources res = pair.getFirst();
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

    List<Attr> list = new ArrayList<Attr>();
    for (Pair<Resources, VirtualFile> pair : getResourceElements()) {
      final Resources res = pair.getFirst();
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
    final String type = AndroidResourceUtil.getResourceClassName(field);
    if (type == null) {
      return Collections.emptyList();
    }

    final String fieldName = field.getName();
    if (fieldName == null) {
      return Collections.emptyList();
    }
    return findResourcesByFieldName(type, fieldName);
  }

  @NotNull
  public List<PsiElement> findResourcesByFieldName(@NotNull String resClassName, @NotNull String fieldName) {
    List<PsiElement> targets = new ArrayList<PsiElement>();
    if (resClassName.equals(ResourceType.ID.getName())) {
      targets.addAll(findIdDeclarations(fieldName));
    }
    for (PsiFile file : findResourceFiles(resClassName, fieldName, false)) {
      targets.add(file);
    }
    for (ResourceElement element : findValueResources(resClassName, fieldName, false)) {
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

  @Override
  @NotNull
  public Collection<String> getResourceNames(@NotNull ResourceType resourceType, boolean publicOnly) {
    AppResourceRepository appResources = AppResourceRepository.getAppResources(myFacet, true);
    Collection<String> resourceNames;
    if (resourceType == ResourceType.STYLEABLE) {
      // Convert from the tag-oriented types that appResource hold to the inner-class oriented type.
      resourceNames = appResources.getItemsOfType(ResourceType.DECLARE_STYLEABLE);
    } else {
      resourceNames = appResources.getItemsOfType(resourceType);
    }
    // We may need to filter out public only, or if the type is attr, filter out android: attributes.
    if (publicOnly || resourceType == ResourceType.ATTR) {
      Set<String> filtered = ContainerUtil.newHashSet(resourceNames.size());
      for (String name : resourceNames) {
        if (resourceType == ResourceType.ATTR) {
          if (!name.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX)) {
            filtered.add(name);
          }
        }
        if (publicOnly) {
          if (!appResources.isPrivate(resourceType, name)) {
            filtered.add(name);
          }
        }
      }
      resourceNames = filtered;
    }
    return resourceNames;
  }
}

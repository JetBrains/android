/*
 * Copyright 2010-2015 JetBrains s.r.o.
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
package org.jetbrains.kotlin.android.navigation;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElementWithRequiredName;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtSimpleNameExpression;

// TODO: ask for extension point
// this class is mostly copied from org.jetbrains.android.AndroidGotoDeclarationHandler
public class KotlinAndroidGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    KtSimpleNameExpression referenceExpression = GotoResourceHelperKt.getReferenceExpression(sourceElement);
    if (referenceExpression == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(referenceExpression);
    if (facet == null) {
      return null;
    }

    AndroidResourceUtil.MyReferredResourceFieldInfo info = GotoResourceHelperKt.getInfo(referenceExpression, facet);

    if (info == null) {
      return null;
    }

    String nestedClassName = info.getClassName();
    String fieldName = info.getFieldName();
    List<PsiElement> resourceList = new ArrayList<>();

    if (info.isFromManifest()) {
      collectManifestElements(nestedClassName, fieldName, facet, resourceList);
    }
    else {
      ModuleResourceManagers managers = ModuleResourceManagers.getInstance(facet);
      ResourceManager manager = info.getNamespace() == ResourceNamespace.ANDROID
                                ? managers.getFrameworkResourceManager(false)
                                : managers.getLocalResourceManager();
      if (manager == null) {
        return null;
      }
      manager.collectLazyResourceElements(info.getNamespace(), nestedClassName, fieldName, false, referenceExpression, resourceList);

      if (manager instanceof LocalResourceManager) {
        LocalResourceManager lrm = (LocalResourceManager) manager;

        if (nestedClassName.equals(ResourceType.ATTR.getName())) {
          for (Attr attr : lrm.findAttrs(info.getNamespace(), fieldName)) {
            resourceList.add(attr.getName().getXmlAttributeValue());
          }
        }
        else if (nestedClassName.equals(ResourceType.STYLEABLE.getName())) {
          for (DeclareStyleable styleable : lrm.findStyleables(info.getNamespace(), fieldName)) {
            resourceList.add(styleable.getName().getXmlAttributeValue());
          }

          for (Attr styleable : lrm.findStyleableAttributesByFieldName(info.getNamespace(), fieldName)) {
            resourceList.add(styleable.getName().getXmlAttributeValue());
          }
        }
      }
    }

    if (resourceList.size() > 1) {
      // Sort to ensure the output is stable, and to prefer the base folders
      Collections.sort(resourceList, AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
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
      String name = nameAttribute.getValue();

      if (AndroidUtils.equal(name, fieldName, false)) {
        XmlElement psiElement = nameAttribute.getXmlAttributeValue();

        if (psiElement != null) {
          result.add(psiElement);
        }
      }
    }
  }

  @Override
  public String getActionText(DataContext context) {
    return null;
  }
}

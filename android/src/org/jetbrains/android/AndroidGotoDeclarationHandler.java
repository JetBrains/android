/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.android.resources.ResourceType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (!(sourceElement instanceof PsiIdentifier)) {
      return null;
    }
    final PsiFile file = sourceElement.getContainingFile();

    if (file == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    final PsiReferenceExpression refExp = PsiTreeUtil.getParentOfType(sourceElement, PsiReferenceExpression.class);
    if (refExp == null) {
      return null;
    }

    AndroidResourceUtil.MyReferredResourceFieldInfo info = AndroidResourceUtil.getReferredResourceOrManifestField(facet, refExp, false);
    if (info == null) {
      PsiElement parent = refExp.getParent();
      if (parent instanceof PsiReferenceExpression) {
        info = AndroidResourceUtil.getReferredResourceOrManifestField(facet, (PsiReferenceExpression)parent, false);
      }
      if (info == null) {
        parent = parent.getParent();
        if (parent instanceof PsiReferenceExpression) {
          info = AndroidResourceUtil.getReferredResourceOrManifestField(facet, (PsiReferenceExpression)parent, false);
        }
      }
    }
    if (info == null) {
      return null;
    }
    final String nestedClassName = info.getClassName();
    final String fieldName = info.getFieldName();
    final List<PsiElement> resourceList = new ArrayList<>();

    if (info.isFromManifest()) {
      collectManifestElements(nestedClassName, fieldName, facet, resourceList);
    }
    else {
      ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
      final ResourceManager manager = info.isSystem()
                                      ? resourceManagers.getSystemResourceManager(false)
                                      : resourceManagers.getLocalResourceManager();
      if (manager == null) {
        return null;
      }
      manager.collectLazyResourceElements(nestedClassName, fieldName, false, refExp, resourceList);

      if (manager instanceof LocalResourceManager) {
        final LocalResourceManager lrm = (LocalResourceManager)manager;

        if (nestedClassName.equals(ResourceType.ATTR.getName())) {
          for (Attr attr : lrm.findAttrs(fieldName)) {
            resourceList.add(attr.getName().getXmlAttributeValue());
          }
        }
        else if (nestedClassName.equals(ResourceType.STYLEABLE.getName())) {
          for (DeclareStyleable styleable : lrm.findStyleables(fieldName)) {
            resourceList.add(styleable.getName().getXmlAttributeValue());
          }

          for (Attr styleable : lrm.findStyleableAttributesByFieldName(fieldName)) {
            resourceList.add(styleable.getName().getXmlAttributeValue());
          }
        }
      }
    }

    if (resourceList.size() > 1) {
      // Sort to ensure the output is stable, and to prefer the base folders
      resourceList.sort(AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    }

    return resourceList.toArray(new PsiElement[resourceList.size()]);
  }

  private static void collectManifestElements(@NotNull String nestedClassName,
                                              @NotNull String fieldName,
                                              @NotNull AndroidFacet facet,
                                              @NotNull List<PsiElement> result) {
    final Manifest manifest = facet.getManifest();

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
      final AndroidAttributeValue<String> nameAttribute = domElement.getName();
      final String name = nameAttribute.getValue();

      if (AndroidUtils.equal(name, fieldName, false)) {
        final XmlElement psiElement = nameAttribute.getXmlAttributeValue();

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

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElementWithRequiredName;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 *
 * Cases covered by this handler:
 * <ul>
 *  <li>Java resource reference in the form "R.type.name"</li>
 *  <li>A reference in XML that resolves to a ResourceReferencePsiElement. This requires a custom gotoDeclarationHandler as a resource may
 *  be declared in multiple locations, however a PsiElement can only have one NavigationElement.</li>
 * </ul>
 */
public class AndroidGotoDeclarationHandler implements GotoDeclarationHandler {

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (sourceElement != null && sourceElement.getLanguage().is(XMLLanguage.INSTANCE)) {
      PsiElement targetElement =
        TargetElementUtil.getInstance().findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, offset);
      if (targetElement == null) {
        return null;
      }
      if (targetElement instanceof ResourceReferencePsiElement) {
        return AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(
          ((ResourceReferencePsiElement)targetElement).getResourceReference(), sourceElement);
      }
    }
    if (!(sourceElement instanceof PsiIdentifier)) {
      return null;
    }
    PsiFile file = sourceElement.getContainingFile();

    if (file == null) {
      return null;
    }

    PsiReferenceExpression refExp = PsiTreeUtil.getParentOfType(sourceElement, PsiReferenceExpression.class);
    if (refExp == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
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

    if (info.isFromManifest()) {
      String nestedClassName = info.getClassName();
      String fieldName = info.getFieldName();
      return collectManifestElements(nestedClassName, fieldName, facet);
    }

    ResourceType resourceType = ResourceType.fromClassName(info.getClassName());
    if (resourceType == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    return AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(
      new ResourceReference(info.getNamespace(), resourceType, info.getFieldName()), refExp);
  }

  @NotNull
  private static PsiElement[] collectManifestElements(@NotNull String nestedClassName,
                                                      @NotNull String fieldName,
                                                      @NotNull AndroidFacet facet) {
    List<PsiElement> result = new ArrayList<>();
    Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    List<? extends ManifestElementWithRequiredName> list;

    if ("permission".equals(nestedClassName)) {
      list = manifest.getPermissions();
    }
    else if ("permission_group".equals(nestedClassName)) {
      list = manifest.getPermissionGroups();
    }
    else {
      return PsiElement.EMPTY_ARRAY;
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

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }
}

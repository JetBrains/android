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

import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (!(sourceElement instanceof PsiIdentifier)) {
      return null;
    }
    PsiFile file = sourceElement.getContainingFile();

    if (file == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    PsiReferenceExpression refExp = PsiTreeUtil.getParentOfType(sourceElement, PsiReferenceExpression.class);
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

    return AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(info, refExp);
  }

  @Override
  public String getActionText(DataContext context) {
    return null;
  }
}

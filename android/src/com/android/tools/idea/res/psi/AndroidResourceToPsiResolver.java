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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

public interface AndroidResourceToPsiResolver {

  /**
   * Resolves a given {@ResourceValue} to PSI, in the context of the given {@link XmlElement} and {@link AndroidFacet}.
   *
   * <p>Used by PSI references in resources XML.
   *
   * @param resourceValue {@link ResourceValue} to resolve
   * @param element {@link XmlElement} the reference being resolved came from
   * @param facet {@link AndroidFacet} associated with the reference being resolved. For files outside of the project (e.g. framework
   *              sources) this is what {@link com.intellij.openapi.module.ModuleUtilCore#findModuleForPsiElement(PsiElement)} chose for the
   *              file (i.e. a module that depends on the file and has the least dependencies).
   */
  @NotNull
  ResolveResult[] resolveToPsi(@NotNull ResourceValue resourceValue,
                               @NotNull XmlElement element,
                               @NotNull AndroidFacet facet);

  /**
   * Returns the {@link PsiElement}s for "go to declaration" action on XML attributes names.
   */
  @NotNull
  PsiElement[] getXmlAttributeNameGotoDeclarationTargets(@NotNull String attributeName,
                                                         @NotNull ResourceNamespace namespace,
                                                         @NotNull PsiElement context,
                                                         @NotNull AndroidFacet facet);

  /**
   * Returns the {@link PsiElement}s for "go to declaration" on fields of R and Manifest classes.
   */
  @NotNull
  PsiElement[] getGotoDeclarationTargets(@NotNull AndroidResourceUtil.MyReferredResourceFieldInfo fieldInfo,
                                         @NotNull PsiReferenceExpression refExpr);

  static AndroidResourceToPsiResolver getInstance() {
    return StudioFlags.RESOLVE_USING_REPOS.get() ? ResourceRepositoryToPsiResolver.INSTANCE : ResourceManagerToPsiResolver.INSTANCE;
  }
}

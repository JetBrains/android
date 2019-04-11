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
package com.android.tools.idea.res.psi

import com.android.ide.common.rendering.api.ResourceNamespace
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.ResolveResult
import com.intellij.psi.xml.XmlElement
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil

object ResourceRepositoryToPsiResolver : AndroidResourceToPsiResolver {
  override fun getXmlAttributeNameGotoDeclarationTargets(attributeName: String,
                                                         namespace: ResourceNamespace,
                                                         context: PsiElement,
                                                         facet: AndroidFacet): Array<PsiElement> {
    TODO("not implemented")
  }

  override fun resolveToPsi(resourceValue: ResourceValue,
                            element: XmlElement,
                            facet: AndroidFacet): Array<out ResolveResult> {
    TODO("not implemented")
  }

  override fun getGotoDeclarationTargets(fieldInfo: AndroidResourceUtil.MyReferredResourceFieldInfo,
                                         refExpr: PsiReferenceExpression): Array<PsiElement> {
    TODO("not implemented")
  }
}

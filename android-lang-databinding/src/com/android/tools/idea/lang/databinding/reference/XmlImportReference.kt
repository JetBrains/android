/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.res.binding.BindingLayoutXml
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.xml.XmlTag

/**
 * Reference that points to an <import> tag in a layout XML file.
 */
internal class XmlImportReference(element: PsiElement,
                                  resolveTo: XmlTag,
                                  private val import: BindingLayoutXml.Import,
                                  private val module: Module) : DbExprReference(element, resolveTo) {
  override val resolvedType: PsiModelClass?
    get() {
      val project = element.project
      return import.type
        .let { type -> JavaPsiFacade.getInstance(project).findClass(type, module.getModuleWithDependenciesAndLibrariesScope(false)) }
        ?.let { psiType -> PsiModelClass(PsiTypesUtil.getClassType(psiType), DataBindingMode.fromPsiElement(element)) }
    }

  override val isStatic: Boolean
    get() = true
}

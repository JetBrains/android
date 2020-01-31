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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

/**
 * Reference that points to an attribute in a layout XML file.
 */
internal class XmlAttributeReference(element: PsiElement,
                                     resolveTo: XmlAttribute) : DbExprReference(element, resolveTo) {
  override val resolvedType: PsiModelClass?
    get() {
      val tag = (resolve() as XmlAttribute).parentOfType<XmlTag>() ?: return null
      val viewClass = tag.references.firstNotNullResult { it.resolve() as? PsiClass } ?: return null
      return PsiModelClass(PsiTypesUtil.getClassType(viewClass), DataBindingMode.fromPsiElement(element))
    }

  override val isStatic: Boolean
    get() = false
}

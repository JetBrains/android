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

import com.android.tools.idea.databinding.util.DataBindingUtil.stripPrefixFromMethod
import com.android.tools.idea.lang.databinding.model.PsiModelMethod
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbLambdaParameters
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.xml.XmlAttribute

/**
 * Reference that refers to a [PsiMethod]
 */
internal class PsiMethodReference(element: PsiElement, method: PsiModelMethod, textRange: TextRange)
  : DbExprReference(element, method.psiMethod, textRange) {

  constructor(expr: PsiDbCallExpr, method: PsiModelMethod)
    : this(expr, method, expr.refExpr.id.textRange.shiftLeft(expr.textOffset))

  constructor(expr: PsiDbRefExpr, method: PsiModelMethod)
    : this(expr, method, expr.id.textRange.shiftLeft(expr.textOffset))

  constructor(expr: PsiDbFunctionRefExpr, method: PsiModelMethod)
    : this(expr, method, expr.id.textRange.shiftLeft(expr.textOffset))

  constructor(attr: XmlAttribute, method: PsiModelMethod)
    : this(attr, method, attr.textRange.shiftLeft(attr.textOffset))

  constructor(parameters: PsiDbLambdaParameters, method: PsiModelMethod)
    : this(parameters, method, parameters.textRange.shiftLeft(parameters.textOffset))

  override val resolvedType = method.returnType

  override val isStatic = false

  override fun handleElementRename(newElementName: String): PsiElement? {
    val identifier = element.findElementAt(rangeInElement.startOffset) as? LeafPsiElement ?: return null
    val resolved = resolve() as? PsiMethod ?: return null

    // Create a light clone of our method so we can call the `stripPrefix` method on it
    val lightMethod = LightMethodBuilder(resolved.manager, resolved.language, newElementName, resolved.parameterList, resolved.modifierList)
      .setMethodReturnType(resolved.returnType)

    // Say the user tries to rename a method as if it were a getter, e.g. "getName". We normally
    // transform those sorts of methods to make them look like a field in data binding expressions,
    // so we apply that same logic here as well, for consistency.
    val stripped = stripPrefixFromMethod(lightMethod)

    identifier.rawReplaceWithText(stripped)
    return identifier
  }
}

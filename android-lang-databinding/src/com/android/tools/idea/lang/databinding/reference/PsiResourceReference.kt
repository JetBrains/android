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
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.android.dom.resources.ResourceValue

/**
 * Reference that refers to a XML resource.
 */
internal class PsiResourceReference(element: PsiElement, resolveTo: PsiElement, private val resourceValue: ResourceValue)
  : DbExprReference(element, resolveTo) {
  override val resolvedType: PsiModelClass?
    get() {
      val psiType = when (resourceValue.resourceType) {
                      // A plurals resource, e.g. @plurals/dog -> "dog" or "dogs", can be queried for the
                      // underlying String value (e.g. "@plurals/dog(2)") or for the plurals resource ID
                      // directly (e.g. "@plurals/dog").
                      "plurals" -> if (element.children.isEmpty()) PsiTypes.intType() else parseType("java.lang.String")
                      "anim" -> parseType("android.view.animation.Animation")
                      "animator" -> parseType("android.animation.Animator")
                      "colorStateList" -> parseType("android.content.res.ColorStateList")
                      "drawable" -> parseType("android.graphics.drawable.Drawable")
                      "stateListAnimator" -> parseType("android.animation.StateListAnimator")
                      "transition" -> parseType("android.transition.Transition")
                      "typedArray" -> parseType("android.content.res.TypedArray")
                      "interpolator" -> parseType("android.view.animation.Interpolator")
                      "bool" -> PsiTypes.booleanType()
                      "color", "dimenOffset", "dimenSize", "id", "integer", "layout" -> PsiTypes.intType()
                      "dimen", "fraction" -> PsiTypes.floatType()
                      "intArray" -> PsiTypes.intType().createArrayType()
                      "string" -> parseType("java.lang.String")
                      "stringArray" -> parseType("java.lang.String")?.createArrayType()
                      "text" -> parseType("java.lang.CharSequence")
                      else -> null
                    } ?: return null
      return PsiModelClass(psiType, DataBindingMode.fromPsiElement(element))
    }
  override val memberAccess = PsiModelClass.MemberAccess.ALL_MEMBERS

  private fun parseType(name: String) = LayoutBindingTypeUtil.parsePsiType(name, element)

  override fun handleElementRename(newElementName: String): PsiElement? {
    val identifier = element.findElementAt(rangeInElement.startOffset) as? LeafPsiElement ?: return null
    val newResourceValue = ResourceValue.referenceTo(
      resourceValue.prefix,
      resourceValue.`package`,
      resourceValue.resourceType,
      newElementName
    )
    identifier.rawReplaceWithText(newResourceValue.toString())
    return element
  }
}

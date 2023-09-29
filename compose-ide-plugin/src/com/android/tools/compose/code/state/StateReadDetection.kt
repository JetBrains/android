/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.code.state

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.descendants
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.idea.structuralsearch.resolveExprType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

private const val FQNAME = "androidx.compose.runtime.State"
private val CLASS_ID_OF_STATE = ClassId.topLevel(FqName(FQNAME))

internal fun createMessage(stateVariable: String, composable: String) =
  "State read: when the value of \"$stateVariable\" changes, \"$composable\" will recompose."

internal fun KtNameReferenceExpression.getStateReadElement(): PsiElement? =
  CachedValuesManager.getProjectPsiDependentCache(this) { it.computeStateReadElement() }

private fun KtNameReferenceExpression.computeStateReadElement(): PsiElement? {
  if (isAssignee()) return null
  if (isImplicitStateRead()) return this
  return getExplicitStateReadElement()
}

/**
 * Returns the element representing the State variable being read, if any.
 *
 * e.g. for `foo.bar.baz.value`, will return the [PsiElement] for `baz`.
 */
private fun KtNameReferenceExpression.getExplicitStateReadElement(): PsiElement? {
  if (text != "value") return null
  return (parent as? KtDotQualifiedExpression)
    ?.takeIf { it.selectorExpression == this }
    ?.receiverExpression
    ?.takeIf { it.isStateType() }
    ?.let {
      when (it) {
        is KtDotQualifiedExpression -> it.selectorExpression
        else -> it
      }
    }
}

/**
 * Returns whether the expression represents an implicit call to `State#getValue`, i.e. if the
 * expression is for a delegated property where the delegate is of type `State`.
 *
 * E.g. for a name reference expression `foo` if `foo` is defined as:
 *
 * `val foo by stateOf(...)`
 */
private fun KtNameReferenceExpression.isImplicitStateRead(): Boolean {
  return (resolveMainReference() as? KtProperty)?.delegateExpression?.isStateType() ?: false
}

private fun KotlinType.isStateType() =
  (fqName?.asString() == FQNAME || supertypes().any { it.fqName?.asString() == FQNAME })

private fun KtAnalysisSession.isStateType(type: KtType): Boolean =
  if (type is KtNonErrorClassType) {
    type.classId == CLASS_ID_OF_STATE ||
      type.getAllSuperTypes().any { it is KtNonErrorClassType && it.classId == CLASS_ID_OF_STATE }
  } else {
    false
  }

@OptIn(KtAllowAnalysisOnEdt::class)
private fun KtExpression.isStateType(): Boolean =
  if (isK2Plugin()) {
    allowAnalysisOnEdt { analyze(this) { getKtType()?.let { isStateType(it) } ?: false } }
  } else {
    resolveExprType()?.isStateType() ?: false
  }

private fun KtNameReferenceExpression.isAssignee(): Boolean {
  return parentOfType<KtBinaryExpression>()
    ?.takeIf { it.operationToken.toString() == "EQ" }
    ?.let { it.left == this || it.left?.descendants()?.contains(this) == true } ?: false
}

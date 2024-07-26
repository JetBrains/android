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

import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.composableScope
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.descendants
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.idea.structuralsearch.resolveExprType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

private val STATE_TYPES = setOf("", "Int", "Long", "Float", "Double")
private val STATE_CLASSES_BY_ACCESSOR =
  STATE_TYPES.associate {
    "${it}Value".replaceFirstChar(Char::lowercase) to
      ClassId.topLevel(FqName("androidx.compose.runtime.${it}State"))
  }
private val GENERIC_STATE_CLASS_ID =
  checkNotNull(STATE_CLASSES_BY_ACCESSOR["value"]) { "No class ID for generic State class!" }

internal data class StateRead(
  val stateVar: KtExpression,
  val scope: KtExpression,
  val scopeName: String,
) {
  companion object {
    fun create(stateVar: KtExpression, scope: KtExpression): StateRead? {
      val scopeName =
        when (scope) {
          is KtLambdaExpression ->
            ComposeBundle.message("state.read.recompose.target.enclosing.lambda")
          is KtPropertyAccessor ->
            if (scope.isGetter) "${scope.property.name}.get()" else return null
          is KtNamedFunction ->
            scope.name
              ?: ComposeBundle.message("state.read.recompose.target.enclosing.anonymous.function")
          else -> scope.name ?: return null
        }
      val bodyScope = (scope as? KtDeclarationWithBody)?.bodyExpression ?: scope
      return StateRead(stateVar, bodyScope, scopeName)
    }
  }
}

internal fun KtNameReferenceExpression.getStateRead(): StateRead? =
  CachedValuesManager.getProjectPsiDependentCache(this) { it.computeStateRead() }

private fun KtNameReferenceExpression.computeStateRead(): StateRead? {
  val scope = composableScope() ?: return null
  val element = computeStateReadElement() ?: return null
  return StateRead.create(element, scope)
}

private fun KtNameReferenceExpression.computeStateReadElement(): KtExpression? {
  if (isAssignee()) return null
  if (isImplicitStateRead()) return this
  return getExplicitStateReadElement()
}

/**
 * Returns the element representing the State variable being read, if any.
 *
 * e.g. for `foo.bar.baz.value`, will return the [PsiElement] for `baz`.
 */
private fun KtNameReferenceExpression.getExplicitStateReadElement(): KtExpression? {
  val stateClassId = STATE_CLASSES_BY_ACCESSOR[text] ?: return null
  return (parent as? KtDotQualifiedExpression)
    ?.takeIf { it.selectorExpression == this }
    ?.receiverExpression
    ?.takeIf { it.isStateType(stateClassId) }
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
private fun KtNameReferenceExpression.isImplicitStateRead(): Boolean =
  (resolveMainReference() as? KtProperty)?.delegateExpression?.isStateType(GENERIC_STATE_CLASS_ID)
    ?: false

private fun KotlinType.isStateType(stateTypeFqName: String) =
  (fqName?.asString() == stateTypeFqName ||
    supertypes().any { it.fqName?.asString() == stateTypeFqName })

private fun KtAnalysisSession.isStateType(type: KtType, stateClassId: ClassId): Boolean =
  type is KtNonErrorClassType &&
    (type.classId == stateClassId ||
      type.getAllSuperTypes().any { it is KtNonErrorClassType && it.classId == stateClassId })

@OptIn(KaAllowAnalysisOnEdt::class)
private fun KtExpression.isStateType(stateClassId: ClassId): Boolean =
  if (KotlinPluginModeProvider.isK2Mode()) {
    allowAnalysisOnEdt {
      analyze(this) { getKtType()?.let { isStateType(it, stateClassId) } ?: false }
    }
  } else {
    resolveExprType()?.isStateType(stateClassId.asFqNameString()) ?: false
  }

private fun KtNameReferenceExpression.isAssignee(): Boolean {
  return parentOfType<KtBinaryExpression>()
    ?.takeIf { it.operationToken.toString() == "EQ" }
    ?.let { it.left == this || it.left?.descendants()?.contains(this) == true } ?: false
}

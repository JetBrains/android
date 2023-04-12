/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.tools.compose

import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.js.translate.callTranslator.getReturnType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

fun isComposeEnabled(element: PsiElement): Boolean = element.getModuleSystem()?.usesCompose ?: false

fun isModifierChainLongerThanTwo(element: KtElement): Boolean {
  if (element.getChildrenOfType<KtDotQualifiedExpression>().isNotEmpty()) {
    val fqName = element.callReturnTypeFqName()?.asString()
    if (fqName == COMPOSE_MODIFIER_FQN) {
      return true
    }
  }
  return false
}

internal fun KotlinType.isClassOrExtendsClass(classFqName:String): Boolean {
  return fqName?.asString() == classFqName || supertypes().any { it.fqName?.asString() == classFqName }
}

internal fun KtValueArgument.matchingParamTypeFqName(callee: KtNamedFunction): FqName? {
  return if (isNamed()) {
    val argumentName = getArgumentName()!!.asName.asString()
    val matchingParam = callee.valueParameters.find { it.name == argumentName } ?: return null
    matchingParam.returnTypeFqName()
  }
  else {
    val argumentIndex = (parent as KtValueArgumentList).arguments.indexOf(this)
    val paramAtIndex = callee.valueParameters.getOrNull(argumentIndex) ?: return null
    paramAtIndex.returnTypeFqName()
  }
}

internal fun KtDeclaration.returnTypeFqName(): FqName? = if (isK2Plugin()) {
  analyze(this) { asFqName(this@returnTypeFqName.getReturnKtType()) }
}
else {
  this.type()?.fqName
}

@OptIn(KtAllowAnalysisOnEdt::class)
internal fun KtElement.callReturnTypeFqName() = if (isK2Plugin()) {
  allowAnalysisOnEdt {
    analyze(this) {
      val callReturnType = this@callReturnTypeFqName.resolveCall()?.singleFunctionCallOrNull()?.symbol?.returnType
      callReturnType?.let { asFqName(it) }
    }
  }
}
else {
  // TODO(jaebaek): `getReturnType()` uses IJ JS parts. Replace it with `resolveToCall(BodyResolveMode.PARTIAL)?.resultingDescriptor?.returnType?.fqName`.
  resolveToCall(BodyResolveMode.PARTIAL)?.getReturnType()?.fqName
}

// TODO(274630452): When the upstream APIs are available, implement it based on `fullyExpandedType` and `KtTypeRenderer`.
private fun KtAnalysisSession.asFqName(type: KtType) = type.expandedClassSymbol?.classIdIfNonLocal?.asSingleFqName()
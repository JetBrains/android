/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util.device

import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.tryEvaluateConstant
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/**
 * Denotes which [PsiElement] may be injected with the [DeviceSpecLanguage].
 *
 * For the @Preview device parameter, the first [KtStringTemplateExpression] may be injected.
 * [DeviceSpecInjectionPerformer] will handle injecting all other [KtStringTemplateExpression]
 * present in the parameter's value.
 */
class DeviceSpecInjectionContributor : LanguageInjectionContributor {
  override fun getInjection(context: PsiElement): Injection? {
    if (
      !StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.get() ||
        context.containingFile.fileType != KotlinFileType.INSTANCE ||
        context !is KtStringTemplateExpression ||
        !context.isInPreviewAnnotation()
    ) {
      return null
    }

    val valueArgument = context.parentOfType<KtValueArgument>() ?: return null

    if (
      !valueArgument.isForDeviceParameter() || valueArgument.getFirstStringExpression() !== context
    ) {
      return null
    }
    return SimpleInjection(DeviceSpecLanguage, "", "", null)
  }
}

/**
 * Injects the [DeviceSpecLanguage] into the given [PsiElement] and any other
 * [PsiLanguageInjectionHost]s available within the same expression via [MultiHostRegistrar] to
 * support concatenated injected elements.
 *
 * Since the Language cannot be injected to constant references, those are resolved and then added
 * to the injection of the closest [PsiLanguageInjectionHost]. Preserving the full statement in the
 * final injected file.
 *
 * E.g:
 * ```
 * const val width = "width=1080px"
 *
 * @Preview(device = "spec:" + width + ",height=1920px")
 * ```
 *
 * Injects the [DeviceSpecLanguage] in the first and last expressions, while the value of the
 * constant (width=1080px) is applied as a prefix to the injected file of the last expression,
 * resulting internally in: `"width=1080px,height1920px"`.
 *
 * So, contents of the injected file within `"spec:"` is: `"spec:width1080px,height1920px"`.
 */
class DeviceSpecInjectionPerformer : LanguageInjectionPerformer {
  override fun isPrimary(): Boolean {
    return false
  }

  override fun performInjection(
    registrar: MultiHostRegistrar,
    injection: Injection,
    context: PsiElement
  ): Boolean {
    val containingExpression =
      context.parentOfType<KtValueArgument>()?.getArgumentExpression() ?: return false

    val internalExpressions =
      PsiTreeUtil.collectElements(containingExpression) {
        when {
          it is PsiLanguageInjectionHost && it.children.isNotEmpty() -> true
          it is KtNameReferenceExpression -> true
          else -> false
        }
      }
    val injectionSegments = internalExpressions.collectInjectionSegments()
    if (injectionSegments.isEmpty()) {
      return false
    }
    registrar.startInjecting(injection.injectedLanguage ?: DeviceSpecLanguage)
    injectionSegments.forEach { it.injectTo(registrar) }
    registrar.doneInjecting()
    return true
  }
}

/**
 * Contains the information used by [MultiHostRegistrar] when injecting a concatenated Language (in
 * segments).
 */
private data class InjectionSegment(
  val injectionHost: PsiLanguageInjectionHost,
  val precedingContent: String = "",
  val followingContent: String = ""
) {
  fun injectTo(registrar: MultiHostRegistrar) {
    registrar.addPlace(
      precedingContent,
      followingContent,
      injectionHost,
      TextRange.from(1, injectionHost.textLength - 1)
    )
  }
}

/**
 * Organizes the elements so that the values of elements referencing a constant are added as a
 * suffix or prefix of a [PsiLanguageInjectionHost] element, depending on their order.
 *
 * @see InjectionSegment
 */
private fun Array<PsiElement>.collectInjectionSegments(): List<InjectionSegment> {
  val collectedSegments = mutableListOf<InjectionSegment>()

  var prefixConstant = ""
  var suffixConstant = ""
  var lastHost: PsiLanguageInjectionHost? = null
  this.forEach { expression ->
    when (expression) {
      is PsiLanguageInjectionHost -> {
        lastHost?.let {
          collectedSegments.add(
            InjectionSegment(
              injectionHost = it,
              precedingContent = prefixConstant,
              followingContent = ""
            )
          )
          prefixConstant = ""
        }

        prefixConstant = suffixConstant
        suffixConstant = ""
        lastHost = expression
      }
      is KtSimpleNameExpression -> {
        suffixConstant += expression.tryEvaluateConstant() ?: ""
      }
    }
  }
  lastHost?.let {
    collectedSegments.add(
      InjectionSegment(
        injectionHost = it,
        precedingContent = prefixConstant,
        followingContent = suffixConstant
      )
    )
  }
  return collectedSegments
}

private fun KtValueArgument.isForDeviceParameter(): Boolean =
  getArgumentName()?.text == PARAMETER_DEVICE

private fun KtValueArgument.getFirstStringExpression(): KtStringTemplateExpression? {
  val containingExpression = getArgumentExpression() ?: return null
  return PsiTreeUtil.findChildOfType(
    containingExpression,
    KtStringTemplateExpression::class.java,
    false
  )
}

private fun PsiLanguageInjectionHost.isInPreviewAnnotation(): Boolean {
  val annotationEntry = parentOfType<KtAnnotationEntry>() ?: return false
  return (annotationEntry.toUElement() as? UAnnotation)?.isPreviewAnnotation() ?: false
}

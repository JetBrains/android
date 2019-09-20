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
package org.jetbrains.android

import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_BANNER
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_DOTS_FOR_OPTIONAL
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_HIDE_RETURN_TYPES
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_HIDE_SPECIAL_LOOKUP_ELEMENTS
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_ICONS
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_LAYOUT_ICON
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_REQUIRED_ONLY
import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_TRAILING_LAMBDA
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.ui.LayeredIcon
import com.intellij.util.castSafelyTo
import icons.AndroidIcons
import icons.StudioIcons
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.completion.BasicLookupElementFactory
import org.jetbrains.kotlin.idea.completion.LambdaSignatureTemplates
import org.jetbrains.kotlin.idea.completion.LookupElementFactory
import org.jetbrains.kotlin.idea.core.completion.DeclarationLookupObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.types.typeUtil.isUnit

private const val COMPOSABLE = "androidx.compose.Composable"

/** TODO: overlay android on some other icon? */
private val COMPOSABLE_FUNCTION_ICON = LayeredIcon(AndroidIcons.Android)

private val signaturesFlags = listOf(
  COMPOSE_COMPLETION_REQUIRED_ONLY,
  COMPOSE_COMPLETION_DOTS_FOR_OPTIONAL,
  COMPOSE_COMPLETION_TRAILING_LAMBDA
)

private val allFlags = signaturesFlags + listOf(
  COMPOSE_COMPLETION_ICONS,
  COMPOSE_COMPLETION_BANNER,
  COMPOSE_COMPLETION_HIDE_SPECIAL_LOOKUP_ELEMENTS,
  COMPOSE_COMPLETION_HIDE_RETURN_TYPES,
  COMPOSE_COMPLETION_LAYOUT_ICON
)

private fun PsiElement.isComposableFunction(): Boolean {
  return this is KtNamedFunction && annotationEntries.any { it.getQualifiedName() == COMPOSABLE }
}

/**
 * Modifies [LookupElement]s for composable functions, to improve Compose editing UX.
 */
class AndroidComposeCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    if (allFlags.all { !it.isOverridden } || !isInsideComposableCode(parameters)) return

    if (COMPOSE_COMPLETION_BANNER.get()) {
      ApplicationManager.getApplication().invokeLater {
        val completionProgress = CompletionServiceImpl.getCurrentCompletionProgressIndicator()
        if (completionProgress != null) {
          val advertiser = completionProgress.lookup.advertiser
          advertiser.clearAdvertisements()
          advertiser.addAdvertisement("Hello from Compose!", AndroidIcons.Android)
        }
      }
    }

    resultSet.runRemainingContributors(parameters) { completionResult ->
      val lookupElement = completionResult.lookupElement
      val psi = lookupElement.psiElement
      val newResult = when {
        psi == null || !psi.isComposableFunction() -> completionResult
        COMPOSE_COMPLETION_HIDE_SPECIAL_LOOKUP_ELEMENTS.get() && lookupElement.isForSpecialLambdaLookupElement() -> null
        else -> completionResult.withLookupElement(ComposeLookupElement(lookupElement))
      }

      newResult?.let(resultSet::passResult)
    }
  }

  private fun isInsideComposableCode(parameters: CompletionParameters): Boolean {
    // TODO: Figure this out.
    return parameters.originalFile.language == KotlinLanguage.INSTANCE
  }

  /**
   * Checks if the [LookupElement] is an additional, "special" lookup element created for functions that can be invoked using the lambda
   * syntax. These are created by [LookupElementFactory.addSpecialFunctionCallElements] and can be confusing for Compose APIs that often
   * use overloaded function names.
   */
  private fun LookupElement.isForSpecialLambdaLookupElement(): Boolean {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    return presentation.tailText?.startsWith(" {") ?: false
  }
}

/**
 * Wraps original Kotlin [LookupElement]s for composable functions to make them stand out more.
 */
private class ComposeLookupElement(original: LookupElement) : LookupElementDecorator<LookupElement>(original) {

  init {
    require(original.psiElement?.isComposableFunction() == true)
  }

  override fun getPsiElement(): KtNamedFunction = super.getPsiElement() as KtNamedFunction

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)

    if (COMPOSE_COMPLETION_ICONS.get()) {
      presentation.icon = COMPOSABLE_FUNCTION_ICON
    }

    val descriptor =
      this.`object`
        .castSafelyTo<DeclarationLookupObject>()
        ?.descriptor
        ?.castSafelyTo<FunctionDescriptor>()
      ?: return

    if (COMPOSE_COMPLETION_HIDE_RETURN_TYPES.get() || COMPOSE_COMPLETION_LAYOUT_ICON.get()) {
      val text = when {
        !COMPOSE_COMPLETION_HIDE_RETURN_TYPES.get() -> presentation.typeText
        descriptor.returnType?.isUnit() == true -> null
        else -> presentation.typeText
      }
      val icon = when {
        !COMPOSE_COMPLETION_LAYOUT_ICON.get() -> presentation.typeIcon
        descriptor.valueParameters.hasTrailingFunction -> StudioIcons.LayoutEditor.Palette.VIEW_STUB
        else -> presentation.typeIcon
      }
      presentation.setTypeText(text, icon)
    }

    if (signaturesFlags.any { it.get()} ) {
      rewriteSignature(descriptor, presentation)
    }
  }

  private fun rewriteSignature(descriptor: FunctionDescriptor, presentation: LookupElementPresentation) {
    val allParameters = descriptor.valueParameters
    val requiredParameters = allParameters.filter { !it.declaresDefaultValue() }
    val visibleParameters = if (COMPOSE_COMPLETION_REQUIRED_ONLY.get()) requiredParameters else allParameters
    val inParens = when {
      visibleParameters.hasTrailingFunction && COMPOSE_COMPLETION_TRAILING_LAMBDA.get() -> visibleParameters.dropLast(1)
      else -> visibleParameters
    }
    val renderer = when {
      COMPOSE_COMPLETION_DOTS_FOR_OPTIONAL.get() && visibleParameters.size < allParameters.size -> SHORT_NAMES_WITH_DOTS
      !COMPOSE_COMPLETION_DOTS_FOR_OPTIONAL.get() && inParens.isEmpty() && visibleParameters.hasTrailingFunction -> {
        // Don't render an empty pair of parenthesis if we're rendering a lambda afterwards.
        null
      }
      else -> BasicLookupElementFactory.SHORT_NAMES_RENDERER
    }

    presentation.clearTail()
    renderer
      ?.renderValueParameters(inParens, false)
      ?.let { presentation.appendTailTextItalic(it, false) }

    if (COMPOSE_COMPLETION_TRAILING_LAMBDA.get() && visibleParameters.hasTrailingFunction) {
      presentation.appendTailText(" " + LambdaSignatureTemplates.DEFAULT_LAMBDA_PRESENTATION, true)
    }
  }

  private val List<ValueParameterDescriptor>.hasTrailingFunction: Boolean get() = lastOrNull()?.type?.isBuiltinFunctionalType == true
}

/**
 * A version of [BasicLookupElementFactory.SHORT_NAMES_RENDERER] that adds `, ...)` at the end of the parameters list.
 */
private val SHORT_NAMES_WITH_DOTS = BasicLookupElementFactory.SHORT_NAMES_RENDERER.withOptions {
  val delegate = DescriptorRenderer.ValueParametersHandler.DEFAULT
  valueParametersHandler = object : DescriptorRenderer.ValueParametersHandler {
    override fun appendAfterValueParameter(
      parameter: ValueParameterDescriptor,
      parameterIndex: Int,
      parameterCount: Int,
      builder: StringBuilder
    ) {
      delegate.appendAfterValueParameter(parameter, parameterIndex, parameterCount, builder)
    }

    override fun appendBeforeValueParameter(
      parameter: ValueParameterDescriptor,
      parameterIndex: Int,
      parameterCount: Int,
      builder: StringBuilder
    ) {
      delegate.appendBeforeValueParameter(parameter, parameterIndex, parameterCount, builder)
    }

    override fun appendBeforeValueParameters(parameterCount: Int, builder: StringBuilder) {
      delegate.appendBeforeValueParameters(parameterCount, builder)
    }

    override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
      builder.append(if (parameterCount == 0) "...)" else ", ...)")
    }
  }
}

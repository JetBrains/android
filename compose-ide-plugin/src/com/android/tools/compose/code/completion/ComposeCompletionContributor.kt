/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose.code.completion

import com.android.ide.common.vectordrawable.VdPreview
import com.android.tools.compose.ComposeSettings
import com.android.tools.compose.aa.code.getComposableFunctionRenderParts
import com.android.tools.compose.code.ComposableFunctionRenderParts
import com.android.tools.compose.code.getComposableFunctionRenderParts
import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.base.CaseFormat
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import icons.StudioIcons
import java.io.BufferedReader
import javax.swing.Icon
import javax.swing.ImageIcon
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.completion.LookupElementFactory
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.kotlin.types.typeUtil.isUnit

private val COMPOSABLE_FUNCTION_ICON = StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION

private fun LookupElement.getFunctionDescriptor(): FunctionDescriptor? {
  return this.`object`
    .asSafely<DescriptorBasedDeclarationLookupObject>()
    ?.descriptor
    ?.asSafely<FunctionDescriptor>()
}

private fun InsertionContext.getParent(): PsiElement? = file.findElementAt(startOffset)?.parent

/**
 * Find the [CallType] from the [InsertionContext]. The [CallType] can be used to detect if the
 * completion is being done in a regular statement, an import or some other expression and decide if
 * we want to apply the [ComposeInsertHandler].
 */
private fun PsiElement?.inferCallType(): CallType<*> {
  // Look for an existing KtSimpleNameExpression to pass to CallTypeAndReceiver.detect so we can
  // infer the call type.
  val namedExpression =
    (this as? KtSimpleNameExpression)?.mainReference?.expression ?: return CallType.DEFAULT
  return CallTypeAndReceiver.detect(namedExpression).callType
}

/**
 * Return true if element is a KDoc.
 *
 * Ideally, we would use [inferCallType] but there doesn't seem to be a [CallType] for a KDoc
 * element.
 */
private fun PsiElement?.isKdoc() = this is KDocName

/** Modifies [LookupElement]s for composable functions, to improve Compose editing UX. */
class ComposeCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet,
  ) {
    if (
      parameters.position.getModuleSystem()?.usesCompose != true ||
        parameters.position.language != KotlinLanguage.INSTANCE
    ) {
      return
    }

    resultSet.runRemainingContributors(parameters) { completionResult ->
      transformCompletionResult(completionResult)?.let(resultSet::passResult)
    }
  }

  private fun transformCompletionResult(completionResult: CompletionResult): CompletionResult? {
    val lookupElement = completionResult.lookupElement

    // If there's no PsiElement, just leave the result alone.
    val psi = lookupElement.psiElement ?: return completionResult

    if (psi.isComposableFunction()) {
      // Get rid of the extra version of the lookup element shown with trailing lambda
      // syntax (i.e. "{ }").
      if (lookupElement.isForSpecialLambdaLookupElement()) return null
      return completionResult.withLookupElement(ComposableFunctionLookupElement(lookupElement))
    }
    if (ComposeMaterialIconLookupElement.appliesTo(psi)) {
      return completionResult.withLookupElement(ComposeMaterialIconLookupElement(lookupElement))
    }
    return completionResult
  }

  /**
   * Checks if the [LookupElement] is an additional, "special" lookup element created for functions
   * that can be invoked using the lambda syntax. These are created by
   * [LookupElementFactory.addSpecialFunctionCallElements] and can be confusing for Compose APIs
   * that often use overloaded function names.
   */
  private fun LookupElement.isForSpecialLambdaLookupElement(): Boolean {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    return presentation.tailText?.startsWith(" {...} (..., ") ?: false
  }
}

/** Wraps original Kotlin [LookupElement]s for composable functions to make them stand out more. */
private class ComposableFunctionLookupElement(original: LookupElement) :
  LookupElementDecorator<LookupElement>(original) {
  /** Set of [CallType]s that should be handled by the [ComposeInsertHandler]. */
  private val validCallTypes = setOf(CallType.DEFAULT, CallType.DOT)

  init {
    require(original.psiElement?.isComposableFunction() == true)
  }

  private val functionInfo = psiElement.getFunctionInfoForCompletion()

  override fun getPsiElement(): KtNamedFunction = super.getPsiElement() as KtNamedFunction

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)

    if (KotlinPluginModeProvider.isK2Mode()) {
      val element = psiElement
      analyze(element) {
        val functionSymbol = element.getFunctionLikeSymbol()
        val typeText = presentation.typeText.takeUnless { functionSymbol.returnType.isUnit }
        presentation.setTypeText(typeText, null)
        presentation.rewriteSignature(getComposableFunctionRenderParts(functionSymbol))
      }
    } else {
      val descriptor = getFunctionDescriptor() ?: return
      val typeText = presentation.typeText.takeUnless { descriptor.returnType?.isUnit() == true }
      presentation.setTypeText(typeText, null)
      presentation.rewriteSignature(descriptor.getComposableFunctionRenderParts())
    }

    presentation.icon = COMPOSABLE_FUNCTION_ICON
  }

  override fun handleInsert(context: InsertionContext) {
    // Allow Kotlin to do the insertion
    super.handleInsert(context)

    // If the function ends in a required lambda, we may need to make adjustments.
    if (!functionInfo.endsInRequiredLambda || !applyComposeLambdaHandling(context)) return

    // For Compose, we always want a required lambda to be added. Check whether the Kotlin plugin
    // already did that.
    if (context.getLastElementInOffset()?.text == "}") return

    // There's a special case where the cursor (prior to completion) was immediately before a block
    // opening, in which case we don't need to add the lambda.
    if (context.getNextElementAfterOffset()?.text?.startsWith("{") == true) return

    // When there are no required parameters before the lambda, the caret will be placed in the
    // lambda, so there needs to be an extra space.
    val trailingLambda = if (functionInfo.hasRequiredParametersBeforeLambda) " { }" else " {  }"

    // Insert the lambda.
    context.document.insertString(context.tailOffset, trailingLambda)
    PsiDocumentManager.getInstance(context.project).commitDocument(context.document)

    // If there are required parameters before the lambda, then we can just leave the function's
    // parens there.
    if (functionInfo.hasRequiredParametersBeforeLambda) return

    // Since there are no required parameters, delete the function's parens.
    val valueArgumentList =
      context.getLastElementInOffset()?.parentOfType<KtCallExpression>()?.valueArgumentList
        ?: return
    valueArgumentList.delete()
    PsiDocumentManager.getInstance(context.project).commitDocument(context.document)

    // Move the caret inside the lambda.
    context.editor.caretModel.moveToOffset(context.tailOffset - 2)
  }

  private fun applyComposeLambdaHandling(context: InsertionContext): Boolean {
    if (!ComposeSettings.getInstance().state.isComposeInsertHandlerEnabled) return false

    val parent = context.getParent()
    if (parent.isKdoc() || parent !is KtNameReferenceExpression) return false

    return validCallTypes.contains(parent.inferCallType())
  }

  private fun LookupElementPresentation.rewriteSignature(parts: ComposableFunctionRenderParts) {
    // If there are fewer than two parameters, then there's no need to rewrite the completion entry
    // to handle verbose @Composable functions.
    if (parts.totalParameterCount < 2) return

    // Rewrite the function signature to avoid showing too many parameters, since @Composable
    // functions often have a large number. The first fragment contains the portion we want to
    // rewrite; the remaining fragments are copied verbatim.
    val existingTailFragments = tailFragments
    clearTail()

    // Write the modified signature.
    parts.parameters?.let { appendTailText(it, /* grayed= */ false) }
    parts.tail?.let { appendTailText(" $it", /* grayed= */ true) }

    // Copy the remaining fragments that came from the Kotlin plugin.
    for (fragment in existingTailFragments.drop(1)) {
      // Technically each fragment may have a color associated with it which we are not persisting.
      // But the only time that can be set is with LookupElementPresentation.setTailText, which
      // clears the tail before adding the fragment with color. That means only the first fragment
      // can have a color, and since we've dropped the first fragment none of the remaining ones
      // will have a color.
      if (fragment.isItalic) appendTailTextItalic(fragment.text, fragment.isGrayed)
      else appendTailText(fragment.text, fragment.isGrayed)
    }
  }
}

/**
 * Lookup element that decorates a Compose material icon property with the actual icon it
 * represents.
 */
@VisibleForTesting
internal class ComposeMaterialIconLookupElement(private val original: LookupElement) :
  LookupElementDecorator<LookupElement>(original) {

  init {
    // We know we'll want material icons, so start warming up the cache.
    ComposeMaterialIconService.getInstance(ApplicationManager.getApplication()).ensureIconsLoaded()
  }

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)

    val fqName = original.psiElement?.kotlinFqName?.asString() ?: return
    getIcon(fqName)?.let { presentation.icon = it }
  }

  companion object {
    /**
     * Map of naming patterns for known Compose material icon themes.
     *
     * The key is the package name, coming from a fully-qualified icon name.
     *
     * The value is a pair that identifies how to construct names that represent these icons. The
     * first string in each pair represents part of a directory name where the theme's icons are
     * stored. The second string value represents the prefix of each image's file name.
     */
    private val themeNamingPatterns =
      mapOf(
        "androidx.compose.material.icons.filled" to Pair("materialicons", "baseline"),
        "androidx.compose.material.icons.rounded" to Pair("materialiconsround", "round"),
        "androidx.compose.material.icons.sharp" to Pair("materialiconssharp", "sharp"),
        "androidx.compose.material.icons.twotone" to Pair("materialiconstwotone", "twotone"),
        "androidx.compose.material.icons.outlined" to Pair("materialiconsoutlined", "outline"),
      )

    /** Whether ComposeMaterialIconLookupElement can apply to the given [LookupElement]. */
    fun appliesTo(psiElement: PsiElement): Boolean {
      if (psiElement !is KtProperty) return false
      val fqName = psiElement.kotlinFqName?.asString() ?: return false

      if (
        !fqName.startsWith("androidx.compose.material.icons") ||
          psiElement.typeReference?.text?.endsWith("ImageVector") != true
      )
        return false

      return themeNamingPatterns.containsKey(fqName.substringBeforeLast('.'))
    }

    /**
     * Converts the property name of a Compose material icon to the snake-case equivalent used in
     * file names, with additional underscores separating digits from non-digit characters.
     *
     * eg: "AccountBox3" -> "account_box_3"
     *
     * If a material icon's name starts with a number, the property name has an underscore prepended
     * to make it a valid identifier, even though the underscore doesn't appear in the file path and
     * name.
     *
     * eg: "_1kPlus42" -> "1k_plus_42"
     */
    private fun String.camelCaseToSnakeCase(): String {
      val str = trimStart('_').replace(Regex("(\\D)(\\d)"), "$1_$2")
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, str)
    }

    @VisibleForTesting
    internal fun String.resourcePathFromFqName(): String? {
      val (directory, filePrefix) = themeNamingPatterns[substringBeforeLast('.')] ?: return null

      val snakeCaseName = substringAfterLast('.').camelCaseToSnakeCase()
      return "images/material/icons/${directory}/${snakeCaseName}/${filePrefix}_${snakeCaseName}_24.xml"
    }

    private fun String.iconFileNameFromFqName(): String? {
      val (_, filePrefix) = themeNamingPatterns[substringBeforeLast('.')] ?: return null

      val snakeCaseName = substringAfterLast('.').camelCaseToSnakeCase()
      return "${filePrefix}_${snakeCaseName}_24.xml"
    }

    /** Returns an [Icon] given an Android Studio resource path. */
    @VisibleForTesting
    internal fun getIcon(fqName: String): Icon? =
      getIconFromMaterialIconsProvider(fqName) ?: getIconFromResources(fqName)

    private fun getIconFromMaterialIconsProvider(fqName: String): Icon? {
      val iconFileName = fqName.iconFileNameFromFqName() ?: return null
      return ComposeMaterialIconService.getInstance(ApplicationManager.getApplication())
        .getIcon(iconFileName)
    }

    private fun getIconFromResources(fqName: String): Icon? {
      val resourcePath = fqName.resourcePathFromFqName() ?: return null

      return ComposeMaterialIconLookupElement::class
        .java
        .classLoader
        .getResourceAsStream(resourcePath)
        ?.use { inputStream ->
          val content = inputStream.bufferedReader().use(BufferedReader::readText)
          val errorLog = StringBuilder()
          val bufferedImage =
            VdPreview.getPreviewFromVectorXml(
              VdPreview.TargetSize.createFromMaxDimension(16),
              content,
              errorLog,
            )
          if (errorLog.isNotEmpty()) {
            Logger.getInstance(ComposeMaterialIconLookupElement::class.java)
              .error(errorLog.toString())
          }

          ImageIcon(bufferedImage)
        }
    }
  }
}

private fun InsertionContext.getLastElementInOffset(): PsiElement? =
  file.findElementAt(tailOffset - 1)?.getPrevSiblingIgnoringWhitespace(true)

private fun InsertionContext.getNextElementAfterOffset(): PsiElement? =
  file.findElementAt(tailOffset)?.getNextSiblingIgnoringWhitespace(true)

/** A class used to keep the result of analysis API for information about parameters. */
private data class FunctionInfo(
  val endsInRequiredLambda: Boolean,
  val hasRequiredParametersBeforeLambda: Boolean,
)

private fun KtNamedFunction.getFunctionInfoForCompletion(): FunctionInfo =
  analyze(this) {
    val allParameters = getFunctionLikeSymbol().valueParameters

    val endsInRequiredLambda =
      allParameters.lastOrNull()?.let {
        !it.isVararg && it.returnType is KtFunctionalType && !it.hasDefaultValue
      } ?: false

    val hasRequiredParametersBeforeLambda =
      endsInRequiredLambda && allParameters.dropLast(1).any { !it.hasDefaultValue && !it.isVararg }

    return FunctionInfo(endsInRequiredLambda, hasRequiredParametersBeforeLambda)
  }

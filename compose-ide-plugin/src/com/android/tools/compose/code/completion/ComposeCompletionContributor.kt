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
import com.android.tools.compose.code.getComposableFunctionRenderParts
import com.android.tools.compose.code.isComposableFunctionParameter
import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.completion.LookupElementFactory
import org.jetbrains.kotlin.idea.completion.handlers.KotlinCallableInsertHandler
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
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.results.argumentValueType
import org.jetbrains.kotlin.types.typeUtil.isUnit
import java.io.BufferedReader
import javax.swing.Icon
import javax.swing.ImageIcon

private val COMPOSABLE_FUNCTION_ICON = StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION

private fun LookupElement.getFunctionDescriptor(): FunctionDescriptor? {
  return this.`object`
    .asSafely<DescriptorBasedDeclarationLookupObject>()
    ?.descriptor
    ?.asSafely<FunctionDescriptor>()
}

private fun ValueParameterDescriptor.isLambdaWithNoParameters() =
  // The only type in the list is the return type (can be Unit).
  type.isFunctionType && argumentValueType.arguments.size == 1

/**
 * true iff the last parameter is required, and a lambda type with no parameters.
 */
private fun ValueParameterDescriptor.isRequiredLambdaWithNoParameters() =
  !hasDefaultValue() && isLambdaWithNoParameters() && varargElementType == null


private fun InsertionContext.getParent(): PsiElement? = file.findElementAt(startOffset)?.parent

/**
 * Find the [CallType] from the [InsertionContext]. The [CallType] can be used to detect if the completion is being done in a regular
 * statement, an import or some other expression and decide if we want to apply the [ComposeInsertHandler].
 */
private fun PsiElement?.inferCallType(): CallType<*> {
  // Look for an existing KtSimpleNameExpression to pass to CallTypeAndReceiver.detect so we can infer the call type.
  val namedExpression = (this as? KtSimpleNameExpression)?.mainReference?.expression ?: return CallType.DEFAULT
  return CallTypeAndReceiver.detect(namedExpression).callType
}

/**
 * Return true if element is a KDoc.
 *
 * Ideally, we would use [inferCallType] but there doesn't seem to be a [CallType] for a KDoc element.
 */
private fun PsiElement?.isKdoc() = this is KDocName

/**
 * Modifies [LookupElement]s for composable functions, to improve Compose editing UX.
 */
class ComposeCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    if (parameters.position.getModuleSystem()?.usesCompose != true ||
        parameters.position.language != KotlinLanguage.INSTANCE) {
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

    // For @Composable functions: remove the "special" lookup element (docs below), but otherwise apply @Composable function decoration.
    if (psi.isComposableFunction()) {
      return if (lookupElement.isForSpecialLambdaLookupElement()) null
      else completionResult.withLookupElement(ComposableFunctionLookupElement(lookupElement))
    }

    // Decorate Compose material icons with the actual icons.
    if (ComposeMaterialIconLookupElement.appliesTo(lookupElement)) {
      // We know we'll want material icons, so start warming up the cache.
      ComposeMaterialIconService.getInstance(ApplicationManager.getApplication()).ensureIconsLoaded()
      return completionResult.withLookupElement(ComposeMaterialIconLookupElement(lookupElement))
    }

    // No transformation needed.
    return completionResult
  }

  /**
   * Checks if the [LookupElement] is an additional, "special" lookup element created for functions that can be invoked using the lambda
   * syntax. These are created by [LookupElementFactory.addSpecialFunctionCallElements] and can be confusing for Compose APIs that often
   * use overloaded function names.
   */
  private fun LookupElement.isForSpecialLambdaLookupElement(): Boolean {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    return presentation.tailText?.startsWith(" {...} (..., ") ?: false
  }
}

/**
 * Wraps original Kotlin [LookupElement]s for composable functions to make them stand out more.
 */
private class ComposableFunctionLookupElement(original: LookupElement) : LookupElementDecorator<LookupElement>(original) {
  /**
   * Set of [CallType]s that should be handled by the [ComposeInsertHandler].
   */
  private val validCallTypes = setOf(CallType.DEFAULT, CallType.DOT)

  init {
    require(original.psiElement?.isComposableFunction() == true)
  }

  override fun getPsiElement(): KtNamedFunction = super.getPsiElement() as KtNamedFunction

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)

    val descriptor = getFunctionDescriptor() ?: return
    presentation.icon = COMPOSABLE_FUNCTION_ICON
    presentation.setTypeText(if (descriptor.returnType?.isUnit() == true) null else presentation.typeText, null)
    rewriteSignature(descriptor, presentation)
  }

  override fun handleInsert(context: InsertionContext) {
    val handler = getInsertHandler(context)
    if (handler == null) super.handleInsert(context) else handler.handleInsert(context, this)
  }

  private fun getInsertHandler(context: InsertionContext): ComposeInsertHandler? {
    if (!ComposeSettings.getInstance().state.isComposeInsertHandlerEnabled) return null

    val parent = context.getParent()
    if (parent.isKdoc() || parent !is KtNameReferenceExpression) return null

    val descriptor = getFunctionDescriptor() ?: return null

    val callType = parent.inferCallType()
    if (!validCallTypes.contains(callType)) return null

    return ComposeInsertHandler(descriptor, callType)
  }

  private fun rewriteSignature(descriptor: FunctionDescriptor, presentation: LookupElementPresentation) {
    val (parameters, tail) = descriptor.getComposableFunctionRenderParts()

    presentation.clearTail()
    parameters?.let { presentation.appendTailTextItalic(it, /* grayed = */ false) }
    tail?.let { presentation.appendTailText(" $it", /* grayed = */ true) }
  }
}

/** Lookup element that decorates a Compose material icon property with the actual icon it represents. */
@VisibleForTesting
internal class ComposeMaterialIconLookupElement(private val original: LookupElement)
  : LookupElementDecorator<LookupElement>(original) {
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
     * The value is a pair that identifies how to construct names that represent these icons. The first string in each pair represents part
     * of a directory name where the theme's icons are stored. The second string value represents the prefix of each image's file name.
     */
    private val themeNamingPatterns = mapOf(
      "androidx.compose.material.icons.filled" to Pair("materialicons", "baseline"),
      "androidx.compose.material.icons.rounded" to Pair("materialiconsround", "round"),
      "androidx.compose.material.icons.sharp" to Pair("materialiconssharp", "sharp"),
      "androidx.compose.material.icons.twotone" to Pair("materialiconstwotone", "twotone"),
      "androidx.compose.material.icons.outlined" to Pair("materialiconsoutlined", "outline"),
    )

    /** Whether ComposeMaterialIconLookupElement can apply to the given [LookupElement]. */
    fun appliesTo(lookupElement: LookupElement): Boolean {
      val psiElement = lookupElement.psiElement ?: return false
      if (psiElement !is KtProperty) return false
      val fqName = psiElement.kotlinFqName?.asString() ?: return false

      if (!fqName.startsWith("androidx.compose.material.icons") ||
          psiElement.typeReference?.text?.endsWith("ImageVector") != true) return false

      return themeNamingPatterns.containsKey(fqName.substringBeforeLast('.'))
    }

    /**
     * Converts the property name of a Compose material icon to the snake-case equivalent used in file names.
     *
     * eg: "AccountBox" -> "account_box"
     *
     * If a material icon's name starts with a number, the property name has an underscore prepended to make it a valid identifier, even
     * though the underscore doesn't appear in the file path and name.
     *
     * eg: "_1kPlus" -> "1k_plus"
     */
    private fun String.camelCaseToSnakeCase(): String {
      val camelName = trimStart('_')
      return camelName.withIndex().joinToString("") { (i, ch) ->
        when {
          i == 0 -> ch.lowercaseChar().toString()
          ch.isUpperCase() -> "_${ch.lowercaseChar()}"
          ch.isDigit() && !camelName[i - 1].isDigit() -> "_$ch"
          else -> ch.toString()
        }
      }
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
    internal fun getIcon(fqName: String): Icon? {
      return getIconFromMaterialIconsProvider(fqName) ?: getIconFromResources(fqName)
    }

    private fun getIconFromMaterialIconsProvider(fqName: String): Icon? {
      val iconFileName = fqName.iconFileNameFromFqName() ?: return null
      return ComposeMaterialIconService.getInstance(ApplicationManager.getApplication()).getIcon(iconFileName)
    }

    private fun getIconFromResources(fqName: String): Icon? {
      val resourcePath = fqName.resourcePathFromFqName() ?: return null

      return ComposeMaterialIconLookupElement::class.java.classLoader.getResourceAsStream(resourcePath)?.use { inputStream ->
        val content = inputStream.bufferedReader().use(BufferedReader::readText)
        val errorLog = StringBuilder()
        val bufferedImage = VdPreview.getPreviewFromVectorXml(VdPreview.TargetSize.createFromMaxDimension(16), content, errorLog)
        if (errorLog.isNotEmpty()) Logger.getInstance(ComposeMaterialIconLookupElement::class.java).error(errorLog.toString())

        ImageIcon(bufferedImage)
      }
    }
  }
}

private fun InsertionContext.getNextElementIgnoringWhitespace(): PsiElement? {
  val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return null
  return elementAtCaret.getNextSiblingIgnoringWhitespace(true) ?: return null
}

private fun InsertionContext.isNextElementOpenCurlyBrace() = getNextElementIgnoringWhitespace()?.text?.startsWith("{") == true

private fun InsertionContext.isNextElementOpenParenthesis() = getNextElementIgnoringWhitespace()?.text?.startsWith("(") == true

private class ComposeInsertHandler(
  private val descriptor: FunctionDescriptor,
  callType: CallType<*>) : KotlinCallableInsertHandler(callType) {
  override fun handleInsert(context: InsertionContext, item: LookupElement) = with(context) {
    super.handleInsert(context, item)

    if (isNextElementOpenParenthesis()) return

    // All Kotlin insertion handlers do this, possibly to post-process adding a new import in the call to super above.
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitAllDocuments()
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

    val templateManager = TemplateManager.getInstance(project)
    val allParameters = descriptor.valueParameters
    val requiredParameters = allParameters.filter { !it.declaresDefaultValue() && it.varargElementType == null }
    val insertLambda = allParameters.lastOrNull()?.isComposableFunctionParameter() == true
                       || allParameters.lastOrNull()?.isRequiredLambdaWithNoParameters() == true
    val inParens = if (insertLambda) requiredParameters.dropLast(1) else requiredParameters

    val template = templateManager.createTemplate("", "").apply {
      isToReformat = true
      setToIndent(true)

      when {
        inParens.isNotEmpty() -> {
          addTextSegment("(")
          inParens.forEachIndexed { index, parameter ->
            if (index > 0) {
              addTextSegment(", ")
            }
            addTextSegment(parameter.name.asString() + " = ")
            if (parameter.isLambdaWithNoParameters()) {
              addVariable(ConstantNode("{ /*TODO*/ }"), true)
            }
            else {
              addVariable(EmptyExpression(), true)
            }
          }
          addTextSegment(")")
        }

        !insertLambda -> addTextSegment("()")
      }

      if (insertLambda && !isNextElementOpenCurlyBrace()) {
        addTextSegment(" {\n")
        addEndVariable()
        addTextSegment("\n}")
      }
    }

    templateManager.startTemplate(editor, template, object : TemplateEditingAdapter() {
      override fun templateFinished(template: Template, brokenOff: Boolean) {
        if (!brokenOff) {
          val callExpression = file.findElementAt(editor.caretModel.offset)?.parentOfType<KtCallExpression>() ?: return
          val valueArgumentList = callExpression.valueArgumentList ?: return
          if (valueArgumentList.arguments.isEmpty() && callExpression.lambdaArguments.isNotEmpty()) {
            runWriteAction { valueArgumentList.delete() }
          }
        }
      }
    })
  }
}

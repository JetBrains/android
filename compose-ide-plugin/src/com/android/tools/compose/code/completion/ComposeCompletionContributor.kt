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

import com.android.tools.compose.ComposeSettings
import com.android.tools.compose.code.getComposableFunctionRenderParts
import com.android.tools.compose.code.isComposableFunctionParameter
import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
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
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import icons.StudioIcons
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.completion.LookupElementFactory
import org.jetbrains.kotlin.idea.completion.handlers.KotlinCallableInsertHandler
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.results.argumentValueType
import org.jetbrains.kotlin.types.typeUtil.isUnit

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
      val lookupElement = completionResult.lookupElement
      val psi = lookupElement.psiElement
      val newResult = when {
        psi == null || !psi.isComposableFunction() -> completionResult
        lookupElement.isForSpecialLambdaLookupElement() -> null
        else -> completionResult.withLookupElement(ComposeLookupElement(lookupElement))
      }

      newResult?.let(resultSet::passResult)
    }
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
private class ComposeLookupElement(original: LookupElement) : LookupElementDecorator<LookupElement>(original) {
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
    val descriptor = getFunctionDescriptor()
    val parent = context.getParent()
    val callType by lazy { parent.inferCallType() }
    return when {
      !ComposeSettings.getInstance().state.isComposeInsertHandlerEnabled -> super.handleInsert(context)
      parent.isKdoc() -> super.handleInsert(context)
      descriptor == null -> super.handleInsert(context)
      !validCallTypes.contains(callType) -> super.handleInsert(context)
      else -> ComposeInsertHandler(descriptor, callType).handleInsert(context, this)
    }
  }

  private fun rewriteSignature(descriptor: FunctionDescriptor, presentation: LookupElementPresentation) {
    val (parameters, tail) = descriptor.getComposableFunctionRenderParts()

    presentation.clearTail()
    parameters?.let { presentation.appendTailTextItalic(it, /* grayed = */ false) }
    tail?.let { presentation.appendTailText(" $it", /* grayed = */ true) }
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
        requiredParameters.size < allParameters.size -> {
          addTextSegment("(")
          addVariable(EmptyExpression(), true)
          addTextSegment(")")
        }
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

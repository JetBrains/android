/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.api.ext.RawText
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.getNextValidParent
import com.android.tools.idea.gradle.dsl.parser.removePsiIfInvalid
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpressionOrEmptyBlock
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import java.math.BigDecimal

internal val ESCAPE_CHILD = Regex("[\\t ]+")

internal fun String.addQuotes(forExpression : Boolean) = if (forExpression) "\"$this\"" else "'$this'"

internal fun KtCallExpression.isBlockElement() : Boolean {
  return lambdaArguments.size == 1 && (valueArgumentList == null || (valueArgumentList as KtValueArgumentList).arguments.size < 2)
}

internal fun KtCallExpression.name() : String? {
  return getCallNameExpression()?.getReferencedName()
}

internal fun getParentPsi(dslElement : GradleDslElement) = dslElement.parent?.create()

internal fun getPsiElementForAnchor(parent : PsiElement, dslAnchor : GradleDslElement?) : PsiElement? {
  var anchorAfter = if (dslAnchor == null) null else findLastPsiElementIn(dslAnchor)
  if (anchorAfter == null && parent is KtBlockExpression) {
    return adjustForKtBlockExpression(parent as KtBlockExpression)
  }
  else {
    while (anchorAfter != null && anchorAfter !is PsiFile && anchorAfter.parent != parent) {
      anchorAfter = anchorAfter.parent
    }
    return when (anchorAfter) {
      is PsiFile -> {
        if (parent is KtBlockExpression) {
          adjustForKtBlockExpression(parent)
        }
        else {
          null
        }
      }
      else -> anchorAfter
    }
  }
}

internal fun adjustForKtBlockExpression(blockExpression: KtBlockExpression) : PsiElement? {
  var element = blockExpression.firstChild

  // Find last empty child after the initial element.
  while (element != null) {
    element = element.nextSibling
    if (element != null && (element.text.isNullOrEmpty() || element.text.matches(ESCAPE_CHILD))) {
      continue
    }
    break
  }
  return element?.prevSibling
}

/**
 * Get the block name with the valid syntax in kotlin.
 * If the block was read from the KTS script, we use the `methodName` to create the block name. Otherwise, if we want to write
 * the block in the build file for the first time, we use maybeCreate because it tries to create the element only if it doesn't exist.
 */
internal fun getOriginalName(methodName : String?, blockName : String): String {
  return if (methodName != null) "$methodName(\"$blockName\")" else "maybeCreate(\"$blockName\")"
}

@Throws(IncorrectOperationException::class)
internal fun createLiteral(context : GradleDslElement, value : Any) : PsiElement? {
   when (value) {
    is String ->  {
      var valueText : String?
      if (StringUtil.isQuotedString(value)) {
        val unquoted = StringUtil.unquoteString(value)
        valueText = StringUtil.escapeStringCharacters(unquoted).addQuotes(true)
      }
      else {
        valueText = StringUtil.escapeStringCharacters(value).addQuotes(false)
      }
      return KtPsiFactory(context.dslFile.project).createExpression(valueText)
    }
    is Int, is Boolean, is BigDecimal -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.toString())
    is RawText -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.getText())
    else -> throw IncorrectOperationException("Expression '${value}' not supported.")
  }
}

internal fun findInjections(
  context: GradleDslSimpleExpression,
  psiElement: PsiElement,
  includeResolved: Boolean,
  injectionElement: PsiElement? = null
): MutableList<GradleReferenceInjection> {
  val noInjections = mutableListOf<GradleReferenceInjection>()
  val injectionPsiElement = injectionElement ?: psiElement
  when (psiElement) {
    // foo
    is KtNameReferenceExpression -> {
      val text = psiElement.text
      // TODO(xof): in Groovy, names are looked up both in the extra properties and in parent blocks.  In Kotlinscript,
      //  the lookup in the two cases is different syntactically: extra["foo"] vs. foo, so we should distinguish that
      //  in reference resolution / injection construction.
      val element = context.resolveReference(text, true)
      return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, text))
    }
    // extra["PROPERTY_NAME"], someMap["MAP_KEY"], someList[0]
    is KtArrayAccessExpression -> {
      val arrayExpression = psiElement.arrayExpression ?: return noInjections
      val name = arrayExpression.text
      // extra["PROPERTY_NAME"]
      //
      // TODO(xof): handle qualified references to extra properties (e.g. rootProject.extra["prop1"])
      if (name == "extra") {
        val indices = psiElement.indexExpressions
        if (indices.size == 1) {
          val index = indices[0]
          if (index is KtStringTemplateExpression && !index.hasInterpolation()) {
            val entries = index.entries
            val entry = entries[0]
            val text = entry.text
            // TODO(xof): unquoting
            val element = context.resolveReference(text, true)
            return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, text))
          }
        }
      }
      // someMap["MAP_KEY"], someList[0], someMultiDimensionalThing["Key"][0][...]
      //
      // TODO(xof): handle dereferences of extra properties (e.g. extra["prop1"]["MAP_KEY"])
      else {
        val text = psiElement.text
        val element = context.resolveReference(text, true)
        return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, text))
      }
      return noInjections
    }
    // "foo bar", "foo $bar", "foo ${extra["PROPERTY_NAME"]}"
    is KtStringTemplateExpression -> {
      if (!psiElement.hasInterpolation()) return noInjections
      return psiElement.entries
        .flatMap { entry -> when(entry) {
          // any constant portion of a KtStringTemplateExpression
          is KtLiteralStringTemplateEntry -> noInjections
          // short-form interpolation $foo -- we know we have just a name, which we can resolve.
          is KtSimpleNameStringTemplateEntry -> entry.expression?.let { findInjections(context, it, includeResolved, entry) } ?: noInjections
          // long-form interpolation ${...} -- compute injections for the contained expression.
          is KtBlockStringTemplateEntry -> entry.expression?.let { findInjections(context, it, includeResolved, entry) } ?: noInjections
          else -> noInjections
        }}
        .toMutableList()
    }
    else -> return noInjections
  }
}

/**
 * Delete the psiElement for the given dslElement.
 */
internal fun deletePsiElement(dslElement : GradleDslElement, psiElement : PsiElement?) {
  if (psiElement == null || !psiElement.isValid) return
  val parent = psiElement.parent
  psiElement.delete()
  maybeDeleteIfEmpty(parent, dslElement)

  // Clear all invalid psiElements in the GradleDslElement tree.
  removePsiIfInvalid(dslElement)
}

internal fun maybeDeleteIfEmpty(psiElement: PsiElement, dslElement: GradleDslElement) {
  val parentDslElement = dslElement.parent
  // We don't want to delete lists and maps if they are empty.
  // For maps, we want to allow deleting a map inside another map, which means that if a map is empty but is inside another map,
  // we should allow deleting it
  if ((parentDslElement is GradleDslExpressionList && !parentDslElement.shouldBeDeleted()) ||
      (parentDslElement is GradleDslExpressionMap && !parentDslElement.shouldBeDeleted()) && parentDslElement.psiElement == psiElement) {
    return
  }
  deleteIfEmpty(psiElement, dslElement)
}

internal fun deleteIfEmpty(psiElement: PsiElement?, containingDslElement: GradleDslElement) {

  var parent = psiElement?.parent ?: return
  val dslParent = getNextValidParent(containingDslElement)

  if (!psiElement.isValid()) {
    // SKip deletion.
  }
  else {
    when (psiElement) {
      is KtScriptInitializer -> {
        if (psiElement.children.isEmpty()) {
          psiElement.delete()
        }
      }
      is KtBinaryExpression -> {  // This includes assignment expressions and maps elements.
        if (psiElement.right == null) psiElement.delete()
      }
      is KtBlockExpression -> {  // This represents Blocks structure without the { }.
        // Check if the block is empty, then delete it.
        // We should not delete a block if it has KtScript as parent because a script should always have a block even if empty.
        if ((dslParent == null || dslParent.isInsignificantIfEmpty) && psiElement.isNullExpressionOrEmptyBlock() &&
            parent !is KtScript) {
          psiElement.delete()
        }
      }
      is KtLambdaArgument -> {
        if (psiElement.getArgumentExpression() == null) {
          psiElement.delete()
        }
      }
      is KtLambdaExpression -> {
        if ((dslParent == null || dslParent.isInsignificantIfEmpty) && psiElement.children.isEmpty()) {
          psiElement.delete()
        }
      }
      is KtFunctionLiteral -> {
        if ((psiElement.bodyExpression == null || psiElement.bodyExpression.isNullExpressionOrEmptyBlock()) &&
            (dslParent == null || dslParent.isInsignificantIfEmpty)) {
          psiElement.delete()
          // If the parent is a KtLambdaExpression, delete it because KtLambdaExpression.getFunctionLiteral() cannot be null.
          if (parent is KtLambdaExpression) {
            val newParent = parent.parent
            parent.delete()
            parent = newParent
          }
        }
      }
      is KtCallExpression -> {  // This includes lists and maps as well.
        val argumentsList = psiElement.valueArgumentList
        val blockArguments = psiElement.lambdaArguments
        if ((argumentsList == null || argumentsList.arguments.isEmpty()) && blockArguments.isEmpty()) {
          psiElement.delete()
        }
      }
      is KtValueArgumentList -> {
        val arguments = psiElement.arguments
        if (arguments.isEmpty()) {
          psiElement.delete()
        }
      }
      is KtValueArgument -> {
        if (psiElement.getArgumentExpression() == null) {
          psiElement.delete()
        }
      }
      // TODO: add support for variables when available on Parser.
    }
  }

  if (!psiElement.isValid && dslParent != null && dslParent.isInsignificantIfEmpty) {
    // If we are deleting the dslElement parent itself ((psiElement == dslParent.psiElement)), move to dslparent as its the new element
    // to be deleted.
    maybeDeleteIfEmpty(parent, if (psiElement == dslParent.psiElement) dslParent else containingDslElement)
  }
}

internal fun processMethodCallElement(expression : GradleDslSettableExpression) : PsiElement? {
  val parent = expression.parent ?: return null
  val parentPsi = parent.create() ?: return null

  val expressionPsi = expression.unsavedValue ?: return null
  var argument : KtValueArgument
  val psiFactory = KtPsiFactory(expressionPsi.project)
  val valueArgument = psiFactory.createArgument(expression.value.toString())
  // support named argument. ex: plugin = "kotlin-android".
  if (expression.name.isNotEmpty()) {
    argument = psiFactory.createArgument(valueArgument.getArgumentExpression(), Name.identifier(expression.name))
  }
  else {
    argument = valueArgument
  }
  val added  = parentPsi.addBefore(argument, parentPsi.lastChild)
  expression.psiElement = added
  expression.commit()
  return expression.psiElement
}

internal fun getKtBlockExpression(psiElement: PsiElement) : KtBlockExpression? {
  if (psiElement is KtBlockExpression) return psiElement
  return (psiElement as? KtCallExpression)?.lambdaArguments?.lastOrNull()?.getLambdaExpression()?.bodyExpression
}

internal fun maybeUpdateName(element : GradleDslElement) {
  val oldName = element.nameElement.namedPsiElement
  val newName = element.nameElement.localName
  if (newName == null || oldName == null) return

  val newElement : PsiElement
  if (oldName is PsiNamedElement) {
    oldName.setName(newName)
    newElement = oldName
  }
  else {
    val psiElement = KtPsiFactory(element.psiElement?.project)?.createExpression(newName)
    newElement = oldName.replace(psiElement)
  }

  element.nameElement.commitNameChange(newElement)
}

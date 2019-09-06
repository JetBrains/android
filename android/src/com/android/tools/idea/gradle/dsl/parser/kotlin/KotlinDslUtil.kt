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
import com.android.tools.idea.gradle.dsl.parser.android.AbstractFlavorTypeDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.getNextValidParent
import com.android.tools.idea.gradle.dsl.parser.removePsiIfInvalid
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpressionOrEmptyBlock
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import java.math.BigDecimal
import kotlin.reflect.KClass

internal val ESCAPE_CHILD = Regex("[\\t ]+")

internal fun String.addQuotes(forExpression : Boolean) = if (forExpression) "\"$this\"" else "'$this'"

internal fun KtCallExpression.isBlockElement() : Boolean {
  return lambdaArguments.size == 1 && (valueArgumentList == null || (valueArgumentList as KtValueArgumentList).arguments.size < 2 &&
                                      isValidBlockName(this.name()))
}

internal fun isValidBlockName(blockName : String?) =
  blockName != null && blockName in listOf("configure", "create", "maybeCreate", "register", "getByName")
/**
 * Check if the caller psiElement is one of the parents for the given psiElement.
 */
internal fun PsiElement?.isParentOf(psiElement: PsiElement) : Boolean {
  var psiElement = psiElement
  while (psiElement != this) {
    psiElement = psiElement?.parent ?: return false
  }
  return true
}

internal fun KtCallExpression.name() : String? {
  return getCallNameExpression()?.getReferencedName()
}

internal fun getParentPsi(dslElement : GradleDslElement) : PsiElement? {
  // For extra block, we don't have a psiElement for the dslElement because in Kotlin we don't use the extra block, so we need to add
  // elements straight to the ExtDslElement' parent.
  return if (dslElement.parent is ExtDslElement) dslElement.parent?.parent?.create() else dslElement.parent?.create()
}

internal fun getPsiElementForAnchor(parent : PsiElement, dslAnchor : GradleDslElement?) : PsiElement? {
  var anchorAfter = if (dslAnchor == null) null else findLastPsiElementIn(dslAnchor)
  if (anchorAfter == null && parent is KtBlockExpression) {
    return adjustForKtBlockExpression(parent)
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

internal fun needToCreateParent(element: GradleDslElement): Boolean {
  val parent = element.parent
  // If the parent is an extra block dslElement, we never create a psiElement for it because we don't use it in kotlin.
  return parent != null && (parent.psiElement == null && parent !is ExtDslElement)
}

/**
 * Get the first non-empty element in a block expression.
 */
internal fun adjustForKtBlockExpression(blockExpression: KtBlockExpression) : PsiElement? {
  var element = blockExpression.firstChild

  // If the first child of the block is not an empty element, return it.
  if (element != null && !(element.text.isNullOrEmpty() || element.text.matches(ESCAPE_CHILD))) {
    return element
  }

  // Find first non-empty child of the block expression.
  while (element != null) {
    element = element.nextSibling
    if (element != null && (element.text.isNullOrEmpty() || element.text.matches(ESCAPE_CHILD))) {
      // This is an empty element, so continue.
      continue
    }
    // We found an element that is not empty, we exit the loop.
    break
  }
  return element
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
        valueText = StringUtil.escapeStringCharacters(value).addQuotes(true)
      }
      return KtPsiFactory(context.dslFile.project).createExpression(valueText)
    }
    is Int, is Boolean, is BigDecimal -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.toString())
    is RawText -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.getText())
    else -> error("Expression '${value}' not supported.")
  }
}

// Check if this is a block with a methodCall as name, and get the name... e.g. getByName("release") -> "release"
// Note: does not check whether this is a valid place for such a thing; this is purely syntax
internal fun methodCallBlockName(expression: KtCallExpression): String? {
  // TODO(xof): should we check the name of the method call against a whitelist? (create, getByName, ...)
  val arguments = expression.valueArgumentList?.arguments ?: return null
  if (arguments.size != 1) return null
  // TODO(xof): we should handle injections / resolving here:
  //  buildTypes.getByName("$foo") { ... }
  //  buildTypes.getByName(foo) { ... }
  val argument = arguments[0].getArgumentExpression()
  when (argument) {
    is KtStringTemplateExpression -> {
      if (argument.hasInterpolation()) return null
      return StringUtil.unquoteString(argument.entries[0].text)
    }
    else -> return null
  }
}

internal fun gradleNameFor(expression: KtExpression): String? {
  val sb = StringBuilder()
  var lastExtra = false
  var allValid = true

  // the visitor here is responsible for converting Kts DSL syntax, e.g. sourceSets.getByName("arbitrary").extra["foo"], into
  // something that the DSL (in particular GradleNameElement) is prepared to accept -- in the above example,
  // sourceSets.arbitrary.ext.foo.  We therefore have to pay attention to method calls and to array dereferences, and rewrite
  // all method calls that name blocks, and any array dereferences of the extra properties.
  expression.accept(object: KtTreeVisitorVoid() {
    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
      expression.arrayExpression?.accept(this, null)
      // translating here between Kts extra property lookup (array access, e.g. extra["foo"])
      // and GradleNameElement's expectation (field dereference, e.g. ext.foo).  Only do this
      // conversion if `extra' is the last thing we've seen in the arrayExpression.
      val index = expression.indexExpressions[0]
      if (lastExtra) {
        sb.append(".${StringUtil.unquoteString(index.text)}")
        lastExtra = false
      }
      else {
        sb.append("[${index.text}]")
      }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
      val name = methodCallBlockName(expression)
      if (name == null) {
        allValid = false
      }
      else {
        sb.append(name)
      }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
      expression.receiverExpression.accept(this, null)
      sb.append('.')
      expression.selectorExpression?.accept(this, null)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
      when (expression) {
        is KtSimpleNameExpression -> {
          lastExtra = (expression.text == "extra")
          sb.append(if (lastExtra) "ext" else expression.text)
        }
        else -> super.visitReferenceExpression(expression)
      }
    }
  }, null)

  return if (allValid) sb.toString() else null
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
    // foo, KotlinCompilerVersion, android.compileSdkVersion
    is KtNameReferenceExpression, is KtDotQualifiedExpression -> {
      val text = psiElement.text
      val element = context.resolveReference(text, true)
      return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, text))
    }
    // extra["PROPERTY_NAME"], someMap["MAP_KEY"], someList[0], rootProject.extra["kotlin_version"]
    is KtArrayAccessExpression -> {
      if (psiElement.arrayExpression == null) return noInjections
      val text = psiElement.text
      val element = context.resolveReference(text, true)
      return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, text))
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
  // If the psiElement is a KtValueArgument, we use the removeArgument method provided by the KTS psi that handles removing COMMAs between arguments.
  if (psiElement is KtValueArgument) {
    (parent as KtValueArgumentList).removeArgument(psiElement)
  }
  else {
    psiElement.delete()
  }
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

  var psiParent = psiElement?.parent ?: return
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
            psiParent !is KtScript) {
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
          if (psiParent is KtLambdaExpression) {
            val newParent = psiParent.parent
            psiParent.delete()
            psiParent = newParent
          }
        }
      }
      is KtCallExpression -> {  // This includes lists and maps as well.
        val argumentsList = psiElement.valueArgumentList
        val blockArguments = psiElement.lambdaArguments
        // Handle cases where the element is a block with callExpression as name (ex: getByName("debug")) => arguments are never empty
        // but if the block {} expression is empty, then we should delete he psiElement.
        if ((argumentsList == null || argumentsList.arguments.isEmpty()) && blockArguments.isEmpty() ||
            (argumentsList?.arguments?.size == 1 && dslParent != null && containingDslElement.isBlockElement
             && containingDslElement is AbstractFlavorTypeDslElement && containingDslElement.methodName == psiElement.name())) {
          psiElement.delete()
        }
      }
      is KtValueArgument -> {
        if (psiElement.getArgumentExpression() == null) {
          (psiParent as KtValueArgumentList).removeArgument(psiElement)
          // Delete any space that might remain after the argument deletion.
          if (psiParent.firstChild.node.elementType == LPAR && psiParent.firstChild.nextSibling.node.elementType == WHITE_SPACE) {
            psiParent.firstChild.nextSibling.delete()
          }
        }
      }
      is KtPropertyDelegate -> {
        if (psiElement.expression == null) psiElement.delete()
      }
      is KtProperty -> {
        // This applies for variables and properties.
        if (psiElement.delegate == null && psiElement.initializer == null) {
          psiElement.delete()
        }
      }
    }
  }

  // If psiParent is a child of the parentDsl psiElement, then we should check if psiParent is empty and should be deleted.
  // For KtValueArgumentList : we can't delete them without deleting the callExpression, because otherwise, if maybeDeleteIfEmpty() called
  // for the psiParent (i.e. callExpression) returns that the callExpression cannot be deleted, we will end up having just the
  // callExpression' reference name, which is invalid. This apply for example to maps that we might not want to delete.
  if ((psiElement is KtValueArgumentList || !psiElement.isValid) && dslParent != null && (dslParent.isInsignificantIfEmpty || dslParent.psiElement.isParentOf(psiParent))) {
    // If we are deleting the dslElement parent itself ((psiElement == dslParent.psiElement)), move to dslParent as it's the new element
    // to be deleted.
    maybeDeleteIfEmpty(psiParent, if (psiElement == dslParent.psiElement) dslParent else containingDslElement)
  }
}

/**
 * Given a literal expression, create it's PsiElement and add it to it's parent psiElement.
 */
internal fun createListElement(expression : GradleDslSettableExpression) : PsiElement? {
  val parent = expression.parent ?: return null
  val parentPsi = parent.create() ?: return null

  val expressionPsi = expression.unsavedValue ?: return null

  val added  = createPsiElementInsideList(parent, expression, parentPsi, expressionPsi) ?: return null
  expression.psiElement = added
  expression.commit()
  return expression.psiElement
}

/**
 * Given an literal that is a map element, create the corresponding psiElement and add it to the map (parent) psiElement, and update
 * the expression value.
 */
internal fun createMapElement(expression : GradleDslSettableExpression) : PsiElement? {
  val parent = requireNotNull(expression.parent)
  val parentPsiElement = parent.create() as? KtCallExpression ?: return null

  expression.psiElement = parentPsiElement
  val expressionValue = expression.unsavedValue ?: return null

  val psiFactory = KtPsiFactory(parentPsiElement.project)
  val expressionRightValue =
    if (expressionValue is KtConstantExpression) expressionValue.text else StringUtil.unquoteString(expressionValue.text).addQuotes(true)
  val argumentStringExpression =
    "${expression.name.addQuotes(true)} to $expressionRightValue"
  val mapArgument = psiFactory.createExpression(argumentStringExpression)

  val argumentValue = psiFactory.createArgument(mapArgument)

  val added =
    parentPsiElement.valueArgumentList?.addArgumentAfter(argumentValue, parentPsiElement.valueArgumentList?.arguments?.lastOrNull())

  val argumentExpression = added?.getArgumentExpression() as? KtBinaryExpression // Map elements are KtBinaryExpression.
  val expressionRight = argumentExpression?.right
  if (argumentExpression == null || expressionRight == null) return null
  
  expression.setExpression(expressionRight)
  expression.commit()
  expression.reset()
  return expression.psiElement

}

/**
 * Add an argument to a GradleDslList (parentDslElement). The PsiElement of the list (parentPsiElement) can either be :
 * KtCallExpression : for cases where we have a list in Kotlin (listOf())
 * KtBinaryExpression : for cases where we have binary expressions ( ex: map arguments or assignment expression)
 * KtValueArgumentList : for all the other cases (ex  : KtCallExpression arguments)
 * Others : not handled.
 */
internal fun createPsiElementInsideList(parentDslElement : GradleDslElement,
                                        dslElement : GradleDslSettableExpression,
                                        parentPsiElement: PsiElement,
                                        psiElement: PsiElement) : PsiElement? {
  val parentPsiElement = when (parentPsiElement){
    is KtCallExpression -> parentPsiElement.valueArgumentList ?: return null
    is KtBinaryExpression -> (parentPsiElement.right as? KtCallExpression)?.valueArgumentList ?: return null
    is KtValueArgumentList -> parentPsiElement
    else -> return null
  }

  val anchor = parentDslElement.requestAnchor(dslElement)

  // Create a valueArgument to add to the list.
  val psiFactory = KtPsiFactory(parentPsiElement.project)
  // support named argument. ex: plugin = "kotlin-android".
  val argument = if (dslElement.name.isNotEmpty()) psiFactory.createArgument(psiElement as? KtExpression, Name.identifier(dslElement.name))
  else psiFactory.createArgument(psiElement as? KtExpression)

  // If the dslElement has an anchor that is not null and that the list is not empty, we add it to the list after the anchor ;
  // otherwise, we add it at the beginning of the list.
  if (parentPsiElement.arguments.isNotEmpty() && anchor != null) {
    val anchorPsi =
      anchor.psiElement as? KtValueArgument ?:
      getNextValidParentPsiElement(anchor.psiElement, KtValueArgument::class) as? KtValueArgument ?: return null

    return parentPsiElement.addArgumentAfter(argument, anchorPsi)
  }
  return parentPsiElement.addArgumentBefore(argument, parentPsiElement.arguments.firstOrNull()).getArgumentExpression()
}

/**
 * Return, if found, first parent psiElement that is of the type eClass, otherwise, return null.
 */
internal fun getNextValidParentPsiElement(psiElement: PsiElement?, eClass: KClass<*>) : PsiElement? {
  var psiElement = psiElement ?: return null
  do {
    psiElement = psiElement.parent ?: return null
  } while (!eClass.isInstance(psiElement))

  return psiElement
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
    val psiElement : PsiElement = if (oldName.node.elementType == IDENTIFIER) {
      KtPsiFactory(element.psiElement?.project).createNameIdentifier(newName)
    } else {
      KtPsiFactory(element.psiElement?.project).createExpression(newName)
    }

    // For Kotlin, committing changes is a bit different, and if the psiElement is invalid, it throws an exception (unlike Groovy), so we
    // need to check if the oldName is still valid, otherwise, we use the psiElement created to update the name.
    if (!oldName.isValid) {
      element.nameElement.commitNameChange(psiElement)
      return
    }
    else {
      newElement = oldName.replace(psiElement)
    }
  }

  element.nameElement.commitNameChange(newElement)
}

internal fun createAndAddClosure(closure : GradleDslClosure, element : GradleDslElement) {
  // If element is a GradleDslMethodCall, then we should consider that this refers to a nested KtCallExpression psiElement in the KTS file
  // (ex: implementation(file())). In such case, element has as psiElement the part that corresponds to "file()" only, and we should not
  // add the block to it but rather to the parent element, which would result in implementation(file()) {}.
  var psiElement =
    (if (element is GradleDslMethodCall) getNextValidParentPsiElement(element.psiElement, KtCallExpression::class) else element.psiElement)
    ?: return

  if (psiElement is KtCallExpression && psiElement.name() != element.name) return

  val psiFactory = KtPsiFactory(psiElement.project)
  psiElement.addAfter(psiFactory.createWhiteSpace(), psiElement.lastChild)
  val block = (psiFactory.createExpression("foo() { }") as? KtCallExpression)?.lambdaArguments?.firstOrNull() ?: return
  val addedBlock =
    (psiElement.addAfter(block, psiElement.lastChild) as? KtLambdaArgument)?.getLambdaExpression()?.bodyExpression ?: return
  closure.psiElement = addedBlock
  closure.applyChanges()
  element.setParsedClosureElement(closure)
  element.setNewClosureElement(null)
}

internal fun addConfigBlock(expression : GradleDslSettableExpression) {
  val unsavedBlock = expression.unsavedConfigBlock ?: return
  val psiElement = expression.psiElement ?: return
  val psiFactory = KtPsiFactory(psiElement.project)

  psiElement.addAfter(psiFactory.createWhiteSpace(), psiElement.lastChild)
  psiElement.addAfter(unsavedBlock, psiElement.lastChild)
  expression.unsavedConfigBlock = null
}

/**
 * Create the PsiElement for a list that is an argument of a map. In kotlin each map argument is a KtBinaryExpression.
 */
internal fun createBinaryExpression(expressionList : GradleDslExpressionList) : PsiElement? {
  val parent = expressionList.parent as? GradleDslExpressionMap ?:
               error("Can't create expression for parent not being GradleDslExpressionMap")

  val parentPsiElement = parent.create() ?: return null

  val psiFactory = KtPsiFactory(parentPsiElement.project)
  val listName = expressionList.name
  if (listName.isEmpty()) error("The list Name can't be empty.")

  val expression = psiFactory.createExpression("\"$listName\" to listOf()") as? KtBinaryExpression ?: return null
  val added : PsiElement?

  val mapPsiElement = when (parentPsiElement) {
    // This is the case when a map is a parameter of a KtCallExpression and use the psiElement of the expression arguments list.
    // Ex. implementation(mapOf()).
    is KtValueArgumentList -> parentPsiElement.arguments[0].getArgumentExpression()
    // This is the case where we can have property = mapOf(), where the map has the psi element of the binary expression.
    is KtBinaryExpression -> requireNotNull(parentPsiElement.right)
    // This is the case where the map dsl element uses it's proper psiElement (i.e. mapOf()).
    is KtCallExpression -> parentPsiElement
    else -> return null
  }

  // Get The map arguments list that will be updated and add a new argument to it.
  val argumentsList = (mapPsiElement as? KtCallExpression)?.valueArgumentList ?: return null
  val mapValueArgument = psiFactory.createArgument(expression)
  val lastArgument = argumentsList.arguments.last()
  added = argumentsList.addArgumentAfter(mapValueArgument, lastArgument)

  if (added is KtValueArgument) {
    expressionList.psiElement = added.getArgumentExpression()
    return expressionList.psiElement
  }

  return null
}

internal fun hasNewLineBetween(start : PsiElement, end : PsiElement) : Boolean {
  if(start.parent === end.parent && start.startOffsetInParent <= end.startOffsetInParent) {
    var element = start
    while (element !== end) {
      // WHITE_SPACE represents a group of escape characters and a new line can have spaces for indentation,
      // so we check that it starts with a new line.
      if (element.node.elementType == WHITE_SPACE && element.node.text.startsWith("\n")) {
        return true
      }
      element = element.nextSibling
    }
    return false
  }
  return true
}

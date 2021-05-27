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
package com.android.tools.idea.layoutinspector.resource

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.lang.Integer.min
import java.util.IdentityHashMap

/**
 * Service to find the [SourceLocation] of a lambda found in Compose.
 */
class LambdaResolver(project: Project) : ComposeResolver(project) {
  private val visitor = LambdaVisitor()

  /**
   * Find the lambda [SourceLocation].
   *
   * The compiler will generate a synthetic class for each lambda invocation.
   * @param packageName the class name of the enclosing class of the lambda. This is the first part of the synthetic class name.
   * @param fileName the name of the enclosing file (without the path).
   * @param lambdaName the second part of the synthetic class name i.e. without the enclosed class name
   * @param functionName the function called if this is a function reference, empty if this is a lambda expression
   * @param startLine the starting line of the lambda invoke method as seen by JVMTI (1 based)
   * @param startLine the last line of the lambda invoke method as seen by JVMTI (1 based)
   */
  fun findLambdaLocation(
    packageName: String,
    fileName: String,
    lambdaName: String,
    functionName: String,
    startLine: Int,
    endLine: Int
  ): SourceLocation {
    if (startLine < 1 || endLine < 1) {
      return unknown(fileName)
    }
    val ktFile = findKotlinFile(fileName) { it == packageName } ?: return unknown(fileName)
    val doc = ktFile.virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) } ?: return unknown(fileName, ktFile)

    val possible = findPossibleLambdas(ktFile, doc, functionName, startLine, endLine)
    val lambda = selectLambdaFromSynthesizedName(possible, lambdaName)
    val checkedStartLine = min(startLine - 1, doc.lineCount)
    val navigatable = lambda?.navigationElement as? Navigatable ?: OpenFileDescriptor(project, ktFile.virtualFile, checkedStartLine, 0)
    val actualLine = 1 + if (lambda != null) doc.getLineNumber(lambda.startOffset) else checkedStartLine
    return SourceLocation("${fileName}:$actualLine", navigatable)
  }

  private fun unknown(fileName: String, ktFile: KtFile? = null): SourceLocation =
    SourceLocation("$fileName:unknown", ktFile)

  /**
   * Return all the lambda/callable reference expressions from [ktFile] that are contained entirely within the line range.
   *
   * If the line range contains multiple lines then make sure all internal lines are fully covered by the returned lambdas.
   *
   * @return a map from a lambda or callable reference to the nesting level from a top element.
   */
  private fun findPossibleLambdas(
    ktFile: KtFile,
    doc: Document,
    functionName: String,
    startLine: Int,
    endLine: Int
  ): Map<KtExpression, Int> {
    val offsetRange = try {
      doc.getLineStartOffset(startLine - 1)..(doc.getLineEndOffset(endLine - 1))
    }
    catch (ex: IndexOutOfBoundsException) {
      return emptyMap()
    }
    if (offsetRange.isEmpty()) {
      return emptyMap()
    }
    val internalRange = if (endLine <= startLine) IntRange.EMPTY else try {
      doc.getLineEndOffset(startLine - 1)..(doc.getLineStartOffset(endLine - 1))
    }
    catch (ex: IndexOutOfBoundsException) {
      IntRange.EMPTY
    }
    val possible = IdentityHashMap<KtExpression, Int>()
    visitor.forEachLambda(ktFile) { expr, nesting, recurse ->
      val range = IntRange(expr.startOffset, expr.endOffset)
      val codeRange = codeRangeOf(expr)
      if (typeMatch(expr, functionName) && offsetRange.contains(codeRange) && range.contains(internalRange)) {
        possible[expr] = nesting
      }
      recurse()
    }
    return possible
  }

  private fun codeRangeOf(expr: KtExpression): IntRange {
    var startOffset = expr.startOffset
    var endOffset = expr.endOffset
    if (expr is KtLambdaExpression) {
      // Skip any preceding comments in the lambda body:
      startOffset = expr.bodyExpression?.children?.firstOrNull()?.startOffset ?: startOffset
      // Skip trailing non code elements in the lambda body:
      endOffset = lastCodeElement(expr)?.endOffset ?: endOffset
    }
    return IntRange(startOffset, endOffset)
  }

  private fun IntRange.contains(range: IntRange): Boolean =
    range.isEmpty() || (contains(range.first) && contains(range.last))

  /**
   * Attempt to go back from the right curly brace in a lambda to the first expression part
   * that is a code element as described in [isCodeElement].
   */
  private fun lastCodeElement(lambda: KtLambdaExpression): PsiElement? {
    var expr = lambda.rightCurlyBrace as? PsiElement ?: return null
    while (!isCodeElement(expr)) {
      while (expr.prevSibling == null) {
        expr = expr.parent ?: return null
        if (expr == lambda) {
          return null
        }
      }
      expr = expr.prevSibling ?: return null
      while (expr.lastChild != null) {
        expr = expr.lastChild
      }
    }
    return expr
  }

  /**
   * Non code elements are: whitespace, an end parenthesis ")", or an end bracket "}"
   */
  private fun isCodeElement(expr: PsiElement): Boolean {
    if (expr is PsiWhiteSpace) {
      return false
    }
    val leafElement = expr as? LeafElement ?: return true
    val token = leafElement.elementType as? KtSingleValueToken ?: return true
    return token.value != ")" && token.value != "}"
  }

  private fun typeMatch(expression: KtExpression, functionName: String): Boolean =
    if (functionName.isNotEmpty()) expression is KtCallableReferenceExpression &&
                                   expression.callableReference.getReferencedName() == functionName
    else expression is KtLambdaExpression

  /**
   * Select the most likely lambda from the [lambdas] found from line numbers, by using the synthetic name [lambdaName].
   */
  private fun selectLambdaFromSynthesizedName(lambdas: Map<KtExpression, Int>, lambdaName: String): KtExpression? {
    when (lambdas.size) {
      0 -> return null
      1 -> return lambdas.keys.single() // no need investigate the lambdaName
    }
    val wantedNestingLevel = lambdaName.count { it == '$' }
    val candidates = lambdas.entries.filter { it.value == wantedNestingLevel }.map { it.key }
    when (candidates.size) {
      0 -> return null // the nesting level didn't match
      1 -> return candidates.first() // only one match for the nesting level, return that match
    }
    val arbitrary = candidates.first()
    val topElement = findParentElement(arbitrary) ?: return null
    val selector = findDesiredLambdaSelectorFromName(lambdaName) ?: return null
    val index = selector - 1
    val nestedUnderTopElement = mutableListOf<KtExpression>()
    visitor.forEachLambda(topElement, excludeTopElements = true) { expression, _, _ -> nestedUnderTopElement.add(expression) }
    val candidate = if (index in nestedUnderTopElement.indices) nestedUnderTopElement[index] else return null
    return if (lambdas.contains(candidate)) candidate else null
  }

  /**
   * Find the selector as indicated by the lambdaName
   *
   * The indices generated by the compiler are 1 based.
   */
  private fun findDesiredLambdaSelectorFromName(lambdaName: String): Int? {
    val part = lambdaName.substringAfterLast('$')
    return part.toIntOrNull()
  }

  /**
   * Find the closest parent element of interest that contains this [lambda].
   *
   * The last index in the synthetic name will be an index of a lambda inside
   * the closest parent that is either a class, method, variable, or another lambda.
   * Find the parent such that we can enumerate the lambdas inside this parent element.
   */
  private fun findParentElement(lambda: KtExpression): KtElement? {
    var next = lambda.parent as? KtElement
    while (next != null) {
      when (next) {
        is KtClass,
        is KtNamedFunction,
        is KtProperty,
        is KtLambdaExpression -> return next

        else -> next = next.parent as? KtElement
      }
    }
    return null
  }

  /**
   * Visitor for finding lambda expressions and function references
   */
  private class LambdaVisitor : KtTreeVisitor<VisitorData>() {
    private var nesting = 0

    /**
     * For each lambda or function reference found in [startElement] call [callable] with the arguments:
     * - the lambda expression found
     * - the nesting level starting at [startElement]
     * - a function to recurse into the lambda
     *
     * If [excludeTopElements] is true the visitor will NOT recurse into the top elements:
     * - class, method, or variable.
     */
    fun forEachLambda(
      startElement: KtElement,
      excludeTopElements: Boolean = false,
      callable: (KtExpression, Int, () -> Unit) -> Unit
    ) {
      nesting = 0
      startElement.acceptChildren(this, VisitorData(excludeTopElements, callable))
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression, data: VisitorData): Void? {
      data.callable(expression, nesting) {
        nesting++
        super.visitLambdaExpression(expression, data)
        nesting--
      }
      return null
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression, data: VisitorData): Void? {
      data.callable(expression, nesting) {
        nesting++
        super.visitCallableReferenceExpression(expression, data)
        nesting--
      }
      return null
    }

    override fun visitClass(klass: KtClass, data: VisitorData): Void? {
      if (!data.excludeTopElements) {
        val nestingBefore = nesting
        nesting = 0
        super.visitClass(klass, data)
        nesting = nestingBefore
      }
      return null
    }

    override fun visitProperty(property: KtProperty, data: VisitorData): Void? {
      if (!data.excludeTopElements) {
        val nestingBefore = nesting
        nesting = 0
        super.visitProperty(property, data)
        nesting = nestingBefore
      }
      return null
    }

    override fun visitNamedFunction(function: KtNamedFunction, data: VisitorData): Void? {
      if (!data.excludeTopElements) {
        val nestingBefore = nesting
        nesting = 0
        super.visitNamedFunction(function, data)
        nesting = nestingBefore
      }
      return null
    }
  }

  private data class VisitorData(
    val excludeTopElements: Boolean,
    val callable: (KtExpression, Int, () -> Unit) -> Unit
  )
}

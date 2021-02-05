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
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
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

    val navigatable = lambda?.navigationElement as? Navigatable ?: return unknown(fileName, ktFile)
    val startLineOffset = doc.getLineStartOffset(startLine)
    val line = startLine + if (lambda.startOffset < startLineOffset) 0 else 1
    return SourceLocation("${fileName}:$line", navigatable)
  }

  private fun unknown(fileName: String, ktFile: KtFile? = null): SourceLocation =
    SourceLocation("$fileName:unknown", ktFile)

  /**
   * Return all the lambda/callable reference expressions from [ktFile] that are contained entirely within the line range.
   */
  private fun findPossibleLambdas(ktFile: KtFile, doc: Document, functionName: String, startLine: Int, endLine: Int): List<KtExpression> {
    val offsetRange = try {
      doc.getLineStartOffset(startLine - 1)..(doc.getLineEndOffset(endLine - 1))
    }
    catch (ex: IndexOutOfBoundsException) {
      return emptyList()
    }
    if (offsetRange.isEmpty()) {
      return emptyList()
    }
    val possible = mutableListOf<KtExpression>()
    visitor.forEachLambda(ktFile) { expr, recurse ->
      if (typeMatch(expr, functionName) && expr.startOffset <= offsetRange.last && expr.endOffset >= offsetRange.first) {
        possible.add(expr)
      }
      recurse()
    }
    return possible
  }

  private fun typeMatch(expression: KtExpression, functionName: String): Boolean =
    if (functionName.isNotEmpty()) expression is KtCallableReferenceExpression &&
                                   expression.callableReference.getReferencedName() == functionName
    else expression is KtLambdaExpression

  /**
   * Select the most likely lambda from the [lambdas] found from line numbers, by using the synthetic name [lambdaName].
   */
  private fun selectLambdaFromSynthesizedName(lambdas: List<KtExpression>, lambdaName: String): KtExpression? {
    when (lambdas.size) {
      0 -> return null
      1 -> return lambdas.single() // no need investigate the lambdaName
    }
    val arbitrary = lambdas.first()
    val topElement = findTopElement(arbitrary) ?: return arbitrary
    val selector = findDesiredLambdaSelectorFromName(lambdaName)
    if (selector.isEmpty()) {
      return arbitrary
    }
    return findLambdaFromSelector(topElement, selector, lambdas) ?: return arbitrary
  }

  /**
   * Find the selector as a list of indices.
   *
   * Each list element correspond to a nesting level among the lambdas found under a top element.
   * The indices generated by the compiler are 1 based.
   */
  private fun findDesiredLambdaSelectorFromName(lambdaName: String): List<Int> {
    val elements = lambdaName.split('$')
    val index = elements.indexOfLast { !isPureDigits(it) } + 1
    if (index > elements.size) {
      return emptyList()
    }
    return elements.subList(index, elements.size).map { it.toInt() }
  }

  private fun isPureDigits(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isDigit() }

  /**
   * Find the closest parent element of interest that contains this [lambda].
   *
   * The synthetic name will include class names, method names, and variable names.
   * Find the closest parent to [lambda] which is one of those 3 elements.
   */
  private fun findTopElement(lambda: KtExpression): KtElement? {
    var next = lambda.parent as? KtElement
    while (next != null) {
      when (next) {
        is KtClass,
        is KtNamedFunction,
        is KtProperty -> return next

        else -> next = next.parent as? KtElement
      }
    }
    return null
  }

  /**
   * Find the most likely lambda from the [top] element based on [selector].
   *
   * @param top the top element which is either a class, method, or variable.
   * @param selector the indices for each nesting level as indicated by the synthetic class name of the lambda.
   * @param lambdas the lambdas found in the line interval.
   */
  private fun findLambdaFromSelector(
    top: KtElement,
    selector: List<Int>,
    lambdas: List<KtExpression>,
  ): KtExpression? {
    var nestingLevel = 0
    val indices = IntArray(selector.size)
    var stop = false
    var bestLambda: KtExpression? = null

    visitor.forEachLambda(top, excludeTopElements = true) forEach@{ expression, recurse ->
      if (stop || (nestingLevel < selector.size && selector[nestingLevel] < ++indices[nestingLevel])) {
        stop = true
        return@forEach
      }
      if (nestingLevel >= selector.size || selector[nestingLevel] > indices[nestingLevel]) {
        return@forEach
      }
      if (expression in lambdas) {
        bestLambda = expression
      }
      nestingLevel++
      recurse()
      nestingLevel--
    }
    return bestLambda
  }

  /**
   * Visitor for finding lambda expressions and function references
   */
  private class LambdaVisitor : KtTreeVisitor<VisitorData>() {

    /**
     * For each lambda or function reference found in [startElement] call
     * [callable] with the arguments: the lambda and a function to recurse into the lambda
     * If [excludeTopElements] is true the visitor will NOT recurse into the top elements:
     * class, method, or variable.
     */
    fun forEachLambda(
      startElement: KtElement,
      excludeTopElements: Boolean = false,
      callable: (KtExpression, () -> Unit) -> Unit
    ) {
      startElement.acceptChildren(this, VisitorData(excludeTopElements, callable))
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression, data: VisitorData): Void? {
      data.callable(expression) { super.visitLambdaExpression(expression, data) }
      return null
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression, data: VisitorData): Void? {
      data.callable(expression) { super.visitCallableReferenceExpression(expression, data) }
      return null
    }

    override fun visitClass(klass: KtClass, data: VisitorData): Void? {
      if (!data.excludeTopElements) {
        super.visitClass(klass, data)
      }
      return null
    }

    override fun visitProperty(property: KtProperty, data: VisitorData): Void? {
      if (!data.excludeTopElements) {
        super.visitProperty(property, data)
      }
      return null
    }

    override fun visitNamedFunction(function: KtNamedFunction, data: VisitorData): Void? {
      if (!data.excludeTopElements) {
        super.visitNamedFunction(function, data)
      }
      return null
    }
  }

  private data class VisitorData(
    val excludeTopElements: Boolean,
    val callable: (KtExpression, () -> Unit) -> Unit
  )
}

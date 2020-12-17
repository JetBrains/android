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
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.math.max

/**
 * Service to find the [SourceLocation] of a lambda found in Compose.
 */
class LambdaResolver(project: Project) : ComposeResolver(project) {
  /**
   * Find the lambda [SourceLocation].
   *
   * The compiler will generate a synthetic class for each lambda invocation.
   * @param enclosedClassName the class name of the enclosing class of the lambda. This is the first part of the synthetic class name.
   * @param lambdaName the second part of the synthetic class name i.e. without the enclosed class name
   * @param startLine the starting line of the lambda invoke method as seen by JVMTI (zero based)
   * @param startLine the last line of the lambda invoke method as seen by JVMTI (zero based)
   */
  fun findLambdaLocation(enclosedClassName: String, lambdaName: String, startLine: Int, endLine: Int): SourceLocation? {
    if (startLine < 0 || endLine < 0) {
      return null
    }
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val enclosedClass = javaPsiFacade.findClass(enclosedClassName, GlobalSearchScope.allScope(project))?.toUElement() ?: return null
    val file = enclosedClass.javaPsi?.containingFile ?: return null
    val doc = file.virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) } ?: return null

    val possible = findPossibleLambdas(enclosedClass, doc, startLine, endLine)
    val lambda = selectLambdaBasedOnSynthesizedLambdaClassName(possible, lambdaName)

    val navigatable = lambda?.sourcePsi?.navigationElement as? Navigatable ?: return null
    val startLineOffset = doc.getLineStartOffset(startLine)
    val line = startLine + if (lambda.sourcePsi!!.startOffset < startLineOffset) 0 else 1
    return SourceLocation("${file.name}:$line", navigatable)
  }

  /**
   * Return all the lambda expressions from [enclosedClass] that are contained entirely within the line range.
   */
  private fun findPossibleLambdas(enclosedClass: UElement, doc: Document, startLine: Int, endLine: Int): List<ULambdaExpression> {
    val startLineOffset = doc.getLineStartOffset(startLine)
    val braceOffset = openBraceAtEol(doc, startLine - 1, startLineOffset) // include open bracket from end of previous line
    val offsetRange = (startLineOffset - braceOffset)..(doc.getLineEndOffset(endLine))
    if (offsetRange.isEmpty()) {
      return emptyList()
    }
    val possible = mutableListOf<ULambdaExpression>()
    val findLambdaWithinRangeVisitor = object : AbstractUastVisitor() {
      override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
        if (node.sourcePsi?.startOffset in offsetRange && node.sourcePsi?.endOffset in offsetRange) {
          possible.add(node)
        }
        return false
      }
    }
    enclosedClass.accept(findLambdaWithinRangeVisitor)
    return possible
  }

  /**
   * Compute the offset from an open brace from the end of the [line] to [startOfNextLine].
   * We need this offset to adjust the offset search range because the start line we get from the agent will be
   * the line after the open brace in this case.
   */
  private fun openBraceAtEol(doc: Document, line: Int, startOfNextLine: Int): Int {
    if (line < 0) {
      return 0
    }
    val start = doc.getLineStartOffset(line)
    val text = doc.text.subSequence(start, startOfNextLine).trimEnd()
    return if (text.endsWith('{')) max(0, startOfNextLine - text.length - start + 1) else 0
  }

  /**
   * Select the most likely lambda from the [lambdas] found from line numbers, by using the synthetic name [lambdaName].
   */
  private fun selectLambdaBasedOnSynthesizedLambdaClassName(lambdas: List<ULambdaExpression>, lambdaName: String): ULambdaExpression? {
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
  private fun findTopElement(lambda: ULambdaExpression): UElement? {
    var next = lambda.uastParent
    while (next != null) {
      when (next) {
        is UClass,
        is UMethod,
        is UVariable -> return next

        else -> next = next.uastParent
      }
    }
    return null
  }

  /**
   * Find the most likely lambda from the [top] element.
   *
   * @param top the top element which is either a class, method, or variable.
   * @param selector the indices for each nesting level as indicated by the synthetic class name of the lambda.
   * @param lambdas the lambdas found in the line interval.
   */
  private fun findLambdaFromSelector(top: UElement, selector: List<Int>, lambdas: List<ULambdaExpression>): ULambdaExpression? {
    var nestingLevel = 0
    val indices = IntArray(selector.size)
    var stop = false
    var bestLambda: ULambdaExpression? = null

    val findLambdaFromSelectorVisitor = object : AbstractUastVisitor() {
      override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
        if (stop || (nestingLevel < selector.size && selector[nestingLevel] < ++indices[nestingLevel])) {
          stop = true
          return true
        }
        if (nestingLevel >= selector.size || selector[nestingLevel] > indices[nestingLevel]) {
          return true
        }
        if (node in lambdas) {
          bestLambda = node
        }
        nestingLevel++
        return false
      }

      override fun afterVisitLambdaExpression(node: ULambdaExpression) {
        nestingLevel--
      }

      override fun visitElement(node: UElement): Boolean = stop
    }
    top.accept(findLambdaFromSelectorVisitor)
    return bestLambda
  }
}

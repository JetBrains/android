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
package com.android.tools.idea.insights.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.UastVisitor

/** Finds the [cause] invocation within the [element]. */
fun findMatchingCause(element: UElement, cause: Cause, range: TextRange): UElement? {
  return when (cause) {
    is Cause.Frame -> findMatchingMethodCall(element, cause, range)
    is Cause.Throwable -> findMatchingThrow(element, cause, range)
  }
}

private fun findMatchingThrow(
  element: UElement,
  cause: Cause.Throwable,
  range: TextRange
): UElement? {
  ProgressManager.checkCanceled()
  val foundChild = AtomicReference<UElement>()
  element.accept(
    object : UastVisitor {
      override fun visitElement(node: UElement) = false

      override fun visitThrowExpression(node: UThrowExpression): Boolean {
        if (node.sourcePsi?.textRange?.intersects(range) != true) {
          return false
        }

        val exception = node.thrownExpression
        if (exception !is UCallExpression) {
          return false
        }
        if (
          exception.kind == UastCallKind.CONSTRUCTOR_CALL &&
            (exception.classReference?.resolve() as? PsiClass)?.qualifiedName == cause.exceptionType
        ) {
          foundChild.set(node)
          return true
        }
        if (exception.resolve()?.returnType.resolve()?.qualifiedName == cause.exceptionType) {
          foundChild.set(node)
          return true
        }
        return false
      }
    }
  )
  foundChild.get()?.let {
    return it
  }
  return findMatchingThrow(element.getParentOfType<UThrowExpression>() ?: return null, cause, range)
}

private fun findMatchingMethodCall(
  element: UElement,
  cause: Cause.Frame,
  range: TextRange
): UElement? {
  ProgressManager.checkCanceled()
  val foundChild = AtomicReference<UElement>()
  element.accept(
    object : UastVisitor {
      override fun visitElement(node: UElement) = false

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (node.sourcePsi?.textRange?.intersects(range) != true) {
          return false
        }

        if (node.kind == UastCallKind.METHOD_CALL || node.kind == UastCallKind.CONSTRUCTOR_CALL) {
          with(node.resolve() ?: return false) {
            if (cause.frame.matches(containingClass?.qualifiedName ?: "", name)) {
              foundChild.set(node)
              return true
            }
          }
        }
        return false
      }
    }
  )
  foundChild.get()?.let {
    return it
  }
  return findMatchingMethodCall(
    element.getParentOfType<UThrowExpression>() ?: return null,
    cause,
    range
  )
}

val PsiFile.candidateFileNames: Set<String>
  get() =
    virtualFile.name.substringBeforeLast(".").let { baseName ->
      setOf("$baseName.java", "$baseName.kt")
    }

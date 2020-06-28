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
package com.android.tools.idea.editors.literals

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.PsiExpressionEvaluator
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.types.TypeUtils
import java.util.Objects

interface PsiElementUniqueIdProvider {
  fun getUniqueId(element: PsiElement): String
}

/**
 * A [PsiElementUniqueIdProvider] that provides a consistent ID across PSI modifications. If a literal survives the PSI modification,
 * it is guaranteed to have the same ID.
 * To do that, this provider will generate a unique ID for the initial call and cache it for subsequent calls to make sure it's maintained.
 *
 * This is meant to be used for testing as the IDs will not be consistent if the file is closed and re-opened.
 */
@VisibleForTesting
object SimplePsiElementUniqueIdProvider : PsiElementUniqueIdProvider {
  private val SAVED_UNIQUE_ID = Key.create<String>("${SimplePsiElementUniqueIdProvider.javaClass.name}.uniqueId")

  private fun calculateNewUniqueId(element: PsiElement): String =
    Objects.hash(element.containingFile.modificationStamp, element.textRange).toString(16)

  override fun getUniqueId(element: PsiElement): String = element.getCopyableUserData(
    SAVED_UNIQUE_ID) ?: calculateNewUniqueId(
    element).also {
    // Save in the cache
    element.putCopyableUserData(SAVED_UNIQUE_ID, it)
  }
}

/**
 * A reference to a literal constant.
 */
interface LiteralReference {
  /** The initial range for the constant. Can be used for sorting and/or highlighting. */
  val initialTextRange: TextRange

  /** Identifier containing the class name and method name where this literal is declared. */
  val elementPath: String

  /** Returns whether this element is still valid (exists in the PSI) */
  val isValid: Boolean

  /** A unique id for this literal reference. The reference will be kept even when the value changes. */
  val uniqueId: String

  /** The text value of [constantValue]. */
  val text: String

  /** The constant value. */
  val constantValue: Any?
}

/**
 * Interface for expression evaluators that calculate the value of an expression.
 */
interface ConstantEvaluator {
  fun evaluate(expression: PsiElement): Any?
}

/**
 * [ConstantEvaluator] for Kotlin.
 */
object KotlinConstantEvaluator : ConstantEvaluator {
  override fun evaluate(expression: PsiElement): Any? {
    require(expression is KtExpression)

    return ConstantExpressionEvaluator.getConstant(expression, expression.analyze())?.getValue(TypeUtils.NO_EXPECTED_TYPE)
  }
}

/**
 * [ConstantEvaluator] for PSI Expressions.
 */
object PsiConstantEvaluator : ConstantEvaluator {
  private val psiEvaluator = PsiExpressionEvaluator()
  override fun evaluate(expression: PsiElement): Any? = psiEvaluator.computeConstantExpression(expression, true)
}

/**
 * [ConstantEvaluator] for PSI elements where we only need the text.
 */
private object PsiTextConstantEvaluator : ConstantEvaluator {
  override fun evaluate(expression: PsiElement): Any? = expression.text
}

data class ElementPath(val className: String, val methodName: String) {
  override fun toString(): String = "${className}.${methodName}"
}

private fun elementPath(element: PsiElement): String {
  val (className, methodName) = when (element) {
    is KtElement -> {
      val className =
        element.containingClass()?.fqName ?: element.containingKtFile.findFacadeClass()?.getQualifiedName() ?: ""
      val methodName = PsiTreeUtil.getContextOfType(element, KtNamedFunction::class.java)?.name

      className to methodName
    }
    else -> {
      PsiTreeUtil.getContextOfType(element, PsiClass::class.java)?.getQualifiedName() to PsiTreeUtil.getContextOfType(element,
                                                                                                                      PsiMethod::class.java)?.name
    }
  }

  return "$className.${methodName ?: "<init>"}"
}

/**
 * [LiteralReference] implementation that keeps track of modifications.
 */
private class LiteralReferenceImpl(originalElement: PsiElement,
                                   uniqueIdProvider: PsiElementUniqueIdProvider,
                                   private val constantEvaluator: ConstantEvaluator) : LiteralReference, ModificationTracker {
  private val elementPointer: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(originalElement)
  override val elementPath = elementPath(originalElement)
  override val initialTextRange: TextRange = originalElement.textRange
  override val uniqueId = uniqueIdProvider.getUniqueId(originalElement)
  private val initialConstantValue = constantEvaluator.evaluate(originalElement)
  private var lastCachedConstantValue = initialConstantValue
  val element: PsiElement?
    get() = ReadAction.compute<PsiElement?, Throwable> { elementPointer.element }
  override val isValid: Boolean
    get() = elementPointer.range != null

  override val constantValue: Any?
    get() = element?.let {
      ReadAction.compute<Any?, Throwable> {
        constantEvaluator.evaluate(it)
      }
    }

  private val fileModificationTracker = ModificationTracker { elementPointer.containingFile?.modificationStamp ?: -1 }
  private var lastFileModificationCount = 0L
  private var localModificationTracker = SimpleModificationTracker()
  override fun getModificationCount(): Long {
    if (!isValid) {
      // The element is not valid anymore so it will not be updated again.
      return localModificationTracker.modificationCount
    }

    if (lastFileModificationCount != fileModificationTracker.modificationCount && lastCachedConstantValue != constantValue) {
      lastFileModificationCount = fileModificationTracker.modificationCount
      localModificationTracker.incModificationCount()
    }
    return localModificationTracker.modificationCount
  }

  override val text: String
    get() = ReadAction.compute<String, Throwable> {
      element?.text ?: "<null>"
    }
}

/**
 * A snapshot for a list of [LiteralReference]s frozen in time. You can obtain a new updated snapshot by calling [newSnapshot].
 */
interface LiteralReferenceSnapshot {
  /**
   * List of all the [LiteralReference]s in this snapshot.
   */
  val all: Collection<LiteralReference>

  /**
   * List of the [LiteralReference]s that have changed since the snapshot was taken.
   */
  val modified: Collection<LiteralReference>

  /**
   * Create a new [LiteralReferenceSnapshot] where all references are up to date.
   */
  fun newSnapshot(): LiteralReferenceSnapshot
}

object EmptyLiteralReferenceSnapshot : LiteralReferenceSnapshot {
  override val all = emptyList<LiteralReference>()
  override val modified = emptyList<LiteralReference>()
  override fun newSnapshot() = this
}

private class LiteralReferenceSnapshotImpl(references: Collection<LiteralReferenceImpl>) : LiteralReferenceSnapshot {
  private val tracking = references
    .filter { it.isValid }
    .map { it to it.modificationCount }
    .toMap()
    .toMutableMap()

  override val all: Collection<LiteralReference>
    get() = tracking.keys
  override val modified: Collection<LiteralReference>
    get() = tracking.filter {
      it.key.modificationCount != it.value && it.key.isValid
    }
      .map { it.key }
      .toList()

  override fun newSnapshot(): LiteralReferenceSnapshot = LiteralReferenceSnapshotImpl(tracking.keys)
}

class LiteralsManager(private val uniqueIdProvider: PsiElementUniqueIdProvider = SimplePsiElementUniqueIdProvider) {
  private fun findLiterals(root: PsiElement,
                           expressionType: Class<*>,
                           constantEvaluator: ConstantEvaluator,
                           elementFilter: (PsiElement) -> Boolean): LiteralReferenceSnapshot {
    val savedLiterals = mutableListOf<LiteralReferenceImpl>()
    ReadAction.nonBlocking {
      root.acceptChildren(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
          if (!elementFilter(element)) return

          if (expressionType.isInstance(element)) {
            constantEvaluator.evaluate(element)?.let {
              savedLiterals.add(LiteralReferenceImpl(element, uniqueIdProvider, constantEvaluator))
              // Finish the expression recursion as soon as we have a valid constant.
              // This is done so for an expression like 3 + 2, we only higlight the expression and not the individual terms.
              return
            }

            // Special case for string templates
            if (element is KtStringTemplateExpression) {
              // A template can generate multiple constants. For example "Hello $name!!" will generate two:
              //  - "Hello "
              //  - "!!"
              // This is not currently supported by the ConstantExpressionEvaluator in the plugin so we handle it here.
              element.entries.forEach {
                if (it is KtLiteralStringTemplateEntry) {
                  savedLiterals.add(LiteralReferenceImpl(it, uniqueIdProvider, PsiTextConstantEvaluator))
                }
              }
              return
            }
          }

          super.visitElement(element)
        }
      })
    }.executeSynchronously()

    return LiteralReferenceSnapshotImpl(savedLiterals)
  }

  fun findLiterals(root: PsiElement): LiteralReferenceSnapshot =
    if (root.language == KotlinLanguage.INSTANCE) {
      findLiterals(root, KtExpression::class.java, KotlinConstantEvaluator) {
        it !is KtAnnotationEntry
      }
    }
    else {
      findLiterals(root, PsiExpression::class.java, PsiConstantEvaluator) {
        it !is PsiAnnotation
      }
    }
}

/**
 * Utility method to highlight a [LiteralReferenceSnapshot] in an editor.
 */
fun LiteralReferenceSnapshot.highlightSnapshotInEditor(project: Project,
                                                       editor: Editor,
                                                       textAttributes: TextAttributes,
                                                       outHighlighters: MutableSet<RangeHighlighter>? = null) {
  val highlightManager = HighlightManager.getInstance(project)
  val elements = all.filterIsInstance<LiteralReferenceImpl>()
    .mapNotNull { it.element }
    .toTypedArray()

  highlightManager.addOccurrenceHighlights(
    editor,
    elements,
    textAttributes,
    false,
    outHighlighters)
}
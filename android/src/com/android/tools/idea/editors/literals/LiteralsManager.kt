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

import com.android.utils.reflection.qualifiedName
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.impl.PsiExpressionEvaluator
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.await
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.getLineNumber
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.types.TypeUtils
import java.util.Objects
import java.util.concurrent.Callable

@FunctionalInterface
interface PsiElementUniqueIdProvider {
  /**
   * Returns a unique id for the given element. This can be used for find an alternative tracking id that does not require holding the whole
   * [PsiElement]. It can be used for example for logging.
   */
  fun getUniqueId(element: PsiElement): String
}

@FunctionalInterface
interface PsiElementLiteralUsageReferenceProvider {
  /**
   * Returns a [LiteralUsageReference] for the given [element] or null it was not possible to find a
   * reference, for example if element is not a valid literal.
   */
  fun getLiteralUsageReference(element: PsiElement, constantEvaluator: ConstantEvaluator): LiteralUsageReference?
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
    Objects.hash(element.containingFile?.modificationStamp ?: -1, element.textRange).toString(16)

  override fun getUniqueId(element: PsiElement): String = element.getCopyableUserData(
    SAVED_UNIQUE_ID) ?: calculateNewUniqueId(
    element).also {
    // Save in the cache
    element.putCopyableUserData(SAVED_UNIQUE_ID, it)
  }
}

/**
 * Default implementation of [PsiElementLiteralUsageReferenceProvider] that creates [LiteralUsageReference]s using
 * the FQN for the class and method name where the [PsiElement] is contained.
 */
private object DefaultPsiElementLiteralUsageReferenceProvider : PsiElementLiteralUsageReferenceProvider {
  override fun getLiteralUsageReference(element: PsiElement, constantEvaluator: ConstantEvaluator): LiteralUsageReference? {
    val containingFile = element.containingFile ?: return null
    return when (element) {
      is KtElement -> {
        val className = element.containingClass()?.fqName ?: FqName(element.containingKtFile.findFacadeClass()?.qualifiedName ?: "")
        val methodPath = getTopMostParentFunction(element)
        val methodName = if (methodPath.isRoot) "<init>" else methodPath.asString()
        val range = constantEvaluator.range(element)
        val virtualFilePath = containingFile.virtualFile?.path ?: return null
        LiteralUsageReference(FqName("$className.$methodName"), virtualFilePath, range,
                              element.getLineNumber())
      }
      else -> {
        val className = PsiTreeUtil.getContextOfType(element, PsiClass::class.java)?.qualifiedName?.let { "$it." } ?: ""
        val methodName = PsiTreeUtil.getContextOfType(element, PsiMethod::class.java)?.name ?: "<init>"
        LiteralUsageReference(FqName("$className.$methodName"), containingFile.name, constantEvaluator.range(element),
                              element.getLineNumber())
      }
    }
  }
}

/**
 * A reference to a literal constant.
 */
interface LiteralReference {
  val containingFile: PsiFile

  /** Filename where this literal is declared */
  val fileName: String
    get() = containingFile.name

  /** The initial range for the constant. Can be used for sorting and/or highlighting. */
  val initialTextRange: TextRange

  /** [LiteralUsageReference]s of the usages of this literal */
  val usages: Collection<LiteralUsageReference>

  /** Returns whether this element is still valid (exists in the PSI) */
  val isValid: Boolean

  /** A unique id for this literal reference. The reference will be kept even when the value changes. */
  val uniqueId: String

  /** The text value of [constantValue]. */
  val text: String

  /** The initial constant value. */
  val initialConstantValue: Any

  /** The current constant value. */
  val constantValue: Any?
}

/**
 * Interface for expression evaluators that calculate the value of an expression and its current position.
 */
interface ConstantEvaluator {
  /**
   * Returns the value of the expression at the moment.
   */
  fun evaluate(expression: PsiElement): Any?

  /**
   * Returns the current [TextRange] for the given [PsiElement].
   */
  fun range(expression: PsiElement): TextRange = expression.textRange ?: TextRange.EMPTY_RANGE
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

/**
 * Special [ConstantEvaluator] to evaluate string templates.
 * A template can generate multiple constants. For example "Hello $name!!" will generate two:
 * - "Hello "
 * - "!!"
 * This is not currently supported by the ConstantExpressionEvaluator in the plugin so we handle it here.
 */
private object KotlinLiteralTemplateConstantEvaluator : ConstantEvaluator {
  override fun evaluate(expression: PsiElement): Any? {
    val element = expression as? KtStringTemplateExpression ?: return PsiTextConstantEvaluator.evaluate(expression)

    return if (element.entries.size == 1 || element.entries.any { it !is KtLiteralStringTemplateEntry }) {
      PsiTextConstantEvaluator.evaluate(expression)
    }
    else {
      element.entries.joinToString("") { it.text }
    }
  }

  override fun range(expression: PsiElement): TextRange {
    val element = expression as? KtStringTemplateExpression ?: return PsiTextConstantEvaluator.range(expression)

    return if (element.entries.size == 1 || element.entries.any { it !is KtLiteralStringTemplateEntry }) {
      PsiTextConstantEvaluator.range(expression)
    }
    else {
      TextRange(
        element.entries.map { it.textRange.startOffset }.minOrNull() ?: 0,
        element.entries.map { it.textRange.endOffset }.maxOrNull() ?: 0
      )
    }
  }
}

/**
 * A usage of a literal. This records the usage positional [FqName]. Think about it as
 * the identifier of where a literal is used.
 */
data class LiteralUsageReference(val fqName: FqName, val fileName: String, val range: TextRange, val lineNumber: Int) {
  override fun toString(): String = "$fqName ($fileName:$lineNumber range=$range)"
}

/**
 * Finds the top most outer parent function and returns the FqName.
 */
private fun getTopMostParentFunction(element: KtElement): FqName {
  val parent = PsiTreeUtil.getContextOfType(element, KtFunction::class.java)
  return if (parent != null) {
    getTopMostParentFunction(parent).child(Name.identifier(parent.name!!))
  }
  else {
    FqName.ROOT
  }
}

/**
 * Finds the usages for the given constant [PsiElement].
 */
private fun findUsages(constantElement: PsiElement,
                       usageReferenceProvider: PsiElementLiteralUsageReferenceProvider,
                       constantEvaluator: ConstantEvaluator): Collection<LiteralUsageReference> =
  listOfNotNull(usageReferenceProvider.getLiteralUsageReference(constantElement, constantEvaluator))

/**
 * [LiteralReference] implementation that keeps track of modifications.
 */
private class LiteralReferenceImpl(originalElement: PsiElement,
                                   uniqueIdProvider: PsiElementUniqueIdProvider,
                                   usageReferenceProvider: PsiElementLiteralUsageReferenceProvider,
                                   override val initialConstantValue: Any,
                                   private val constantEvaluator: ConstantEvaluator,
                                   onElementAttached: (PsiElement, LiteralReference) -> Unit) : LiteralReference, ModificationTracker {
  init {
    onElementAttached(originalElement, this)
  }

  private val elementPointer = ReattachableSmartPsiElementPointer(originalElement) { onElementAttached(it, this) }

  // The originalElement.containingFile not being nullable is enforced during the visit the PsiElements
  override val containingFile = originalElement.containingFile!!
  override val usages = findUsages(originalElement, usageReferenceProvider, constantEvaluator)
  override val initialTextRange: TextRange = constantEvaluator.range(originalElement)
  override val uniqueId = uniqueIdProvider.getUniqueId(originalElement)
  private var lastCachedConstantValue: Any? = initialConstantValue
  val element: PsiElement?
    get() = ReadAction.compute<PsiElement?, Throwable> { elementPointer.element }
  override val isValid: Boolean
    get() = ReadAction.compute<Boolean, Throwable> { elementPointer.range != null }

  override val constantValue: Any?
    get() = element?.let {
      ReadAction.compute<Any?, Throwable> {
        try {
          constantEvaluator.evaluate(it)
        } catch (_: IndexNotReadyException) {
          // If not in smart mode, just return the last cached value
          lastCachedConstantValue
        }
      }
    }

  private val fileModificationTracker = ModificationTracker {
    elementPointer.containingFile?.let { if (it.isValid) it.modificationStamp else -1 } ?: -1
  }
  private var lastFileModificationCount = 0L
  private var localModificationTracker = SimpleModificationTracker()
  override fun getModificationCount(): Long {
    if (!isValid) {
      // The element is not valid anymore so it will not be updated again.
      return localModificationTracker.modificationCount
    }

    if (lastFileModificationCount != fileModificationTracker.modificationCount && lastCachedConstantValue != constantValue) {
      lastFileModificationCount = fileModificationTracker.modificationCount
      lastCachedConstantValue = constantValue
      localModificationTracker.incModificationCount()
    }
    return localModificationTracker.modificationCount
  }

  override val text: String
    get() = ReadAction.compute<String, Throwable> {
      element?.let {
        constantEvaluator.evaluate(it) as? String ?: it.text
      } ?: "<null>"
    }
}

/**
 * A snapshot for a list of [LiteralReference]s frozen in time. You can obtain a new updated snapshot by calling [newSnapshot].
 *
 * The snapshot can be highlighter in the editor by calling [highlightSnapshotInEditor].
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

/**
 * Class that manages the literals present in a [PsiElement] tree. Use this class to obtain an snapshot of the
 * literals present in a [PsiElement].
 *
 * @param uniqueIdProvider A [PsiElementUniqueIdProvider] used to uniquely identify the literals found by this manager.
 * @param literalUsageReferenceProvider A [PsiElementLiteralUsageReferenceProvider] that returns a [LiteralUsageReference] for the given
 *  [PsiElement].
 */
class LiteralsManager(
  private val uniqueIdProvider: PsiElementUniqueIdProvider = SimplePsiElementUniqueIdProvider,
  private val literalUsageReferenceProvider: PsiElementLiteralUsageReferenceProvider = DefaultPsiElementLiteralUsageReferenceProvider) {
  /** Types that can be considered "literals". */
  private val literalsTypes = setOf(KtConstantExpression::class.java, KtUnaryExpression::class.java)
  private val literalReadingExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "LiteralsManager executor", (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
  )
  private val LOG = Logger.getInstance(LiteralsManager::class.java)

  private suspend fun findLiterals(root: PsiElement,
                                   constantType: Collection<Class<*>>,
                                   constantEvaluator: ConstantEvaluator,
                                   elementFilter: (PsiElement) -> Boolean): LiteralReferenceSnapshot {
    val savedLiterals = ReadAction.nonBlocking(Callable<Collection<LiteralReferenceImpl>> {
      val savedLiterals = mutableListOf<LiteralReferenceImpl>()
      try {
        root.acceptChildren(object : PsiRecursiveElementWalkingVisitor() {
          override fun visitElement(element: PsiElement) {
            if (!elementFilter(element) || !element.isValid || element.containingFile == null) return

            // Special case for string templates
            if (element is KtStringTemplateExpression) {
              // A template can generate multiple constants. For example "Hello $name!!" will generate two:
              //  - "Hello "
              //  - "!!"
              // This is not currently supported by the ConstantExpressionEvaluator in the plugin so we handle it here.
              if (element.entries.size == 1 || element.entries.any { it !is KtLiteralStringTemplateEntry }) {
                element.entries.forEach {
                  if (it is KtLiteralStringTemplateEntry) {
                    savedLiterals.add(
                      LiteralReferenceImpl(it,
                                           uniqueIdProvider,
                                           literalUsageReferenceProvider,
                                           it.text,
                                           PsiTextConstantEvaluator,
                                           Companion::markAsManaged))
                  }
                }
              }
              else {
                // All sub elements are string entries so handle as one string
                savedLiterals.add(
                  LiteralReferenceImpl(element, uniqueIdProvider, literalUsageReferenceProvider,
                                       KotlinLiteralTemplateConstantEvaluator.evaluate(element) as String,
                                       KotlinLiteralTemplateConstantEvaluator,
                                       Companion::markAsManaged))
              }
              return
            }

            if (constantType.any { it.isInstance(element) }) {
              constantEvaluator.evaluate(element)?.let {
                // This is a regular constant, save it.
                savedLiterals.add(LiteralReferenceImpl(element,
                                                       uniqueIdProvider,
                                                       literalUsageReferenceProvider,
                                                       it,
                                                       constantEvaluator,
                                                       Companion::markAsManaged))
              }
              return
            }

            super.visitElement(element)
          }
        })
      }
      catch (_: IndexNotReadyException) {}
      catch (_: ProcessCanceledException) {
        // After 222.2889.14 the visitor can throw ProcessCanceledException instead of IndexNotReadyException if in dumb mode.
      }
      savedLiterals
    }).submit(literalReadingExecutor).await()

    return if (savedLiterals.isNotEmpty()) LiteralReferenceSnapshotImpl(savedLiterals) else EmptyLiteralReferenceSnapshot
  }

  /**
   * Finds the literals in the given tree root [PsiElement] and returns a [LiteralReferenceSnapshot].
   */
  suspend fun findLiterals(root: PsiElement): LiteralReferenceSnapshot =
    if (root.language == KotlinLanguage.INSTANCE) {
      findLiterals(root, literalsTypes, KotlinConstantEvaluator) {
        it !is KtAnnotationEntry // Exclude annotations since we do not process literals in them.
        && it !is KtSimpleNameExpression // Exclude variable constants.
      }
    }
    else {
      LOG.warn("Only Kotlin is supported for LiveLiterals")
      EmptyLiteralReferenceSnapshot
    }

  companion object {
    private val MANAGED_KEY: Key<LiteralReference> = Key.create(Companion::MANAGED_KEY.qualifiedName)

    /**
     * If the element has a [LiteralReference] associated, this will return it.
     */
    internal fun getLiteralReference(element: PsiElement): LiteralReference? {
      if (!element.isValid) return null

      return element.getCopyableUserData(MANAGED_KEY)
    }

    /**
     * Marks the given element as managed.
     */
    private fun markAsManaged(element: PsiElement, literalReference: LiteralReference) = element.putCopyableUserData(MANAGED_KEY, literalReference)
  }
}

/**
 * Utility method to highlight a [LiteralReferenceSnapshot] in an editor.
 */
fun LiteralReferenceSnapshot.highlightSnapshotInEditor(project: Project,
                                                       editor: Editor,
                                                       textAttributesKey: TextAttributesKey,
                                                       outHighlighters: MutableSet<RangeHighlighter>? = null,
                                                       referenceFilter: (LiteralReference) -> Boolean = { true }) {
  val highlightManager = HighlightManager.getInstance(project)
  val elements = all.filterIsInstance<LiteralReferenceImpl>()
    .filter { referenceFilter(it) }
    .mapNotNull { it.element }
    .toTypedArray()

  val resultHighlighters = mutableSetOf<RangeHighlighter>()
  highlightManager.addOccurrenceHighlights(
    editor,
    elements,
    textAttributesKey,
    false,
    resultHighlighters)
  resultHighlighters.forEach {
    it.isGreedyToLeft = true
    it.isGreedyToRight = true
  }
  outHighlighters?.addAll(resultHighlighters)
}
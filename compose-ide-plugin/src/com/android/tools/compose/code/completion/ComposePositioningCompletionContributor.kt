/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.compose.isComposeEnabled
import com.android.tools.compose.matchingParamTypeFqName
import com.android.tools.compose.returnTypeFqName
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Represents a class in the Compose library containing Alignment or Arrangement properties which
 * might be suggested as completions.
 */
private data class ClassWithDeclarationsToSuggest(
  /** Package on which this class resides. */
  private val packageName: String,
  /**
   * Short(er) name of the class containing properties that can be suggested. This may contain
   * multiple dot-separated pieces, and is intended to be concatenated with [packageName] to get the
   * interface's fully-qualified name.
   */
  private val classShortName: String,
  /**
   * Short name of the class to be imported when a suggestion is made. This differs from
   * [classShortName] in the case of Companions.
   */
  private val classShortNameToImport: String = classShortName,
  /**
   * Prefix applied to the property when completing. This is most often the same as
   * [classShortName], but may contain nested classes (e.g. "Arrangement.Absolute").
   */
  private val propertyCompletionPrefix: String = classShortName,
) {

  private val classFqName = "$packageName.$classShortName"
  private val classToImport = "$packageName.$classShortNameToImport"

  /**
   * Returns [LookupElement]s for the given [PsiElement].
   *
   * @param typeToSuggest Fully-qualified name of the type required for suggested
   *   properties. @typeText Display text of the type to be used when rendering the [LookupElement]/
   */
  fun getLookupElements(
    elementToComplete: PsiElement,
    typeToSuggest: String,
    typeText: String
  ): List<LookupElement> {
    val project = elementToComplete.project

    // It's necessary to ensure these are distinct, because in some circumstances there may be
    // multiple versions of the class returned when
    // searching with 'allScope'.
    return KotlinFullClassNameIndex.get(classFqName, project, project.allScope())
      .flatMap { it.getPropertiesByType(project)[typeToSuggest] ?: emptyList() }
      .distinctBy { it.kotlinFqName }
      .mapNotNull { createLookupElement(it, elementToComplete, typeText) }
  }

  private fun createLookupElement(
    elementToSuggest: KtDeclaration,
    elementToComplete: PsiElement,
    typeText: String
  ): LookupElement? {
    val lookupStringWithClass = "${propertyCompletionPrefix}.${elementToSuggest.name}"
    val presentableTailText = " ($packageName)"

    // If the user's already typed part of a dot expression, then not all the given suggestions from
    // this class will be applicable.
    // For example, if the user has typed "Arrangement.Absolute.L", we want to exclude
    // "Arrangement.Left".
    val alreadyCompletedPrefix =
      elementToComplete
        .parentOfType<KtDotQualifiedExpression>()
        ?.receiverExpression
        ?.normalizedExpressionText()
        ?.let { "$it." } ?: ""

    if (!lookupStringWithClass.startsWith(alreadyCompletedPrefix)) return null

    // When completing one of these properties with a dot expression, we want the lookup string to
    // exclude any full piece that's already
    // been typed. For example, if the suggestion is "Arrangement.Absolute.Left" and the user has
    // types "Arrangement.Ab", we want the
    // resulting lookup string to be "Absolute.Left".
    val mainLookupString = lookupStringWithClass.removePrefix(alreadyCompletedPrefix)

    val builder =
      LookupElementBuilder.create(elementToSuggest, mainLookupString)
        .bold()
        .withTailText(presentableTailText, true)
        .withTypeText(typeText)
        .withInsertHandler lambda@{ context, _ ->
          // Add import in addition to filling in the completion.
          val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
          val ktFile = context.file as KtFile
          if (KotlinPluginModeProvider.isK2Mode()) {
            ktFile.addImport(FqName(classToImport))
            psiDocumentManager.commitAllDocuments()
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
          } else {
            val modifierDescriptor =
              ktFile.resolveImportReference(FqName(classToImport)).singleOrNull()
            if (modifierDescriptor != null) {
              ImportInsertHelper.getInstance(context.project)
                .importDescriptor(ktFile, modifierDescriptor)
              psiDocumentManager.commitAllDocuments()
              psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
            }
          }
        }

    return object : LookupElementDecorator<LookupElement>(builder) {
      override fun renderElement(presentation: LookupElementPresentation) {
        super.renderElement(presentation)
        presentation.icon = DefaultLookupItemRenderer.getRawIcon(builder)
      }
    }
  }

  companion object {
    /**
     * Gets a list of this class's properties grouped by their fully-qualified type name. This
     * result is cached for fast retrieval.
     */
    private fun KtClassOrObject.getPropertiesByType(
      project: Project
    ): Map<String, List<KtProperty>> {
      return CachedValuesManager.getManager(project).getCachedValue(this) {
        val result =
          declarations
            .filterIsInstance<KtProperty>()
            .mapNotNull { property ->
              property.returnTypeFqName()?.asString()?.let { Pair(it, property) }
            }
            .groupBy({ it.first }, { it.second })
        CachedValueProvider.Result.create(result, this)
      }
    }

    /**
     * Given a dot-qualified expression, returns a normalized form of the name. This removes
     * inconsistencies that may be introduced by whitespace within the name; so the expression "com
     * . foo .bar" will be reduced to "com.foo.bar".
     */
    private fun KtExpression.normalizedExpressionText(): String? {
      return when (this) {
        is KtDotQualifiedExpression -> {
          val leftSide = receiverExpression.normalizedExpressionText() ?: return null
          val rightSide = selectorExpression?.normalizedExpressionText() ?: return null
          "$leftSide.$rightSide"
        }
        is KtSimpleNameExpression -> getIdentifier()?.text
        else -> null
      }
    }
  }
}

/** Represents one of the interface types used to specify Alignment or Arrangement. */
private data class PositioningInterface(
  /** Package on which this interface resides. */
  private val packageName: String,
  /**
   * Short(er) name of the interface. This may contain multiple dot-separated pieces, and is
   * intended to be concatenated with [packageName] to get the interface's fully-qualified name.
   */
  private val interfaceName: String,
  /**
   * A list of classes on which to search for properties implementing this interface, which can be
   * used for suggestions.
   */
  private val suggestedCompletionPropertyClasses: List<ClassWithDeclarationsToSuggest>,
  /**
   * An additional type that is allowed for suggestions in addition to [interfaceName]. The type
   * must reside on the same [packageName].
   */
  private val additionalTypeToSuggest: String? = null,
  /**
   * Collection of weights to be used when ranking suggestions. The key is a short type name
   * residing on [packageName], corresponding to one of the positioning interfaces being handled.
   * The value is a simple priority: larger values result in a higher position in the completion
   * list. This value is added to any weight in [weightsByParentClass].
   */
  private val weightsByType: Map<String, Int>,
  /**
   * Collection of weights to be used when ranking suggestions. The key is a short type name
   * residing on [packageName], corresponding to the class on which a suggested property is defined.
   * The value is a simple priority: larger values result in a higher position in the completion
   * list. This value is added to any weight in [weightsByType].
   */
  private val weightsByParentClass: Map<String, Int>,
) {

  val interfaceFqName = "$packageName.$interfaceName"

  private val weightsByFullyQualifiedType =
    weightsByType.mapKeys { (key, _) -> "$packageName.$key" }
  private val weightsByFullyQualifiedParentClass =
    weightsByParentClass.mapKeys { (key, _) -> "$packageName.$key" }

  /**
   * A list of types that are allowed for suggestions.
   *
   * The first [String] in this [Pair] represents the fully-qualified name of the type. The second
   * [String] in this [Pair] represents a shorter version of the type that can be displayed on the
   * right side of the auto-completion dialog.
   */
  private val typesToSuggest: List<Pair<String, String>> = buildList {
    add("$packageName.$interfaceName" to interfaceName)
    additionalTypeToSuggest?.let { add("$packageName.$it" to it) }
  }

  /** Returns [LookupElement]s for the given [PsiElement]. */
  fun getSuggestedCompletions(elementToComplete: PsiElement): List<LookupElement> {
    return suggestedCompletionPropertyClasses.flatMap { clazz ->
      typesToSuggest.flatMap { typeToSuggest ->
        clazz.getLookupElements(elementToComplete, typeToSuggest.first, typeToSuggest.second)
      }
    }
  }

  /** Returns the weight to use for the given [LookupElement]. */
  fun getWeight(lookupElement: LookupElement): Int {
    val psiElement = lookupElement.psiElement ?: return 0

    val lookupElementTypeName = (psiElement as? KtDeclaration)?.returnTypeFqName()?.asString()
    val typeWeight = lookupElementTypeName?.let { weightsByFullyQualifiedType[it] } ?: 0

    val lookupElementParentClassName =
      (psiElement as? KtDeclaration)?.containingClassOrObject?.fqName?.asString()
    val containingClassWeight =
      lookupElementParentClassName?.let { weightsByFullyQualifiedParentClass[it] } ?: 0
    return typeWeight + containingClassWeight
  }

  companion object {

    /** Returns an applicable [PositioningInterface] for the given [PsiElement] if one exists. */
    fun forCompletionElement(psiElement: PsiElement): PositioningInterface? {
      // Arrangement and Alignment completions are handled when completing arguments and properties
      // only.
      val elementToCompleteTypeFqName =
        psiElement.argumentTypeFqName ?: psiElement.propertyTypeFqName ?: return null
      return VALUES[elementToCompleteTypeFqName]
    }

    private val PsiElement.propertyTypeFqName: String?
      get() {
        val property = contextOfType<KtProperty>() ?: return null
        return property.returnTypeFqName()?.asString()
      }

    private val PsiElement.argumentTypeFqName: String?
      get() {
        val argument =
          contextOfType<KtValueArgument>().takeIf { it !is KtLambdaArgument } ?: return null

        val callExpression = argument.parentOfType<KtCallElement>() ?: return null
        val callee =
          callExpression.calleeExpression?.mainReference?.resolve() as? KtNamedFunction
            ?: return null

        val argumentTypeFqName = argument.matchingParamTypeFqName(callee)

        return argumentTypeFqName?.asString()
      }

    private const val ALIGNMENT_PACKAGE = "androidx.compose.ui"
    private const val ARRANGEMENT_PACKAGE = "androidx.compose.foundation.layout"

    private val ALIGNMENT_CLASSES_FOR_SUGGESTIONS =
      listOf(
        ClassWithDeclarationsToSuggest(
          packageName = ALIGNMENT_PACKAGE,
          classShortName = "Alignment.Companion",
          classShortNameToImport = "Alignment",
          propertyCompletionPrefix = "Alignment",
        ),
        ClassWithDeclarationsToSuggest(
          packageName = ALIGNMENT_PACKAGE,
          classShortName = "AbsoluteAlignment",
        ),
      )

    private val ARRANGEMENT_CLASSES_FOR_SUGGESTIONS =
      listOf(
        ClassWithDeclarationsToSuggest(
          packageName = ARRANGEMENT_PACKAGE,
          classShortName = "Arrangement",
        ),
        ClassWithDeclarationsToSuggest(
          packageName = ARRANGEMENT_PACKAGE,
          classShortName = "Arrangement.Absolute",
          classShortNameToImport = "Arrangement",
        ),
      )

    private val VALUES =
      listOf(
          PositioningInterface(
            packageName = ALIGNMENT_PACKAGE,
            interfaceName = "Alignment",
            ALIGNMENT_CLASSES_FOR_SUGGESTIONS,
            weightsByType =
              mapOf(
                "Alignment" to 10,
                "Alignment.Horizontal" to -10,
                "Alignment.Vertical" to -10,
              ),
            weightsByParentClass =
              mapOf(
                "Alignment.Companion" to 2,
                "Alignment" to 2,
                "AbsoluteAlignment" to 1,
              ),
          ),
          PositioningInterface(
            packageName = ALIGNMENT_PACKAGE,
            interfaceName = "Alignment.Horizontal",
            ALIGNMENT_CLASSES_FOR_SUGGESTIONS,
            weightsByType =
              mapOf(
                "Alignment.Horizontal" to 10,
                "Alignment" to -10,
                "Alignment.Vertical" to -10,
              ),
            weightsByParentClass =
              mapOf(
                "Alignment.Companion" to 2,
                "Alignment" to 2,
                "AbsoluteAlignment" to 1,
              ),
          ),
          PositioningInterface(
            packageName = ALIGNMENT_PACKAGE,
            interfaceName = "Alignment.Vertical",
            ALIGNMENT_CLASSES_FOR_SUGGESTIONS,
            weightsByType =
              mapOf(
                "Alignment.Vertical" to 10,
                "Alignment" to -10,
                "Alignment.Horizontal" to -10,
              ),
            weightsByParentClass =
              mapOf(
                "Alignment.Companion" to 2,
                "Alignment" to 2,
                "AbsoluteAlignment" to 1,
              ),
          ),
          PositioningInterface(
            packageName = ARRANGEMENT_PACKAGE,
            interfaceName = "Arrangement.Horizontal",
            ARRANGEMENT_CLASSES_FOR_SUGGESTIONS,
            additionalTypeToSuggest = "Arrangement.HorizontalOrVertical",
            weightsByType =
              mapOf(
                "Arrangement.Horizontal" to 10,
                "Arrangement.HorizontalOrVertical" to 10,
                "Arrangement.Vertical" to -10,
              ),
            weightsByParentClass =
              mapOf(
                "Arrangement" to 2,
                "Arrangement.Absolute" to 1,
              ),
          ),
          PositioningInterface(
            packageName = ARRANGEMENT_PACKAGE,
            interfaceName = "Arrangement.Vertical",
            ARRANGEMENT_CLASSES_FOR_SUGGESTIONS,
            additionalTypeToSuggest = "Arrangement.HorizontalOrVertical",
            weightsByType =
              mapOf(
                "Arrangement.Vertical" to 10,
                "Arrangement.HorizontalOrVertical" to 10,
                "Arrangement.Horizontal" to -10,
              ),
            weightsByParentClass =
              mapOf(
                "Arrangement" to 2,
                "Arrangement.Absolute" to 1,
              ),
          ),
          PositioningInterface(
            packageName = ARRANGEMENT_PACKAGE,
            interfaceName = "Arrangement.HorizontalOrVertical",
            ARRANGEMENT_CLASSES_FOR_SUGGESTIONS,
            weightsByType =
              mapOf(
                "Arrangement.HorizontalOrVertical" to 10,
                "Arrangement.Vertical" to -10,
                "Arrangement.Horizontal" to -10,
              ),
            weightsByParentClass =
              mapOf(
                "Arrangement" to 2,
                "Arrangement.Absolute" to 1,
              ),
          ),
        )
        .associateBy { it.interfaceFqName }
  }
}

/**
 * Suggests completion for the Alignment and Arrangement interfaces. Both interfaces have Horizontal
 * and Vertical variants which the default auto-completion intermixes, even though only one subset
 * is generally applicable in any given completion.
 */
class ComposePositioningCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val elementToComplete = parameters.position

    if (!isComposeEnabled(elementToComplete) || parameters.originalFile !is KtFile) return

    // Add any suggested elements needed for this element.
    val lookupElements =
      PositioningInterface.forCompletionElement(elementToComplete)
        ?.getSuggestedCompletions(elementToComplete) ?: return
    result.addAllElements(lookupElements)

    // Run the remaining contributors, removing any duplicates of the items that have already been
    // suggested.
    val addedElements = lookupElements.mapNotNull { it.psiElement?.kotlinFqName }.toSet()
    result.runRemainingContributors(parameters) { completionResult ->
      val alreadyAddedElement =
        completionResult.lookupElement.psiElement?.kotlinFqName?.let { addedElements.contains(it) }
          ?: false
      if (!alreadyAddedElement) {
        result.passResult(completionResult)
      }
    }
  }
}

/** Suggests completion for the Alignment and Arrangement interfaces. */
class ComposePositioningCompletionWeigher : CompletionWeigher() {
  override fun weigh(lookupElement: LookupElement, location: CompletionLocation): Int? {
    val parameters = location.completionParameters
    val elementToComplete = parameters.position
    if (!isComposeEnabled(elementToComplete) || parameters.originalFile !is KtFile) {
      // Return null when this isn't a completion we care about to avoid any further comparisons or
      // object allocations.
      return null
    }

    // Since this is a completion involving one of the types handled here, we want to rank
    // everything. If it's not an element being
    // adjusted, then the weight of '0' will effectively let the item pass through unmodified.
    return PositioningInterface.forCompletionElement(elementToComplete)?.getWeight(lookupElement)
      ?: 0
  }
}

/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.service

import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.idea.preview.flow.createKotlinModificationFlow
import com.android.tools.idea.preview.flow.createModuleRootListenerFlow
import com.android.tools.preview.config.PARAMETER_NAME
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A project service that provides a hot [StateFlow] of all resolved [PreviewDefinition]s in the
 * project.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Service(Service.Level.PROJECT)
class PreviewDefinitionProvider(private val project: Project, scope: CoroutineScope) {
  val logger = Logger.getInstance(PreviewDefinitionProvider::class.java)

  val previews: StateFlow<Map<KtNamedFunction, List<PreviewDefinition>>> =
    combine(
        createPreviewInvalidationFlow(project),
        project.service<PreviewAnnotationProvider>().allPreviewAnnotationsFlow,
      ) { _, allPreviewAnnotationFqns ->
        allPreviewAnnotationFqns
      }
      .mapLatest { allPreviewAnnotationFqns ->
        val (previews, duration) =
          measureTimedValue { computeAllPreviews(allPreviewAnnotationFqns) }
        logger.debug(
          "PreviewProvider calculation took ${duration.inWholeMilliseconds}ms. Found ${previews.size} previews."
        )
        previews
      }
      .stateIn(scope, SharingStarted.Eagerly, initialValue = emptyMap())

  /**
   * Finds all `@Composable` functions with a `@Preview` annotation (either directly or through a
   * multi-preview annotation) and resolves them into a map of [PreviewDefinition]s.
   *
   * The "computation" is a multi-step process:
   * 1. It uses the Kotlin index to quickly find all functions that are potentially annotated with a
   *    preview.
   * 2. It then resolves each of these functions to confirm they have a valid preview annotation.
   * 3. For each valid function, it recursively expands any multi-preview annotations to find all
   *    the leaf `@Preview` annotations.
   * 4. Finally, it builds a [PreviewDefinition] for each leaf annotation.
   */
  private suspend fun computeAllPreviews(
    allPreviewAnnotationFqns: Set<String>
  ): Map<KtNamedFunction, List<PreviewDefinition>> {
    if (allPreviewAnnotationFqns.isEmpty()) {
      return emptyMap()
    }
    val annotatedFunctions = findAnnotatedFunctions(allPreviewAnnotationFqns)

    return smartReadAction(project) {
      val multiPreviewExpander = MultiPreviewExpander(allPreviewAnnotationFqns)
      annotatedFunctions
        .mapNotNull { function ->
          ProgressManager.checkCanceled()
          try {
            val resolvedPreviews = resolveFunctionPreviews(function, multiPreviewExpander)
            if (resolvedPreviews.isNotEmpty()) function to resolvedPreviews else null
          } catch (e: Exception) {
            logger.warn("Could not resolve previews for ${function.name}", e)
            null
          }
        }
        .toMap()
    }
  }

  /**
   * Finds all functions in the project that are annotated with any of the given preview annotation
   * FQNs.
   */
  private suspend fun findAnnotatedFunctions(
    allPreviewAnnotationFqns: Set<String>
  ): Set<KtNamedFunction> =
    smartReadAction(project) {
      val functionsToInspect = mutableSetOf<KtNamedFunction>()
      val uniqueShortNames = allPreviewAnnotationFqns.map { it.substringAfterLast('.') }.toSet()

      for (shortName in uniqueShortNames) {
        ProgressManager.checkCanceled()
        val annotationEntries =
          KotlinAnnotationsIndex[shortName, project, GlobalSearchScope.projectScope(project)]

        for (entry in annotationEntries) {
          ProgressManager.checkCanceled()
          val function = entry.parent.parent as? KtNamedFunction ?: continue
          if (functionsToInspect.contains(function)) {
            continue
          }
          // We found a function with an annotation of the right name, but we need to
          // resolve it to be sure it's the right one.
          analyze(entry) {
            val functionSymbol = function.symbol
            if (
              functionSymbol.annotations.any {
                it.classId?.asFqNameString() in allPreviewAnnotationFqns
              }
            ) {
              functionsToInspect.add(function)
            }
          }
        }
      }
      functionsToInspect
    }

  /**
   * Resolves all [PreviewDefinition]s for a given [KtNamedFunction] by expanding its annotations.
   */
  private fun resolveFunctionPreviews(
    function: KtNamedFunction,
    multiPreviewExpander: MultiPreviewExpander,
  ): List<PreviewDefinition> {
    val resolvedPreviews = mutableListOf<PreviewDefinition>()
    analyze(function) {
      val functionSymbol = function.symbol
      for (annotation in functionSymbol.annotations) {
        ProgressManager.checkCanceled()
        val leafPreviews = multiPreviewExpander.expandAnnotationToLeafPreviews(this, annotation)
        leafPreviews.forEach { leafAnnotation ->
          val leafName = extractNameArgument(leafAnnotation)
          resolvedPreviews.add(
            PreviewDefinition.create(function, leafAnnotation.psi as KtAnnotationEntry, leafName)
          )
        }
      }
    }
    return resolvedPreviews
  }

  /**
   * Extracts the value of the `name` argument from a `@Preview` or multi-preview annotation.
   *
   * @return The string value of the `name` argument, or `null` if it's not present or empty.
   */
  private fun extractNameArgument(annotation: KaAnnotation): String? {
    val nameArgument = annotation.arguments.find { it.name.asString() == PARAMETER_NAME }

    val constantValue = (nameArgument?.expression as? KaAnnotationValue.ConstantValue)?.value

    return (constantValue as? KaConstantValue.StringValue)?.value?.takeIf { it.isNotEmpty() }
  }
}

/**
 * Handles the logic of recursively expanding a [KaAnnotation] to its leaf `@Preview` annotations.
 * This class is designed to be used for a single, top-level preview resolution pass, and it caches
 * the results of the expansion to avoid re-computation and handle recursive multi-preview
 * definitions.
 */
private class MultiPreviewExpander(private val allPreviewAnnotationFqns: Set<String>) {
  /**
   * Cache for the results of expanding multi-preview annotations to their leaf `@Preview`s. This is
   * crucial for performance and to handle recursive multi-preview definitions.
   */
  private val expansionCache = mutableMapOf<String, List<KaAnnotation>>()

  /**
   * Recursively expands a [KaAnnotation] to its leaf `@Preview` annotations.
   *
   * @param annotation The annotation to expand.
   * @return A list of leaf `@Preview` [KaAnnotation]s.
   */
  fun expandAnnotationToLeafPreviews(
    session: KaSession,
    annotation: KaAnnotation,
  ): List<KaAnnotation> {
    val fqn = annotation.classId?.asFqNameString() ?: return emptyList()

    // The base @Preview annotation is the leaf of the recursion. It should not be cached,
    // as different @Preview annotations have different arguments.
    if (fqn == COMPOSE_PREVIEW_ANNOTATION_FQN) {
      return listOf(annotation)
    }

    // Check the cache first for any known FQN.
    if (expansionCache.containsKey(fqn)) {
      return expansionCache.getValue(fqn)
    }

    // If it's not in the cache, check if it's a valid preview-defining annotation.
    // If not, we don't need to process it further.
    if (!allPreviewAnnotationFqns.contains(fqn)) {
      return emptyList()
    }

    // To prevent infinite recursion, mark this FQN as "in progress".
    expansionCache[fqn] = emptyList()

    // This is a multi-preview annotation, so we need to expand it.
    val annotationClassSymbol =
      session.run { annotation.constructorSymbol?.containingSymbol as? KaClassSymbol }
        ?: return emptyList()
    val result =
      annotationClassSymbol.annotations.flatMap { expandAnnotationToLeafPreviews(session, it) }

    // Cache the final, computed result.
    expansionCache[fqn] = result
    return result
  }
}

@OptIn(FlowPreview::class)
private fun createPreviewInvalidationFlow(project: Project) =
  merge(createKotlinModificationFlow(project), createModuleRootListenerFlow(project))
    .debounce(250)
    .onStart { emit(Unit) }

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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.tools.idea.compose.preview.service

import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.idea.preview.flow.createKotlinModificationFlow
import com.android.tools.idea.preview.flow.createModuleRootListenerFlow
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.types.Variance

/**
 * A project service that provides a cached, cancellable, and auto-updating [Flow] of all preview
 * annotations in a project.
 *
 * This service finds all direct and indirect usages of the `@Preview` annotation, including custom
 * annotations and type aliases. The [allPreviewAnnotationsFlow] is the primary entry point,
 * providing a [StateFlow] that always holds the most up-to-date set of preview annotation FQNs. The
 * flow automatically updates in response to relevant PSI and module dependency modifications.
 */
@Service(Service.Level.PROJECT)
class PreviewAnnotationProvider(private val project: Project, scope: CoroutineScope) {

  val logger = Logger.getInstance(PreviewAnnotationProvider::class.java)

  /**
   * A [StateFlow] that provides a complete, up-to-date set of all preview-defining annotation FQNs.
   *
   * This flow listens for both Kotlin code modifications and module root changes. Upon triggering,
   * it first checks for the existence of the `@Preview` annotation. If it exists, it performs an
   * efficient, indexed-based search for all direct and indirect usages. If not, it emits an empty
   * set.
   */
  @OptIn(FlowPreview::class)
  val allPreviewAnnotationsFlow: StateFlow<Set<String>> =
    // 1. Invalidate on any relevant change to project dependencies or Kotlin source code.
    merge(createModuleRootListenerFlow(project), createKotlinModificationFlow(project))
      // 2. Batch rapid fire changes to avoid unnecessary work.
      .debounce(250)
      // 3. Trigger the first calculation immediately on startup.
      .onStart { emit(Unit) }
      // 4. Perform the annotation search, cancelling any previous search if a new event arrives.
      //    The expensive search is only performed if the @Preview annotation is actually present.
      .mapLatest {
        val previewExists =
          smartReadAction(project) {
            JavaPsiFacade.getInstance(project)
              .findClass(COMPOSE_PREVIEW_ANNOTATION_FQN, GlobalSearchScope.allScope(project)) !=
              null
          }

        if (previewExists) {
          var previews: Set<String>
          val duration = measureTimeMillis { previews = getAllPreviewAnnotations() }
          logger.debug(
            "PreviewAnnotationProvider calculation took ${duration}ms. Found ${previews.size} previews."
          )
          previews
        } else {
          logger.debug("Preview annotation does not exist. Emitting empty set.")
          emptySet()
        }
      }
      // 5. Convert the cold flow into a hot, cached StateFlow for sharing the result.
      .stateIn(scope, SharingStarted.Eagerly, initialValue = emptySet())

  /**
   * Finds the fully-qualified names (FQNs) of all annotation classes that are themselves annotated
   * with a specific annotation.
   *
   * This function is crucial for discovering "multi-preview" annotations, which are custom
   * annotations that bundle one or more `@Preview` annotations. For example, if a user defines
   * `@LandscapePreview` and annotates it with `@Preview`, this function will find
   * `com.example.LandscapePreview` when searching for annotations annotated with
   * `androidx.compose.ui.tooling.preview.Preview`.
   *
   * @param annotatedWith The FQN of the annotation to search for.
   * @return A set of FQNs of annotation classes that are annotated with [annotatedWith].
   */
  private fun findMultipreviewAnnotationClasses(annotatedWith: String): Set<String> {
    val definitions = mutableSetOf<String>()
    val shortName = annotatedWith.substringAfterLast('.')

    val annotationCandidates = KotlinAnnotationsIndex[shortName, project, project.projectScope()]
    for (candidate in annotationCandidates) {
      ProgressManager.checkCanceled()
      val parentClass = candidate.parent.parent as? KtClass
      if (parentClass == null || !parentClass.isAnnotation()) {
        continue // Not annotating an annotation, skip immediately.
      }
      analyze(candidate) {
        val resolvedFqn =
          candidate
            .resolveToCall()
            ?.singleConstructorCallOrNull()
            ?.symbol
            ?.containingClassId
            ?.asFqNameString()
        if (resolvedFqn == annotatedWith) {
          parentClass.fqName?.asString()?.let { fqn -> definitions.add(fqn) }
        }
      }
    }
    return definitions
  }

  /**
   * Finds the fully-qualified names (FQNs) of all type aliases that expand to a specific FQN.
   *
   * This is important for cases where developers use `typealias` to create shorter or more
   * descriptive names for preview annotations. For example, if a user defines `typealias BigPreview
   * = androidx.compose.ui.tooling.preview.Preview`, this function will find
   * `com.example.BigPreview` when searching for type aliases that expand to
   * `androidx.compose.ui.tooling.preview.Preview`.
   *
   * @param expandingTo The FQN that the type alias should expand to.
   * @return A set of FQNs of type aliases that expand to [expandingTo].
   */
  @OptIn(KaExperimentalApi::class)
  private fun findTypeAliases(expandingTo: String): Set<String> {
    val definitions = mutableSetOf<String>()
    val shortName = expandingTo.substringAfterLast('.')

    val aliasCandidates =
      KotlinTypeAliasByExpansionShortNameIndex[shortName, project, project.projectScope()]
    for (alias in aliasCandidates) {
      ProgressManager.checkCanceled()
      analyze(alias) {
        val aliasSymbol = alias.symbol
        val rightSideType =
          aliasSymbol.expandedType.render(
            KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
            Variance.INVARIANT,
          )

        if (rightSideType == expandingTo) {
          alias.fqName?.asString()?.let { fqn -> definitions.add(fqn) }
        }
      }
    }
    return definitions
  }

  /**
   * The core, uncached implementation of the annotation search.
   *
   * This function performs a breadth-first search to find all preview annotations in the project.
   * It starts with the base `@Preview` annotation and iteratively finds all annotations and type
   * aliases that are directly or indirectly annotated with it.
   *
   * @return A set of FQNs of all preview annotations.
   */
  private suspend fun getAllPreviewAnnotations(): Set<String> {
    val allPreviewFqns = mutableSetOf<String>()
    // A queue of FQNs to process. We start with the base @Preview annotation and add any new
    // multi-preview annotations or type aliases we find.
    val processingQueue = ArrayDeque<String>()

    // Seed the search with the base @Preview annotation.
    processingQueue.add(COMPOSE_PREVIEW_ANNOTATION_FQN)
    allPreviewFqns.add(COMPOSE_PREVIEW_ANNOTATION_FQN)

    // Process the queue until we have found all transitive annotations.
    while (processingQueue.isNotEmpty()) {
      val currentFqn = processingQueue.removeFirst()

      // Find all annotations and type aliases that are directly annotated with or expand to the
      // current FQN.
      val directAnnotations =
        smartReadAction(project) { findMultipreviewAnnotationClasses(currentFqn) }
      val directAliases = smartReadAction(project) { findTypeAliases(currentFqn) }

      (directAnnotations + directAliases).forEach { fqn ->
        // If we haven't seen this FQN before, add it to our results and to the queue to
        // process in a subsequent iteration.
        if (allPreviewFqns.add(fqn)) {
          processingQueue.add(fqn)
        }
      }
    }
    return allPreviewFqns
  }
}

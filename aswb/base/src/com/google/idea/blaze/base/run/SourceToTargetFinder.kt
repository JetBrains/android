/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run

import com.google.common.util.concurrent.ListenableFuture
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.RuleType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Optional
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Searches through the transitive rdeps map for blaze rules of a certain type which build a given
 * source file.
 */
interface SourceToTargetFinder {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<SourceToTargetFinder> =
      ExtensionPointName.create("com.google.idea.blaze.SourceToTargetFinder")

    /**
     * Iterates through all [SourceToTargetFinder]s, returning the first non-empty result found.
     *
     * This method blocks until a result is found or all finders have finished.
     */
    @JvmStatic
    fun findTargetsForSourceFile(
      project: Project,
      sourceFile: File,
      ruleType: Optional<RuleType>,
    ): Collection<TargetInfo> {
      return findTargetsForSourceFiles(project, setOf(sourceFile), ruleType)
    }

    /**
     * Iterates through all [SourceToTargetFinder]s, returning the first non-empty result found.
     *
     * This method blocks until a result is found or all finders have finished.
     */
    @JvmStatic
    fun findTargetsForSourceFiles(
      project: Project,
      sourceFiles: Set<File>,
      ruleType: Optional<RuleType>,
    ): Collection<TargetInfo> {
      val finders = EP_NAME.extensions
      if (finders.isEmpty()) {
        return emptyList()
      }

      return runBlockingCancellable {
        val sortedFinders = finders.sortedByDescending { it.priority() }
        val futures = sortedFinders.map { it.targetsForSourceFiles(project, sourceFiles, ruleType) }

        // Check for immediately available results in priority order.
        futures.asSequence()
          .filter { it.isDone }
          .mapNotNull { getValueSafe { it.get() } }
          .firstOrNull { it.isNotEmpty() }
          ?.let { return@runBlockingCancellable it }

        // Race the remaining futures.
        channelFlow {
          futures.forEach { future ->
            launch {
              val result = getValueSafe { future.await() }
              if (!result.isNullOrEmpty()) {
                send(result)
              }
            }
          }
        }.firstOrNull() ?: emptyList()
      }
    }

    private inline fun <T> getValueSafe(block: () -> T): T? {
      try {
        return block()
      }
      catch (e: Exception) {
        if (e is ProcessCanceledException || e is CancellationException || e is InterruptedException) {
          throw e
        }
        thisLogger().error(e)
        return null
      }
    }
  }

  /**
   * Finds all rules of the given type 'reachable' from the given source files (i.e. with one of the
   * sources included in srcs, deps or runtime_deps).
   */
  fun targetsForSourceFiles(
    project: Project,
    sourceFiles: Set<File>,
    ruleType: Optional<RuleType>,
  ): ListenableFuture<Collection<TargetInfo>>

  /**
   * Priority of this finder. Higher priority finders are preferred when multiple finders return results simultaneously.
   */
  fun priority(): Int = 0
}

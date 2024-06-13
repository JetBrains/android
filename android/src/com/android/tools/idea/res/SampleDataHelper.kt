/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.SampleDataResourceValue
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet

/**
 * Return all the [SampleDataResourceItem] representing images in all namespaces accessible from
 * this repository
 */
fun ResourceRepository.getSampleDataOfType(
  type: SampleDataResourceItem.ContentType
): Sequence<SampleDataResourceItem> {
  val namespaces = this.namespaces.asSequence()

  return namespaces
    .flatMap { this.getResources(it, ResourceType.SAMPLE_DATA).values().asSequence() }
    .filterIsInstance<SampleDataResourceItem>()
    .filter { it.contentType == type }
}

/**
 * Get all Drawable resource available for this [SampleDataResourceItem].
 *
 * If this item is does not have the [SampleDataResourceItem.ContentType.IMAGE], an empty list will
 * be return
 */
fun SampleDataResourceItem.getDrawableResources(): List<ResourceValue> {
  if (this.contentType != SampleDataResourceItem.ContentType.IMAGE) {
    return emptyList()
  }
  val value = resourceValue as SampleDataResourceValue
  return value.valueAsLines.map { line ->
    ResourceValueImpl(
      referenceToSelf.namespace,
      ResourceType.DRAWABLE,
      referenceToSelf.name,
      line,
      value.libraryName,
    )
  }
}

/** Loads the [SampleDataResourceItem]s for the given [facet]. */
private suspend fun loadSampleDataItems(
  facet: AndroidFacet,
  repository: SampleDataResourceRepository,
): ImmutableList<SampleDataResourceItem> {
  val psiManager = PsiManager.getInstance(facet.getMainModule().project)
  val lookupModules =
    listOf(facet.mainModule) + facet.mainModule.getModuleSystem().getResourceModuleDependencies()

  // This collects all modules and dependencies and finds the sampledata directory in all of them.
  // The order is relevant since the
  // modules will override sampledata from parents (for example the app module from a library
  // module).
  return lookupModules
    .mapNotNull { it.getModuleSystem().getSampleDataDirectory().toVirtualFile() }
    .flatMap { it.children.toList() }
    .mapNotNull {
      readAction { if (it.isDirectory) psiManager.findDirectory(it) else psiManager.findFile(it) }
    }
    .flatMap {
      withContext(AndroidDispatchers.diskIoThread) {
        SampleDataResourceItem.getFromPsiFileSystemItem(repository, it)
      }
    }
    .toImmutableList()
}

/**
 * Loads the [SampleDataResourceItem]s for the given [facet] asynchronously using the given
 * executor. This method returns a [CompletableFuture] that will complete with the result or an
 * exception if there is an error. If the returned [CompletableFuture] is cancelled, the loading
 * task will be interrupted.
 */
internal fun loadSampleDataItemsAsync(
  facet: AndroidFacet,
  repository: SampleDataResourceRepository,
  executor: Executor,
): CompletableFuture<List<SampleDataResourceItem>> =
  AndroidCoroutineScope(repository)
    .async(executor.asCoroutineDispatcher()) {
      return@async loadSampleDataItems(facet, repository)
    }
    .asCompletableFuture()

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
package com.android.tools.idea.res.psi

import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor

/**
 * Looks up definitions of an Android resource.
 */
class ResourceDefinitionSearch : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(/* requireReadAction = */ true) {

  override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>) {
    val project = queryParameters.project
    val element = queryParameters.element
    val refElement = ResourceReferencePsiElement.create(element) ?: return
    val resourceRepositoryManager = StudioResourceRepositoryManager.getInstance(element) ?: return
    val resourceReference = refElement.resourceReference
    val repository = resourceRepositoryManager.getResourcesForNamespace(resourceReference.namespace) ?: return
    var minQualifiers = Int.MAX_VALUE
    for (resource in repository.getResources(resourceReference).filterDefinitions(project).sortedBy { it.configuration.qualifiers.size }) {
      val declaration = ResourceRepositoryToPsiResolver.resolveToDeclaration(resource, project)
      if (declaration != null) {
        val numQualifiers = resource.configuration.qualifiers.size
        if (numQualifiers > minQualifiers) {
          break
        }
        minQualifiers = numQualifiers
        if (!consumer.process(declaration)) {
          break
        }
      }
    }
  }
}
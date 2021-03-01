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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.ViewNodeCache
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersResponse

/**
 * The max initial elements requested for a List/Array.
 */
const val MAX_INITIAL_ITERABLE_SIZE = 5

/**
 * Cache of compose parameters, to avoid expensive refetches when possible.
 */
class ComposeParametersCache(
  private val client: ComposeLayoutInspectorClient,
  model: InspectorModel
) : ViewNodeCache<ComposeParametersData>(model), ViewNodeAndResourceLookup by model {

  override suspend fun fetchDataFor(root: ViewNode, node: ViewNode): ComposeParametersData? {
    val response = client.getParameters(root.drawId, node.drawId)
    return if (response != GetParametersResponse.getDefaultInstance()) {
      ComposeParametersDataGenerator(StringTableImpl(response.stringsList), this).generate(root.drawId, response.parameterGroup)
    }
    else {
      null
    }
  }

  private suspend fun fetchMoreDataFor(
    rootId: Long,
    reference: ParameterReference,
    startIndex: Int,
    maxElements: Int
  ): ParameterGroupItem? {
    val response = client.getParameterDetails(rootId, reference, startIndex, maxElements)
    return if (response != GetParameterDetailsResponse.getDefaultInstance()) {
      ComposeParametersDataGenerator(StringTableImpl(response.stringsList), this).generate(rootId, reference.nodeId, response.parameter)
    }
    else {
      null
    }
  }

  fun setAllFrom(response: GetAllParametersResponse) {
    val stringTable = StringTableImpl(response.stringsList)
    for (group in response.parameterGroupsList) {
      val rootId = response.rootViewId
      setDataFor(rootId, group.composableId, ComposeParametersDataGenerator(stringTable, this).generate(rootId, group))
    }
  }

  override fun resolve(rootId: Long, reference: ParameterReference, callback: (ParameterGroupItem?) -> Unit) {
    val cachedParameter = lookupInCache(rootId, reference, null)
    if (cachedParameter == null || cachedParameter.children.isNotEmpty() || !allowFetching) {
      return callback(cachedParameter)
    }

    CoroutineScope(Dispatchers.Unconfined).launch {
      val parameter = withContext(AndroidDispatchers.workerThread) {
        fetchMoreDataFor(rootId, reference, 0, MAX_INITIAL_ITERABLE_SIZE)
      }
      ApplicationManager.getApplication().invokeLater {
        if (parameter != null) {
          lookupInCache(rootId, reference, parameter)
        }
        callback(parameter)
      }
    }
  }

  private fun lookupInCache(rootId: Long, reference: ParameterReference, replacement: ParameterGroupItem?): ParameterGroupItem? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val data = getCachedDataFor(rootId, reference.nodeId) ?: return null
    if (reference.parameterIndex !in data.parameterList.indices) {
      return null
    }
    var parameter = data.parameterList[reference.parameterIndex] as? ParameterGroupItem ?: return null
    for (referenceIndex in reference.indices) {
      var next = if (referenceIndex in parameter.children.indices) parameter.children[referenceIndex] else null
      if (next == null || next.index != -1) {
        val elementIndex = parameter.children.binarySearch { it.index - referenceIndex }
        if (elementIndex < 0) {
          return null
        }
        next = parameter.children[elementIndex]
      }
      parameter = next as? ParameterGroupItem ?: return null
    }
    replacement?.let { parameter.cloneChildrenFrom(replacement) }
    return parameter
  }
}

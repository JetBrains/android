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
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.ViewNodeCache
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.property.ptable.PTableGroupModification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersResponse

/** The max initial elements requested for a List/Array. */
const val MAX_INITIAL_ITERABLE_SIZE = 5

/** Cache of compose parameters, to avoid expensive refetches when possible. */
class ComposeParametersCache(
  private val client: ComposeLayoutInspectorClient?,
  model: InspectorModel,
) : ViewNodeCache<ComposeParametersData>(model), ViewNodeAndResourceLookup by model {

  override suspend fun fetchDataFor(root: ViewNode, node: ViewNode): ComposeParametersData? {
    val anchorHash = (node as? ComposeViewNode)?.anchorHash ?: 0
    val response = client?.getParameters(root.drawId, node.drawId, anchorHash) ?: return null
    return if (response != GetParametersResponse.getDefaultInstance()) {
      ComposeParametersDataGenerator(StringTableImpl(response.stringsList), this)
        .generate(root.drawId, response.parameterGroup)
    } else {
      null
    }
  }

  private suspend fun fetchMoreDataFor(
    rootId: Long,
    reference: ParameterReference,
    startIndex: Int,
    maxElements: Int,
  ): ParameterGroupItem? {
    val response =
      client?.getParameterDetails(rootId, reference, startIndex, maxElements) ?: return null
    return if (response != GetParameterDetailsResponse.getDefaultInstance()) {
      ComposeParametersDataGenerator(StringTableImpl(response.stringsList), this)
        .generate(rootId, reference.nodeId, reference.kind, response.parameter)
    } else {
      null
    }
  }

  fun setAllFrom(response: GetAllParametersResponse) {
    val stringTable = StringTableImpl(response.stringsList)
    for (group in response.parameterGroupsList) {
      val rootId = response.rootViewId
      setDataFor(
        rootId,
        group.composableId,
        ComposeParametersDataGenerator(stringTable, this).generate(rootId, group),
      )
    }
  }

  /**
   * Resolve a [reference] found in a [ParameterGroupItem].
   *
   * This method is supposed to find/load child parameter information.
   *
   * The [reference] could be a reference to:
   * - another [ParameterGroupItem] already in the parameter cache, if so return that parameter
   * - reference to a parameter not retrieved from the device yet, if so load it from the device
   *
   * For references to List/Array we need to pay attention to [startIndex]:
   * - if another parameter in the cache already has the requested element, return that parameter
   * - otherwise load more child elements from the device and update the cached parameter to avoid a
   *   later download of the same information.
   */
  override fun resolve(
    rootId: Long,
    reference: ParameterReference,
    startIndex: Int,
    maxElements: Int,
    callback: (ParameterGroupItem?, PTableGroupModification?) -> Unit,
  ) {
    val cachedParameter = lookupInCache(rootId, reference)
    if (
      (cachedParameter != null && cachedParameter.lastRealChildReferenceIndex >= startIndex) ||
        !allowFetching
    ) {
      return callback(cachedParameter, null)
    }

    CoroutineScope(Dispatchers.Unconfined).launch {
      val expansion =
        withContext(AndroidDispatchers.workerThread) {
          fetchMoreDataFor(rootId, reference, startIndex, maxElements)
        }
      ApplicationManager.getApplication().invokeLater {
        if (cachedParameter != null) {
          val modification = expansion?.let { cachedParameter.applyReplacement(it) }
          callback(cachedParameter, modification)
        } else {
          callback(expansion, null)
        }
      }
    }
  }

  /**
   * Find a parameter from the parameter cache from a [reference].
   *
   * First identify the composite parameter from rootId, nodeId & parameterIndex. Then use
   * [ParameterReference.indices] to navigate in a nested composite parameter value.
   */
  private fun lookupInCache(rootId: Long, reference: ParameterReference): ParameterGroupItem? {
    ThreadingAssertions.assertEventDispatchThread()
    val data = getCachedDataFor(rootId, reference.nodeId) ?: return null
    val parameters = data.parametersOfKind(reference.kind)
    if (reference.parameterIndex !in parameters.indices) {
      return null
    }
    var parameter = parameters[reference.parameterIndex] as? ParameterGroupItem ?: return null
    for (referenceIndex in reference.indices) {
      val elementIndex = parameter.elementIndexOf(referenceIndex)
      if (elementIndex < 0) {
        return null
      }
      parameter = parameter.children[elementIndex] as? ParameterGroupItem ?: return null
    }
    return parameter
  }
}

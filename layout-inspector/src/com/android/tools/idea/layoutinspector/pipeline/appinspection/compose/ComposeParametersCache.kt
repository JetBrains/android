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

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.ViewNodeCache
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParametersResponse

/**
 * Cache of compose parameters, to avoid expensive refetches when possible.
 */
class ComposeParametersCache(private val client: ComposeLayoutInspectorClient,
                             model: InspectorModel) : ViewNodeCache<ComposeParametersData>(model) {
  override suspend fun fetchDataFor(root: ViewNode, node: ViewNode): ComposeParametersData? {
    val response = client.getParameters(root.drawId, node.drawId)
    return if (response != GetParametersResponse.getDefaultInstance()) {
      ComposeParametersDataGenerator(StringTableImpl(response.stringsList), response.parameterGroup, model).generate()
    }
    else {
      null
    }
  }

  fun setAllFrom(response: GetAllParametersResponse) {
    val stringTable = StringTableImpl(response.stringsList)
    for (group in response.parameterGroupsList) {
      setDataFor(response.rootViewId, group.composableId, ComposeParametersDataGenerator(stringTable, group, model).generate())
    }
  }
}

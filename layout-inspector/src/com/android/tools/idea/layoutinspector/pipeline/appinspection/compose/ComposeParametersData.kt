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

import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.res.colorToString
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Parameter
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ParameterGroup
import java.awt.Color

class ComposeParametersData(
  /**
   * The parameters associated with a composable as a list.
   */
  val parameterList: List<ParameterItem>,

  /**
   * The parameters associated with a composable as a properties table.
   */
  val parameters: PropertiesTable<InspectorPropertyItem>
)

/**
 * Bridge between incoming proto data and classes expected by the Studio properties framework.
 */
class ComposeParametersDataGenerator(
  private val stringTable: StringTableImpl,
  private val lookup: ViewNodeAndResourceLookup) {

  fun generate(rootId: Long, parameterGroup: ParameterGroup): ComposeParametersData {
    val parameterList = parameterGroup.parameterList.map { it.toParameterItem(rootId, parameterGroup.composableId) }
    return ComposeParametersData(parameterList, toPropertiesTable(parameterList))
  }

  fun generate(rootId: Long, composableId: Long, parameter: Parameter): ParameterGroupItem? =
    parameter.toParameterItem(rootId, composableId) as? ParameterGroupItem

  private fun toPropertiesTable(parameterList: List<ParameterItem>): PropertiesTable<InspectorPropertyItem> {
    val propertyTable = HashBasedTable.create<String, String, InspectorPropertyItem>()
    parameterList.forEach { propertyTable.put(it.namespace, it.name, it) }
    return PropertiesTable.create(propertyTable)
  }

  private fun Parameter.toParameterItem(rootId: Long, composableId: Long): ParameterItem {
    val name = stringTable[name]
    if (type == Parameter.Type.LAMBDA || type == Parameter.Type.FUNCTION_REFERENCE) {
      return LambdaParameterItem(
        name = name,
        viewId = composableId,
        rootId = rootId,
        index = index,
        packageName = stringTable[lambdaValue.packageName],
        fileName = stringTable[lambdaValue.fileName],
        lambdaName = stringTable[lambdaValue.lambdaName],
        functionName = stringTable [lambdaValue.functionName],
        startLineNumber = lambdaValue.startLineNumber,
        endLineNumber = lambdaValue.endLineNumber,
        lookup = lookup
      )
    }

    val value: Any = when (type) {
      Parameter.Type.STRING -> stringTable[int32Value]
      Parameter.Type.BOOLEAN -> (int32Value == 1)
      Parameter.Type.INT32 -> int32Value
      Parameter.Type.INT64 -> int64Value
      Parameter.Type.DOUBLE -> doubleValue
      Parameter.Type.FLOAT,
      Parameter.Type.DIMENSION_DP,
      Parameter.Type.DIMENSION_SP,
      Parameter.Type.DIMENSION_EM -> floatValue
      //Parameter.Type.RESOURCE -> TODO: Support converting resource type
      Parameter.Type.COLOR -> colorToString(Color(int32Value))
      else -> ""
    }
    val type = type.convert()
    val reference = reference.convert()

    if (elementsList.isEmpty() && reference == null) {
      return ParameterItem(
        name,
        type,
        value.toString(),
        composableId,
        lookup,
        rootId,
        index
      )
    }
    else {
      return ParameterGroupItem(
        name,
        type,
        value.toString(),
        composableId,
        lookup,
        rootId,
        index,
        reference,
        elementsList.mapTo(mutableListOf()) { it.toParameterItem(rootId, composableId) })
    }
  }
}

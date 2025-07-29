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

import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import java.awt.Polygon
import java.awt.Rectangle
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetRecompositionStateReadResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.RecompositionStateRead
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.RecompositionStateReadEvent

fun LayoutInspectorComposeProtocol.Quad.toPolygon(): Polygon {
  return Polygon(intArrayOf(x0, x1, x2, x3), intArrayOf(y0, y1, y2, y3), 4)
}

fun LayoutInspectorComposeProtocol.Rect.toRectangle() = Rectangle(x, y, w, h)

fun LayoutInspectorComposeProtocol.Parameter.Type.convert(): PropertyType {
  return when (this) {
    LayoutInspectorComposeProtocol.Parameter.Type.STRING -> PropertyType.STRING
    LayoutInspectorComposeProtocol.Parameter.Type.BOOLEAN -> PropertyType.BOOLEAN
    LayoutInspectorComposeProtocol.Parameter.Type.DOUBLE -> PropertyType.DOUBLE
    LayoutInspectorComposeProtocol.Parameter.Type.FLOAT -> PropertyType.FLOAT
    LayoutInspectorComposeProtocol.Parameter.Type.INT32 -> PropertyType.INT32
    LayoutInspectorComposeProtocol.Parameter.Type.INT64 -> PropertyType.INT64
    LayoutInspectorComposeProtocol.Parameter.Type.COLOR -> PropertyType.COLOR
    LayoutInspectorComposeProtocol.Parameter.Type.RESOURCE -> PropertyType.RESOURCE
    LayoutInspectorComposeProtocol.Parameter.Type.DIMENSION_DP -> PropertyType.DIMENSION_DP
    LayoutInspectorComposeProtocol.Parameter.Type.DIMENSION_SP -> PropertyType.DIMENSION_SP
    LayoutInspectorComposeProtocol.Parameter.Type.DIMENSION_EM -> PropertyType.DIMENSION_EM
    LayoutInspectorComposeProtocol.Parameter.Type.LAMBDA -> PropertyType.LAMBDA
    LayoutInspectorComposeProtocol.Parameter.Type.FUNCTION_REFERENCE ->
      PropertyType.FUNCTION_REFERENCE
    LayoutInspectorComposeProtocol.Parameter.Type.ITERABLE -> PropertyType.ITERABLE
    else -> error { "Unhandled parameter type $this" }
  }
}

fun LayoutInspectorComposeProtocol.ParameterReference.Kind.convert(): ParameterKind =
  when (this) {
    LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL -> ParameterKind.Normal
    LayoutInspectorComposeProtocol.ParameterReference.Kind.MERGED_SEMANTICS ->
      ParameterKind.MergedSemantics
    LayoutInspectorComposeProtocol.ParameterReference.Kind.UNMERGED_SEMANTICS ->
      ParameterKind.UnmergedSemantics
    else -> ParameterKind.Unknown
  }

fun LayoutInspectorComposeProtocol.ParameterReference.convert(): ParameterReference? {
  if (this == LayoutInspectorComposeProtocol.ParameterReference.getDefaultInstance()) {
    return null
  }
  return ParameterReference(
    composableId,
    anchorHash,
    kind.convert(),
    parameterIndex,
    convertCompositeIndexList(compositeIndexList),
  )
}

private fun convertCompositeIndexList(reference: List<Int>): IntArray {
  if (reference.isEmpty()) {
    return emptyIntArray()
  }
  return IntArray(reference.size) { index -> reference[index] }
}

fun GetComposablesResponse.countNodes(): Array<Any?> {
  val result = Counts(0, 0, 0)
  rootsList.forEach { root -> root.nodesList.forEach { it.countNodes(result, 0) } }
  return arrayOf(result.nodes, result.systemNodes, result.depth)
}

private fun ComposableNode.countNodes(counts: Counts, depth: Int) {
  counts.nodes++
  counts.depth = maxOf(depth, counts.depth)
  if (flags.and(ComposableNode.Flags.SYSTEM_CREATED_VALUE) != 0) {
    counts.systemNodes++
  }
  childrenList.forEach { it.countNodes(counts, depth + 1) }
}

private data class Counts(var nodes: Int, var systemNodes: Int, var depth: Int)

fun convertStateRead(
  response: GetRecompositionStateReadResponse,
  lookup: ViewNodeAndResourceLookup,
): List<RecomposeStateReadData> {
  val stringTable = StringTableImpl(response.stringsList)
  val valueGenerator = ComposeParametersDataGenerator(stringTable, lookup)
  return convertRecompositionStateRead(response.read, stringTable, valueGenerator)
}

fun convertStateReadEvent(
  event: RecompositionStateReadEvent,
  lookup: ViewNodeAndResourceLookup,
  consume: (Int, List<RecomposeStateReadData>) -> Unit,
) {
  val stringTable = StringTableImpl(event.stringsList)
  val valueGenerator = ComposeParametersDataGenerator(stringTable, lookup)
  event.readList.forEach { read ->
    consume(
      read.recompositionNumber,
      convertRecompositionStateRead(read, stringTable, valueGenerator),
    )
  }
}

fun convertRecompositionStateRead(
  read: RecompositionStateRead,
  stringTable: StringTable,
  valueGenerator: ComposeParametersDataGenerator,
): List<RecomposeStateReadData> {
  return read.readList.map { read ->
    val item = valueGenerator.generateItem(-1L, -1L, ParameterKind.Unknown, read.value)
    RecomposeStateReadData(
      item,
      read.valueInstanceHash,
      read.invalidated,
      read.stackTraceLineList.map {
        TraceElement(
          declaringClass = stringTable[it.declaringClass],
          methodName = stringTable[it.methodName],
          fileName = stringTable[it.fileName],
          lineNumber = it.lineNumber,
        )
      },
    )
  }
}

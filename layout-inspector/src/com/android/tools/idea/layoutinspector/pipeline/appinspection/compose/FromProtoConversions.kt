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

import com.android.tools.idea.layoutinspector.properties.PropertyType
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import java.awt.Polygon
import java.awt.Shape

fun LayoutInspectorComposeProtocol.Quad.toShape(): Shape {
  return Polygon(intArrayOf(x0, x1, x2, x3), intArrayOf(y0, y1, y2, y3), 4)
}

fun LayoutInspectorComposeProtocol.Parameter.Type.convert(): PropertyType {
  return when(this) {
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
    LayoutInspectorComposeProtocol.Parameter.Type.FUNCTION_REFERENCE -> PropertyType.FUNCTION_REFERENCE
    LayoutInspectorComposeProtocol.Parameter.Type.ITERABLE -> PropertyType.ITERABLE
    else -> error { "Unhandled parameter type $this"}
  }
}

fun LayoutInspectorComposeProtocol.ParameterReference.Kind.convert(): ParameterKind = when (this) {
  LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL -> ParameterKind.Normal
  LayoutInspectorComposeProtocol.ParameterReference.Kind.MERGED_SEMANTICS -> ParameterKind.MergedSemantics
  LayoutInspectorComposeProtocol.ParameterReference.Kind.UNMERGED_SEMANTICS -> ParameterKind.UnmergedSemantics
  else -> ParameterKind.Unknown
}

fun LayoutInspectorComposeProtocol.ParameterReference.convert(): ParameterReference? {
  if (this == LayoutInspectorComposeProtocol.ParameterReference.getDefaultInstance()) {
    return null
  }
  return ParameterReference(
    composableId,
    kind.convert(),
    parameterIndex,
    convertCompositeIndexList(compositeIndexList)
  )
}

private fun convertCompositeIndexList(reference: List<Int>): IntArray {
  if (reference.isEmpty()) {
    return emptyIntArray()
  }
  return IntArray(reference.size) { index -> reference[index] }
}

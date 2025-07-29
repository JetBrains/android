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
@file:Suppress("TestFunctionName")

package com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl

import com.android.tools.idea.layoutinspector.util.TestStringTable
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Bounds
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableRoot
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetRecompositionStateReadResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Parameter
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Parameter.Type
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ParameterGroup
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ParameterReference
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Quad
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.RecompositionStateRead
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.RecompositionStateReadEvent
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Rect
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Resource
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.StackTraceLine
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.StateRead
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.StringEntry

// example: "at androidx.compose.runtime.CompositionImpl.recordReadOf(Composition.kt:1015)"
private val stackTraceLinePattern =
  Regex("\\s*at ([\\w$\\.]+)\\.([\\w$-]+)\\(([ $\\.\\w]+):(-?\\d+)\\)")

// Need to create a helper function to avoid name ambiguity
private fun createComposableString(id: Int, str: String): StringEntry {
  return StringEntry.newBuilder().setId(id).setStr(str).build()
}

fun ComposableString(id: Int, str: String) = createComposableString(id, str)

fun GetComposablesResponse.Builder.ComposableString(id: Int, str: String) {
  addStrings(createComposableString(id, str))
}

// Need to create a helper function to avoid name ambiguity
private fun createComposableRoot(init: ComposableRoot.Builder.() -> Unit): ComposableRoot {
  return ComposableRoot.newBuilder().apply(init).build()
}

fun ComposableRoot(init: ComposableRoot.Builder.() -> Unit) = createComposableRoot(init)

fun GetComposablesResponse.Builder.ComposableRoot(init: ComposableRoot.Builder.() -> Unit) {
  addRoots(createComposableRoot(init))
}

// Need to create a helper function to avoid name ambiguity
private fun createComposableNode(init: ComposableNode.Builder.() -> Unit): ComposableNode {
  return ComposableNode.newBuilder().apply(init).build()
}

fun ComposableNode(init: ComposableNode.Builder.() -> Unit) = createComposableNode(init)

fun ComposableNode.Builder.ComposableNode(init: ComposableNode.Builder.() -> Unit) {
  addChildren(createComposableNode(init))
}

fun ComposableRoot.Builder.ComposableNode(init: ComposableNode.Builder.() -> Unit) {
  addNodes(createComposableNode(init))
}

fun ComposableBounds(layout: Rect, render: Quad? = null): Bounds {
  return Bounds.newBuilder()
    .apply {
      this.layout = layout
      render?.let { this.render = it }
    }
    .build()
}

fun ComposableRectBounds(x: Int, y: Int, w: Int, h: Int): Bounds {
  return Bounds.newBuilder().apply { this.layout = ComposableRect(x, y, w, h) }.build()
}

fun ComposableRect(w: Int, h: Int): Rect = ComposableRect(0, 0, w, h)

fun ComposableRect(x: Int, y: Int, w: Int, h: Int): Rect {
  return Rect.newBuilder().setX(x).setY(y).setW(w).setH(h).build()
}

fun ComposableQuad(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int): Quad {
  return Quad.newBuilder()
    .apply {
      this.x0 = x0
      this.y0 = y0
      this.x1 = x1
      this.y1 = y1
      this.x2 = x2
      this.y2 = y2
      this.x3 = x3
      this.y3 = y3
    }
    .build()
}

fun ComposableResource(type: Int, namespace: Int, name: Int): Resource {
  return Resource.newBuilder()
    .apply {
      this.type = type
      this.namespace = namespace
      this.name = name
    }
    .build()
}

fun ParameterGroup(init: ParameterGroup.Builder.() -> Unit): ParameterGroup {
  return ParameterGroup.newBuilder().apply(init).build()
}

fun ExpandedParameter(init: Parameter.Builder.() -> Unit): Parameter {
  return Parameter.newBuilder().apply(init).build()
}

fun ParameterGroup.Builder.Parameter(init: Parameter.Builder.() -> Unit) {
  addParameter(Parameter.newBuilder().apply(init).build())
}

fun Parameter.Builder.Element(init: Parameter.Builder.() -> Unit) {
  addElements(Parameter.newBuilder().apply(init).build())
}

fun Parameter.Builder.Reference(init: ParameterReference.Builder.() -> Unit) {
  referenceBuilder.apply(init).build()
}

fun RecompositionStateReadResponse(
  init: RecompositionStateReadResponseBuilder.() -> Unit
): GetRecompositionStateReadResponse {
  val builder = RecompositionStateReadResponseBuilder()
  builder.init()
  return builder.build()
}

class RecompositionStateReadResponseBuilder {
  private val builder = GetRecompositionStateReadResponse.newBuilder()
  private val strings = TestStringTable()

  fun AnchorHash(value: Int) {
    builder.anchorHash = value
  }

  fun FirstRecomposition(value: Int) {
    builder.firstRecomposition = value
  }

  fun RecompositionStateRead(init: RecompositionStateReadBuilder.() -> Unit) {
    val read = RecompositionStateReadBuilder(strings)
    read.init()
    builder.read = read.build()
  }

  fun build(): GetRecompositionStateReadResponse {
    builder.addAllStrings(strings.asComposeStrings())
    return builder.build()
  }
}

fun MakeRecompositionStateReadEvent(
  init: RecompositionStateReadEventBuilder.() -> Unit
): RecompositionStateReadEvent {
  val builder = RecompositionStateReadEventBuilder()
  builder.init()
  return builder.build()
}

class RecompositionStateReadEventBuilder {
  private val builder = RecompositionStateReadEvent.newBuilder()
  private val strings = TestStringTable()

  fun AnchorHash(value: Int) {
    builder.anchorHash = value
  }

  fun RecompositionStateRead(init: RecompositionStateReadBuilder.() -> Unit) {
    val read = RecompositionStateReadBuilder(strings)
    read.init()
    builder.addRead(read.build())
  }

  fun build(): RecompositionStateReadEvent {
    builder.addAllStrings(strings.asComposeStrings())
    return builder.build()
  }
}

class RecompositionStateReadBuilder(private val strings: TestStringTable) {
  private val builder = RecompositionStateRead.newBuilder()

  fun Recomposition(value: Int) {
    builder.recompositionNumber = value
  }

  fun StateRead(init: StateReadBuilder.() -> Unit) {
    val read = StateReadBuilder(strings)
    read.init()
    builder.addRead(read.build())
  }

  fun build(): RecompositionStateRead = builder.build()
}

class StateReadBuilder(private val strings: TestStringTable) {
  private val builder = StateRead.newBuilder()

  fun ValueInstance(value: Int) {
    builder.valueInstanceHash = value
  }

  fun Invalidated(value: Boolean) {
    builder.invalidated = value
  }

  fun Parameter(name: String, type: Type, value: Any, init: ParameterBuilder.() -> Unit = {}) {
    val parameter = ParameterBuilder(name, type, value, strings)
    parameter.init()
    builder.value = parameter.build()
  }

  fun StackTraceLine(
    declaringClass: String,
    methodName: String,
    fileName: String,
    lineNumber: Int,
  ) {
    builder.addStackTraceLine(
      StackTraceLine.newBuilder().apply {
        this.declaringClass = strings.add(declaringClass)
        this.methodName = strings.add(methodName)
        this.fileName = strings.add(fileName)
        this.lineNumber = lineNumber
      }
    )
  }

  fun ParseStackTraceLines(trace: String) {
    trace.lines().forEach { line ->
      val match = stackTraceLinePattern.matchEntire(line) ?: error("Could not parse: \"$line\"")
      StackTraceLine(
        match.groupValues[1],
        match.groupValues[2],
        match.groupValues[3],
        match.groupValues[4].toInt(),
      )
    }
  }

  fun build(): StateRead = builder.build()
}

class ParameterBuilder(name: String, type: Type, value: Any, private val strings: TestStringTable) {
  private val builder = Parameter.newBuilder()

  init {
    builder.name = strings.add(name)
    builder.type = type
    setValue(type, value)
  }

  private fun setValue(type: Type, value: Any) {
    when (type) {
      Type.STRING -> builder.int32Value = strings.add(value.toString())
      Type.BOOLEAN -> builder.int32Value = if (value == true) 1 else 0
      Type.DOUBLE -> builder.doubleValue = value as Double
      Type.FLOAT -> builder.floatValue = value as Float
      Type.INT32 -> builder.int32Value = value as Int
      Type.INT64 -> builder.int64Value = value as Long
      Type.COLOR -> builder.int32Value = value as Int
      Type.DIMENSION_DP -> builder.floatValue = value as Float
      Type.DIMENSION_SP -> builder.floatValue = value as Float
      Type.DIMENSION_EM -> builder.floatValue = value as Float
      Type.ITERABLE -> builder.int32Value = strings.add(value.toString())
      else -> error("unexpected type: $type")
    }
  }

  fun Element(name: String, type: Type, value: Any, init: ParameterBuilder.() -> Unit = {}) {
    val parameter = ParameterBuilder(name, type, value, strings)
    parameter.init()
    builder.addElements(parameter.build())
  }

  fun Reference(anchorHash: Int) {
    builder.referenceBuilder.apply {
      this.kind = ParameterReference.Kind.NORMAL
      this.anchorHash = anchorHash
    }
  }

  fun build(): Parameter = builder.build()
}

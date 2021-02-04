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

import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Bounds
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableRoot
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Parameter
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ParameterGroup
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Quad
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Rect
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Resource
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.StringEntry

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
  return Bounds.newBuilder().apply {
    this.layout = layout
    render?.let { this.render = it }
  }.build()
}

fun ComposableRect(w: Int, h: Int): Rect = ComposableRect(0, 0, w, h)

fun ComposableRect(x: Int, y: Int, w: Int, h: Int): Rect {
  return Rect.newBuilder().setX(x).setY(y).setW(w).setH(h).build()
}

fun ComposableQuad(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int): Quad {
  return Quad.newBuilder().apply {
    this.x0 = x0
    this.y0 = y0
    this.x1 = x1
    this.y1 = y1
    this.x2 = x2
    this.y2 = y2
    this.x3 = x3
    this.y3 = y3
  }.build()
}

fun ComposableResource(type: Int, namespace: Int, name: Int): Resource {
  return Resource.newBuilder().apply {
    this.type = type
    this.namespace = namespace
    this.name = name
  }.build()
}

fun ParameterGroup(init: ParameterGroup.Builder.() -> Unit): ParameterGroup {
  return ParameterGroup.newBuilder().apply(init).build()
}

fun ParameterGroup.Builder.Parameter(init: Parameter.Builder.() -> Unit) {
  addParameter(Parameter.newBuilder().apply(init).build())
}

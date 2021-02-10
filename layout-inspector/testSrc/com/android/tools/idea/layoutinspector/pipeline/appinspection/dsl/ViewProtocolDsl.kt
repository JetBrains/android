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

import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Bounds
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Property
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertyGroup
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Quad
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Rect
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StringEntry
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ViewNode

// Need to create a helper function to avoid name ambiguity
private fun createViewString(id: Int, str: String): StringEntry {
  return StringEntry.newBuilder().setId(id).setStr(str).build()
}

fun ViewString(id: Int, str: String) = createViewString(id, str)

fun LayoutEvent.Builder.ViewString(id: Int, str: String) {
  addStrings(createViewString(id, str))
}

// Need to create a helper function to avoid name ambiguity
private fun createViewNode(init: ViewNode.Builder.() -> Unit): ViewNode {
  return ViewNode.newBuilder().apply(init).build()
}

fun ViewNode(init: ViewNode.Builder.() -> Unit) = createViewNode(init)

fun ViewNode.Builder.ViewNode(init: ViewNode.Builder.() -> Unit) {
  addChildren(createViewNode(init))
}

fun ViewBounds(layout: Rect, render: Quad? = null): Bounds {
  return Bounds.newBuilder().apply {
    this.layout = layout
    render?.let { this.render = it }
  }.build()
}

fun ViewRect(w: Int, h: Int): Rect = ViewRect(0, 0, w, h)

fun ViewRect(x: Int, y: Int, w: Int, h: Int): Rect {
  return Rect.newBuilder().setX(x).setY(y).setW(w).setH(h).build()
}

fun ViewQuad(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int): Quad {
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

fun ViewResource(type: Int, namespace: Int, name: Int): Resource {
  return Resource.newBuilder().apply {
    this.type = type
    this.namespace = namespace
    this.name = name
  }.build()
}

fun PropertyGroup(init: PropertyGroup.Builder.() -> Unit): PropertyGroup {
  return PropertyGroup.newBuilder().apply(init).build()
}

fun PropertyGroup.Builder.Property(init: Property.Builder.() -> Unit) {
  addProperty(Property.newBuilder().apply(init).build())
}

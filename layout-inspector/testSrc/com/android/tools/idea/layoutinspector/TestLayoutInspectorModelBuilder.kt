/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import java.awt.Rectangle

fun model(body: InspectorModelDescriptor.() -> Unit) = InspectorModelDescriptor().also(body).build()

class InspectorViewDescriptor(private val id: String,
                              private val type: String,
                              private val x: Int,
                              private val y: Int,
                              private val width: Int,
                              private val height: Int) {
  private val children = mutableListOf<InspectorViewDescriptor>()

  constructor(id: String, type: String, rect: Rectangle) : this(id, type, rect.x, rect.y, rect.width, rect.height)

  fun view(id: String,
           x: Int,
           y: Int,
           width: Int,
           height: Int,
           type: String = "android.view.View",
           body: InspectorViewDescriptor.() -> Unit = {}) =
    children.add(InspectorViewDescriptor(id, type, x, y, width, height).apply(body))

  fun view(id: String, rect: Rectangle, type: String = "android.view.View", body: InspectorViewDescriptor.() -> Unit = {}) =
    view(id, rect.x, rect.y, rect.width, rect.height, type, body)

  fun build(): InspectorView {
    val result = InspectorView(id, type, x, y, width, height, listOf())
    children.mapTo(result.children) { it.build() }
    return result
  }
}

class InspectorModelDescriptor {
  lateinit var root: InspectorViewDescriptor

  fun view(id: String,
           x: Int,
           y: Int,
           width: Int,
           height: Int,
           type: String = "android.view.View",
           body: InspectorViewDescriptor.() -> Unit = {}) {
    root = InspectorViewDescriptor(id, type, x, y, width, height).apply(body)
  }

  fun view(id: String, rect: Rectangle, type: String = "android.view.View", body: InspectorViewDescriptor.() -> Unit = {}) =
    view(id, rect.x, rect.y, rect.width, rect.height, type, body)

  fun build() = InspectorModel(root.build())
}
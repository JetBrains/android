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

import com.android.SdkConstants.CLASS_VIEW
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.util.ConfigurationBuilder
import com.android.tools.idea.layoutinspector.util.TestStringTable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import java.awt.Image
import java.awt.Rectangle

// TODO: find a way to indicate that this is a api 29+ model without having to specify an image on a subnode
fun model(project: Project = mock(Project::class.java), body: InspectorModelDescriptor.() -> Unit) =
  InspectorModelDescriptor(project).also(body).build()

fun view(drawId: Long,
         x: Int = 0,
         y: Int = 0,
         width: Int = 0,
         height: Int = 0,
         qualifiedName: String = CLASS_VIEW,
         viewId: ResourceReference? = null,
         textValue: String = "",
         imageBottom: Image? = null,
         imageTop: Image? = null,
         layoutFlags: Int = 0,
         layout: ResourceReference? = null,
         body: InspectorViewDescriptor.() -> Unit = {}) =
  InspectorViewDescriptor(drawId, qualifiedName, x, y, width, height, viewId, textValue, imageBottom, imageTop, layoutFlags, layout)
    .also(body).build()

class InspectorViewDescriptor(private val drawId: Long,
                              private val qualifiedName: String,
                              private val x: Int,
                              private val y: Int,
                              private val width: Int,
                              private val height: Int,
                              private val viewId: ResourceReference?,
                              private val textValue: String,
                              private val imageBottom: Image?,
                              private val imageTop: Image?,
                              private val layoutFlags: Int,
                              private val layout: ResourceReference?) {
  private val children = mutableListOf<InspectorViewDescriptor>()

  fun view(drawId: Long,
           x: Int = 0,
           y: Int = 0,
           width: Int = 0,
           height: Int = 0,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           imageBottom: Image? = null,
           imageTop: Image? = null,
           layoutFlags: Int = 0,
           layout: ResourceReference? = null,
           body: InspectorViewDescriptor.() -> Unit = {}) =
    children.add(InspectorViewDescriptor(
      drawId, qualifiedName, x, y, width, height, viewId, textValue, imageBottom, imageTop, layoutFlags, layout).apply(body))

  fun view(drawId: Long,
           rect: Rectangle,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           imageBottom: Image? = null,
           layout: ResourceReference? = null,
           body: InspectorViewDescriptor.() -> Unit = {}) =
    view(drawId, rect.x, rect.y, rect.width, rect.height, qualifiedName, viewId, textValue, imageBottom, null, 0, layout, body)

  fun build(): ViewNode {
    val result = ViewNode(drawId, qualifiedName, layout, x, y, 0, 0, width, height, viewId, textValue, layoutFlags)
    result.imageBottom = imageBottom
    result.imageTop = imageTop
    children.forEach { result.children.add(it.build()) }
    result.children.forEach { it.parent = result }
    return result
  }
}

class InspectorModelDescriptor(val project: Project) {
  private lateinit var root: InspectorViewDescriptor

  fun view(drawId: Long,
           x: Int = 0,
           y: Int = 0,
           width: Int = 0,
           height: Int = 0,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           imageBottom: Image? = null,
           imageTop: Image? = null,
           layoutFlags: Int = 0,
           layout: ResourceReference? = null,
           body: InspectorViewDescriptor.() -> Unit = {}) {
    root = InspectorViewDescriptor(
      drawId, qualifiedName, x, y, width, height, viewId, textValue, imageBottom, imageTop, layoutFlags, layout).apply(body)
  }

  fun view(drawId: Long,
           rect: Rectangle,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           imageBottom: Image? = null,
           body: InspectorViewDescriptor.() -> Unit = {}) =
    view(drawId, rect.x, rect.y, rect.width, rect.height, qualifiedName, viewId, textValue, imageBottom, null, 0, null, body)

  fun build(): InspectorModel {
    val windowRoot = root.build()
    val model = InspectorModel(project)
    model.update(windowRoot, windowRoot.drawId, listOf(windowRoot.drawId))
    if (ModuleManager.getInstance(project) != null) {
      val strings = TestStringTable()
      val config = ConfigurationBuilder(strings)
      model.resourceLookup.updateConfiguration(config.makeDummyConfiguration(project), strings)
    }
    return model
  }
}
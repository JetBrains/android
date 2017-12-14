// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.util.ColoredIconGenerator
import com.android.tools.idea.common.util.iconToImage
import com.android.tools.idea.naveditor.model.schema
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.xml.XmlFile
import icons.AndroidIcons
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.Color
import java.awt.Image

sealed class Destination {
  abstract fun addToGraph()
  abstract val label: String
  abstract val thumbnail: Image?

  var component: NlComponent? = null

  @VisibleForTesting
  data class RegularDestination @JvmOverloads constructor(
      val parent: NlComponent, val tag: String, val destinationLabel: String? = null, val className: String? = null,
      val qualifiedName: String? = null, val idBase: String = className ?: tag, private val layoutFile: XmlFile? = null)
    : Destination() {

    // TODO: render thumbnail with border
    override val thumbnail: Image? by lazy {
      if (parent.model.schema.getDestinationType(tag) == NavigationSchema.DestinationType.ACTIVITY) {
        iconToImage(StudioIcons.NavEditor.ExistingDestinations.ACTIVITY)
      }
      else {
        iconToImage(StudioIcons.NavEditor.ExistingDestinations.DESTINATION)
      }
    }

    override val label: String
      get() = layoutFile?.let { FileUtil.getNameWithoutExtension(it.name) } ?: className ?: tag

    override fun addToGraph() {
      val model = parent.model
      object : WriteCommandAction<Unit>(model.project, "Add $className", model.file) {
        override fun run(result: Result<Unit>) {
          val tag = parent.tag.createChildTag(tag, null, null, true)
          val newComponent = model.createComponent(tag, parent, null)
          newComponent.assignId(idBase)
          newComponent.setAndroidAttribute(SdkConstants.ATTR_NAME, qualifiedName)
          newComponent.setAndroidAttribute(SdkConstants.ATTR_LABEL, label)
          layoutFile?.let {
            // TODO: do this the right way
            val layoutId = "@${ResourceType.LAYOUT.getName()}/${FileUtil.getNameWithoutExtension(it.name)}"
            newComponent.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, layoutId)
          }
          newComponent.setAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LABEL, destinationLabel)
          component = newComponent
        }
      }.execute()
    }
  }

  data class IncludeDestination(val graph: String, val parent: NlComponent) : Destination() {
    override fun addToGraph() {
      val model = parent.model
      object : WriteCommandAction<Unit>(model.project, "Add include", model.file) {
        override fun run(result: Result<Unit>) {
          val tag = parent.tag.createChildTag(SdkConstants.TAG_INCLUDE, null, null, true)
          val newComponent = model.createComponent(tag, parent, null)
          newComponent.setAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_GRAPH,
              "@${ResourceType.NAVIGATION.getName()}/${FileUtil.getNameWithoutExtension(graph)}")
          component = newComponent
        }
      }.execute()
    }

    override val label = graph

    override val thumbnail: Image? by lazy { iconToImage(StudioIcons.NavEditor.ExistingDestinations.NESTED) }

  }
}
/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.google.common.base.Joiner
import com.intellij.openapi.roots.ui.CellAppearanceEx
import com.intellij.ui.HtmlListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES
import com.intellij.ui.treeStructure.SimpleNode

internal class TargetConfigurationNode(
  configuration: Configuration,
  uiSettings: PsUISettings
) : AbstractPsNode(uiSettings), CellAppearanceEx {
  private val types: List<String>

  init {
    myName = configuration.name
    icon = configuration.icon
    types = configuration.types
  }

  override fun getChildren(): Array<SimpleNode> = SimpleNode.NO_CHILDREN

  override fun getText(): String = myName

  override fun customize(renderer: HtmlListCellRenderer<*>) {}

  override fun customize(component: SimpleColoredComponent) {
    component.append(" ")
    val text: String = when {
      types.isEmpty() -> ""
      types.size == 1 -> types[0]
      else -> types.joinToString(", ")
    }
    component.append("($text)", GRAY_ATTRIBUTES)
  }
}

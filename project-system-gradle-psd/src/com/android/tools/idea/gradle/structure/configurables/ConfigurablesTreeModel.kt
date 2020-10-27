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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.structure.model.PsModel
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultTreeModel

/**
 * A tree model that represents a hierarchy of [NamedConfigurable]s.
 */
class ConfigurablesTreeModel(
  val rootNode: MasterDetailsComponent.MyNode
) : DefaultTreeModel(rootNode)

/**
 * Finds a [MasterDetailsComponent.MyNode] for a given [PsModel] in the tree.
 */
fun MasterDetailsComponent.MyNode.findChildFor(model: Any): MasterDetailsComponent.MyNode? =
  children().asSequence().mapNotNull { it as? MasterDetailsComponent.MyNode }.find { it.configurable?.editableObject === model }

inline fun <reified T> MasterDetailsComponent.MyNode.getModel(): T? =
  (userObject as? NamedConfigurable<*>)?.editableObject as? T

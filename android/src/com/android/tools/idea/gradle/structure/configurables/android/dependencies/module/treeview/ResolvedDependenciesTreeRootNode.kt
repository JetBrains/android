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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AndroidArtifactNode
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsVariant

class ResolvedDependenciesTreeRootNode(module: PsAndroidModule, uiSettings: PsUISettings) :
  AbstractPsResettableNode<PsAndroidModule>(module, uiSettings) {

  override fun createChildren(): List<AbstractPsModelNode<*>> {
    val variantsByName = mutableMapOf<String, PsVariant>()
    for (module in models) {
      module.variants.forEach { variant -> variantsByName[variant.name] = variant }
    }
    return createChildren(variantsByName)
  }

  private fun createChildren(variantsByName: Map<String, PsVariant>): List<AndroidArtifactNode> {
    val childrenNodes = mutableListOf<AndroidArtifactNode>()

    val variants = variantsByName
      .entries
      .sortedBy { it.key }
      .map { it.value }
    for (variant in variants) {
      variant.forEachArtifact { artifact ->
        val artifactNode = AndroidArtifactNode(this, artifact)
        childrenNodes.add(artifactNode)
      }
    }

    return childrenNodes
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsVariant
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule

class ResolvedDependenciesTreeRootNode(val module: PsModule, uiSettings: PsUISettings) :
  AbstractPsResettableNode<PsModule>(module, uiSettings) {

  override fun createChildren(): List<AbstractPsModelNode<*>> =
    when (module) {
      is PsAndroidModule -> {
        createChildren(module.resolvedVariants.associateBy { it.name })
      }
      is PsJavaModule -> {
        listOf(AndroidArtifactNode(this, module))
      }
      else -> listOf()
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

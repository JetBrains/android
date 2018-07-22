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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsResolvedDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifactDependencyCollection
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsResolvedLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import com.google.common.collect.Lists
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import com.intellij.ui.treeStructure.SimpleNode

class LibraryDependencyNode : AbstractDependencyNode<PsLibraryAndroidDependency> {
  private val myChildren = Lists.newArrayList<AbstractDependencyNode<*>>()
  private val dependencyComparator: PsDependencyComparator

  constructor(
    parent: AbstractPsNode,
    collection: PsAndroidArtifactDependencyCollection?,
    dependency: PsLibraryAndroidDependency,
    forceGroupId: Boolean
  ) : super(parent, dependency) {
    dependencyComparator = PsDependencyComparator(uiSettings)
    setUp(dependency, collection, forceGroupId)
  }

  constructor(
    parent: AbstractPsNode,
    collection: PsAndroidArtifactDependencyCollection?,
    dependencies: List<PsLibraryAndroidDependency>,
    forceGroupId: Boolean
  ) : super(parent, dependencies) {
    dependencyComparator = PsDependencyComparator(uiSettings)
    setUp(dependencies[0], collection, forceGroupId)
  }

  private fun setUp(dependency: PsLibraryAndroidDependency, collection: PsAndroidArtifactDependencyCollection?, forceGroupId: Boolean) {
    myName = getText(dependency, forceGroupId)
    // TODO(b/74380202): Setup children from Pom dependencies without a PsAndroidDependencyCollection.
    if (collection != null && dependency is PsResolvedLibraryAndroidDependency) {
      val transitiveDependencies = dependency.getTransitiveDependencies()

      myChildren.addAll(
        transitiveDependencies
          .sortedWith(dependencyComparator)
          .map { transitiveLibrary -> LibraryDependencyNode(this, collection, transitiveLibrary, forceGroupId) })
    }
  }

  private fun getText(dependency: PsLibraryAndroidDependency, forceGroupId: Boolean): String {
    val resolvedSpec = dependency.spec
    // TODO(b/74948244): Display POM dependency promotions correctly.
    if (dependency is PsResolvedLibraryAndroidDependency &&
        dependency.hasPromotedVersion() &&
        parent !is LibraryDependencyNode) {
      // Show only "promoted" version for declared nodes.
      // TODO(b/74424544): Find a better representation for multiple versions here.
      val declaredSpecs =
        (dependency as PsResolvedDependency)
          .getParsedModels()
          .filterIsInstance<ArtifactDependencyModel>()
          .joinToString(separator = ",") { it.version().toString() }

      val version = declaredSpecs + "→" + resolvedSpec.version
      return getTextForSpec(resolvedSpec.name, version, resolvedSpec.group,
                            uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID)
    }
    return resolvedSpec.getDisplayText(forceGroupId || uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID, true)
  }

  private fun getTextForSpec(name: String, version: String, group: String?, showGroupId: Boolean): String =
    buildString {
      if (showGroupId && isNotEmpty(group)) {
        append(group)
        append(GRADLE_PATH_SEPARATOR)
      }
      append(name)
      append(GRADLE_PATH_SEPARATOR)
      append(version)
    }

  override fun getChildren(): Array<SimpleNode> = myChildren.toTypedArray()

  override fun matches(model: PsModel): Boolean {
    return when (model) {
      is PsDeclaredLibraryDependency -> {
        // Only top level LibraryDependencyNodes can match declared dependencies.
        val nodeSpec = firstModel.spec  // All the models have the same library key.
        parent !is LibraryDependencyNode && model.spec.toLibraryKey() == nodeSpec.toLibraryKey() && run {
          val parsedModel = model.parsedModel
          models.any { ourModel ->
            parsedModel is ArtifactDependencyModel &&
            getDependencyParsedModels(ourModel).any { resolvedFromParsedDependency ->
              resolvedFromParsedDependency is ArtifactDependencyModel &&
              parsedModel.configurationName() == resolvedFromParsedDependency.configurationName()
            }
          }
        }
      }
      else -> false
    }
  }
}

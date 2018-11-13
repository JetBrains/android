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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsResolvedDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedJarDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedLibraryDependency
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.SimpleNode
import java.io.File


fun <T> createResolvedLibraryDependencyNode(
  parent: AbstractPsNode,
  dependency: T,
  forceGroupId: Boolean
): LibraryDependencyNode
  where T : PsResolvedLibraryDependency,
        T : PsLibraryDependency,
        T : PsResolvedDependency,
        T : PsBaseDependency {

  fun setUpChildren(parent: AbstractPsNode, dependency: T): List<LibraryDependencyNode> =
    dependency
      .getTransitiveDependencies()
      .sortedWith(PsDependencyComparator(parent.uiSettings))
      .map { transitiveLibrary ->
        @Suppress("UNCHECKED_CAST")
        createResolvedLibraryDependencyNode(parent, transitiveLibrary as T, forceGroupId)
      }

  val name = getText(parent, dependency, forceGroupId, parent.uiSettings)
  return ResolvedLibraryDependencyNode(parent, dependency, name).also { it.children = setUpChildren(it, dependency) }
}

fun <T> createLibraryDependencyNode(
  parent: AbstractPsNode,
  dependencies: List<T>,
  forceGroupId: Boolean
): LibraryDependencyNode
  where T : PsLibraryDependency,
        T : PsBaseDependency {

  val name = getText(parent, dependencies[0], forceGroupId, parent.uiSettings)
  return LibraryDependencyNode(parent, dependencies, name)
}

private fun getText(parent: AbstractPsNode, dependency: PsLibraryDependency, forceGroupId: Boolean, uiSettings: PsUISettings): String {
  val resolvedSpec = dependency.spec
  // TODO(b/74948244): Display POM dependency promotions correctly.
  if (dependency is PsResolvedLibraryDependency &&
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

open class LibraryDependencyNode(
  parent: AbstractPsNode,
  dependencies: List<PsLibraryDependency>,
  name: String
) : AbstractDependencyNode<PsLibraryDependency>(parent, dependencies) {
  init {
    myName = name
  }

  internal var children: List<AbstractDependencyNode<*>> = listOf()

  override fun getChildren(): Array<SimpleNode> = children.toTypedArray()

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

class ResolvedLibraryDependencyNode(
  parent: AbstractPsNode,
  val dependency: PsResolvedLibraryDependency,
  name: String
) : LibraryDependencyNode(parent, listOf(dependency), name) {

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val spec = dependency.spec
    presentation.clearText()
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    if (spec.group != null) {
      presentation.addText(" (${spec.group})", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }
}

class JarDependencyNode(
  parent: AbstractPsNode,
  val dependency: List<PsJarDependency>
) : AbstractDependencyNode<PsJarDependency>(parent, dependency) {
  override fun getChildren(): Array<SimpleNode> = arrayOf()

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val file = File(dependency.first().filePath)
    presentation.clearText()
    presentation.addText(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    if (!file.parentFile?.path.isNullOrEmpty()) {
      presentation.addText(" (${file.parentFile?.path})", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }
}
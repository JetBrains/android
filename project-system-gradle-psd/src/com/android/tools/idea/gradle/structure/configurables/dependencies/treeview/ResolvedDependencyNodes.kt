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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.SdkConstants
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedJarDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedModuleDependency
import com.android.tools.idea.gradle.structure.model.targetModuleResolvedDependencies
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.containers.SortedList
import java.io.File

abstract class AbstractResolvedDependencyNode<T : PsBaseDependency> : AbstractPsModelNode<T> {

  val isDeclared: Boolean get() = models.any { it.isDeclared }
  final override val models: List<T>

  protected constructor(parent: AbstractPsNode, dependency: T) : super(parent, parent.uiSettings) {
    models = listOf(dependency)
    updateNameAndIcon()
  }

  protected constructor(parent: AbstractPsNode, dependencies: List<T>) : super(parent, parent.uiSettings) {
    models = dependencies
    updateNameAndIcon()
  }

  companion object {
    fun createResolvedNode(parent: AbstractPsNode, dependency: PsBaseDependency): AbstractResolvedDependencyNode<*>? =
      when (dependency) {
        is PsResolvedLibraryDependency -> ResolvedLibraryDependencyNode(parent, dependency)
        is PsResolvedModuleDependency -> ResolvedModuleDependencyNode(parent, dependency)
        is PsResolvedJarDependency -> ResolvedJarDependencyNode(parent, listOf(dependency))
        else -> null
      }
  }
}

class ResolvedModuleDependencyNode(
  parent: AbstractPsNode,
  dependency: PsResolvedModuleDependency
) : AbstractResolvedDependencyNode<PsModuleDependency>(parent, dependency) {

  private val myChildren: List<AbstractPsModelNode<*>>

  init {
    myName = dependency.toText()
    myChildren = dependency.targetModuleResolvedDependencies?.let { createNodesForResolvedDependencies(this, it) }.orEmpty()
  }

  override fun getChildren(): Array<SimpleNode> = myChildren.toTypedArray()
}

class ResolvedLibraryDependencyNode(
  parent: AbstractPsNode,
  val dependency: PsResolvedLibraryDependency
) : AbstractResolvedDependencyNode<PsLibraryDependency>(parent, listOf(dependency)) {

  private var cachedChildren: Array<SimpleNode>? = null

  init {
    myName = getText(parent, dependency, parent.uiSettings)
  }

  override fun getChildren(): Array<SimpleNode> = cachedChildren ?: createChildren().toTypedArray<SimpleNode>().also { cachedChildren = it }

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val spec = dependency.spec
    presentation.clearText()
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    if (spec.group != null) {
      presentation.addText(" (${spec.group})", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }

  private fun createChildren(): List<AbstractResolvedDependencyNode<*>> = dependency
    .getTransitiveDependencies()
    .sortedWith(PsDependencyComparator(this.uiSettings))
    .map { transitiveLibrary ->
      @Suppress("UNCHECKED_CAST")
      (ResolvedLibraryDependencyNode(this, transitiveLibrary as PsResolvedLibraryDependency))
    }
}

class ResolvedJarDependencyNode(
  parent: AbstractPsNode,
  val dependency: List<PsJarDependency>
) : AbstractResolvedDependencyNode<PsJarDependency>(parent, dependency) {
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

fun createNodesForResolvedDependencies(
  parent: AbstractPsNode,
  collection: PsDependencyCollection<*, *, *, *>
): List<AbstractPsModelNode<*>> {
  val allTransitive = hashSetOf<String>()
  val children = ArrayList<AbstractPsModelNode<*>>()

  val declared = SortedList(
    PsDependencyComparator(parent.uiSettings))
  val mayBeTransitive = ArrayList<PsLibraryDependency>()
  for (dependency in collection.modules) {
    if (dependency.isDeclared) {
      declared.add(dependency)
    }
    addTransitive(dependency, allTransitive)
  }
  for (dependency in collection.libraries) {
    if (dependency.isDeclared) {
      declared.add(dependency)
    }
    else {
      mayBeTransitive.add(dependency)
    }
    addTransitive(dependency, allTransitive)
  }

  // Any other dependencies that are not declared, but somehow were not found as transitive.
  val otherUnrecognised = mayBeTransitive
    .filter { it -> !allTransitive.contains(it.spec.compactNotation()) }
  declared.addAll(otherUnrecognised)

  declared.addAll(collection.jars)

  for (dependency in declared) {
    val child = AbstractResolvedDependencyNode.createResolvedNode(
      parent, dependency)
    if (child != null) {
      children.add(child)
    }
  }

  return children
}

private fun getText(parent: AbstractPsNode, dependency: PsLibraryDependency, uiSettings: PsUISettings): String {
  val resolvedSpec = dependency.spec
  // TODO(b/74948244): Display POM dependency promotions correctly.
  if (dependency is PsResolvedLibraryDependency &&
      dependency.hasPromotedVersion() &&
      parent !is ResolvedLibraryDependencyNode) {
    // Show only "promoted" version for declared nodes.
    // TODO(b/74424544): Find a better representation for multiple versions here.
    val declaredSpecs =
      (dependency as PsResolvedDependency)
        .getParsedModels()
        .filterIsInstance<ArtifactDependencyModel>()
        .joinToString(separator = ",") { it.version().toString() }

    val version = declaredSpecs + "→" + resolvedSpec.version
    return getTextForSpec(resolvedSpec.name, version, resolvedSpec.group, uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID)
  }
  return resolvedSpec.getDisplayText(uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID, true)
}

private fun getTextForSpec(name: String, version: String, group: String?, showGroupId: Boolean): String =
  buildString {
    if (showGroupId && StringUtil.isNotEmpty(group)) {
      append(group)
      append(SdkConstants.GRADLE_PATH_SEPARATOR)
    }
    append(name)
    append(SdkConstants.GRADLE_PATH_SEPARATOR)
    append(version)
  }

private fun addTransitive(dependency: PsLibraryDependency, allTransitive: MutableSet<String>) {
  if (dependency is PsResolvedLibraryDependency) {
    for (transitive in dependency.getTransitiveDependencies()) {
      if (allTransitive.add(transitive.spec.compactNotation())) {
        addTransitive(transitive, allTransitive)
      }
    }
  }
}

private fun addTransitive(dependency: PsModuleDependency, allTransitive: MutableSet<String>) {
  if (dependency is PsResolvedModuleDependency) {
    dependency.targetModuleResolvedDependencies?.libraries?.forEach { transitive ->
      if (allTransitive.add(transitive.spec.compactNotation())) {
        addTransitive(transitive, allTransitive)
      }
    }
    dependency.targetModuleResolvedDependencies?.modules?.forEach { transitive ->
      if (allTransitive.add(transitive.gradlePath)) {
        addTransitive(transitive, allTransitive)
      }
    }
  }
}

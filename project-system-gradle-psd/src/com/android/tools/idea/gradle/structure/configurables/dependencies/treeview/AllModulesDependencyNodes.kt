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

import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.graph.DependencyKey
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import java.io.File

abstract class AbstractDependencyNode<K, T : PsBaseDependency>(parent: AbstractPsNode)
  : AbstractPsResettableNode<K, AbstractDependencyNode<*, *>, T>(parent, parent.uiSettings) {

  val isDeclared: Boolean get() = models.any { it.isDeclared }
  final override var models: List<T> = listOf()

  open fun init(dependencies: List<T>) {
    models = dependencies
    updateNameAndIcon()
  }
}

class LibraryDependencyNode(parent: AbstractPsNode) : AbstractDependencyNode<PsArtifactDependencySpec, PsLibraryDependency>(parent) {
  enum class Mode { SPECIFIC, GROUP, VERSION }

  private var nodeMode: Mode = Mode.SPECIFIC

  override fun getKeys(from: Unit): Set<PsArtifactDependencySpec> =
    when (nodeMode) {
      Mode.SPECIFIC, Mode.VERSION -> setOf()
      Mode.GROUP -> models.groupBy { it.spec }.keys.sortedBy { it.version }.toSet()
    }

  override fun create(key: PsArtifactDependencySpec): AbstractDependencyNode<*, *> = LibraryDependencyNode(this)

  override fun update(key: PsArtifactDependencySpec, node: AbstractDependencyNode<*, *>) {
    val models = this.models.filter { it.spec == key }
    val libraryDependencyNode = node as LibraryDependencyNode
    libraryDependencyNode.init(Mode.VERSION, models)
  }

  override fun buildName(): String {
    val spec = firstModel.spec
    return when (nodeMode) {
      Mode.SPECIFIC -> "${spec.group.orEmpty()}:${spec.name}:${spec.version.orEmpty()}"
      Mode.GROUP -> "${spec.group.orEmpty()}:${spec.name}"
      Mode.VERSION -> "${spec.name}:${spec.version.orEmpty()}"
    }
  }

  fun init(nodeMode: Mode, dependencies: List<PsLibraryDependency>) {
    this.nodeMode = nodeMode
    super.init(dependencies)
  }

  override fun init(dependencies: List<PsLibraryDependency>) {
    this.nodeMode = when {
      dependencies.distinctBy { it.spec }.size == 1 -> Mode.SPECIFIC
      else -> Mode.GROUP
    }
    super.init(dependencies)
  }
}

class JarDependencyNode(parent: AbstractPsNode) : AbstractDependencyNode<DependencyKey, PsJarDependency>(parent) {
  override fun getKeys(from: Unit): Set<DependencyKey> = setOf()
  override fun create(key: DependencyKey): Nothing = throw UnsupportedOperationException()
  override fun update(key: DependencyKey, node: AbstractDependencyNode<*, *>) = Unit

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val file = File(models.first().filePath)
    presentation.clearText()
    presentation.addText(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    if (!file.parentFile?.path.isNullOrEmpty()) {
      presentation.addText(" (${file.parentFile?.path})", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }
}

class ModuleDependencyNode(parent: AbstractPsNode) : AbstractDependencyNode<DependencyKey, PsModuleDependency>(parent) {
  override fun init(dependencies: List<PsModuleDependency>) {
    myName = dependencies.first().toText()
    super.init(dependencies)
  }

  override fun getKeys(from: Unit): Set<DependencyKey> = setOf()
  override fun create(key: DependencyKey): Nothing = throw UnsupportedOperationException()
  override fun update(key: DependencyKey, node: AbstractDependencyNode<*, *>) = Unit
}

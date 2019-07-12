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

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.SimpleNode
import java.io.File


fun <T> createGroupOrLibraryDependencyNode(
  parent: AbstractPsNode,
  libraryKey: PsLibraryKey,
  dependencies: List<T>
): AbstractDependencyNode<PsLibraryDependency>
  where T : PsLibraryDependency,
        T : PsBaseDependency = when {
  dependencies.distinctBy { it.spec }.size == 1 ->
    createLibraryDependencyNode(parent, dependencies, forceGroupId = true)
  else ->
    object : LibraryDependencyNode(parent, dependencies, libraryKey.toString()) {
      override fun createChildren(): List<AbstractDependencyNode<*>> =
        dependencies.groupBy { it.spec }
          .entries
          .sortedBy { it.key.version }
          .map { (_, list) -> createLibraryDependencyNode(this, list, false) }
    }
}

fun <T> createLibraryDependencyNode(
  parent: AbstractPsNode,
  dependencies: List<T>,
  forceGroupId: Boolean
): LibraryDependencyNode
  where T : PsLibraryDependency,
        T : PsBaseDependency {

  val name = getText(parent, dependencies[0], forceGroupId, parent.uiSettings)
  return object : LibraryDependencyNode(parent, dependencies, name) {
    override fun createChildren(): List<AbstractDependencyNode<*>> = listOf()
  }
}

abstract class LibraryDependencyNode(
    parent: AbstractPsNode,
    dependencies: List<PsLibraryDependency>,
    name: String
) : AbstractDependencyNode<PsLibraryDependency>(parent, dependencies) {
  init {
    myName = name
  }

  private var cachedChildren: Array<SimpleNode>? = null

  protected abstract fun createChildren(): List<AbstractDependencyNode<*>>

  override fun getChildren(): Array<SimpleNode> = cachedChildren ?: createChildren().toTypedArray<SimpleNode>().also { cachedChildren = it }
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

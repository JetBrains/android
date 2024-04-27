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
package com.android.tools.idea.gradle.structure.configurables

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer

/**
 * A container of configurables for type [ModelT].
 */
interface ContainerConfigurable<ModelT> : Disposable {
  fun getChildrenModels(): Collection<ModelT>
  fun createChildConfigurable(model: ModelT): NamedConfigurable<out ModelT>
  fun onChange(disposable: Disposable, listener: () -> Unit)
}

/**
 * A [NamedConfigurable] container without an options panel holding configurables for type [ModelT].
 */
abstract class NamedContainerConfigurableBase<ModelT>(
  private val name: String
) : NamedConfigurable<String>(), ContainerConfigurable<ModelT> {
  override fun setDisplayName(name: String?) = Unit
  override fun apply() = Unit
  override fun getBannerSlogan() = name
  override fun createOptionsPanel() = null
  override fun isModified() = false
  override fun getEditableObject() = name
  override fun getDisplayName() = name
}

/**
 * Creates a tree representing the hierarchy of [ContainerConfigurable]s represented by its root [rootConfigurable].
 */
fun createTreeModel(rootConfigurable: NamedConfigurable<*>): ConfigurablesTreeModel =
  ConfigurablesTreeModel(MasterDetailsComponent.MyNode(rootConfigurable, false))
    .also { it.initializeNode(it.rootNode, fromConfigurable = rootConfigurable) }

/**
 * Initializes [node] which children of [fromConfigurable] and subscribes to change notifications from [fromConfigurable].
 */
private fun <T : NamedConfigurable<*>> ConfigurablesTreeModel.initializeNode(
  node: MasterDetailsComponent.MyNode,
  fromConfigurable: T
) {
  (fromConfigurable as? ContainerConfigurable<*>)?.let {
    this.updateChildrenOf(node, fromConfigurable)
    it.onChange(fromConfigurable) { this.updateChildrenOf(node, fromConfigurable = it) }
  }
}

/**
 * Updates [parentNode]'s collection of nodes so that it reflects the children of [fromConfigurable].
 */
private fun <T> ConfigurablesTreeModel.updateChildrenOf(
  parentNode: MasterDetailsComponent.MyNode,
  fromConfigurable: ContainerConfigurable<T>
) {
  val children = fromConfigurable.getChildrenModels().toSet()
  val existing =
    parentNode
      .childNodes
      .map { it.configurable.editableObject to it }
      .toMap()
  existing
    .filterKeys { !children.contains(it) }
    .forEach {
      // Remove any nodes that should no longer be there.
      removeNodeFromParent(it.value)
      (it.value.configurable as? Disposable)?.let { Disposer.dispose(it)}
    }
  children
    .forEachIndexed { index, model ->
      val existingNode = existing[model]
      when {
        existingNode != null ->
          // Move existing nodes to the right positions if required.
          if (getIndexOfChild(parentNode, existingNode) != index) {
            removeNodeFromParent(existingNode)
            insertNodeInto(existingNode, parentNode, index)
          } else {
            this.nodeChanged(existingNode)
          }
        else -> {
          // Create any new nodes and insert them at their positions.
          val configurable = fromConfigurable.createChildConfigurable(model)
          if (configurable is Disposable) {
            Disposer.register(fromConfigurable, configurable)
          }
          insertNodeInto(
            MasterDetailsComponent.MyNode(configurable, false)
              .also { initializeNode(it, fromConfigurable = configurable) },
            parentNode,
            index)
        }
      }
    }
}

private val MasterDetailsComponent.MyNode.childNodes
  get() = children().asSequence().mapNotNull { (it as? MasterDetailsComponent.MyNode) }

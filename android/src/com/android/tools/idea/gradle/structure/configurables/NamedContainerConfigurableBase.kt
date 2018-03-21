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

import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultMutableTreeNode

/**
 * A container of configurables for type [T].
 */
interface ContainerConfigurable<T> {
  fun getChildren(): List<NamedConfigurable<T>>
}

/**
 * A [NamedConfigurable] container without an options panel holding configurables for type [T].
 */
abstract class NamedContainerConfigurableBase<T>(private val name: String) : NamedConfigurable<String>(), ContainerConfigurable<T> {
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
fun createConfigurablesTree(rootConfigurable: NamedConfigurable<*>): DefaultMutableTreeNode {

  fun nodeFromConfigurable(configurable: NamedConfigurable<*>): MasterDetailsComponent.MyNode {
    return MasterDetailsComponent.MyNode(configurable, false).apply {
      val children = (configurable as? ContainerConfigurable<*>)?.getChildren()
      children?.forEach {
        add(nodeFromConfigurable(it))
      }
    }
  }

  return nodeFromConfigurable(rootConfigurable)
}

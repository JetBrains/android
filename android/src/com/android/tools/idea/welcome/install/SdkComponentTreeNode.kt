/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.sdklib.repository.AndroidSdkHandler

/**
 * The [TreeNode] representation of an SDK component that may be installed by the first run wizard.
 */
abstract class SdkComponentTreeNode(val description: String) {
  companion object {
    @JvmStatic
    fun areAllRequiredComponentsAvailable(rootNode: SdkComponentTreeNode): Boolean {
      return !rootNode.allChildren.stream().anyMatch { node: SdkComponentTreeNode? ->
        node is InstallableSdkComponentTreeNode && !node.unavailablePackages.isEmpty()
      }
    }
  }

  abstract val label: String
  abstract val childrenToInstall: Collection<InstallableSdkComponentTreeNode>
  abstract val isChecked: Boolean
  abstract val immediateChildren: Collection<SdkComponentTreeNode>
  val allChildren: Collection<SdkComponentTreeNode>
    get() = listOf(this).plus(immediateChildren.flatMap { it.allChildren })

  abstract val isEnabled: Boolean

  override fun toString(): String = label

  abstract fun updateState(handler: AndroidSdkHandler)

  abstract fun toggle(isSelected: Boolean)
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * A non-leaf tree node. It is not possible to install it.
 */
class SdkComponentCategoryTreeNode(
  override val label: String, description: String, private val components: Collection<SdkComponentTreeNode>
) : SdkComponentTreeNode(description) {
  override val childrenToInstall: Collection<InstallableSdkComponentTreeNode> get() = components.flatMap(SdkComponentTreeNode::childrenToInstall)
  override val isEnabled: Boolean get() = components.any(SdkComponentTreeNode::isEnabled)
  override val isChecked: Boolean get() = components.none (SdkComponentTreeNode::isChecked)
  override val immediateChildren: Collection<SdkComponentTreeNode> get() = components

  override fun updateState(handler: AndroidSdkHandler) = components.forEach { it.updateState(handler) }
  override fun toggle(isSelected: Boolean) = components.forEach { it.toggle(isSelected) }
}

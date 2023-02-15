/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.MutableTreeNode

/**
 * The main category nodes for BTI.
 *
 * Includes: Workers, Alarms, Jobs, Wake Locks.
 */
class BackgroundTaskCategoryNode(val name: String, emptyMessage: String) :
  DefaultMutableTreeNode(name) {
  private val emptyMessageNode = EmptyMessageNode(emptyMessage)

  init {
    super.add(emptyMessageNode)
  }

  override fun remove(childIndex: Int) {
    super.remove(childIndex)
    if (childCount <= 0) {
      super.add(emptyMessageNode)
    }
  }

  override fun remove(aChild: MutableTreeNode?) {
    super.remove(aChild)
    if (childCount <= 0) {
      super.add(emptyMessageNode)
    }
  }

  override fun add(newChild: MutableTreeNode?) {
    super.add(newChild)
    if (isNodeChild(emptyMessageNode)) {
      super.remove(emptyMessageNode)
    }
  }
}

class EmptyMessageNode(val message: String) : DefaultMutableTreeNode(message)

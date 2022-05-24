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
package com.android.tools.idea.common.error

import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference
import javax.swing.tree.TreePath

// For IJ's Async tree purpose, this model needs to implement InvokerSupplier.
class DesignerCommonIssueModel @TestOnly constructor(parent: Disposable, invoker: Invoker?)
  : BaseTreeModel<NodeDescriptor<*>>(), InvokerSupplier {

  constructor(parent: Disposable): this(parent, null)

  private val _invoker = invoker ?: Invoker.forBackgroundThreadWithReadAction(this)
  private val root = AtomicReference<DesignerCommonIssueNode>()

  init {
    Disposer.register(parent, this)
  }

  override fun getInvoker() = _invoker

  override fun getRoot(): DesignerCommonIssueNode? {
    return root.get()
  }

  override fun getChildren(parent: Any): List<NodeDescriptor<*>> {
    assert(invoker.isValidThread) { "unexpected thread" }
    val node = parent as? DesignerCommonIssueNode ?: return emptyList()
    val children = node.getChildren()
    if (children.isEmpty()) {
      return emptyList()
    }
    node.update()
    children.forEach { it.update() } // update presentation of child nodes before processing
    return children.toList()
  }

  fun setRoot(root: DesignerCommonIssueNode?) {
    this.root.set(root)
    structureChanged(null)
  }

  fun structureChanged(path: TreePath?) {
    treeStructureChanged(path, null, null)
  }

  override fun dispose() {
    super.dispose()
    setRoot(null)
  }
}

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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.tree.LeafState
import com.intellij.util.concurrency.Invoker
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignerCommonIssueModelTest {

  @JvmField
  @Rule
  val rule = EdtAndroidProjectRule(AndroidProjectRule.inMemory())

  @Test
  fun test() {
    val invoker = Invoker.forEventDispatchThread(rule.testRootDisposable)
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val root = TestNode()

    runInEdtAndGet {
      invoker.invoke {
        model.root = root
        assertEquals(0, model.getChildCount(model.root))

        val child1 = TestNode(parentDescriptor = root)
        val child2 = TestNode(parentDescriptor = root)
        root.addChild(child1)
        root.addChild(child2)

        model.structureChanged(null)
        assertEquals(2, model.getChildCount(model.root))
      }
    }
  }
}

class TestNode(private val name: String = "", parentDescriptor: NodeDescriptor<DesignerCommonIssueNode>? = null)
  : DesignerCommonIssueNode(null, parentDescriptor) {

  private val children = mutableListOf<DesignerCommonIssueNode>()

  override fun updatePresentation(presentation: PresentationData) = Unit

  override fun getName(): String = this.name

  override fun getChildren(): List<DesignerCommonIssueNode> = children

  override fun getLeafState(): LeafState = LeafState.DEFAULT

  fun addChild(node: DesignerCommonIssueNode) {
    children.add(node)
  }
}

class TestIssueNode(issue: Issue) : IssueNode(null, issue, null)

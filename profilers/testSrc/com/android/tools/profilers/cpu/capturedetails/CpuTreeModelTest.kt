/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.Utils
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test

typealias Assertion<T> = (T) -> Unit

class CpuTreeModelTest {

  @Test
  fun testTreeUpdate() {
    val tree = CpuTreeNodeTest.TopDownTest.createTree()
    val topDown = Aggregate.TopDown.rootAt(tree)
    val range = Range(-Double.MAX_VALUE, Double.MAX_VALUE)
    val model = CpuTreeModel(ClockType.GLOBAL, range, topDown, Utils::runOnUi)

    assertTree(id("A") and total(30.0) and childrenTotal(21.0),
               assertTree(id("B") and total(8.0 + 7.0) and childrenTotal(11.0),
                          assertTree(id("E") and total(2.0 + 3.0) and childrenTotal(0.0)),
                          assertTree(id("G") and total(4.0) and childrenTotal(0.0)),
                          assertTree(id("D") and total(2.0) and childrenTotal(0.0))),
               assertTree(id("C") and total(6.0) and childrenTotal(2.0),
                          assertTree(id("F") and total(2.0) and childrenTotal(0.0))))(model.root)

    range.set(0.0, 10.0)
    assertTree(id("A") and total(10.0) and childrenTotal(8.0),
               assertTree(id("B") and total(8.0) and childrenTotal(4.0),
                          assertTree(id("D") and total(2.0) and childrenTotal(0.0)),
                          assertTree(id("E") and total(2.0) and childrenTotal(0.0))))(model.root)

    range.set(8.0, 25.0)
    assertTree(id("A") and total(17.0) and childrenTotal(10.0),
               assertTree(id("C") and total(6.0) and childrenTotal(2.0),
                          assertTree(id("F") and total(2.0) and childrenTotal(0.0))),
               assertTree(id("B") and total(1.0 + 3.0) and childrenTotal(4.0),
                          assertTree(id("E") and total(1.0 + 3.0) and childrenTotal(0.0))))(model.root)
  }

  @Test
  fun testRootNodeIdValid() {
    var topDown = Aggregate.TopDown.rootAt(CpuTreeNodeTest.TopDownTest.newNode("", 0, 10))
    val range = Range(-Double.MAX_VALUE, Double.MAX_VALUE)
    var model = CpuTreeModel(ClockType.GLOBAL, range, topDown, Utils::runOnUi)
    assertThat(model.isRootNodeIdValid).isFalse()
    topDown = Aggregate.TopDown.rootAt(CpuTreeNodeTest.TopDownTest.newNode("Valid", 0, 10))
    model = CpuTreeModel(ClockType.GLOBAL, range, topDown, Utils::runOnUi)
    assertThat(model.isRootNodeIdValid).isTrue()
  }

  @Test
  fun testAspectFiredAfterTreeModelChange() {
    val tree = CpuTreeNodeTest.TopDownTest.createTree()
    val topDown = Aggregate.TopDown.rootAt(tree)
    val range = Range(-Double.MAX_VALUE, Double.MAX_VALUE)
    val model = CpuTreeModel(ClockType.GLOBAL, range, topDown, Utils::runOnUi)
    val observer = AspectObserver()
    val treeModelChangeCount = intArrayOf(0)
    model.aspect.addDependency(observer).onChange(CpuTreeModel.Aspect.TREE_MODEL) { treeModelChangeCount[0]++ }
    assertThat(treeModelChangeCount[0]).isEqualTo(0)
    range.set(0.0, 10.0)
    assertThat(treeModelChangeCount[0]).isEqualTo(1)
  }

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()

    infix fun<T> Assertion<T>.and(that: Assertion<T>): Assertion<T> = { this(it); that(it) }

    private fun id(id: String): Assertion<CpuTreeNode<*>> = { assertThat(it.base.id).isEqualTo(id) }
    private fun total(t: Double): Assertion<CpuTreeNode<*>> = { assertThat(it.total).isWithin(0.0).of(t) }
    private fun childrenTotal(t: Double): Assertion<CpuTreeNode<*>> = { assertThat(it.childrenTotal).isWithin(0.0).of(t) }

    private fun assertTree(onNode: Assertion<CpuTreeNode<*>>, vararg onChildren: Assertion<CpuTreeNode<*>>): Assertion<CpuTreeNode<*>> = {
      onNode(it)
      assertThat(onChildren.size == it.children.size)
      (onChildren zip it.children).forEach { (assert, tree) -> assert(tree) }
    }
  }
}
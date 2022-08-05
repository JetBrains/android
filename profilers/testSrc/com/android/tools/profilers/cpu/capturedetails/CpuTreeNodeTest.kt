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

import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.capturedetails.CpuTreeModelTest.Companion.and
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
import org.junit.Test

//typealias TreeAssertion =
class CpuTreeNodeTest {
  class TopDownTest {
    @Test
    fun testTreeMerger() {
      assertTree(id("A"),
                 assertTree(id("B"),
                            assertTree(id("D")),
                            assertTree(id("E")),
                            assertTree(id("G"))),
                 assertTree(id("C"), assertTree(id("F"))))(Aggregate.TopDown.rootAt(createTree()))
    }

    @Test
    fun testTreeMergeWithFilter() {
      val root = createTree()

      // set node "A->B" unmatch.
      root.children[2].filterType = CaptureNode.FilterType.UNMATCH
      // set node "A->B->E" and "A->B->G" unmatch.
      root.children[2].children.forEach { it.filterType = CaptureNode.FilterType.UNMATCH }

      assertTree(id("A") and matched,
                 assertTree(id("B") and matched,
                            assertTree(id("D") and matched),
                            assertTree(id("E") and matched)),
                 assertTree(id("C") and matched,
                            assertTree(id("F") and matched)),
                 assertTree(id("B") and unmatched,
                            assertTree(id("E") and unmatched),
                            assertTree(id("G") and unmatched)))(Aggregate.TopDown.rootAt(root))
    }

    @Test
    fun testTreeTime() {
      val root = newNode("A", 0, 10)
      root.addChild(newNode("D", 3, 5))
      root.addChild(newNode("E", 7, 9))
      val topDown = CpuTreeNode.of(Aggregate.TopDown.rootAt(root),
                                   ClockType.GLOBAL,
                                   Range(root.start.toDouble(), root.end.toDouble()),
                                   null)
      assertThat(topDown.total).isEqualTo(10.0)
      assertThat(topDown.self).isEqualTo(6.0)
    }

    @Test
    fun testTreeData() {
      val rootModel = JavaMethodModel("A", "com.package.Class")
      val topDown = Aggregate.TopDown.rootAt(newNode(rootModel, 0, 10, ClockType.GLOBAL))
      val model = topDown.methodModel
      assertEquals(rootModel, topDown.methodModel)
      assertEquals("com.package.Class.A", rootModel.fullName)
      assertThat(model).isInstanceOf(JavaMethodModel::class.java)
      assertEquals("com.package.Class", (model as JavaMethodModel).className)
      assertEquals("A", model.name)
    }

    @Test
    fun testThreadTime() {
      val root = newNode(SingleNameModel("A"), 0, 10, ClockType.THREAD)
      root.addChild(newNode("D", 3, 5))
      root.addChild(newNode("E", 7, 9))
      val topDown = CpuTreeNode.of(Aggregate.TopDown.rootAt(root),
                                   ClockType.THREAD, Range(root.start.toDouble(), root.end.toDouble()),
                                   null)
      assertThat(topDown.total).isEqualTo(9.0)
      assertThat(topDown.self).isEqualTo(7.0)
    }

    companion object {
      /**
       * Creates a test to be used for testing. The shape of the tree is as follows:
       * 0123456789012345678901234567890
       * A          |-----------------------------|
       * +- B        |-------|
       * |  +-D        |-|
       * |  +-E            |-|
       * +- C                    |-----|
       * |  +-F                  |-|
       * +- B                             |------|
       * +-E                           |--|
       * +-G                              |---|
       */
      fun createTree(): CaptureNode {
        val root = newNode("A", 0, 30)
        var node = newNode("B", 1, 9)
        node.addChild(newNode("D", 3, 5))
        node.addChild(newNode("E", 7, 9))
        root.addChild(node)
        node = newNode("C", 13, 19)
        node.addChild(newNode("F", 13, 15))
        root.addChild(node)
        node = newNode("B", 22, 29)
        node.addChild(newNode("E", 22, 25))
        node.addChild(newNode("G", 25, 29))
        root.addChild(node)
        return root
      }

      fun newNode(method: String, start: Long, end: Long) =
        newNode(SingleNameModel(method), start, end, ClockType.GLOBAL)

      fun newNode(method: CaptureNodeModel, start: Long, end: Long, clockType: ClockType) =
        CaptureNode(method, clockType).apply {
          startGlobal = start
          endGlobal = end
          startThread = start
          endThread = end - 1
        }

      private fun id(id: String): Assertion<Aggregate<*>> = { assertThat(it.id).isEqualTo(id) }
      private val unmatched: Assertion<Aggregate<*>> = { assertThat(it.isUnmatched).isTrue() }
      private val matched: Assertion<Aggregate<*>> = { assertThat(it.isUnmatched).isFalse() }
      private fun assertTree(onNode: Assertion<Aggregate<*>>, vararg onChildren: Assertion<Aggregate<*>>): Assertion<Aggregate<*>> =
        { tree ->
          onNode(tree)
          assertThat(tree.children).hasSize(onChildren.size)
          (onChildren zip tree.children).forEach { (assert, child) -> assert(child) }
        }
    }
  }

  class BottomUpNodeTest {
    @Test
    fun testComplexBottomUpNode() {
      val expectedNodes = listOf(
        ExpectedNode.newRoot(40.0, 30.0),
        ExpectedNode("main", 40.0, 30.0),
        ExpectedNode("A", 20.0, 10.0),
        ExpectedNode("main", 15.0, 5.0),
        ExpectedNode("C", 5.0, 5.0),
        ExpectedNode("main", 5.0, 5.0),
        ExpectedNode("B", 15.0, 0.0),
        ExpectedNode("A", 10.0, 0.0),
        ExpectedNode("main", 5.0, 0.0),
        ExpectedNode("C", 5.0, 0.0),
        ExpectedNode("main", 5.0, 0.0),
        ExpectedNode("main", 5.0, 0.0),
        ExpectedNode("C", 10.0, 5.0),
        ExpectedNode("main", 10.0, 5.0)
      )
      traverseAndCheck(createComplexTree(), expectedNodes)
    }

    /**
     * The structure of the tree:
     * main [0..20]
     * -> A [0..10] -> B [2..7] -> C [3..4]
     * -> B [15..20]
     */
    @Test
    fun testNodeHasManyDirectCallers() {
      val expectedNodes = listOf(
        ExpectedNode.newRoot(20.0, 15.0),
        ExpectedNode("main", 20.0, 15.0),  // Subtree of node "A"
        ExpectedNode("A", 10.0, 5.0),
        ExpectedNode("main", 10.0, 5.0),  // Subtree of node "B"
        ExpectedNode("B", 10.0, 1.0),
        ExpectedNode("A", 5.0, 1.0),
        ExpectedNode("main", 5.0, 1.0),
        ExpectedNode("main", 5.0, 0.0),  // Subtree of node "C"
        ExpectedNode("C", 1.0, 0.0),
        ExpectedNode("B", 1.0, 0.0),
        ExpectedNode("A", 1.0, 0.0),
        ExpectedNode("main", 1.0, 0.0)
      )

      // Construct the tree
      val root = newNode("main", 0, 20)
      val childA = newNode("A", 0, 10)
      root.addChild(childA)
      root.addChild(newNode("B", 15, 20))
      childA.addChild(newNode("B", 2, 7))
      childA.children[0].addChild(newNode("C", 3, 4))
      traverseAndCheck(root, expectedNodes)
    }

    @Test
    fun testEmptyNodesDoNotGetAddedAsChildren() {
      val root = newNode("", 0, 20)
      val childA = newNode("A", 0, 10)
      root.addChild(childA)
      root.addChild(newNode("B", 15, 20))
      childA.addChild(newNode("B", 2, 7))
      childA.children[0].addChild(newNode("C", 3, 4))
      val node = Aggregate.BottomUp.rootAt(root)
      assertThat(node.children.none { it.id == root.data.id }).isTrue()
    }

    /**
     * The structure of the tree:
     * main [0..20] -> A [0..15] -> B [3..13] -> B [5..10] -> B [5..7]
     */
    @Test
    fun testDirectRecursion() {
      val expectedNodes = listOf(
        ExpectedNode.newRoot(20.0, 15.0),
        ExpectedNode("main", 20.0, 15.0),  // Subtree of node "A"
        ExpectedNode("A", 15.0, 10.0),
        ExpectedNode("main", 15.0, 10.0),  // Subtree of Node "B"
        ExpectedNode("B", 10.0, 0.0),
        ExpectedNode("A", 10.0, 5.0),
        ExpectedNode("main", 10.0, 5.0),  // "B" -> "B"
        ExpectedNode("B", 5.0, 0.0),  // "A" -> "B" -> "B"
        ExpectedNode("A", 5.0, 2.0),
        ExpectedNode("main", 5.0, 2.0),  // "B" -> "B" -> "B"
        ExpectedNode("B", 2.0, 0.0),
        ExpectedNode("A", 2.0, 0.0),
        ExpectedNode("main", 2.0, 0.0)
      )
      // Construct the tree
      val root = newNode("main", 0, 20)
      addChainSubtree(root, newNode("A", 0, 15), newNode("B", 3, 13),
                      newNode("B", 5, 10), newNode("B", 5, 7))
      traverseAndCheck(root, expectedNodes)
    }

    /**
     * The structure of the tree:
     * main [0..30] -> A [5..25] -> B [5..20] -> A [10..20] -> B [15..16]
     */
    @Test
    fun testIndirectRecursion() {
      val expectedNodes = listOf(
        ExpectedNode.newRoot(30.0, 20.0),
        ExpectedNode("main", 30.0, 20.0),  // Subtree of the node "A"
        // A
        ExpectedNode("A", 20.0, 6.0),  // main -> A
        ExpectedNode("main", 20.0, 15.0),  // B -> A
        ExpectedNode("B", 10.0, 1.0),  // A -> B -> A
        ExpectedNode("A", 10.0, 1.0),  // main -> A -> B -> A
        ExpectedNode("main", 10.0, 1.0),  // Subtree of the node "B"
        // B
        ExpectedNode("B", 15.0, 9.0),  // A -> B
        ExpectedNode("A", 15.0, 9.0),  // main -> A -> B
        ExpectedNode("main", 15.0, 10.0),  // B -> A -> B
        ExpectedNode("B", 1.0, 0.0),  // A -> B -> A -> B
        ExpectedNode("A", 1.0, 0.0),  // main -> A -> B -> A -> B
        ExpectedNode("main", 1.0, 0.0)
      )

      // Construct the tree
      val root = newNode("main", 0, 30)
      addChainSubtree(root, newNode("A", 5, 25), newNode("B", 5, 20),
                      newNode("A", 10, 20), newNode("B", 15, 16))
      traverseAndCheck(root, expectedNodes)
    }

    /**
     * The structure of the tree:
     * main [0..40]
     * -> A [0..25] -> B [0..20] -> C [5..15]
     * -> D [30..40] -> C [30..35] -> B [30..33]
     */
    @Test
    fun testStaticRecursion() {
      val expectedNodes = listOf(
        ExpectedNode.newRoot(40.0, 35.0),
        ExpectedNode("main", 40.0, 35.0),  // Subtree of the node "A"
        // A
        ExpectedNode("A", 25.0, 20.0),  // main -> A
        ExpectedNode("main", 25.0, 20.0),  // Subtree of the node "B"
        // B
        ExpectedNode("B", 23.0, 10.0),  // A -> B
        ExpectedNode("A", 20.0, 10.0),  // main -> A -> B
        ExpectedNode("main", 20.0, 10.0),  // C -> B
        ExpectedNode("C", 3.0, 0.0),  // D -> C -> B
        ExpectedNode("D", 3.0, 0.0),  // main -> D -> C -> B
        ExpectedNode("main", 3.0, 0.0),  // Subtree of the node "C"
        // C
        ExpectedNode("C", 15.0, 3.0),  // B -> C
        ExpectedNode("B", 10.0, 0.0),  // A -> B -> C
        ExpectedNode("A", 10.0, 0.0),  // main -> A -> B -> C
        ExpectedNode("main", 10.0, 0.0),  // D -> C
        ExpectedNode("D", 5.0, 3.0),  // main -> D -> C
        ExpectedNode("main", 5.0, 3.0),  // Subtree of the node "D"
        // D
        ExpectedNode("D", 10.0, 5.0),  // main -> D
        ExpectedNode("main", 10.0, 5.0)
      )
      val root = newNode("main", 0, 40)
      addChainSubtree(root, newNode("A", 0, 25), newNode("B", 0, 20),
                      newNode("C", 5, 15))
      addChainSubtree(root, newNode("D", 30, 40), newNode("C", 30, 35),
                      newNode("B", 30, 33))
      traverseAndCheck(root, expectedNodes)
    }

    /**
     * The structure of the tree:
     * main
     * -> A [0..100]  -> A [0..40]  -> A [0..20]
     * -> B [21..40]  -> A [25..28]
     * -> B [45..100] -> A [50..70] -> B [55..65]
     *
     * From the structure of call tree above, we can infer that the top of the call stack looks like
     * (including the timestamps and method name):
     * 0 - A - 20 - A - 21 - B - 25 - A - 28 - B - 40 - A - 45 - B - 50 - A - 55 - B - 65 - A - 70 - B - 100
     */
    @Test
    fun testPartialRangeWithMixedTwoMethods() {
      val root = newNode("main", 0, 100)
      addChainSubtree(root, newNode("A", 0, 100), newNode("A", 0, 40),
                      newNode("A", 0, 20))
      addChainSubtree(root.children[0], newNode("B", 45, 100), newNode("A", 50, 70),
                      newNode("B", 55, 65))
      addChainSubtree(root.children[0].children[0], newNode("B", 21, 40),
                      newNode("A", 25, 28))
      val node = Aggregate.BottomUp.rootAt(root)

      CpuTreeNode.of(node.children.first { it.id == "A" },
                     ClockType.GLOBAL, Range(0.0, 100.0), null).let { nodeA ->
        assertThat(nodeA.total).isWithin(EPS).of(100.0)
        assertThat(nodeA.childrenTotal).isWithin(EPS).of(61.0)
      }

      CpuTreeNode.of(node.children.first { it.id == "A" },
                     ClockType.GLOBAL, Range(21.0, 40.0), null).let { nodeA ->
        assertThat(nodeA.total).isWithin(EPS).of(19.0)
        assertThat(nodeA.childrenTotal).isWithin(EPS).of(16.0)
      }

      CpuTreeNode.of(node.children.first { it.id == "A" },
                     ClockType.GLOBAL, Range(66.0, 71.0), null).let { nodeA ->
        assertThat(nodeA.total).isWithin(EPS).of(5.0)
        assertThat(nodeA.childrenTotal).isWithin(EPS).of(1.0)
      }
    }

    /**
     * The structure of the tree:
     * main [0..100]
     * -> A* [0..50]
     * -> B* [0..50]
     * -> A  [51..100]
     * -> B  [51..75]
     * -> B* [76..99]
     *
     * where A* and B* unmatched nodes.
     */
    @Test
    fun testWithUnmatchedNodes() {
      val root = newNode("main", 0, 100)
      addChildren(root,
                  newNode("A", 0, 50, true),
                  newNode("A", 51, 100, false))
      addChildren(root.getChildAt(0),
                  newNode("B", 0, 50, true))
      addChildren(root.getChildAt(1),
                  newNode("B", 51, 75),
                  newNode("B", 76, 99, true))
      val expectedNodes = listOf(
        ExpectedNode("Root", 100.0, 99.0, false),  // main
        ExpectedNode("main", 100.0, 99.0, false),  // A*
        ExpectedNode("A", 50.0, 50.0, true),  // A* -> main
        ExpectedNode("main", 50.0, 50.0, false),  // B*
        ExpectedNode("B", 73.0, 0.0, true),  // B* -> A*
        ExpectedNode("A", 50.0, 0.0, true),  // B* -> A* -> main
        ExpectedNode("main", 50.0, 0.0, false),  // B* -> A
        ExpectedNode("A", 23.0, 0.0, false),  // B* -> A -> main
        ExpectedNode("main", 23.0, 0.0, false),  // A
        ExpectedNode("A", 49.0, 47.0, false),  // A -> main
        ExpectedNode("main", 49.0, 47.0, false),  // B
        ExpectedNode("B", 24.0, 0.0, false),  // B -> A
        ExpectedNode("A", 24.0, 0.0, false),  // B -> A -> main
        ExpectedNode("main", 24.0, 0.0, false)
      )
      traverseAndCheck(root, expectedNodes)
    }

    private class ExpectedNode(method: String, val total: Double, val childrenTotal: Double, val isUnmatch: Boolean = false) {
      val id = SingleNameModel(method).id

      private constructor(total: Double, childrenTotal: Double): this("Root", total, childrenTotal)

      companion object {
        fun newRoot(total: Double, childrenTotal: Double) = ExpectedNode(total, childrenTotal)
      }
    }

    companion object {
      private const val EPS = 1e-5
      private fun traverseAndCheck(root: CaptureNode, expectedNodes: List<ExpectedNode>) {
        val traverseOrder = ArrayList<CpuTreeNode<Aggregate.BottomUp>>()
        traverse(CpuTreeNode.of(Aggregate.BottomUp.rootAt(root),
                                ClockType.GLOBAL,
                                Range(root.start.toDouble(), root.end.toDouble()),
                                null),
                 traverseOrder)
        checkTraverseOrder(expectedNodes, traverseOrder)
      }

      private fun addChildren(node: CaptureNode, vararg children: CaptureNode) {
        for (child in children) {
          node.addChild(child)
        }
      }

      private fun addChainSubtree(root: CaptureNode, vararg chainNodes: CaptureNode) {
        var last = root
        for (node in chainNodes) {
          last.addChild(node)
          last = node
        }
      }

      private fun traverse(node: CpuTreeNode<Aggregate.BottomUp>, traverseOrder: MutableList<CpuTreeNode<Aggregate.BottomUp>>) {
        traverseOrder.add(node)
        for (child in node.children) {
          traverse(child, traverseOrder)
        }
      }

      private fun checkTraverseOrder(expected: List<ExpectedNode>, actual: List<CpuTreeNode<Aggregate.BottomUp>>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
          val node = actual[i]
          assertEquals(expected[i].id, node.base.id)
          assertEquals(expected[i].total, node.total, EPS)
          assertEquals(expected[i].childrenTotal, node.childrenTotal, EPS)
          assertEquals(expected[i].isUnmatch, node.base.isUnmatched)
        }
      }

      fun newNode(method: String, start: Long, end: Long, unmatched: Boolean) =
        CaptureNode(SingleNameModel(method)).apply {
          startGlobal = start
          endGlobal = end
          startThread = start
          endThread = end
          filterType = if (unmatched) CaptureNode.FilterType.UNMATCH else CaptureNode.FilterType.MATCH
        }

      fun newNode(method: String, start: Long, end: Long) =
        newNode(method, start, end, false)

      private fun createComplexTree(): CaptureNode {
        val root = newNode("main", 0, 40)
        val childA = newNode("A", 0, 15)
        val childC = newNode("C", 20, 30)
        val childB = newNode("B", 35, 40)

        root.addChild(childA)
        root.addChild(childC)
        root.addChild(childB)
        childA.addChild(newNode("B", 5, 10))
        childC.addChild(newNode("A", 20, 25))
        childC.children[0].addChild(newNode("B", 20, 25))
        return root
      }
    }
  }
}
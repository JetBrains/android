/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

class CaptureNodeTest {

  @Test
  fun captureNodeSpecificMethods() {
    val node = CaptureNode(StubCaptureNodeModel())
    assertThat(node.clockType).isEqualTo(ClockType.GLOBAL)

    node.startThread = 3
    node.endThread = 5
    node.startGlobal = 3
    node.endGlobal = 13

    assertThat(node.startThread).isEqualTo(3)
    assertThat(node.endThread).isEqualTo(5)
    assertThat(node.startGlobal).isEqualTo(3)
    assertThat(node.endGlobal).isEqualTo(13)

    assertThat(node.threadGlobalRatio()).isWithin(0.0001).of(0.2)
  }

  @Test
  fun hNodeApiMethods() {
    val node = CaptureNode(StubCaptureNodeModel())

    node.startThread = 0
    node.endThread = 10
    node.startGlobal = 20
    node.endGlobal = 50

    assertThat(node.clockType).isEqualTo(ClockType.GLOBAL)
    assertThat(node.start).isEqualTo(20)
    assertThat(node.end).isEqualTo(50)
    assertThat(node.duration).isEqualTo(30)

    node.clockType = ClockType.THREAD
    assertThat(node.clockType).isEqualTo(ClockType.THREAD)
    assertThat(node.start).isEqualTo(0)
    assertThat(node.end).isEqualTo(10)
    assertThat(node.duration).isEqualTo(10)
  }

  @Test
  fun addChild() {
    val realParent = CaptureNode(StubCaptureNodeModel())
    val childA = CaptureNode(StubCaptureNodeModel())
    val visualParent = VisualNodeCaptureNode(StubCaptureNodeModel(), ClockType.GLOBAL)

    realParent.addChild(childA)

    assertThat(childA.parent).isEqualTo(realParent)
    assertThat(realParent.getChildAt(0)).isEqualTo(childA)

    visualParent.addChild(childA)
    assertThat(childA.parent).isEqualTo(realParent)
    assertThat(realParent.getChildAt(0)).isEqualTo(childA)
    assertThat(visualParent.getChildAt(0)).isEqualTo(childA)
  }

  @Test
  fun testFilter() {
    val node = createFilterTestTree()
    val filterResult = node.applyFilter(Filter("myPackage"))

    // Verify filter result
    assertThat(filterResult.matchCount).isEqualTo(3)
    assertThat(filterResult.totalCount).isEqualTo(14)
    assertThat(filterResult.isFilterEnabled).isTrue()

    // mainPackage.main
    assertThat(node.filterType).isEqualTo(CaptureNode.FilterType.MATCH)

    // mainPackage.main
    checkChildrenFilterType(node, CaptureNode.FilterType.MATCH, CaptureNode.FilterType.EXACT_MATCH, CaptureNode.FilterType.UNMATCH)

    // mainPackage.main -> otherPackage.method1
    checkChildrenFilterType(node.firstChild!!, CaptureNode.FilterType.EXACT_MATCH, CaptureNode.FilterType.UNMATCH)
    // mainPackage.main -> otherPackage.method1 -> myPackage.method2
    checkChildrenFilterType(node.firstChild!!.firstChild!!, CaptureNode.FilterType.MATCH, CaptureNode.FilterType.EXACT_MATCH)
    // mainPackage.main -> otherPackage.method1 -> otherPackage.method3
    checkChildrenFilterType(node.firstChild!!.getChildAt(1)!!, CaptureNode.FilterType.UNMATCH, CaptureNode.FilterType.UNMATCH)

    // mainPackage.main -> myPackage.method1
    checkChildrenFilterType(node.getChildAt(1)!!, CaptureNode.FilterType.MATCH, CaptureNode.FilterType.MATCH)
    // mainPackage.main -> otherPackage.method2
    checkChildrenFilterType(node.getChildAt(2)!!, CaptureNode.FilterType.UNMATCH, CaptureNode.FilterType.UNMATCH)
  }

  @Test
  fun testEmptyFilter() {
    val root = createFilterTestTree()
    val filterResult = root.applyFilter(Filter.EMPTY_FILTER)
    assertThat(filterResult.matchCount).isEqualTo(14)
    assertThat(filterResult.totalCount).isEqualTo(14)
    assertThat(filterResult.isFilterEnabled).isFalse()
    root.descendantsStream.forEach { n -> assertThat(n.filterType).isEqualTo(CaptureNode.FilterType.MATCH) }
  }

  @Test
  fun testGetRootNode() {
    val root = createFilterTestTree()
    assertThat(root.findRootNode()).isSameAs(root)
    assertThat(root.getChildAt(0)!!.findRootNode()).isSameAs(root)
    assertThat(root.getChildAt(1)!!.getChildAt(0)!!.findRootNode()).isSameAs(root)
    assertThat(root.getChildAt(0)!!.getChildAt(1)!!.getChildAt(0)!!.findRootNode()).isSameAs(root)
  }

  @Test
  fun testGetTopKNodesWithoutNameMapping() {
    val root = createFilterTestTree()
    val longestNodes =
      root.getTopKNodes(
        4,
        "otherPackage.method4",
        compareBy { it.duration },
        emptyMap(),
      )
    assertThat(longestNodes.map { it.duration }).containsExactly(100L, 100L, 99L, 40L)
  }

  @Test
  fun testGetTopKNodesWithNameMapping() {
    val root = createFilterTestTree()
    val nameToNodes = CpuThreadTrackModel.getNameToNodesMapping(root)
    val longestNodes =
      root.getTopKNodes(
        4,
        "otherPackage.method4",
        compareBy { it.duration },
        nameToNodes,
      )
    assertThat(longestNodes.map { it.duration }).containsExactly(100L, 100L, 99L, 40L)
  }

  @Test
  fun testFilterAspect() {
    val node = CaptureNode(SingleNameModel("Foo"))
    val latch = CountDownLatch(1)
    node.aspectModel.addDependency(AspectObserver()).onChange(CaptureNode.Aspect.FILTER_APPLIED) { latch.countDown() }
    node.applyFilter(Filter())
    assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isTrue()
  }

  @Test
  fun abbreviationCollapseAdjacentUninterestingNodes() {
    fun node(data: CaptureNodeModel, vararg children: CaptureNode): CaptureNode = CaptureNode(data).apply { addChildren(children.toList()) }

    val interestingData: CaptureNodeModel = SingleNameModel("interesting")
    val uninterestingData: CaptureNodeModel = SingleNameModel("uninteresting")

    val tree =
      node(
        interestingData,
        node(
          interestingData,
          node(interestingData),
          node(interestingData),
        ),
        node(
          uninterestingData,
          node(interestingData),
          node(uninterestingData),
        ),
        node(
          uninterestingData,
          node(interestingData),
          node(
            uninterestingData,
            node(interestingData),
            node(interestingData),
          ),
        ),
      )

    val abbreviatedTree = tree.abbreviatedBy({ it.data == uninterestingData }, uninterestingData)

    // All opaque nodes above are adjacent, so abbreviated tree should only have 1 opaque node
    assertThat(abbreviatedTree.fold({ if (it.data == uninterestingData) 1 else 0 }, Int::plus)).isEqualTo(1)
    // The only opaque node should have a total of 4 transparent children from collapsing
    assertThat(abbreviatedTree.fold({ if (it.data == uninterestingData) it.childCount else 0 }, Int::plus)).isEqualTo(4)
  }

  companion object {
    /** Creates a tree that is used in [testFilter] */
    private fun createFilterTestTree(): CaptureNode {
      val root = createNode("mainPackage.main", 0, 1000)
      root.addChild(createNode("otherPackage.method1", 0, 500))
      root.addChild(createNode("myPackage.method1", 600, 700))
      root.addChild(createNode("otherPackage.method2", 800, 1000))

      root.getChildAt(1)!!.addChild(createNode("otherPackage.method3", 600, 650))
      root.getChildAt(1)!!.addChild(createNode("otherPackage.method4", 660, 700))

      root.getChildAt(2)!!.addChild(createNode("otherPackage.method3", 800, 850))
      root.getChildAt(2)!!.addChild(createNode("otherPackage.method4", 860, 900))

      val first = root.firstChild!!
      first.addChild(createNode("myPackage.method2", 0, 200))
      first.addChild(createNode("otherPackage.method3", 300, 500))

      first.getChildAt(0)!!.addChild(createNode("otherPackage.method4", 0, 100))
      first.getChildAt(0)!!.addChild(createNode("myPackage.method3", 101, 200))

      first.getChildAt(1)!!.addChild(createNode("otherPackage.method4", 300, 400))
      first.getChildAt(1)!!.addChild(createNode("otherPackage.method4", 401, 500))

      return root
    }

    private fun checkChildrenFilterType(node: CaptureNode, vararg filterTypes: CaptureNode.FilterType) {
      assertThat(node.children).hasSize(filterTypes.size)
      for (i in filterTypes.indices) {
        assertThat(node.children[i].filterType).isEqualTo(filterTypes[i])
      }
    }

    private fun createNode(fullMethodName: String, start: Long, end: Long): CaptureNode {
      val className = fullMethodName.substringBeforeLast('.')
      val methodName = fullMethodName.substringAfterLast('.')

      return CaptureNode(JavaMethodModel(methodName, className)).apply {
        clockType = ClockType.GLOBAL
        startGlobal = start
        endGlobal = end
        startThread = start
        endThread = end
      }
    }
  }
}

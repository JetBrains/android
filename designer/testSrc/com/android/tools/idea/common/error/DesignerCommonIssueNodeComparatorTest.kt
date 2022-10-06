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
package com.android.tools.idea.common.error

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.uibuilder.surface.NlAtfIssue
import com.android.tools.idea.validator.ValidatorData
import com.intellij.lang.annotation.HighlightSeverity
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test

class DesignerCommonIssueNodeComparatorTest {

  @Test
  fun testSortedBySeverityOnly() {
    val fileName = TestNode()

    val node1 = IssueNode(null, TestIssue(summary = "aaa", severity = HighlightSeverity.ERROR), fileName)
    val node2 = IssueNode(null, TestIssue(summary = "AAA", severity = HighlightSeverity.ERROR), fileName)
    val node3 = IssueNode(null, TestIssue(summary = "bbb", severity = HighlightSeverity.WARNING), fileName)
    val node4 = IssueNode(null, TestIssue(summary = "BBB", severity = HighlightSeverity.WARNING), fileName)
    val node5 = IssueNode(null, TestIssue(summary = "ccc", severity = HighlightSeverity.INFORMATION), fileName)
    val node6 = IssueNode(null, TestIssue(summary = "CCC", severity = HighlightSeverity.INFORMATION), fileName)

    val comparator = DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = false)

    val sorted = listOf(node5, node3, node4, node6, node2, node1).sortedWith(comparator)
    assertEquals(listOf(node2, node1, node3, node4, node5, node6), sorted)
  }

  @Test
  fun testSortedByNameOnly() {
    val fileName = TestNode()
    val node1 = IssueNode(null, TestIssue(summary = "xxx", severity = HighlightSeverity.ERROR), fileName)
    val node2 = IssueNode(null, TestIssue(summary = "zzz", severity = HighlightSeverity.ERROR), fileName)
    val node3 = IssueNode(null, TestIssue(summary = "XXX", severity = HighlightSeverity.WARNING), fileName)
    val node4 = IssueNode(null, TestIssue(summary = "ZZZ", severity = HighlightSeverity.WARNING), fileName)
    val node5 = IssueNode(null, TestIssue(summary = "xxX", severity = HighlightSeverity.INFORMATION), fileName)
    val node6 = IssueNode(null, TestIssue(summary = "zzZ", severity = HighlightSeverity.INFORMATION), fileName)

    val comparator = DesignerCommonIssueNodeComparator(sortedBySeverity = false, sortedByName = true)

    val sorted = listOf(node5, node3, node4, node6, node2, node1).sortedWith(comparator)
    assertEquals(listOf(node1, node5, node3, node2, node6, node4), sorted)
  }

  @Test
  fun testSortedBySeverityAndName() {
    val fileName = TestNode()
    val node1 = IssueNode(null, TestIssue(summary = "aaa", severity = HighlightSeverity.ERROR), fileName)
    val node2 = IssueNode(null, TestIssue(summary = "AAA", severity = HighlightSeverity.ERROR), fileName)
    val node3 = IssueNode(null, TestIssue(summary = "bbb", severity = HighlightSeverity.WARNING), fileName)
    val node4 = IssueNode(null, TestIssue(summary = "BBB", severity = HighlightSeverity.WARNING), fileName)
    val node5 = IssueNode(null, TestIssue(summary = "ccc", severity = HighlightSeverity.INFORMATION), fileName)
    val node6 = IssueNode(null, TestIssue(summary = "CCC", severity = HighlightSeverity.INFORMATION), fileName)

    val comparator = DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true)

    val sorted = listOf(node5, node3, node4, node6, node2, node1).sortedWith(comparator)
    assertEquals(listOf(node1, node2, node3, node4, node5, node6), sorted)
  }

  @Test
  fun testNotSorted() {
    val fileName = TestNode()
    val node1 = IssueNode(null, TestIssue(summary = "aaa", severity = HighlightSeverity.ERROR), fileName)
    val node2 = IssueNode(null, TestIssue(summary = "AAA", severity = HighlightSeverity.ERROR), fileName)
    val node3 = IssueNode(null, TestIssue(summary = "bbb", severity = HighlightSeverity.WARNING), fileName)
    val node4 = IssueNode(null, TestIssue(summary = "BBB", severity = HighlightSeverity.WARNING), fileName)
    val node5 = IssueNode(null, TestIssue(summary = "ccc", severity = HighlightSeverity.INFORMATION), fileName)
    val node6 = IssueNode(null, TestIssue(summary = "CCC", severity = HighlightSeverity.INFORMATION), fileName)

    val comparator = DesignerCommonIssueNodeComparator(sortedBySeverity = false, sortedByName = false)

    val original = listOf(node5, node3, node4, node6, node2, node1)
    val sorted = original.sortedWith(comparator)
    assertEquals(original, sorted)
  }
}

class IssueNodeSeverityComparatorTest {

  @Test
  fun testSortedBySeverity() {
    // If they are not IssueNode, we don't care about their order.
    val errorNode = IssueNode(null, TestIssue(severity = HighlightSeverity.ERROR), null)
    val warningNode = IssueNode(null, TestIssue(severity = HighlightSeverity.WARNING), null)
    val informationNode = IssueNode(null, TestIssue(severity = HighlightSeverity.INFORMATION), null)

    // Basic cases
    assertTrue(IssueNodeSeverityComparator.compare(null, null) == 0)
    assertTrue(IssueNodeSeverityComparator.compare(null, errorNode) < 0)
    assertTrue(IssueNodeSeverityComparator.compare(errorNode, null) > 0)

    // The more important issues show first, thus: error < warning < information.
    assertTrue(IssueNodeSeverityComparator.compare(errorNode, warningNode) < 0)
    assertTrue(IssueNodeSeverityComparator.compare(warningNode, errorNode) > 0)

    assertTrue(IssueNodeSeverityComparator.compare(errorNode, informationNode) < 0)
    assertTrue(IssueNodeSeverityComparator.compare(informationNode, errorNode) > 0)

    assertTrue(IssueNodeSeverityComparator.compare(warningNode, informationNode) < 0)
    assertTrue(IssueNodeSeverityComparator.compare(informationNode, warningNode) > 0)

    val errorNode2 = IssueNode(null, TestIssue(severity = HighlightSeverity.ERROR), null)
    val warningNode2 = IssueNode(null, TestIssue(severity = HighlightSeverity.WARNING), null)
    val informationNode2 = IssueNode(null, TestIssue(severity = HighlightSeverity.INFORMATION), null)

    assertEquals(listOf(errorNode, errorNode2, warningNode, warningNode2, informationNode, informationNode2),
                 listOf(warningNode, informationNode, informationNode2, errorNode, warningNode2, errorNode2)
                   .sortedWith(IssueNodeSeverityComparator))
  }
}

class IssueNodeNameComparatorTest {

  @Test
  fun testSortedByName() {
    val nodeA = TestNode("nodeA")
    val nodeB = TestNode("nodeB")
    // Note: We ignore the case when comparing
    val nodeAUpperCase = TestNode("NodeA")

    // Basic cases
    assertTrue(IssueNodeNameComparator.compare(null, null) == 0)
    assertTrue(IssueNodeNameComparator.compare(null, nodeA) < 0)
    assertTrue(IssueNodeNameComparator.compare(nodeA, null) > 0)
    assertTrue(IssueNodeNameComparator.compare(nodeA, nodeA) == 0)

    assertTrue(IssueNodeNameComparator.compare(nodeA, nodeB) < 0)
    assertTrue(IssueNodeNameComparator.compare(nodeB, nodeA) > 0)

    assertTrue(IssueNodeNameComparator.compare(nodeB, nodeAUpperCase) > 0)
    assertTrue(IssueNodeNameComparator.compare(nodeAUpperCase, nodeB) < 0)

    assertTrue(IssueNodeNameComparator.compare(nodeA, nodeAUpperCase) < 0)
    assertTrue(IssueNodeNameComparator.compare(nodeAUpperCase, nodeA) > 0)

    assertEquals(listOf(nodeA, nodeAUpperCase, nodeB), listOf(nodeAUpperCase, nodeA, nodeB).sortedWith(IssueNodeNameComparator))
  }
}

class PreprocessNodeComparatorTest {
  @Test
  fun testSortingNonATFIssues() {
    val nodeA = TestNode("nodeA")
    val nodeB = TestNode("nodeB")
    // Note: We ignore the case when comparing
    val nodeAUpperCase = TestNode("NodeA")

    // Basic cases
    assertEquals(0, PreprocessNodeComparator.compare(null, null))
    assertEquals(0, PreprocessNodeComparator.compare(null, nodeA))
    assertEquals(0, PreprocessNodeComparator.compare(nodeA, null))
    assertEquals(0, PreprocessNodeComparator.compare(nodeA, nodeA))

    assertEquals(0, PreprocessNodeComparator.compare(nodeA, nodeB))
    assertEquals(0, PreprocessNodeComparator.compare(nodeB, nodeA))

    assertEquals(0, PreprocessNodeComparator.compare(nodeB, nodeAUpperCase))
    assertEquals(0, PreprocessNodeComparator.compare(nodeAUpperCase, nodeB))

    assertEquals(0, PreprocessNodeComparator.compare(nodeA, nodeAUpperCase))
    assertEquals(0, PreprocessNodeComparator.compare(nodeAUpperCase, nodeA))

    assertEquals(listOf(nodeA, nodeAUpperCase, nodeB), listOf(nodeA, nodeAUpperCase, nodeB).sortedWith(PreprocessNodeComparator))
  }

  @Test
  fun testSortingATFIssues() {

    val atfNodeA = TestIssueNode(TestAtfIssue("AAA"))
    val atfNodeB = TestIssueNode(TestAtfIssue("BBB"))
    val otherNode = TestNode("other")

    // Basic cases
    assertEquals(0, PreprocessNodeComparator.compare(null, atfNodeA))
    assertEquals(0, PreprocessNodeComparator.compare(atfNodeA, null))
    assertEquals(0, PreprocessNodeComparator.compare(atfNodeA, atfNodeA))

    assertEquals(0, PreprocessNodeComparator.compare(atfNodeA, otherNode))
    assertEquals(0, PreprocessNodeComparator.compare(otherNode, atfNodeA))

    assertTrue(PreprocessNodeComparator.compare(atfNodeA, atfNodeB) < 0)
    assertTrue(PreprocessNodeComparator.compare(atfNodeB, atfNodeA) > 0)

    assertEquals(listOf(atfNodeA, atfNodeB), listOf(atfNodeB, atfNodeA).sortedWith(PreprocessNodeComparator))
  }
}

private class TestAtfIssue(override val summary: String):
  NlAtfIssue(createIssueValidatorData(), IssueSource.NONE, mock(), null)

fun createIssueValidatorData(): ValidatorData.Issue {
  return ValidatorData.Issue.IssueBuilder()
    .setCategory("")
    .setType(ValidatorData.Type.ACCESSIBILITY)
    .setMsg("")
    .setLevel(ValidatorData.Level.ERROR)
    .setSourceClass("")
    .build()
}

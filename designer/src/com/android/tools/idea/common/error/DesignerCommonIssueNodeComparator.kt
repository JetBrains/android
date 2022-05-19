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

class DesignerCommonIssueNodeComparator(sortedBySeverity: Boolean, sortedByName: Boolean) : Comparator<DesignerCommonIssueNode> {
  private val comparator: Comparator<DesignerCommonIssueNode>

  init {
    var comparator = compareBy<DesignerCommonIssueNode> { 0 }
    if (sortedBySeverity) {
      comparator = comparator.thenComparing(IssueNodeSeverityComparator)
    }
    if (sortedByName) {
      comparator = comparator.thenComparing(IssueNodeNameComparator)
    }
    this.comparator = comparator
  }

  override fun compare(o1: DesignerCommonIssueNode?, o2: DesignerCommonIssueNode?): Int = comparator.compare(o1, o2)
}

object IssueNodeSeverityComparator : Comparator<DesignerCommonIssueNode> {
  override fun compare(o1: DesignerCommonIssueNode?, o2: DesignerCommonIssueNode?): Int {
    if (o1 == null) {
      return if (o2 == null) 0 else -1
    }
    if (o2 == null) {
      return 1
    }
    if (o1 !is IssueNode || o2 !is IssueNode) {
      // We don't care about the order if they were not IssueNode
      return 0
    }
    // Use minus operator to show the more important issue first.
    return - o1.issue.severity.compareTo(o2.issue.severity)
  }
}

object IssueNodeNameComparator : Comparator<DesignerCommonIssueNode> {
  override fun compare(o1: DesignerCommonIssueNode?, o2: DesignerCommonIssueNode?): Int {
    if (o1 == null) {
      return if (o2 == null) 0 else -1
    }
    if (o2 == null) {
      return 1
    }
    val isEqualsIgnoredCase = o1.name.compareTo(o2.name, true)

    if (isEqualsIgnoredCase != 0) {
      return isEqualsIgnoredCase
    }
    // If they are same regardless the case, the lower case should be first.
    // Node: 'a' compare to 'A' is positive, but we want the lower case be first. Reverse the result by using minus operator.
    return - o1.name.compareTo(o2.name)
  }
}

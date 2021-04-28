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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.google.common.collect.ImmutableList

/**
 * Returns all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult].
 */
fun analyze(result: RenderResult): ImmutableList<RenderErrorModel.Issue> {
  return ImmutableList.copyOf(analyzeBounds(result))
}

/**
 * Analyze the given [RenderResult] for issues where a child view is not fully contained within
 * the bounds of its parent, and return all such issues.
 */
private fun analyzeBounds(renderResult: RenderResult): List<RenderErrorModel.Issue> {
  val issues = mutableListOf<RenderErrorModel.Issue>()
  for (root in renderResult.rootViews) {
    findBoundIssues(root, issues)
  }
  return issues
}

private fun findBoundIssues(root: ViewInfo, issues: MutableList<RenderErrorModel.Issue>) {
  val rootWidth = root.right - root.left
  val rootHeight = root.bottom - root.top
  for (child in root.children) {
    // Bounds of children are defined relative to their parent
    if (child.top < 0 || child.bottom > rootHeight || child.left < 0 || child.right > rootWidth) {
      issues.add(RenderErrorModel.Issue.builder().setSummary("$child is not fully visible in layout").build())
    }
    findBoundIssues(child, issues)
  }
}
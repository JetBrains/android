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
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.collect.ImmutableList

private const val BOTTOM_NAVIGATION_CLASS_NAME = "com.google.android.material.bottomnavigation.BottomNavigationView"
private const val BOTTOM_NAVIGATION_ISSUE_MESSAGE = "BottomNavigationView should not be used in layouts larger than 600dp"

/**
 * Returns all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
 */
fun analyzeAfterModelUpdate(result: RenderResult, sceneManager: LayoutlibSceneManager): ImmutableList<RenderErrorModel.Issue> {
  val issues = mutableListOf<RenderErrorModel.Issue>()
  issues.addAll(analyzeBounds(result))
  issues.addAll(analyzeBottomNavigation(result, sceneManager))
  return ImmutableList.copyOf(issues)
}

/**
 * Returns all the [VisualLintAtfIssue]  found when analyzing the given [RenderResult] after render is complete.
 */
fun analyzeAfterRenderComplete(renderResult: RenderResult, model: NlModel): ImmutableList<VisualLintAtfIssue> {
  // TODO: This might change to return list of issues instead.
  val atfAnalyzer = VisualLintAtfAnalysis(model)
  return ImmutableList.copyOf(atfAnalyzer.validateAndUpdateLint(renderResult))
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

/**
 * Analyze the given [RenderResult] for issues where a BottomNavigationView is wider than 600dp.
 */
private fun analyzeBottomNavigation(renderResult: RenderResult, sceneManager: LayoutlibSceneManager): List<RenderErrorModel.Issue> {
  val issues = mutableListOf<RenderErrorModel.Issue>()
  for (root in renderResult.rootViews) {
    findBottomNavigationIssue(root, sceneManager, issues)
  }
  return issues
}

private fun findBottomNavigationIssue(root: ViewInfo, sceneManager: LayoutlibSceneManager, issues: MutableList<RenderErrorModel.Issue>) {
  if (root.className == BOTTOM_NAVIGATION_CLASS_NAME) {
    val widthInDp = Coordinates.pxToDp(sceneManager, root.right - root.left)
    if (widthInDp > 600) {
      issues.add(RenderErrorModel.Issue.builder().setSummary(BOTTOM_NAVIGATION_ISSUE_MESSAGE).build())
    }
  }
  for (child in root.children) {
    findBottomNavigationIssue(child, sceneManager, issues)
  }
}

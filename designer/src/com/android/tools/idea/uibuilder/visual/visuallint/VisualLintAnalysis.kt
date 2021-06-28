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

import android.view.View
import android.widget.TextView
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.collect.ImmutableList
import com.intellij.lang.annotation.HighlightSeverity

private const val BOTTOM_NAVIGATION_CLASS_NAME = "com.google.android.material.bottomnavigation.BottomNavigationView"
private const val BOTTOM_NAVIGATION_ISSUE_MESSAGE = "BottomNavigationView should not be used in layouts larger than 600dp"

/**
 * Returns all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
 */
fun analyzeAfterModelUpdate(result: RenderResult, sceneManager: LayoutlibSceneManager): ImmutableList<VisualLintRenderIssue> {
  val issues = mutableListOf<VisualLintRenderIssue>()
  val model = sceneManager.model
  issues.addAll(analyzeBounds(result, model))
  issues.addAll(analyzeBottomNavigation(result, sceneManager))
  issues.addAll(analyzeOverlap(result, model))
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
private fun analyzeBounds(renderResult: RenderResult, model: NlModel): List<VisualLintRenderIssue> {
  val issues = mutableListOf<VisualLintRenderIssue>()
  for (root in renderResult.rootViews) {
    findBoundIssues(root, model, issues)
  }
  return issues
}

private fun findBoundIssues(root: ViewInfo, model: NlModel, issues: MutableList<VisualLintRenderIssue>) {
  val rootWidth = root.right - root.left
  val rootHeight = root.bottom - root.top
  for (child in root.children) {
    // Bounds of children are defined relative to their parent
    if (child.top < 0 || child.bottom > rootHeight || child.left < 0 || child.right > rootWidth) {
      val renderIssue = RenderErrorModel.Issue.builder()
        .setSummary("${simpleName(child)} is not fully visible in layout")
        .setSeverity(HighlightSeverity.WARNING)
        .build()
      val component = componentFromViewInfo(child, model)
      issues.add(VisualLintRenderIssue(renderIssue, model, component))
    }
    findBoundIssues(child, model, issues)
  }
}

/**
 * Analyze the given [RenderResult] for issues where a BottomNavigationView is wider than 600dp.
 */
private fun analyzeBottomNavigation(renderResult: RenderResult, sceneManager: LayoutlibSceneManager): List<VisualLintRenderIssue> {
  val issues = mutableListOf<VisualLintRenderIssue>()
  for (root in renderResult.rootViews) {
    findBottomNavigationIssue(root, sceneManager, issues)
  }
  return issues
}

private fun findBottomNavigationIssue(root: ViewInfo, sceneManager: LayoutlibSceneManager, issues: MutableList<VisualLintRenderIssue>) {
  if (root.className == BOTTOM_NAVIGATION_CLASS_NAME) {
    val widthInDp = Coordinates.pxToDp(sceneManager, root.right - root.left)
    if (widthInDp > 600) {
      val renderIssue = RenderErrorModel.Issue.builder()
        .setSummary(BOTTOM_NAVIGATION_ISSUE_MESSAGE)
        .setSeverity(HighlightSeverity.WARNING)
        .build()
      val model = sceneManager.model
      val component = componentFromViewInfo(root, model)
      issues.add(VisualLintRenderIssue(renderIssue, sceneManager.model, component))
    }
  }
  for (child in root.children) {
    findBottomNavigationIssue(child, sceneManager, issues)
  }
}

/**
 * Analyze the given [RenderResult] for issues where a view is covered by another sibling view, and return all such issues.
 * Limit to covered [TextView] as they are the most likely to be wrongly covered by another view.
 */
private fun analyzeOverlap(renderResult: RenderResult, model: NlModel): List<VisualLintRenderIssue> {
  val issues = mutableListOf<VisualLintRenderIssue>()
  for (root in renderResult.rootViews) {
    findOverlapIssues(root, model, issues)
  }
  return issues
}

private fun findOverlapIssues(root: ViewInfo, model: NlModel, issues: MutableList<VisualLintRenderIssue>) {
  val children = root.children.filter { it.cookie != null && (it.viewObject as? View)?.visibility == View.VISIBLE }
  for (i in children.indices) {
    val firstView = children[i]
    if (firstView.viewObject !is TextView) {
      continue
    }
    for (j in (i + 1) until children.size) {
      val secondView = children[j]
      if (firstView.right <= secondView.left || firstView.left >= secondView.right) {
        continue
      }
      if (firstView.bottom > secondView.top && firstView.top  < secondView.bottom) {
        val renderIssue = RenderErrorModel.Issue.builder()
          .setSummary("${simpleName(firstView)} is covered by ${simpleName(secondView)}")
          .setSeverity(HighlightSeverity.WARNING)
          .build()
        val component = componentFromViewInfo(firstView, model)
        issues.add(VisualLintRenderIssue(renderIssue, model, component))
      }
    }
  }
  for (child in children) {
    findOverlapIssues(child, model, issues)
  }
}

private fun simpleName(view: ViewInfo): String {
  val tagName = (view.cookie as? TagSnapshot)?.tagName ?: view.className
  return tagName.substringAfterLast('.')
}

private fun componentFromViewInfo(viewInfo: ViewInfo, model: NlModel): NlComponent? {
  val tag = (viewInfo.cookie as? TagSnapshot)?.tag ?: return null
  return model.findViewByTag(tag)
}

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
import com.android.tools.idea.uibuilder.lint.createDefaultHyperLinkListener
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkListener

private const val BOTTOM_NAVIGATION_CLASS_NAME = "com.google.android.material.bottomnavigation.BottomNavigationView"
private const val BOTTOM_NAVIGATION_ISSUE_MESSAGE = "BottomNavigationView should not be used in layouts larger than 600dp"
private val BOTTOM_NAVIGATION_ISSUE_CONTENT = HtmlBuilder()
  .add("Material Design recommends that bottom navigation should only be used for displays less than 600dp in width.")
  .newline()
  .add("Consider using a navigation rail or navigation drawer instead for larger screens.")

enum class VisualLintErrorType {
  BOUNDS, BOTTOM_NAV, OVERLAP, LONG_TEXT, ATF, LOCALE_TEXT
}

/**
 * Collects in [issues] all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
 */
fun analyzeAfterModelUpdate(result: RenderResult,
                            sceneManager: LayoutlibSceneManager,
                            issues: VisualLintIssues,
                            baseConfigIssues: VisualLintBaseConfigIssues) {
  // TODO: Remove explicit use of mutable collections as argument for this method
  val model = sceneManager.model
  analyzeBounds(result, model, issues)
  analyzeBottomNavigation(result, sceneManager, issues)
  analyzeOverlap(result, model, issues)
  analyzeLongText(result, model, issues)
  analyzeLocaleText(result, baseConfigIssues, model, issues)
}

/**
 * Collects in [issues] all the [VisualLintAtfIssue] found when analyzing the given [RenderResult] after render is complete.
 */
fun analyzeAfterRenderComplete(renderResult: RenderResult, model: NlModel,
                               issues: VisualLintIssues) {
  val atfAnalyzer = VisualLintAtfAnalysis(model)
  val atfIssues: List<VisualLintAtfIssue> = atfAnalyzer.validateAndUpdateLint(renderResult)
  // TODO: Equals and hashcode might need to change here.
  issues.addAll(VisualLintErrorType.ATF, atfIssues)
}

/**
 * Analyze the given [RenderResult] for issues where a child view is not fully contained within
 * the bounds of its parent, and collect all such issues in [issues].
 */
private fun analyzeBounds(renderResult: RenderResult, model: NlModel, issues: VisualLintIssues) {
  for (root in renderResult.rootViews) {
    findBoundIssues(root, model, issues)
  }
}

private fun findBoundIssues(root: ViewInfo, model: NlModel, issues: VisualLintIssues) {
  val rootWidth = root.right - root.left
  val rootHeight = root.bottom - root.top
  for (child in root.children) {
    // Bounds of children are defined relative to their parent
    if (child.top < 0 || child.bottom > rootHeight || child.left < 0 || child.right > rootWidth) {
      val viewName = simpleName(child)
      val summary = "$viewName is partially hidden in layout"
      val provider = { count: Int ->
        HtmlBuilder()
          .add("$viewName is partially hidden in layout because it is not contained within the bounds of its parent in ${previewConfigurations(count)}.")
          .newline()
          .add("Fix this issue by adjusting the size or position of $viewName.")
      }
      createIssue(child, model, summary, VisualLintErrorType.BOUNDS, issues, provider)
    }
    findBoundIssues(child, model, issues)
  }
}

private fun previewConfigurations(count: Int): String {
  return if (count == 1) "a preview configuration" else "$count preview configurations"
}

/**
 * Analyze the given [RenderResult] for issues where a BottomNavigationView is wider than 600dp.
 */
private fun analyzeBottomNavigation(renderResult: RenderResult,
                                    sceneManager: LayoutlibSceneManager,
                                    issues: VisualLintIssues) {
  for (root in renderResult.rootViews) {
    findBottomNavigationIssue(root, sceneManager, issues)
  }
}

private fun findBottomNavigationIssue(root: ViewInfo,
                                      sceneManager: LayoutlibSceneManager,
                                      issues: VisualLintIssues) {
  if (root.className == BOTTOM_NAVIGATION_CLASS_NAME) {
    val widthInDp = Coordinates.pxToDp(sceneManager, root.right - root.left)
    if (widthInDp > 600) {
      createIssue(
        root,
        sceneManager.model,
        BOTTOM_NAVIGATION_ISSUE_MESSAGE,
        BOTTOM_NAVIGATION_ISSUE_CONTENT,
        VisualLintErrorType.BOTTOM_NAV,
        issues)
    }
  }
  for (child in root.children) {
    findBottomNavigationIssue(child, sceneManager, issues)
  }
}

/**
 * Analyze the given [RenderResult] for issues where a view is covered by another sibling view, and collect all such issues in [issues].
 * Limit to covered [TextView] as they are the most likely to be wrongly covered by another view.
 */
private fun analyzeOverlap(renderResult: RenderResult, model: NlModel, issues: VisualLintIssues) {
  for (root in renderResult.rootViews) {
    findOverlapIssues(root, model, issues)
  }
}

private fun findOverlapIssues(root: ViewInfo, model: NlModel, issues: VisualLintIssues) {
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
      if (firstView.bottom > secondView.top && firstView.top < secondView.bottom) {
        val content = HtmlBuilder().add("The content of ${simpleName(firstView)} is partially hidden.")
          .newline()
          .add("This may pose a problem for the readability of the text it contains.")
        createIssue(firstView, model, "${simpleName(firstView)} is covered by ${simpleName(secondView)}", content,
                    VisualLintErrorType.OVERLAP, issues)
      }
    }
  }
  for (child in children) {
    findOverlapIssues(child, model, issues)
  }
}

/**
 * Analyze the given [RenderResult] for issues where a line of text is longer than 120 characters,
 * and collect all such issues in [issues].
 */
private fun analyzeLongText(renderResult: RenderResult,
                            model: NlModel,
                            issues: VisualLintIssues) {
  for (root in renderResult.rootViews) {
    findLongText(root, model, issues)
  }
}

private fun findLongText(root: ViewInfo, model: NlModel, issues: VisualLintIssues) {
  if (root.viewObject is TextView) {
    val layout = (root.viewObject as TextView).layout
    for (i in 0 until layout.lineCount) {
      val numChars = layout.getLineVisibleEnd(i) - layout.getLineStart(i) + 1
      if (numChars > 120) {
        val viewName = simpleName(root)
        val summary = "$viewName has lines containing more than 120 characters"
        val url = "https://material.io/design/layout/responsive-layout-grid.html#breakpoints"
        val provider = { count: Int ->
          HtmlBuilder()
            .add("$viewName has lines containing more than 120 characters in ${previewConfigurations(count)}.")
            .newline()
            .add("Material Design recommends reducing the width of TextView or switching to a [multi-column layout] ")
            .addLink("($url)", url)
            .add(" for breakpoints over 600dp.")
        }
        createIssue(root, model, summary, VisualLintErrorType.LONG_TEXT, issues, provider, createDefaultHyperLinkListener(url))
        break
      }
    }
  }
  for (child in root.children) {
    findLongText(child, model, issues)
  }
}

/** Create [VisualLintRenderIssue] and add to [issues]. */
fun createIssue(view: ViewInfo,
                model: NlModel,
                message: String,
                contentDescription: HtmlBuilder,
                type: VisualLintErrorType,
                issues: VisualLintIssues) {
  return createIssue(view, model, message, type, issues, { contentDescription })
}

/** Create [VisualLintRenderIssue] and add to [issues]. */
fun createIssue(view: ViewInfo,
                model: NlModel,
                message: String,
                type: VisualLintErrorType,
                issues: VisualLintIssues,
                contentDescriptionProvider: (Int) -> HtmlBuilder,
                hyperlinkListener: HyperlinkListener? = null) {
  val component = componentFromViewInfo(view, model)
  issues.add(
    type,
    VisualLintRenderIssue.builder()
      .summary(message)
      .severity(HighlightSeverity.WARNING)
      .model(model)
      .components(if (component == null) mutableListOf() else mutableListOf(component))
      .contentDescriptionProvider(contentDescriptionProvider)
      .hyperlinkListener(hyperlinkListener)
      .build()
  )
}


private fun simpleName(view: ViewInfo): String {
  val tagName = (view.cookie as? TagSnapshot)?.tagName ?: view.className
  return tagName.substringAfterLast('.')
}

private fun componentFromViewInfo(viewInfo: ViewInfo, model: NlModel): NlComponent? {
  val tag = (viewInfo.cookie as? TagSnapshot)?.tag ?: return null
  return model.findViewByTag(tag)
}

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
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.android.tools.idea.uibuilder.lint.createDefaultHyperLinkListener
import com.android.utils.HtmlBuilder
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateClickableBoundsCheck
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.event.HyperlinkListener

private const val BOTTOM_NAVIGATION_CLASS_NAME = "com.google.android.material.bottomnavigation.BottomNavigationView"
private const val BOTTOM_APP_BAR_CLASS_NAME = "com.google.android.material.bottomappbar.BottomAppBar"
private const val TOP_APP_BAR_URL = "https://material.io/components/app-bars-top/android"
private const val NAVIGATION_RAIL_URL = "https://material.io/components/navigation-rail/android"
private const val NAVIGATION_DRAWER_URL = "https://material.io/components/navigation-drawer/android"

enum class VisualLintErrorType {
  BOUNDS, BOTTOM_NAV, BOTTOM_APP_BAR, OVERLAP, LONG_TEXT, ATF, LOCALE_TEXT
}

/**
 * Collects in [issueProvider] all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
 */
fun analyzeAfterModelUpdate(result: RenderResult,
                            model: NlModel,
                            issueProvider: VisualLintIssueProvider,
                            baseConfigIssues: VisualLintBaseConfigIssues,
                            analyticsManager: VisualLintAnalyticsManager) {
  // TODO: Remove explicit use of mutable collections as argument for this method
  analyzeBounds(result, model, issueProvider, analyticsManager)
  analyzeBottomNavigation(result, model, issueProvider, analyticsManager)
  analyzeBottomAppBar(result, model, issueProvider, analyticsManager)
  analyzeOverlap(result, model, issueProvider, analyticsManager)
  analyzeLongText(result, model, issueProvider, analyticsManager)
  analyzeLocaleText(result, baseConfigIssues, model, issueProvider, analyticsManager)
  if (StudioFlags.NELE_ATF_IN_VISUAL_LINT.get()) {
    analyzeAtf(result, model, issueProvider)
  }
}

/**
 * Analyze the given [RenderResult] for issues related to ATF that overlaps with visual lint.
 * For now, it only runs [DuplicateClickableBoundsCheck] among all other atf checks.
 *
 * To run more checks, update the policy in [VisualLintAtfAnalysis.validateAndUpdateLint]
 */
private fun analyzeAtf(renderResult: RenderResult, model: NlModel,
                               issueProvider: VisualLintIssueProvider) {
  val atfAnalyzer = VisualLintAtfAnalysis(model)
  val atfIssues: List<VisualLintAtfIssue> = atfAnalyzer.validateAndUpdateLint(renderResult)
  // TODO: Equals and hashcode might need to change here.
  issueProvider.addAllIssues(VisualLintErrorType.ATF, atfIssues)
}

/**
 * Analyze the given [RenderResult] for issues where a child view is not fully contained within
 * the bounds of its parent, and collect all such issues in [issueProvider].
 */
private fun analyzeBounds(renderResult: RenderResult,
                          model: NlModel,
                          issueProvider: VisualLintIssueProvider,
                          analyticsManager: VisualLintAnalyticsManager) {
  for (root in renderResult.rootViews) {
    findBoundIssues(root, model, issueProvider, analyticsManager)
  }
}

private fun findBoundIssues(root: ViewInfo,
                            model: NlModel,
                            issueProvider: VisualLintIssueProvider,
                            analyticsManager: VisualLintAnalyticsManager) {
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
      createIssue(child, model, summary, VisualLintErrorType.BOUNDS, issueProvider, provider, analyticsManager)
    }
    findBoundIssues(child, model, issueProvider, analyticsManager)
  }
}

private fun previewConfigurations(count: Int): String {
  return if (count == 1) "a preview configuration" else "$count preview configurations"
}

/**
 * Analyze the given [RenderResult] for issues where a BottomNavigationView is wider than 600dp.
 */
private fun analyzeBottomNavigation(renderResult: RenderResult,
                                    model: NlModel,
                                    issueProvider: VisualLintIssueProvider,
                                    analyticsManager: VisualLintAnalyticsManager) {
  for (root in renderResult.rootViews) {
    findBottomNavigationIssue(root, model, issueProvider, analyticsManager)
  }
}

private fun findBottomNavigationIssue(root: ViewInfo,
                                      model: NlModel,
                                      issueProvider: VisualLintIssueProvider,
                                      analyticsManager: VisualLintAnalyticsManager) {
  if (root.className == BOTTOM_NAVIGATION_CLASS_NAME) {
    /* This is needed, as visual lint analysis need to run outside the context of scene. */
    val widthInDp = Coordinates.pxToDp(model, root.right - root.left)
    if (widthInDp > 600) {
      val content =  { count: Int ->
        HtmlBuilder()
          .add("Bottom navigation bar is not recommended for breakpoints over 600dp, ")
          .add("which affects ${previewConfigurations(count)}.")
          .newline()
          .add("Material Design recommends replacing bottom navigation bar with ")
          .addLink("navigation rail", NAVIGATION_RAIL_URL)
          .add(" or ")
          .addLink("navigation drawer", NAVIGATION_DRAWER_URL)
          .add(" for breakpoints over 600dp.")
      }
      createIssue(
        root,
        model,
        "Bottom navigation bar is not recommended for breakpoints over 600dp",
        VisualLintErrorType.BOTTOM_NAV,
        issueProvider,
        content,
        analyticsManager,
        createDefaultHyperLinkListener())
    }
  }
  for (child in root.children) {
    findBottomNavigationIssue(child, model, issueProvider, analyticsManager)
  }
}

/**
 * Analyze the given [RenderResult] for issues where a BottomAppBar is used on non-compact screens.
 */
private fun analyzeBottomAppBar(renderResult: RenderResult,
                                model: NlModel,
                                issueProvider: VisualLintIssueProvider,
                                analyticsManager: VisualLintAnalyticsManager) {
  val configuration = model.configuration
  val orientation = configuration.deviceState?.orientation ?: return
  val dimension = configuration.device?.getScreenSize(orientation) ?: return
  val width = Coordinates.pxToDp(model, dimension.width)
  val height = Coordinates.pxToDp(model, dimension.height)
  if (width > 600 && height > 360) {
    for (root in renderResult.rootViews) {
      findBottomAppBarIssue(root, model, issueProvider, analyticsManager)
    }
  }
}

private fun findBottomAppBarIssue(root: ViewInfo,
                                  model: NlModel,
                                  issueProvider: VisualLintIssueProvider,
                                  analyticsManager: VisualLintAnalyticsManager) {
  if (root.className == BOTTOM_APP_BAR_CLASS_NAME) {
    val content = { count: Int ->
      HtmlBuilder()
        .add("Bottom app bars are only recommended for compact screens, ")
        .add("which affects ${previewConfigurations(count)}.")
        .newline()
        .add("Material Design recommends replacing bottom app bar with ")
        .addLink("navigation rail", NAVIGATION_RAIL_URL)
        .add(", ")
        .addLink("navigation drawer", NAVIGATION_DRAWER_URL)
        .add(" or ")
        .addLink("top app bar", TOP_APP_BAR_URL)
        .add(" for breakpoints over 600dp.")
    }
    createIssue(
      root,
      model,
      "Bottom app bars are only recommended for compact screens",
      VisualLintErrorType.BOTTOM_APP_BAR,
      issueProvider,
      content,
      analyticsManager,
      createDefaultHyperLinkListener())
  }
  root.children.forEach { findBottomAppBarIssue(it, model, issueProvider, analyticsManager) }
}

/**
 * Analyze the given [RenderResult] for issues where a view is covered by another sibling view,
 * and collect all such issues in [issueProvider].
 * Limit to covered [TextView] as they are the most likely to be wrongly covered by another view.
 */
private fun analyzeOverlap(renderResult: RenderResult,
                           model: NlModel,
                           issueProvider: VisualLintIssueProvider,
                           analyticsManager: VisualLintAnalyticsManager) {
  for (root in renderResult.rootViews) {
    findOverlapIssues(root, model, issueProvider, analyticsManager)
  }
}

private fun findOverlapIssues(root: ViewInfo,
                              model: NlModel,
                              issueProvider: VisualLintIssueProvider,
                              analyticsManager: VisualLintAnalyticsManager) {
  val children = root.children.filter { it.cookie != null && (it.viewObject as? View)?.visibility == View.VISIBLE }
  for (i in children.indices) {
    val firstView = children[i]
    // TODO: Can't create unit test due to this check. Figure out a way around later.
    if (firstView.viewObject !is TextView) {
      continue
    }
    for (j in children.indices) {
      val secondView = children[j]
      if (firstView == secondView) {
        continue
      }
      if (firstView.right <= secondView.left || firstView.left >= secondView.right) {
        continue
      }
      if (firstView.bottom > secondView.top && firstView.top < secondView.bottom) {
        if (isPartiallyHidden(firstView, i, secondView, j, model)) {
          val content = HtmlBuilder().add("The content of ${simpleName(firstView)} is partially hidden.")
            .newline()
            .add("This may pose a problem for the readability of the text it contains.")
          // TODO: Highlight both first and second view in design surface
          createIssue(firstView, model, "${simpleName(firstView)} is covered by ${simpleName(secondView)}",
                      content, VisualLintErrorType.OVERLAP, issueProvider, analyticsManager)
        }
      }
    }
  }
  for (child in children) {
    findOverlapIssues(child, model, issueProvider, analyticsManager)
  }
}

/**
 * Given two view info that overlaps in bounds, and their respective indices in layout,
 * figure out of [firstViewInfo] is being overlapped and partially hidden by [secondViewInfo]
 */
@VisibleForTesting
fun isPartiallyHidden(firstViewInfo: ViewInfo, i: Int, secondViewInfo: ViewInfo, j: Int, model: NlModel): Boolean {

  val comp1 = componentFromViewInfo(firstViewInfo, model)
  val comp2 = componentFromViewInfo(secondViewInfo, model)

  // Try to see if we can compare elevation attribute if it exists.
  if (comp1 != null && comp2 != null) {
    val elev1 = ConstraintComponentUtilities.getDpValue(
      comp1, comp1.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION))
    val elev2 = ConstraintComponentUtilities.getDpValue(
      comp2, comp2.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION))

    if (elev1 < elev2) {
      return true
    } else if (elev1 > elev2) {
      return false
    }
    // If they're the same, leave it to the index to resolve overlapping logic.
  }

  // else rely on index.
  return i < j
}

/**
 * Analyze the given [RenderResult] for issues where a line of text is longer than 120 characters,
 * and collect all such issues in [issueProvider].
 */
private fun analyzeLongText(renderResult: RenderResult,
                            model: NlModel,
                            issueProvider: VisualLintIssueProvider,
                            analyticsManager: VisualLintAnalyticsManager) {
  for (root in renderResult.rootViews) {
    findLongText(root, model, issueProvider, analyticsManager)
  }
}

private fun findLongText(root: ViewInfo, model: NlModel, issueProvider: VisualLintIssueProvider, analyticsManager: VisualLintAnalyticsManager) {
  (root.viewObject as? TextView)?.layout?.let {
    for (i in 0 until it.lineCount) {
      val numChars = it.getLineVisibleEnd(i) - it.getEllipsisCount(i) - it.getLineStart(i) + 1
      if (numChars > 120) {
        val viewName = simpleName(root)
        val summary = "$viewName has lines containing more than 120 characters"
        val url = "https://material.io/design/layout/responsive-layout-grid.html#breakpoints"
        val provider = { count: Int ->
          HtmlBuilder()
            .add("$viewName has lines containing more than 120 characters in ${previewConfigurations(count)}.")
            .newline()
            .add("Material Design recommends reducing the width of TextView or switching to a ")
            .addLink("multi-column layout", url)
            .add(" for breakpoints over 600dp.")
        }
        createIssue(root, model, summary, VisualLintErrorType.LONG_TEXT, issueProvider, provider, analyticsManager,
                    createDefaultHyperLinkListener())
        break
      }
    }
  }
  for (child in root.children) {
    findLongText(child, model, issueProvider, analyticsManager)
  }
}

/** Create [VisualLintRenderIssue] and add to [issueProvider]. */
fun createIssue(view: ViewInfo,
                model: NlModel,
                message: String,
                contentDescription: HtmlBuilder,
                type: VisualLintErrorType,
                issueProvider: VisualLintIssueProvider,
                analyticsManager: VisualLintAnalyticsManager) {
  return createIssue(view, model, message, type, issueProvider, { contentDescription }, analyticsManager)
}

/** Create [VisualLintRenderIssue] and add to [issueProvider]. */
fun createIssue(view: ViewInfo,
                model: NlModel,
                message: String,
                type: VisualLintErrorType,
                issueProvider: VisualLintIssueProvider,
                contentDescriptionProvider: (Int) -> HtmlBuilder,
                analyticsManager: VisualLintAnalyticsManager,
                hyperlinkListener: HyperlinkListener? = null) {
  val component = componentFromViewInfo(view, model)
  analyticsManager.trackIssueCreation(type)
  issueProvider.addIssue(
    type,
    VisualLintRenderIssue.builder()
      .summary(message)
      .severity(HighlightSeverity.WARNING)
      .model(model)
      .components(if (component == null) mutableListOf() else mutableListOf(component))
      .contentDescriptionProvider(contentDescriptionProvider)
      .hyperlinkListener(hyperlinkListener)
      .type(type)
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

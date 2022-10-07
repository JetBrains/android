/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.navigation

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.navigation.PreviewNavigation.LOG
import com.android.tools.idea.preview.navigation.DefaultNavigationHandler
import com.android.tools.idea.uibuilder.model.viewInfo
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.core.util.getLineStartOffset

private object PreviewNavigation {
  val LOG = Logger.getInstance(PreviewNavigation::class.java)
}

/**
 * Converts a [SourceLocation] into a [Navigatable]. If the [SourceLocation] does not point to a
 * file within the project, it is not possible to create a [Navigatable] and the method will return
 * null.
 */
private fun SourceLocation.toNavigatable(module: Module): Navigatable? {
  val project = module.project
  val sourceLocationWithVirtualFile =
    if (this is SourceLocationWithVirtualFile) this
    else this.asSourceLocationWithVirtualFile(module) ?: return null
  val psiFile =
    PsiManager.getInstance(project).findFile(sourceLocationWithVirtualFile.virtualFile)
      ?: return null
  return PsiNavigationSupport.getInstance()
    .createNavigatable(
      project,
      sourceLocationWithVirtualFile.virtualFile,
      // PsiFile.getLineStartOffset is 0 based, while the source information is 1 based so subtract
      // 1
      psiFile.getLineStartOffset(sourceLocationWithVirtualFile.lineNumber - 1) ?: 0
    )
}

/** Utility method that dumps the given [ComposeViewInfo] list to the log. */
private fun dumpViewInfosToLog(module: Module, viewInfos: List<ComposeViewInfo>, indent: Int = 0) {
  val margin = "-".repeat(indent)
  viewInfos.forEach {
    LOG.debug("$margin $it navigatable=${it.sourceLocation.toNavigatable(module)}")
    dumpViewInfosToLog(module, it.children, indent + 1)
  }
}

/**
 * Returns a list of [SourceLocation]s that references to the source code position of the Composable
 * at the given x, y pixel coordinates. The list is sorted with the elements deeper in the hierarchy
 * at the top.
 */
@VisibleForTesting
fun findComponentHits(
  module: Module,
  rootViewInfo: ViewInfo,
  @AndroidCoordinate x: Int,
  @AndroidCoordinate y: Int
): List<SourceLocation> {
  val allViewInfos =
    parseViewInfo(rootViewInfo = rootViewInfo, lineNumberMapper = remapInline(module), logger = LOG)

  if (LOG.isDebugEnabled) {
    dumpViewInfosToLog(module, allViewInfos)
  }

  return allViewInfos
    .findHitWithDepth(x, y)
    // We do not need to keep hits without source information
    .filter { it.second.sourceLocation.lineNumber >= 0 }
    // Sort by the hit depth. Elements lower in the hierarchy, are at the top. If they are the same
    // level, order by line number
    .sortedWith(
      compareByDescending<Pair<Int, ComposeViewInfo>> { it.first }.thenByDescending {
        it.second.sourceLocation.lineNumber
      }
    )
    .map { it.second.sourceLocation }
}

/**
 * Returns a [Navigatable] that references to the source code position of the Composable at the
 * given x, y pixel coordinates. An optional [locationFilter] can be passed to select a certain type
 * of hit, for example, filtering by filename.
 */
fun findNavigatableComponentHit(
  module: Module,
  rootViewInfo: ViewInfo,
  @AndroidCoordinate x: Int,
  @AndroidCoordinate y: Int,
  locationFilter: (SourceLocation) -> Boolean = { true }
): Navigatable? {
  val hits = findComponentHits(module, rootViewInfo, x, y).filter(locationFilter)

  if (LOG.isDebugEnabled) {
    LOG.debug("${hits.size} hits found in")
    hits.filter { it.toNavigatable(module) != null }.forEach {
      LOG.debug("  Navigatable hit: ${it}")
    }
  }

  return hits.mapNotNull { runReadAction { it.toNavigatable(module) } }.firstOrNull()
}

private fun findNavigatableComponent(
  sceneView: SceneView,
  @SwingCoordinate hitX: Int,
  @SwingCoordinate hitY: Int,
  requestFocus: Boolean,
  fileName: String
): Navigatable? {
  val x = Coordinates.getAndroidX(sceneView, hitX)
  val y = Coordinates.getAndroidY(sceneView, hitY)
  LOG.debug { "handleNavigate x=$x, y=$y" }

  val model = sceneView.sceneManager.model

  // Find component to navigate to
  val root = model.components[0]
  val viewInfo = root.viewInfo ?: return null
  return findNavigatableComponentHit(model.module, viewInfo, x, y) {
    // We apply a filter to the hits. If requestFocus is true (the user double clicked), we allow
    // any hit even if it's not in the current
    // file. If requestFocus is false, we only allow single clicks
    requestFocus || it.fileName == fileName
  }
}

/** Handles navigation for compose preview when NlDesignSurface preview is clicked. */
class ComposePreviewNavigationHandler : DefaultNavigationHandler(::findNavigatableComponent)

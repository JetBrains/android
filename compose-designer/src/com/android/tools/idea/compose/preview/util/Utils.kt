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
package com.android.tools.idea.compose.preview.util

import com.android.resources.ScreenOrientation
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.compose.isValidPreviewLocation
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.ComposeViewInfo
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.findSmallestHit
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.projectsystem.isTestFile
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.util.isAndroidModule
import com.android.tools.idea.util.isCommonWithAndroidModule
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Segment
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.awt.Dimension
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

fun Segment?.containsOffset(offset: Int) =
  this?.let { it.startOffset <= offset && offset <= it.endOffset } ?: false

/**
 * For a SceneView that contains a valid Compose Preview, get its root component, that must be a
 * ComposeViewAdapter.
 */
fun SceneView.getRootComponent(): NlComponent? {
  val root = sceneManager.model.treeReader.components.firstOrNull()
  assert(root == null || root.tagName == COMPOSE_VIEW_ADAPTER_FQN) {
    "Expected the root component of a Compose Preview to be a $COMPOSE_VIEW_ADAPTER_FQN, but found ${root!!.tagName}"
  }
  return root
}

fun SceneView.getSmallestViewInfos(x: Int, y: Int, logger: Logger): Collection<ComposeViewInfo>? {
  val androidX = Coordinates.getAndroidX(this, x)
  val androidY = Coordinates.getAndroidY(this, y)
  val deepestViewInfos =
    this.scene.root
      ?.nlComponent
      ?.viewInfo
      ?.let { viewInfo -> parseViewInfo(viewInfo, logger) }
      ?.findSmallestHit(androidX, androidY)

  return deepestViewInfos
}

/** Returns true if the ComposeViewAdapter component of this SceneView is currently selected. */
fun SceneView.isRootComponentSelected() =
  getRootComponent()?.let { surface.selectionModel.isSelected(it) } == true

/**
 * Whether fast preview is available. In addition to checking its normal availability from
 * [FastPreviewManager], we also verify that essentials mode is not enabled, because fast preview
 * should not be available in this case.
 */
fun isFastPreviewAvailable(project: Project) =
  FastPreviewManager.getInstance(project).isAvailable &&
    !PreviewEssentialsModeManager.isEssentialsModeEnabled

fun DataContext.previewElement(): PsiComposePreviewElementInstance? =
  getData(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE)

fun NlDataProvider.previewElement(): PsiComposePreviewElementInstance? =
  getData(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE)

/**
 * Whether this function is not in a test file and is properly annotated with
 * [COMPOSE_PREVIEW_ANNOTATION_FQN], and validating the location of Previews.
 *
 * @see [isValidPreviewLocation]
 */
@RequiresReadLock
internal fun KtNamedFunction.isValidComposePreviewForRunConfiguration() =
  !isInTestFile() &&
    isInAndroidOrCommonModule() &&
    isValidPreviewLocation() &&
    annotationEntries.any { annotation ->
      (annotation.toUElement() as? UAnnotation)?.isPreviewAnnotation() == true
    }

private fun KtNamedFunction.isInAndroidOrCommonModule(): Boolean {
  val module = ModuleUtilCore.findModuleForFile(containingFile) ?: return false
  return module.isAndroidModule() || module.isCommonWithAndroidModule()
}

private fun KtNamedFunction.isInTestFile() =
  isTestFile(this.project, this.containingFile.virtualFile)

private fun calculateDimensions(x: Int, y: Int, mScreenOrientation: ScreenOrientation?): Dimension {
  // Determine if the desired orientation needs a swap.
  val shouldSwapDimensions = (x > y) != (mScreenOrientation == ScreenOrientation.LANDSCAPE)

  return if (shouldSwapDimensions) {
    Dimension(y, x)
  } else {
    Dimension(x, y)
  }
}

fun Configuration.deviceSize(): Dimension {
  val deviceState = deviceState ?: return Dimension(0, 0)
  val orientation = deviceState.orientation
  val x = deviceState.hardware.screen.xDimension
  val y = deviceState.hardware.screen.yDimension
  return calculateDimensions(x, y, orientation)
}

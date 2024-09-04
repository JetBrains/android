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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiFile
import org.jetbrains.android.AndroidTestCase

internal object EmptyModelsProvider : VisualizationModelsProvider {
  override fun createNlModels(
    parentDisposable: Disposable,
    file: PsiFile,
    buildTarget: AndroidBuildTargetReference,
  ): List<NlModel> = emptyList()
}

class VisualizationActionManagerTest : AndroidTestCase() {

  fun testPopupMenuActions() {
    val actionManager =
      VisualizationActionManager(NlSurfaceBuilder.build(project, testRootDisposable)) {
        EmptyModelsProvider
      }
    val popupMenuGroup = actionManager.getPopupMenuActions(null)
    val actions = popupMenuGroup.getChildren(ActionManager.getInstance())
    assertTrue(actions[0] is ZoomInAction)
    assertTrue(actions[1] is ZoomOutAction)
    assertTrue(actions[2] is ZoomToFitAction)
  }

  fun testToolbarActions() {
    val actionManager =
      VisualizationActionManager(NlSurfaceBuilder.build(project, testRootDisposable)) {
        EmptyModelsProvider
      }
    assertEquals(0, actionManager.getToolbarActions(emptyList()).childrenCount)
  }
}

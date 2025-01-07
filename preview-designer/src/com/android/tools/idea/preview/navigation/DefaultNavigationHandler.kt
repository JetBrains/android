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
package com.android.tools.idea.preview.navigation

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import java.util.WeakHashMap
import kotlinx.coroutines.withContext

/**
 * Navigation handler that defaults navigation from a [SceneView] to a particular predefined (via
 * [setDefaultLocation]) position in a file. However, it prioritizes the result of
 * [componentNavigationDelegate] if that find a better match (usually used for subcomponents
 * navigation).
 */
open class DefaultNavigationHandler(
  private val componentNavigationDelegate:
    (sceneView: SceneView, hitX: Int, hitY: Int, requestFocus: Boolean, fileName: String) -> List<
        Navigatable?
      >
) : PreviewNavigationHandler {
  private val LOG = Logger.getInstance(DefaultNavigationHandler::class.java)
  // Default location to use when components are not found
  @VisibleForTesting val defaultNavigationMap = WeakHashMap<NlModel, Pair<String, Navigatable>>()

  /** Add default navigation location for model. */
  override fun setDefaultLocation(model: NlModel, psiFile: PsiFile, offset: Int) {
    LOG.debug { "Default location set to ${psiFile.name}:$offset" }
    defaultNavigationMap[model] =
      psiFile.name to
        PsiNavigationSupport.getInstance()
          .createNavigatable(model.project, psiFile.virtualFile!!, offset)
  }

  override suspend fun handleNavigate(sceneView: SceneView, requestFocus: Boolean): Boolean {
    return (defaultNavigationMap[sceneView.sceneManager.model]?.second?.apply {
        withContext(uiThread) { navigate(requestFocus) }
      } != null)
      .also { LOG.debug { "Navigated to default? $it" } }
  }

  override suspend fun findNavigatablesWithCoordinates(
    sceneView: SceneView,
    @SwingCoordinate hitX: Int,
    @SwingCoordinate hitY: Int,
    requestFocus: Boolean,
  ): List<Navigatable?> {
    val fileName = defaultNavigationMap[sceneView.sceneManager.model]?.first ?: ""
    return componentNavigationDelegate(sceneView, hitX, hitY, requestFocus, fileName)
  }

  override suspend fun navigateTo(
    sceneView: SceneView,
    navigatable: Navigatable,
    requestFocus: Boolean,
  ): Boolean {
    val fileName = defaultNavigationMap[sceneView.sceneManager.model]?.first ?: ""
    withContext(uiThread) { navigatable.navigate(requestFocus) }
    return true
  }

  override fun dispose() {
    defaultNavigationMap.clear()
  }
}

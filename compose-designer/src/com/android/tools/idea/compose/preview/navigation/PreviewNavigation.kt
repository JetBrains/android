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

import com.android.SdkConstants
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.dimensionToString
import com.android.tools.idea.uibuilder.model.createChild
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ThrowableRunnable


/**
 * Handles navigation for compose preview when NlDesignSurface preview is clicked.
 */
class PreviewNavigationHandler : NlDesignSurface.NavigationHandler {

  // Default location to use when components are not found
  private val defaultNavigationMap = HashMap<NlModel, Navigatable>()

  // Component level navigation info
  private val componentNavigationMap = HashMap<NlComponent, Navigatable>()

  // List of supported files
  private val files: MutableSet<VirtualFile> = mutableSetOf()

  /**
   * Add default navigation location for model.
   */
  fun addDefaultLocation(model: NlModel, navigatable: Navigatable, file: VirtualFile) {
    defaultNavigationMap[model] = navigatable
    files.add(file)
  }

  /**
   * Add component level navigation location.
   */
  fun addComponentLocation(component: NlComponent, navigatable: Navigatable)  {
    componentNavigationMap[component] = navigatable
  }

  /**
   * Get virtual file based on the name.
   */
  fun getVirtualFile(name: String): VirtualFile? {
    val file = files.find { it.name == name } ?: return null
    return if (file.isValid) file else null
  }

  override fun handleNavigate(sceneView: SceneView,
                              models: ImmutableList<NlModel>,
                              requestFocus: Boolean,
                              component: NlComponent?) {
    val navigateTo = componentNavigationMap[component]
    navigateTo?.navigate(requestFocus) ?: navigateToDefault(sceneView, models, requestFocus)
  }

  override fun isFileHandled(filename: String?): Boolean {
    return filename != null && files.any { it.name == filename }
  }

  private fun navigateToDefault(sceneView: SceneView, models: ImmutableList<NlModel>, requestFocus: Boolean) {
    for (model in models) {
      if (sceneView.model == model) {
        val navigatable = defaultNavigationMap[model] ?: return
        navigatable.navigate(requestFocus)
        return
      }
    }
  }

  override fun dispose() {
    defaultNavigationMap.clear()
    componentNavigationMap.clear()
    files.clear()
  }
}

class PreviewModelUpdater(val surface: NlDesignSurface) : DefaultModelUpdater() {
  override fun update(model: NlModel, newRoot: XmlTag?, roots: MutableList<NlModel.TagSnapshotTreeNode>) {

    // TODO: Perhaps there's a better place to register the listener. It must be after
    // the scene manager is set though.
    surface.sceneManager!!.addRenderListener(object : RenderListener {
      override fun onRenderCompleted() {
        // Make sure you de-register after.
        surface.sceneManager!!.removeRenderListener(this)

        if (model.components.isEmpty()) {
          return
        }

        val root = model.components[0]
        val viewInfo = root.viewInfo
        val viewObject = viewInfo!!.viewObject
        val composeViewInfo = parseViewInfo(viewObject,
                                            isFileHandled = {
                                              surface.navigationHandler?.isFileHandled(
                                                it) ?: false
                                            })

        if (composeViewInfo.isEmpty()) {
          return
        }

        createSceneGraph(composeViewInfo, root, surface)
        surface.sceneManager!!.update()

      }
    })

    super.update(model, newRoot, roots)
  }
}

private fun createSceneGraph(list: List<ComposeViewInfo>, parent: NlComponent, surface: NlDesignSurface) {

  if (!ApplicationManager.getApplication().isWriteAccessAllowed) {
    WriteAction.run(ThrowableRunnable<RuntimeException> {
      createSceneGraph(list, parent, surface)
    })
    return
  }

  for (viewInfo in list) {
    // If the file is not found, it'll be goto the default location by handler. Skip.
    val navigationHandler = surface.navigationHandler as PreviewNavigationHandler
    val file = navigationHandler.getVirtualFile(viewInfo.sourceLocation.fileName) ?: continue

    // TODO1: Apparently ALL views (View, FrameLayout etc) are draggable (resizable too) due to their singleton hnadlers.
    //        Might need to send custom info to disable adding targets.
    // TODO2: Sometimes bounds are wrongly placed (stack returned from Compose is matched with the wrong model.
    val bounds = viewInfo.bounds
    val child = parent.createChild(
      "View",
      false,
      null,
      null,
      surface,
      null,
      InsertType.CREATE)!!.apply {

      setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH,
                   dimensionToString(bounds.width.toInt()))
      setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT,
                   dimensionToString(bounds.height.toInt()))
      setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                   dimensionToString(bounds.top.toInt()))
      setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                   dimensionToString(bounds.left.toInt()))

      x = bounds.left.toInt()
      y = bounds.top.toInt()
      w = bounds.width.toInt()
      h = bounds.height.toInt()
    }

    val navigable: Navigatable = PsiNavigationSupport.getInstance().createNavigatable(surface.project, file,
                                                                                      viewInfo.sourceLocation.lineNumber)
    navigationHandler.addComponentLocation(child, navigable)
    parent.addChild(child)
  }
}
